[CmdletBinding()]
param(
    [string]$ApkPath = (
        Join-Path $PSScriptRoot '..\..\app\build\outputs\apk\debug\app-debug.apk'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$verifier = Join-Path $PSScriptRoot '..\verify-release-apk.ps1'

function Invoke-Verifier {
    param([Parameter(Mandatory)][string]$Tag)

    $output = & pwsh -NoProfile -File $verifier -Tag $Tag -ApkPath $ApkPath 2>&1
    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join [Environment]::NewLine)
    }
}

$valid = Invoke-Verifier -Tag 'v0.1.0'
if ($valid.ExitCode -ne 0) {
    throw "Expected v0.1.0 to pass, got exit $($valid.ExitCode): $($valid.Output)"
}
if ($valid.Output -notmatch 'package=com\.mobisentinel\.app versionName=0\.1\.0 versionCode=1000') {
    throw "Expected verified metadata output, got: $($valid.Output)"
}

$mismatch = Invoke-Verifier -Tag 'v0.1.1'
if ($mismatch.ExitCode -eq 0) {
    throw 'Expected mismatched versionName to fail'
}
if ($mismatch.Output -notmatch 'Expected versionName 0\.1\.1, found 0\.1\.0') {
    throw "Expected versionName mismatch message, got: $($mismatch.Output)"
}

$invalid = Invoke-Verifier -Tag '0.1.0'
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
