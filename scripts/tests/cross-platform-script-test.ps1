[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$paths = @(
    'scripts/create-production-signing.ps1',
    'scripts/configure-production-environment.ps1',
    'scripts/tests/create-production-signing-test.ps1'
)

foreach ($relativePath in $paths) {
    $path = Join-Path $repositoryRoot $relativePath
    $content = Get-Content -LiteralPath $path -Raw
    if ($content -match 'Get-Command\s+[^\r\n,]+,\s*[^\r\n]+-ErrorAction\s+Stop') {
        throw "$relativePath resolves Windows and Unix executable names in one terminating Get-Command call"
    }
}

Write-Output 'Cross-platform PowerShell command resolution test passed'
