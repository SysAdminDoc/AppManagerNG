# SPDX-License-Identifier: GPL-3.0-or-later

param(
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string] $Output = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $Root "app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json"
}

$apiLevels = @{
    "LOLLIPOP" = 21
    "LOLLIPOP_MR1" = 22
    "M" = 23
    "N" = 24
    "N_MR1" = 25
    "O" = 26
    "O_MR1" = 27
    "P" = 28
    "Q" = 29
    "R" = 30
    "S" = 31
    "S_V2" = 32
    "TIRAMISU" = 33
    "UPSIDE_DOWN_CAKE" = 34
    "VANILLA_ICE_CREAM" = 35
    "BAKLAVA" = 36
}

function Remove-JavaComments {
    param([string] $Text)
    $withoutBlocks = [regex]::Replace($Text, "(?s)/\*.*?\*/", "")
    return [regex]::Replace($withoutBlocks, "(?m)//.*$", "")
}

function Get-ApiLevel {
    param([string] $Annotation)
    if ($Annotation -match "VERSION_CODES\.([A-Z0-9_]+)") {
        $name = $Matches[1]
        if ($apiLevels.ContainsKey($name)) {
            return $apiLevels[$name]
        }
    }
    if ($Annotation -match "RequiresApi\((?:api\s*=\s*)?(\d+)\)") {
        return [int] $Matches[1]
    }
    return 1
}

function Split-ParameterTypes {
    param([string] $Parameters)
    $parameters = $Parameters.Trim()
    if ($parameters.Length -eq 0) {
        return @()
    }
    $items = New-Object System.Collections.Generic.List[string]
    $depth = 0
    $start = 0
    for ($i = 0; $i -lt $parameters.Length; ++$i) {
        $ch = $parameters[$i]
        if ($ch -eq "<") {
            ++$depth
        } elseif ($ch -eq ">") {
            if ($depth -gt 0) { --$depth }
        } elseif ($ch -eq "," -and $depth -eq 0) {
            $items.Add($parameters.Substring($start, $i - $start).Trim())
            $start = $i + 1
        }
    }
    $items.Add($parameters.Substring($start).Trim())

    $types = New-Object System.Collections.Generic.List[string]
    foreach ($item in $items) {
        $type = $item -replace "@[A-Za-z0-9_.$]+(?:\([^)]*\))?\s*", ""
        $type = $type -replace "\bfinal\s+", ""
        $type = $type -replace "\s+", " "
        $type = $type.Trim()
        if ($type -match "(.+)\s+[A-Za-z_][A-Za-z0-9_]*$") {
            $type = $Matches[1].Trim()
        }
        if ($type.Length -gt 0) {
            $types.Add($type)
        }
    }
    return $types.ToArray()
}

function Get-RuntimeName {
    param(
        [string] $PackageName,
        [string] $SimpleName,
        [string] $RefineAs,
        [string] $ParentRuntimeName
    )
    if (-not [string]::IsNullOrWhiteSpace($ParentRuntimeName)) {
        return "$ParentRuntimeName`$$SimpleName"
    }
    if (-not [string]::IsNullOrWhiteSpace($RefineAs)) {
        if ($RefineAs.Contains(".")) {
            return $RefineAs
        }
        return "$PackageName.$RefineAs"
    }
    if ($SimpleName.EndsWith("Hidden")) {
        return "$PackageName.$($SimpleName.Substring(0, $SimpleName.Length - 6))"
    }
    return "$PackageName.$SimpleName"
}

