[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$generator = Join-Path $PSScriptRoot '..\create-production-signing.ps1'
$configurator = Join-Path $PSScriptRoot '..\configure-production-environment.ps1'
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporary = Join-Path $temporaryRoot (
    'MobiSentinel-environment-test-' + [guid]::NewGuid().ToString('N')
)
$global:MobiSentinelFakeGhCalls = [Collections.Generic.List[string]]::new()

function global:gh {
    [CmdletBinding(PositionalBinding = $false)]
    param(
        [Parameter(ValueFromPipeline)]
        [AllowNull()]
        [object]$InputObject,

        [Parameter(ValueFromRemainingArguments)]
        [object[]]$Arguments
    )

    begin {
        $stdinLength = 0
    }
    process {
        if ($null -ne $InputObject) {
            $stdinLength += ([string]$InputObject).Length
        }
    }
    end {
        $argumentText = ($Arguments | ForEach-Object { [string]$_ }) -join ' '
        $global:MobiSentinelFakeGhCalls.Add(
            "$argumentText|stdinLength=$stdinLength"
        )
        $global:LASTEXITCODE = 0

        if ($argumentText -match '/secrets' -and $argumentText -notmatch '--method PUT') {
            @(
                'ANDROID_SIGNING_KEY_BASE64',
                'ANDROID_SIGNING_STORE_PASSWORD',
                'ANDROID_SIGNING_KEY_ALIAS',
                'ANDROID_SIGNING_KEY_PASSWORD'
            ) | Write-Output
        }
    }
}

try {
    & pwsh -NoProfile -File $generator -OutputDirectory $temporary | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to generate temporary signing material'
    }

    $recoveryPath = Join-Path $temporary 'mobisentinel-production-recovery.env'
    $secretValues = Get-Content -LiteralPath $recoveryPath |
        ForEach-Object { $_.Substring($_.IndexOf('=') + 1) }

    $output = & $configurator `
        -Repository 'example/MobiSentinel' `
        -SigningDirectory $temporary
    if ($LASTEXITCODE -ne 0) {
        throw "Configurator failed: $($output -join [Environment]::NewLine)"
    }

    $calls = $global:MobiSentinelFakeGhCalls -join [Environment]::NewLine
    if ($calls -notmatch 'api --method PUT repos/example/MobiSentinel/environments/production') {
        throw 'Configurator did not create the production environment'
    }
    foreach ($name in @(
        'ANDROID_SIGNING_KEY_BASE64',
        'ANDROID_SIGNING_STORE_PASSWORD',
        'ANDROID_SIGNING_KEY_ALIAS',
        'ANDROID_SIGNING_KEY_PASSWORD'
    )) {
        if ($calls -notmatch "secret set $name --env production --repo example/MobiSentinel") {
            throw "Configurator did not set $name"
        }
    }
    foreach ($secret in $secretValues) {
        if (-not [string]::IsNullOrEmpty($secret) -and $calls.Contains($secret)) {
            throw 'Configurator exposed a recovery value in GitHub CLI arguments'
        }
    }
    if ($output -match '[-_A-Za-z0-9]{40,}') {
        throw 'Configurator output appears to contain secret material'
    }

    Write-Output 'GitHub production environment configurator test passed'
} finally {
    Remove-Item Function:\gh -ErrorAction SilentlyContinue
    $global:MobiSentinelFakeGhCalls = $null
    $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
    if (-not $resolvedTemporary.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to clean an environment test directory outside temp'
    }
    if (Test-Path -LiteralPath $resolvedTemporary) {
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}
