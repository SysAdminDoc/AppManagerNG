# SPDX-License-Identifier: GPL-3.0-or-later

[CmdletBinding()]
param(
    [string] $GradleCmd = ".\gradlew.bat",
    [string] $OutDir = "build\reproducible-release"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Resolve-Path (Join-Path $scriptDir "..")
Set-Location $rootDir

$apkDir = "app\build\outputs\apk\release"
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
    $apks = @(Get-ChildItem -LiteralPath $apkDir -Filter "*.apk" -File | Sort-Object Name)
    if ($apks.Count -eq 0) {
        throw "No release APKs were produced in $apkDir"
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
    $shaLine = "$firstHash  $(Split-Path -Leaf $publishApk)"
    $publishSha = "$publishApk.sha256"
    Set-Content -Path $publishSha -Value $shaLine -Encoding ascii
    $shaLines += $shaLine
    $assetLines += $publishApk
    $assetLines += $publishSha
    Write-Host "Reproducible release APK verified: $name $firstHash"
}

Set-Content -Path $combinedSha -Value $shaLines -Encoding ascii
Set-Content -Path $assetList -Value $assetLines -Encoding ascii
