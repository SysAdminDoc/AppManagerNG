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

if (Test-Path $OutDir) {
    Remove-Item -LiteralPath $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $firstDir, $secondDir, $publishDir | Out-Null

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
    & $GradleCmd --no-daemon --stacktrace clean ':app:assembleRelease' 2>&1 | ForEach-Object {
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

Invoke-ReproducibleBuild -Label "first" -DestinationDir $firstDir
Invoke-ReproducibleBuild -Label "second" -DestinationDir $secondDir

$firstNames = @(Get-ChildItem -LiteralPath $firstDir -Filter "*.apk" -File | Sort-Object Name | Select-Object -ExpandProperty Name)
$secondNames = @(Get-ChildItem -LiteralPath $secondDir -Filter "*.apk" -File | Sort-Object Name | Select-Object -ExpandProperty Name)
$apkSetDiff = @(Compare-Object -ReferenceObject $firstNames -DifferenceObject $secondNames)
if ($apkSetDiff.Count -ne 0) {
    $apkSetDiff | Out-String | Set-Content -Path (Join-Path $OutDir "apk-list.diff") -Encoding ascii
    throw "Release APK set changed across two clean builds."
}

$assetLines = @()
$shaLines = @()
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

Set-Content -Path $combinedSha -Value $shaLines -Encoding ascii
Set-Content -Path $assetList -Value $assetLines -Encoding ascii
