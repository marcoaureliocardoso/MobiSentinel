[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$verifier = Join-Path $PSScriptRoot '..\verify-release-apk.ps1'
$generator = Join-Path $PSScriptRoot '..\create-production-signing.ps1'
$buildFile = Join-Path $repositoryRoot 'app\build.gradle.kts'
$gradleName = if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }
$gradle = Join-Path $repositoryRoot $gradleName
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporary = Join-Path $temporaryRoot (
    'MobiSentinel-verifier-test-' + [guid]::NewGuid().ToString('N')
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

function Invoke-Verifier {
    param(
        [Parameter(Mandatory)][string]$Tag,
        [Parameter(Mandatory)][string]$ApkPath,
        [Parameter(Mandatory)][string]$FingerprintPath
    )

    $output = & pwsh -NoProfile -File $verifier `
        -Tag $Tag `
        -ApkPath $ApkPath `
        -CertificateSha256Path $FingerprintPath 2>&1
    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join [Environment]::NewLine)
    }
}

$signingVariables = @(
    'ANDROID_SIGNING_STORE_FILE',
    'ANDROID_SIGNING_STORE_PASSWORD',
    'ANDROID_SIGNING_KEY_ALIAS',
    'ANDROID_SIGNING_KEY_PASSWORD'
)
$previousEnvironment = @{}
foreach ($name in $signingVariables) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}

try {
    $generatorOutput = & pwsh -NoProfile -File $generator `
        -OutputDirectory $temporary 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Signing generator failed: $($generatorOutput -join [Environment]::NewLine)"
    }

    $recovery = Read-RecoveryValues -Path (
        Join-Path $temporary 'mobisentinel-production-recovery.env'
    )
    $env:ANDROID_SIGNING_STORE_FILE = Join-Path $temporary 'mobisentinel-production.p12'
    $env:ANDROID_SIGNING_STORE_PASSWORD = $recovery['ANDROID_SIGNING_STORE_PASSWORD']
    $env:ANDROID_SIGNING_KEY_ALIAS = $recovery['ANDROID_SIGNING_KEY_ALIAS']
    $env:ANDROID_SIGNING_KEY_PASSWORD = $recovery['ANDROID_SIGNING_KEY_PASSWORD']

    $buildOutput = & $gradle --no-daemon `
        :app:assembleDebug `
        :app:assembleRelease 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "APK builds failed: $($buildOutput -join [Environment]::NewLine)"
    }

    $releaseApk = Join-Path $repositoryRoot 'app\build\outputs\apk\release\app-release.apk'
    $debugApk = Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $fingerprintPath = Join-Path $temporary 'release-certificate.sha256'
    $wrongFingerprintPath = Join-Path $temporary 'wrong-certificate.sha256'
    [IO.File]::WriteAllText(
        $wrongFingerprintPath,
        ('0' * 64) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )

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

    $valid = Invoke-Verifier `
        -Tag $validTag `
        -ApkPath $releaseApk `
        -FingerprintPath $fingerprintPath
    if ($valid.ExitCode -ne 0) {
        throw "Expected $validTag to pass, got exit $($valid.ExitCode): $($valid.Output)"
    }
    $metadataPattern = (
        'package=br\.com\.marcocardoso\.mobisentinel ' +
        'versionName={0} versionCode={1} certificateSha256=[0-9a-f]{{64}}'
    ) -f [regex]::Escape($versionName), $versionCode
    if ($valid.Output -notmatch $metadataPattern) {
        throw "Expected verified metadata and certificate output, got: $($valid.Output)"
    }

    $mismatch = Invoke-Verifier `
        -Tag "v$mismatchVersion" `
        -ApkPath $releaseApk `
        -FingerprintPath $fingerprintPath
    if ($mismatch.ExitCode -eq 0 -or $mismatch.Output -notmatch 'Expected versionName') {
        throw "Expected mismatched versionName to fail clearly, got: $($mismatch.Output)"
    }

    $invalid = Invoke-Verifier `
        -Tag $versionName `
        -ApkPath $releaseApk `
        -FingerprintPath $fingerprintPath
    if ($invalid.ExitCode -eq 0 -or $invalid.Output -notmatch 'must use vX\.Y\.Z') {
        throw "Expected tag without v prefix to fail clearly, got: $($invalid.Output)"
    }

    $oversized = Invoke-Verifier `
        -Tag 'v0.1000.0' `
        -ApkPath $releaseApk `
        -FingerprintPath $fingerprintPath
    if ($oversized.ExitCode -eq 0 -or $oversized.Output -notmatch '0\.\.999') {
        throw "Expected oversized version component to fail clearly, got: $($oversized.Output)"
    }

    $wrongCertificate = Invoke-Verifier `
        -Tag $validTag `
        -ApkPath $releaseApk `
        -FingerprintPath $wrongFingerprintPath
    if ($wrongCertificate.ExitCode -eq 0 -or
        $wrongCertificate.Output -notmatch 'certificate SHA-256') {
        throw "Expected wrong certificate to fail clearly, got: $($wrongCertificate.Output)"
    }

    $debug = Invoke-Verifier `
        -Tag $validTag `
        -ApkPath $debugApk `
        -FingerprintPath $fingerprintPath
    if ($debug.ExitCode -eq 0 -or $debug.Output -notmatch 'debuggable') {
        throw "Expected debuggable APK to be rejected clearly, got: $($debug.Output)"
    }

    Write-Output (
        'APK verifier tests passed: signed release, metadata, tag syntax, ' +
        'component range, certificate mismatch, and debuggable rejection'
    )
} finally {
    foreach ($name in $signingVariables) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name])
    }

    $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
    if (-not $resolvedTemporary.StartsWith(
        $temporaryRoot,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw 'Refusing to clean a verifier test directory outside the system temp root'
    }
    if (Test-Path -LiteralPath $resolvedTemporary) {
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}

# Negative cases intentionally leave the last child process with exit code 1.
$global:LASTEXITCODE = 0
