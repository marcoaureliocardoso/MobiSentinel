[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryPrefix = $repositoryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ($resolvedOutput.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing recovery material must be generated outside the repository'
}

New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

$keystore = Join-Path $resolvedOutput 'mobisentinel-production.p12'
$recovery = Join-Path $resolvedOutput 'mobisentinel-production-recovery.env'
$fingerprint = Join-Path $resolvedOutput 'release-certificate.sha256'
$readme = Join-Path $resolvedOutput 'README.txt'
$certificate = Join-Path $resolvedOutput '.mobisentinel-certificate.der'
$outputs = @($keystore, $recovery, $fingerprint, $readme)

foreach ($path in $outputs) {
    if (Test-Path -LiteralPath $path) {
        throw "Refusing to replace existing signing material: '$path'"
    }
}

$keytool = (Get-Command keytool, keytool.exe -ErrorAction Stop |
    Select-Object -First 1).Source
$passwordBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
$password = [Convert]::ToBase64String($passwordBytes).
    TrimEnd('=').
    Replace('+', '-').
    Replace('/', '_')

$env:MOBISENTINEL_GENERATED_STORE_PASSWORD = $password
$env:MOBISENTINEL_GENERATED_KEY_PASSWORD = $password
try {
    & $keytool -genkeypair -noprompt `
        -alias mobisentinel `
        -keyalg RSA `
        -keysize 4096 `
        -validity 36500 `
        -dname 'CN=MobiSentinel Release,O=Marco Cardoso,C=BR' `
        -keystore $keystore `
        -storetype PKCS12 `
        '-storepass:env' MOBISENTINEL_GENERATED_STORE_PASSWORD `
        '-keypass:env' MOBISENTINEL_GENERATED_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) {
        throw 'keytool failed to generate the production signing key'
    }

    & $keytool -exportcert `
        -alias mobisentinel `
        -keystore $keystore `
        -storetype PKCS12 `
        '-storepass:env' MOBISENTINEL_GENERATED_STORE_PASSWORD `
        -file $certificate | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'keytool failed to export the production certificate'
    }

    $certificateSha256 = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).
        Hash.ToLowerInvariant()
    [IO.File]::WriteAllText(
        $fingerprint,
        $certificateSha256 + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )

    $recoveryLines = @(
        "ANDROID_SIGNING_STORE_PASSWORD=$password",
        'ANDROID_SIGNING_KEY_ALIAS=mobisentinel',
        "ANDROID_SIGNING_KEY_PASSWORD=$password"
    )
    [IO.File]::WriteAllText(
        $recovery,
        ($recoveryLines -join [Environment]::NewLine) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )

    $instructions = @(
        'MobiSentinel production signing recovery',
        '',
        'Keep all four files private. Anyone with this PKCS#12 file and the',
        'recovery credentials can sign an Android update as MobiSentinel.',
        '',
        'Restore GitHub Secrets with scripts/configure-production-environment.ps1.',
        'The public certificate fingerprint is release-certificate.sha256.'
    )
    [IO.File]::WriteAllText(
        $readme,
        ($instructions -join [Environment]::NewLine) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )
} catch {
    foreach ($path in $outputs) {
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
    }
    throw
} finally {
    Remove-Item Env:MOBISENTINEL_GENERATED_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MOBISENTINEL_GENERATED_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $certificate -Force -ErrorAction SilentlyContinue
    if ($null -ne $passwordBytes) {
        [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    }
    $password = $null
}

Write-Output 'Created production signing recovery files:'
$outputs | ForEach-Object { Write-Output (Split-Path $_ -Leaf) }
