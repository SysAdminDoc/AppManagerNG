# SPDX-License-Identifier: GPL-3.0-or-later

[CmdletBinding()]
param(
    [string] $GradleCmd = ".\gradlew.bat",
    [string] $PythonCmd = "python",
    [string] $OutDir = "build\reproducible-release"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Resolve-Path (Join-Path $scriptDir "..")
Set-Location $rootDir

$apkRoot = "app\build\outputs\apk"
$firstDir = Join-Path $OutDir "first"
$secondDir = Join-Path $OutDir "second"
$publishDir = Join-Path $OutDir "publish"
$assetList = Join-Path $OutDir "release-assets.txt"
$combinedSha = Join-Path $OutDir "sha256.txt"
$serverJarReport = Join-Path $OutDir "server-jars.txt"

if (Test-Path $OutDir) {
    Remove-Item -LiteralPath $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $firstDir, $secondDir, $publishDir | Out-Null

function Set-BuildTimeSource {
    $sourceDateEpoch = [Environment]::GetEnvironmentVariable("SOURCE_DATE_EPOCH", "Process")
    if ($sourceDateEpoch -match '^\d+$') {
        Write-Host "Build timestamp source: SOURCE_DATE_EPOCH=$sourceDateEpoch"
        return
    }

    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($null -ne $git) {
        $commitSeconds = (& $git.Source show --no-patch --format=%ct HEAD 2>$null).Trim()
        if ($LASTEXITCODE -eq 0 -and $commitSeconds -match '^\d+$') {
            [Environment]::SetEnvironmentVariable("SOURCE_DATE_EPOCH", $commitSeconds, "Process")
            Write-Host "Build timestamp source: Git HEAD commit time ($commitSeconds)"
            return
        }
    }

    Write-Warning "Build timestamp source: none; release Gradle tasks will fail closed."
}

Set-BuildTimeSource

function Copy-ServerJars {
    param(
        [string] $SourceRoot,
        [string] $DestinationDir
    )

    New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null
    foreach ($name in @("am.jar", "main.jar")) {
        $source = Join-Path $SourceRoot "app\src\main\assets\$name"
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Missing generated server jar $source"
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $DestinationDir $name) -Force
    }
}

function Get-ReleaseApks {
    $apks = @(Get-ChildItem -LiteralPath $apkRoot -Recurse -Filter "*.apk" -File |
        Where-Object { $_.DirectoryName -match '[\\/]release$' } |
        Sort-Object Name)
    if ($apks.Count -eq 0) {
        throw "No release APKs were produced under $apkRoot"
    }
    $duplicates = @($apks | Group-Object Name | Where-Object { $_.Count -gt 1 })
    if ($duplicates.Count -gt 0) {
        $details = $duplicates | ForEach-Object {
            "$($_.Name): $((($_.Group | Select-Object -ExpandProperty FullName) -join ', '))"
        }
        throw "Release APK basenames are not unique: $($details -join '; ')"
    }
    return $apks
}

function Get-PublishApkName {
    param([string] $Name)

    $variant = $Name
    if ($variant.StartsWith("app-")) {
        $variant = $variant.Substring(4)
    }
    if ($variant.EndsWith(".apk")) {
        $variant = $variant.Substring(0, $variant.Length - 4)
    }
    return "AppManagerNG-reproducible-$variant.apk"
}

function Invoke-ReproducibleBuild {
    param(
        [string] $Label,
        [string] $DestinationDir
    )

    Write-Host "Clean build $Label"
    # --no-build-cache: `clean` empties the project's build directory but not Gradle's build
    # cache, so without this the second build restores task outputs produced by the first one.
    # That makes the two builds dependent and the comparison meaningless.
    & $GradleCmd --no-daemon --no-build-cache --stacktrace clean ':app:assembleRelease' 2>&1 | ForEach-Object {
        Write-Host $_
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Gradle release build failed during $Label build."
    }

    $apks = Get-ReleaseApks
    foreach ($apk in $apks) {
        Copy-Item -LiteralPath $apk.FullName -Destination (Join-Path $DestinationDir $apk.Name) -Force
    }
    Copy-ServerJars -SourceRoot (Get-Location) -DestinationDir (Join-Path $DestinationDir "server-jars")

    $hashLines = @()
    foreach ($apk in @(Get-ChildItem -LiteralPath $DestinationDir -Filter "*.apk" -File | Sort-Object Name)) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk.FullName).Hash.ToLowerInvariant()
        $hashLines += "$hash  $($apk.Name)"
    }
    Set-Content -Path (Join-Path $OutDir "$Label.sha256") -Value $hashLines -Encoding ascii
}

