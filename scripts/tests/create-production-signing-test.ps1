[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$generator = Join-Path $PSScriptRoot '..\create-production-signing.ps1'
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporary = Join-Path $temporaryRoot (
    'MobiSentinel-signing-test-' + [guid]::NewGuid().ToString('N')
)

function Read-RecoveryValues {
    param([Parameter(Mandatory)][string]$Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid recovery entry: $line"
        }
        $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
    }
    return $values
}

try {
    $output = & pwsh -NoProfile -File $generator -OutputDirectory $temporary 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Generator failed: $($output -join [Environment]::NewLine)"
    }

    $expected = @(
        'mobisentinel-production.p12',
        'mobisentinel-production-recovery.env',
        'release-certificate.sha256',
        'README.txt'
    )
    foreach ($name in $expected) {
        $path = Join-Path $temporary $name
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing generated file: $name"
        }
        if ((Get-Item -LiteralPath $path).Length -le 0) {
            throw "Generated file is empty: $name"
        }
    }

    $recoveryPath = Join-Path $temporary 'mobisentinel-production-recovery.env'
    $recovery = Read-RecoveryValues -Path $recoveryPath
    $requiredRecovery = @(
        'ANDROID_SIGNING_STORE_PASSWORD',
        'ANDROID_SIGNING_KEY_ALIAS',
        'ANDROID_SIGNING_KEY_PASSWORD'
    )
    foreach ($name in $requiredRecovery) {
        if (-not $recovery.ContainsKey($name) -or
            [string]::IsNullOrWhiteSpace($recovery[$name])) {
            throw "Missing recovery value: $name"
        }
    }
    if ($recovery['ANDROID_SIGNING_KEY_ALIAS'] -ne 'mobisentinel') {
        throw 'Unexpected signing key alias'
    }

    $password = [string]$recovery['ANDROID_SIGNING_STORE_PASSWORD']
    if (($output -join [Environment]::NewLine).Contains($password)) {
        throw 'Generator output exposed the signing password'
    }

    $fingerprintPath = Join-Path $temporary 'release-certificate.sha256'
    $fingerprint = (Get-Content -LiteralPath $fingerprintPath -Raw).Trim()
    if ($fingerprint -notmatch '^[0-9a-f]{64}$') {
        throw 'Fingerprint must contain 64 lowercase hexadecimal characters'
    }

    $keytool = (Get-Command keytool, keytool.exe -ErrorAction Stop |
        Select-Object -First 1).Source
    $keystore = Join-Path $temporary 'mobisentinel-production.p12'
    $certificate = Join-Path $temporary 'test-certificate.der'
    $env:MOBISENTINEL_TEST_STORE_PASSWORD = $password
    try {
        & $keytool -list -keystore $keystore -storetype PKCS12 `
            '-storepass:env' MOBISENTINEL_TEST_STORE_PASSWORD `
            -alias mobisentinel | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'keytool could not read the generated keystore'
        }

        & $keytool -exportcert -keystore $keystore -storetype PKCS12 `
            '-storepass:env' MOBISENTINEL_TEST_STORE_PASSWORD `
            -alias mobisentinel -file $certificate | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'keytool could not export the generated certificate'
        }
    } finally {
        Remove-Item Env:MOBISENTINEL_TEST_STORE_PASSWORD -ErrorAction SilentlyContinue
    }

    $actualFingerprint = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).
        Hash.ToLowerInvariant()
    if ($actualFingerprint -ne $fingerprint) {
        throw "Fingerprint mismatch: expected $fingerprint, found $actualFingerprint"
    }

    Write-Output 'Production signing generator test passed'
} finally {
    $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
    if (-not $resolvedTemporary.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to clean a signing test directory outside the system temp root'
    }
    if (Test-Path -LiteralPath $resolvedTemporary) {
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}
