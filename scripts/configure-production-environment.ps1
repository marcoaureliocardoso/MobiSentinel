[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository,

    [Parameter(Mandatory)]
    [string]$SigningDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-RecoveryValues {
    param([Parameter(Mandatory)][string]$Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid recovery entry in '$Path'"
        }
        $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
    }
    return $values
}

function Set-GitHubEnvironmentSecret {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    $Value | & gh secret set $Name `
        --env production `
        --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub environment secret '$Name'"
    }
}

$resolvedDirectory = (Resolve-Path -LiteralPath $SigningDirectory).Path
$keystore = Join-Path $resolvedDirectory 'mobisentinel-production.p12'
$recoveryPath = Join-Path $resolvedDirectory 'mobisentinel-production-recovery.env'
foreach ($path in @($keystore, $recoveryPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required signing recovery file was not found: '$path'"
    }
}

Get-Command gh, gh.exe -ErrorAction Stop | Select-Object -First 1 | Out-Null
$recovery = Read-RecoveryValues -Path $recoveryPath
$requiredRecovery = @(
    'ANDROID_SIGNING_STORE_PASSWORD',
    'ANDROID_SIGNING_KEY_ALIAS',
    'ANDROID_SIGNING_KEY_PASSWORD'
)
foreach ($name in $requiredRecovery) {
    if (-not $recovery.ContainsKey($name) -or
        [string]::IsNullOrWhiteSpace($recovery[$name])) {
        throw "Recovery file is missing '$name'"
    }
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
try {
    & gh api --method PUT "repos/$Repository/environments/production" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create GitHub environment 'production'"
    }

    Set-GitHubEnvironmentSecret -Name 'ANDROID_SIGNING_KEY_BASE64' -Value $base64
    foreach ($name in $requiredRecovery) {
        Set-GitHubEnvironmentSecret -Name $name -Value ([string]$recovery[$name])
    }

    $actualNames = @(
        & gh api `
            "repos/$Repository/environments/production/secrets" `
            --jq '.secrets[].name'
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to list GitHub production environment secrets'
    }

    $expectedNames = @('ANDROID_SIGNING_KEY_BASE64') + $requiredRecovery
    $missing = $expectedNames | Where-Object { $_ -notin $actualNames }
    if ($missing) {
        throw "GitHub production environment is missing: $($missing -join ', ')"
    }

    Write-Output 'Configured GitHub production environment secrets:'
    $expectedNames | Sort-Object | ForEach-Object { Write-Output $_ }
} finally {
    $base64 = $null
    foreach ($name in $requiredRecovery) {
        $recovery[$name] = $null
    }
}
