[CmdletBinding()]
param(
    [string]$ApkPath = (
        Join-Path $PSScriptRoot '..\..\app\build\outputs\apk\debug\app-debug.apk'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot '..\verify-release-apk.ps1'
$buildFile = Join-Path $PSScriptRoot '..\..\app\build.gradle.kts'
$buildText = Get-Content -LiteralPath $buildFile -Raw
$versionMatch = [regex]::Match(
    $buildText,
    'AppVersion\.parse\("((0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*))"\)\s*//\s*x-release-please-version'
)
if (-not $versionMatch.Success) {
    throw "Unable to find the Release Please version marker in '$buildFile'"
}

$versionName = $versionMatch.Groups[1].Value
$components = 2..4 | ForEach-Object { [int]$versionMatch.Groups[$_].Value }
$versionCode = $components[0] * 1000000 + $components[1] * 1000 + $components[2]
$validTag = "v$versionName"
$mismatchVersion = if ($versionName -eq '0.0.0') { '0.0.1' } else { '0.0.0' }
$mismatchTag = "v$mismatchVersion"

function Invoke-Verifier {
    param([Parameter(Mandatory)][string]$Tag)

    $output = & pwsh -NoProfile -File $verifier -Tag $Tag -ApkPath $ApkPath 2>&1
    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join [Environment]::NewLine)
    }
}

$valid = Invoke-Verifier -Tag $validTag
if ($valid.ExitCode -ne 0) {
    throw "Expected $validTag to pass, got exit $($valid.ExitCode): $($valid.Output)"
}
$metadataPattern = 'package=com\.mobisentinel\.app versionName={0} versionCode={1}' -f
    [regex]::Escape($versionName),
    $versionCode
if ($valid.Output -notmatch $metadataPattern) {
    throw "Expected verified metadata output, got: $($valid.Output)"
}

$mismatch = Invoke-Verifier -Tag $mismatchTag
if ($mismatch.ExitCode -eq 0) {
    throw 'Expected mismatched versionName to fail'
}
$mismatchPattern = 'Expected versionName {0}, found {1}' -f
    [regex]::Escape($mismatchVersion),
    [regex]::Escape($versionName)
if ($mismatch.Output -notmatch $mismatchPattern) {
    throw "Expected versionName mismatch message, got: $($mismatch.Output)"
}

$invalid = Invoke-Verifier -Tag $versionName
if ($invalid.ExitCode -eq 0) {
    throw 'Expected tag without v prefix to fail'
}
if ($invalid.Output -notmatch 'must use vX\.Y\.Z') {
    throw "Expected canonical tag message, got: $($invalid.Output)"
}

$oversized = Invoke-Verifier -Tag 'v0.1000.0'
if ($oversized.ExitCode -eq 0) {
    throw 'Expected oversized version component to fail'
}
if ($oversized.Output -notmatch '0\.\.999') {
    throw "Expected component range message, got: $($oversized.Output)"
}

Write-Output 'APK verifier tests passed: valid, mismatch, syntax, and component range'

# Negative cases intentionally leave the last child process with exit code 1.
# Reset it so dot-sourced CI shells report the successful test suite correctly.
$global:LASTEXITCODE = 0