function Get-RelativePathCompat {
    param(
        [string] $BasePath,
        [string] $FullPath
    )
    $base = (Resolve-Path -LiteralPath $BasePath).Path.TrimEnd("\", "/") + "\"
    $full = (Resolve-Path -LiteralPath $FullPath).Path
    if ($full.StartsWith($base, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($base.Length)
    }
    return $full
}

$hiddenApiRoot = Join-Path $Root "hiddenapi/src/main/java"
$classes = New-Object System.Collections.Generic.List[object]

Get-ChildItem -Path $hiddenApiRoot -Recurse -Filter "*.java" |
    Sort-Object FullName |
    ForEach-Object {
        $file = $_
        $relative = (Get-RelativePathCompat $Root $file.FullName).Replace("\", "/")
        if ($relative -eq "hiddenapi/src/main/java/misc/utils/HiddenUtil.java") {
            return
        }

        $text = Get-Content -Raw -LiteralPath $file.FullName
        if ($text -notmatch "(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*;") {
            return
        }
        $packageName = $Matches[1]
        if ($packageName -eq "android.annotation") {
            return
        }

        $cleanText = Remove-JavaComments $text
        $lines = $cleanText -split "\r?\n"
        $classStack = New-Object System.Collections.Generic.List[object]
        $byName = @{}
        $braceDepth = 0
        $pendingMinSdk = 1
        $pendingDeprecated = $false
        $pendingRefineAs = $null
        $buffer = ""

        foreach ($rawLine in $lines) {
            $line = $rawLine.Trim()
            if ($line.Length -eq 0) {
                continue
            }
            if ($line -match "^@RequiresApi") {
                $pendingMinSdk = Get-ApiLevel $line
                continue
            }
            if ($line -match "^@Deprecated\b") {
                $pendingDeprecated = $true
                continue
            }
            if ($line -match "^@RefineAs\(([A-Za-z0-9_.]+)\.class\)") {
                $pendingRefineAs = $Matches[1]
                continue
            }
            if ($line.StartsWith("@")) {
                continue
            }

            $buffer = ($buffer + " " + $line).Trim()
            if ($line -notmatch "[;{}]") {
                continue
            }

            $statement = ($buffer -replace "\s+", " ").Trim()
            $buffer = ""

            while ($classStack.Count -gt 0 -and $classStack[$classStack.Count - 1].Depth -gt $braceDepth) {
                $classStack.RemoveAt($classStack.Count - 1)
            }

            if ($statement -match "\b(class|interface|@interface|enum)\s+([A-Za-z_][A-Za-z0-9_]*)") {
                $simpleName = $Matches[2]
                $parent = if ($classStack.Count -gt 0) { $classStack[$classStack.Count - 1] } else { $null }
                $stubName = if ($null -ne $parent) { "$($parent.Stub)`$$simpleName" } else { "$packageName.$simpleName" }
                $runtimeName = Get-RuntimeName $packageName $simpleName $pendingRefineAs $(if ($null -ne $parent) { $parent.Runtime } else { $null })
                $entry = [ordered]@{
                    sourceFile = $relative
                    stub = $stubName
                    runtime = $runtimeName
                    minSdk = [Math]::Max($(if ($null -ne $parent) { $parent.MinSdk } else { 1 }), $pendingMinSdk)
                    members = New-Object System.Collections.Generic.List[object]
                }
                $classes.Add($entry)
                $byName[$stubName] = $entry
                $classDepth = $braceDepth + (($statement.ToCharArray() | Where-Object { $_ -eq "{" }).Count)
                if ($classDepth -le $braceDepth) {
                    $classDepth = $braceDepth + 1
                }
                $classStack.Add([pscustomobject]@{
                    Stub = $stubName
                    Runtime = $runtimeName
                    Depth = $classDepth
                    MinSdk = $entry.minSdk
                    SimpleName = $simpleName
                })
                $pendingMinSdk = 1
                $pendingDeprecated = $false
                $pendingRefineAs = $null
            } elseif ($classStack.Count -gt 0 -and $braceDepth -eq $classStack[$classStack.Count - 1].Depth) {
                $current = $classStack[$classStack.Count - 1]
                $entry = $byName[$current.Stub]
                $memberMinSdk = [Math]::Max($current.MinSdk, $pendingMinSdk)
                if ($statement -match "\(" -and $statement -match "\)") {
                    if ($statement -notmatch "^(if|for|while|switch|catch)\b") {
                        $methodName = $null
                        if ($statement -match "([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)") {
                            $methodName = $Matches[1]
                            $parameters = @(Split-ParameterTypes $Matches[2])
                        }
                        if (-not [string]::IsNullOrWhiteSpace($methodName) -and $methodName -ne $current.SimpleName) {
                            $entry.members.Add([ordered]@{
                                kind = "method"
                                name = $methodName
                                parameterCount = $parameters.Count
                                parameters = $parameters
                                minSdk = $memberMinSdk
                                deprecated = $pendingDeprecated
                            })
                        }
                    }
                } elseif ($statement -match "(?:^| )(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?(?:/\*final\*/\s+)?[A-Za-z0-9_.$<>\[\]?]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*(=|;)") {
                    $fieldName = $Matches[1]
                    $entry.members.Add([ordered]@{
                        kind = "field"
                        name = $fieldName
                        minSdk = $memberMinSdk
                        deprecated = $pendingDeprecated
                    })
                }
                $pendingMinSdk = 1
                $pendingDeprecated = $false
                $pendingRefineAs = $null
            }

            $open = ($statement.ToCharArray() | Where-Object { $_ -eq "{" }).Count
            $close = ($statement.ToCharArray() | Where-Object { $_ -eq "}" }).Count
            $braceDepth += $open - $close
            while ($classStack.Count -gt 0 -and $classStack[$classStack.Count - 1].Depth -gt $braceDepth) {
                $classStack.RemoveAt($classStack.Count - 1)
            }
        }
    }

$payload = [ordered]@{
    schema = 1
    generatedBy = "scripts/generate-hidden-api-baseline.ps1"
    generatedOn = (Get-Date -Format "yyyy-MM-dd")
    sourceRoot = "hiddenapi/src/main/java"
    classes = $classes
}

$outDir = Split-Path -Parent $Output
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$json = $payload | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $Output -Value $json -Encoding utf8
Write-Host "Wrote $($classes.Count) hidden API class descriptors to $Output"
