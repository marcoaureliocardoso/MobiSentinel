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

$ghCommand = $null
foreach ($name in @('gh', 'gh.exe')) {
    $ghCommand = Get-Command $name -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($ghCommand) {
        break
    }
}
if (-not $ghCommand) {
    throw 'GitHub CLI was not found on PATH'
}
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
    $environmentEndpoint = "repos/$Repository/environments/production"
    $branchPoliciesEndpoint = "$environmentEndpoint/deployment-branch-policies"
    $environmentPolicy = @{
        deployment_branch_policy = @{
            protected_branches = $false
            custom_branch_policies = $true
        }
    } | ConvertTo-Json -Depth 3 -Compress
    $environmentPolicy | & gh api `
        --method PUT `
        $environmentEndpoint `
        --input - | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to protect GitHub environment 'production'"
    }

    $deploymentPolicies = @(
        & gh api $branchPoliciesEndpoint `
            --paginate `
            --jq '.branch_policies[] | "\(.type):\(.name)"'
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to list production deployment branch policies'
    }
    if ('branch:master' -notin $deploymentPolicies) {
        & gh api `
            --method POST `
            $branchPoliciesEndpoint `
            -f name=master `
            -f type=branch | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to restrict production deployments to master'
        }
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

    $customBranchPolicies = & gh api $environmentEndpoint `
        --jq '.deployment_branch_policy.custom_branch_policies'
    if ($LASTEXITCODE -ne 0 -or $customBranchPolicies -ne 'true') {
        throw 'GitHub production environment does not require custom branch policies'
    }
    $protectedBranches = & gh api $environmentEndpoint `
        --jq '.deployment_branch_policy.protected_branches'
    if ($LASTEXITCODE -ne 0 -or $protectedBranches -ne 'false') {
        throw 'GitHub production environment has an unexpected protected-branches mode'
    }
    $deploymentPolicies = @(
        & gh api $branchPoliciesEndpoint `
            --paginate `
            --jq '.branch_policies[] | "\(.type):\(.name)"'
    )
    if ($LASTEXITCODE -ne 0 -or
        $deploymentPolicies.Count -ne 1 -or
        $deploymentPolicies[0] -ne 'branch:master') {
        throw 'GitHub production deployments must be restricted exactly to master'
    }

    Write-Output 'Configured GitHub production environment secrets:'
    $expectedNames | Sort-Object | ForEach-Object { Write-Output $_ }
    Write-Output 'Restricted GitHub production deployments to branch:master'
} finally {
    $base64 = $null
    $environmentPolicy = $null
    foreach ($name in $requiredRecovery) {
        $recovery[$name] = $null
    }
}
