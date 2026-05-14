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

$apkPath = "app\build\outputs\apk\release\app-release.apk"
$firstApk = Join-Path $OutDir "app-release-first.apk"
$secondApk = Join-Path $OutDir "app-release-second.apk"
$publishApk = Join-Path $OutDir "AppManagerNG-reproducible-release.apk"

if (Test-Path $OutDir) {
    Remove-Item -LiteralPath $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Invoke-ReproducibleBuild {
    param(
        [string] $Label,
        [string] $Destination
    )

    Write-Host "Clean build $Label"
    & $GradleCmd --no-daemon --stacktrace clean ':app:assembleRelease' 2>&1 | ForEach-Object {
        Write-Host $_
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Gradle release build failed during $Label build."
    }
    if (-not (Test-Path $apkPath)) {
        throw "Expected release APK was not produced at $apkPath"
    }

    Copy-Item -LiteralPath $apkPath -Destination $Destination -Force
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    Set-Content -Path (Join-Path $OutDir "$Label.sha256") -Value "$hash  $Destination" -Encoding ascii
    return $hash
}

$firstHash = Invoke-ReproducibleBuild -Label "first" -Destination $firstApk
$secondHash = Invoke-ReproducibleBuild -Label "second" -Destination $secondApk

if ($firstHash -ne $secondHash) {
    throw "Release APK is not reproducible across two clean builds. first=$firstHash second=$secondHash"
}

Copy-Item -LiteralPath $firstApk -Destination $publishApk -Force
Set-Content -Path (Join-Path $OutDir "sha256.txt") -Value "$firstHash  $(Split-Path -Leaf $publishApk)" -Encoding ascii
Write-Host "Reproducible release APK verified: $firstHash"
