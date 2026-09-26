# SPDX-License-Identifier: GPL-3.0-or-later

# Windows entry point for the reproducible release check. The check itself is
# scripts/verify_reproducible_release.sh, so both entry points build, compare and publish the
# same artifacts: release APKs, R8 mappings and the privileged server jars. This script only finds
# Git for Windows' bash and hands the options over as the environment variables that script reads.

[CmdletBinding()]
param(
    [string] $GradleCmd = "./gradlew",
    [string] $PythonCmd = "",
    [string] $OutDir = "reproducible-release"
)

$ErrorActionPreference = "Stop"

$rootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $rootDir

# A bare `bash` on Windows is usually WSL, which brings a different Gradle, Python and file system.
# Use Git for Windows' bash, the same one scripts/release_gate.py picks.
$bash = @(
    (Join-Path $env:ProgramFiles "Git\bin\bash.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "Git\bin\bash.exe")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
if (-not $bash) {
    throw "Git for Windows' bash.exe was not found. Install Git for Windows, or run scripts/verify_reproducible_release.sh from a POSIX shell."
}

$settings = @{
    GRADLE_CMD    = $GradleCmd
    PYTHON_CMD    = $PythonCmd
    REPRO_OUT_DIR = $OutDir
}
$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
        $value = $settings[$name]
        [Environment]::SetEnvironmentVariable($name, $(if ($value) { $value } else { $null }), "Process")
    }
    & $bash "scripts/verify_reproducible_release.sh"
    if ($LASTEXITCODE -ne 0) {
        throw "Reproducible release verification failed with exit code $LASTEXITCODE."
    }
} finally {
    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
    }
}