function Invoke-NativePageAlignmentCheck {
    param([string] $ApkPath)

    $python = Get-Command $PythonCmd -ErrorAction SilentlyContinue
    if ($null -eq $python) {
        throw "Python command '$PythonCmd' was not found; cannot verify native 16 KB page alignment."
    }
    & $python.Source "scripts\verify-native-page-alignment.py" $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "Native 16 KB page-alignment verification failed for $ApkPath"
    }
}

function Invoke-ReleaseSbomGeneration {
    param([string] $SbomPath)

    $python = Get-Command $PythonCmd -ErrorAction SilentlyContinue
    if ($null -eq $python) {
        throw "Python command '$PythonCmd' was not found; cannot generate the release SBOM."
    }
    & $python.Source "scripts\generate-cyclonedx-sbom.py" --output $SbomPath
    if ($LASTEXITCODE -ne 0) {
        throw "CycloneDX SBOM generation failed for $SbomPath"
    }
    & $python.Source "scripts\generate-cyclonedx-sbom.py" --check $SbomPath
    if ($LASTEXITCODE -ne 0) {
        throw "CycloneDX SBOM validation failed for $SbomPath"
    }
}

function Invoke-CrossEnvironmentServerJarCheck {
    $status = @(git status --porcelain --untracked-files=no)
    if ($status.Count -ne 0) {
        throw "Cross-environment server-jar verification requires a clean Git checkout."
    }

    $worktreeParent = Join-Path ([IO.Path]::GetTempPath()) ("AppManagerNG-jar-repro-" + [Guid]::NewGuid().ToString("N"))
    $worktreeDir = Join-Path $worktreeParent "source"
    $crossDir = Join-Path $OutDir "server-jars\different-environment"
    New-Item -ItemType Directory -Force -Path $worktreeParent, $crossDir | Out-Null
    $worktreeAdded = $false
    $environmentNames = @("TZ", "LC_ALL", "LANG", "USER", "USERNAME", "LOGNAME")
    $oldEnvironment = @{}

    try {
        & git worktree add --detach $worktreeDir HEAD | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create the cross-environment Git worktree."
        }
        $worktreeAdded = $true
        if (Test-Path -LiteralPath "local.properties" -PathType Leaf) {
            Copy-Item -LiteralPath "local.properties" -Destination (Join-Path $worktreeDir "local.properties") -Force
        }

        foreach ($name in $environmentNames) {
            $oldEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            [Environment]::SetEnvironmentVariable($name, "jar-repro-builder", "Process")
        }
        [Environment]::SetEnvironmentVariable("TZ", "Pacific/Auckland", "Process")
        [Environment]::SetEnvironmentVariable("LC_ALL", "C", "Process")
        [Environment]::SetEnvironmentVariable("LANG", "C", "Process")

        Write-Host "=== Cross-environment server-jar build ==="
        Push-Location $worktreeDir
        try {
            & ".\gradlew.bat" --no-daemon --no-build-cache --stacktrace clean `
                ":server:compileReleaseJavaWithJavac" ":server:createReleaseServerJars" `
                "-Duser.timezone=Pacific/Auckland" "-Duser.language=en" "-Duser.country=NZ"
            if ($LASTEXITCODE -ne 0) {
                throw "Cross-environment server-jar Gradle build failed."
            }
        } finally {
            Pop-Location
        }
        Copy-ServerJars -SourceRoot $worktreeDir -DestinationDir $crossDir
    } finally {
        foreach ($name in $environmentNames) {
            [Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name], "Process")
        }
        if ($worktreeAdded) {
            & git worktree remove --force $worktreeDir 2>$null | Out-Null
        }
        if (Test-Path -LiteralPath $worktreeParent) {
            Remove-Item -LiteralPath $worktreeParent -Recurse -Force
        }
    }

    $report = @()
    foreach ($name in @("am.jar", "main.jar")) {
        $firstHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $FIRST_DIR "server-jars\$name")).Hash.ToLowerInvariant()
        $crossHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $crossDir $name)).Hash.ToLowerInvariant()
        $line = "$name first=$firstHash different-environment=$crossHash"
        Write-Host $line
        $report += $line
        if ($firstHash -ne $crossHash) {
            throw "Server jar $name is not reproducible across environments."
        }
    }
    Set-Content -Path $serverJarReport -Value $report -Encoding ascii
}

Invoke-ReproducibleBuild -Label "first" -DestinationDir $firstDir
Invoke-ReproducibleBuild -Label "second" -DestinationDir $secondDir
Invoke-CrossEnvironmentServerJarCheck

$firstNames = @(Get-ChildItem -LiteralPath $firstDir -Filter "*.apk" -File | Sort-Object Name | Select-Object -ExpandProperty Name)
$secondNames = @(Get-ChildItem -LiteralPath $secondDir -Filter "*.apk" -File | Sort-Object Name | Select-Object -ExpandProperty Name)
$apkSetDiff = @(Compare-Object -ReferenceObject $firstNames -DifferenceObject $secondNames)
if ($apkSetDiff.Count -ne 0) {
    $apkSetDiff | Out-String | Set-Content -Path (Join-Path $OutDir "apk-list.diff") -Encoding ascii
    throw "Release APK set changed across two clean builds."
}

$assetLines = @()
$shaLines = @()
$assetLines += $serverJarReport
foreach ($name in $firstNames) {
    $firstApk = Join-Path $firstDir $name
    $secondApk = Join-Path $secondDir $name
    $firstHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $firstApk).Hash.ToLowerInvariant()
    $secondHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $secondApk).Hash.ToLowerInvariant()
    if ($firstHash -ne $secondHash) {
        throw "Release APK $name is not reproducible across two clean builds. first=$firstHash second=$secondHash"
    }

    $publishApk = Join-Path $publishDir (Get-PublishApkName -Name $name)
    Copy-Item -LiteralPath $firstApk -Destination $publishApk -Force
    Invoke-NativePageAlignmentCheck -ApkPath $publishApk
    $shaLine = "$firstHash  $(Split-Path -Leaf $publishApk)"
    $publishSha = "$publishApk.sha256"
    Set-Content -Path $publishSha -Value $shaLine -Encoding ascii
    $shaLines += $shaLine
    $assetLines += $publishApk
    $assetLines += $publishSha
    Write-Host "Reproducible release APK verified: $name $firstHash"
}

$sbomPath = Join-Path $publishDir "AppManagerNG-reproducible.cdx.json"
Invoke-ReleaseSbomGeneration -SbomPath $sbomPath
$assetLines += $sbomPath

$python = Get-Command $PythonCmd -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw "Python command '$PythonCmd' was not found; cannot run the dependency CVE release gate."
}
& $python.Source "scripts\run_dependency_cve_gate.py" `
    --gradle-cmd $GradleCmd `
    --out-dir $publishDir
if ($LASTEXITCODE -ne 0) {
    throw "Blocking dependency CVE release gate failed."
}
$assetLines += (Join-Path $publishDir "dependency-check-report.html")
$assetLines += (Join-Path $publishDir "dependency-check-report.sarif")
$assetLines += (Join-Path $publishDir "dependency-cve-receipt.json")

Set-Content -Path $combinedSha -Value $shaLines -Encoding ascii
Set-Content -Path $assetList -Value $assetLines -Encoding ascii
