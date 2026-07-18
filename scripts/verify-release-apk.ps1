[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Tag,

    [Parameter(Mandatory)]
    [string]$ApkPath,

    [Parameter(Mandatory)]
    [string]$CertificateSha256Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$tagMatch = [regex]::Match(
    $Tag,
    '^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$'
)
if (-not $tagMatch.Success) {
    throw "Tag '$Tag' must use vX.Y.Z with canonical numeric components"
}

$components = 1..3 | ForEach-Object { [long]$tagMatch.Groups[$_].Value }
if ($components | Where-Object { $_ -gt 999 }) {
    throw "Tag '$Tag' must keep every component in 0..999"
}

$versionName = $Tag.Substring(1)
$expectedVersionCode = [int](
    $components[0] * 1000000 + $components[1] * 1000 + $components[2]
)
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$resolvedCertificateSha256 = (
    Resolve-Path -LiteralPath $CertificateSha256Path
).Path
$expectedCertificateSha256 = (
    Get-Content -LiteralPath $resolvedCertificateSha256 -Raw
).Trim()
if ($expectedCertificateSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'Expected certificate SHA-256 must contain 64 lowercase hexadecimal characters'
}

function Resolve-AndroidSdk {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }

    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $localProperties = Join-Path $repositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_.StartsWith('sdk.dir=') } |
            Select-Object -First 1
        if ($sdkLine) {
            return $sdkLine.Substring('sdk.dir='.Length).
                Replace('\:', ':').
                Replace('\\', '\')
        }
    }

    throw 'Android SDK was not found through ANDROID_HOME or local.properties'
}

function Resolve-AndroidBuildTool {
    param(
        [Parameter(Mandatory)]
        [string[]]$Names
    )

    $command = Get-Command $Names -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) {
        return $command.Source
    }

    $buildTools = Join-Path (Resolve-AndroidSdk) 'build-tools'
    $directories = Get-ChildItem -LiteralPath $buildTools -Directory |
        Sort-Object {
            [version](($_.Name -split '-')[0])
    } -Descending
    foreach ($directory in $directories) {
        foreach ($name in $Names) {
            $candidate = Join-Path $directory.FullName $name
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }
    throw "Android build tool '$($Names[0])' was not found under '$buildTools'"
}

$aapt = Resolve-AndroidBuildTool -Names @('aapt', 'aapt.exe')
$badging = & $aapt dump badging $resolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "aapt failed to inspect '$resolvedApk'"
}

if ($badging | Where-Object { $_ -match '^application-debuggable' }) {
    throw "Refusing debuggable APK '$resolvedApk'"
}

$packageLine = $badging | Select-Object -First 1
$packageMatch = [regex]::Match(
    $packageLine,
    "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'"
)
if (-not $packageMatch.Success) {
    throw "Unable to parse APK package metadata: $packageLine"
}

$actualPackage = $packageMatch.Groups[1].Value
$actualVersionCode = $packageMatch.Groups[2].Value
$actualVersionName = $packageMatch.Groups[3].Value

if ($actualPackage -ne 'br.com.marcocardoso.mobisentinel') {
    throw "Expected package br.com.marcocardoso.mobisentinel, found $actualPackage"
}
if ($actualVersionName -ne $versionName) {
    throw "Expected versionName $versionName, found $actualVersionName"
}
if ($actualVersionCode -ne $expectedVersionCode.ToString()) {
    throw "Expected versionCode $expectedVersionCode, found $actualVersionCode"
}

$apksigner = Resolve-AndroidBuildTool -Names @(
    'apksigner',
    'apksigner.bat',
    'apksigner.exe'
)
$signatureOutput = & $apksigner verify `
    --verbose `
    --print-certs `
    --min-sdk-version 26 `
    $resolvedApk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw (
        "apksigner rejected '$resolvedApk': " +
        ($signatureOutput -join [Environment]::NewLine)
    )
}

$signatureText = $signatureOutput -join [Environment]::NewLine
$signerCountMatch = [regex]::Match(
    $signatureText,
    '(?im)^Number of signers:\s*(\d+)\s*$'
)
if (-not $signerCountMatch.Success -or $signerCountMatch.Groups[1].Value -ne '1') {
    throw 'Expected exactly one APK signer'
}
$certificateMatches = [regex]::Matches(
    $signatureText,
    '(?im)^(?:V\d+(?:\.\d+)? Signer|Signer #\d+): certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
)
$certificateDigests = @(
    $certificateMatches |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } |
        Sort-Object -Unique
)
if ($certificateDigests.Count -ne 1) {
    throw 'Expected exactly one APK signer certificate SHA-256 digest'
}
$actualCertificateSha256 = $certificateDigests[0]
if ($actualCertificateSha256 -ne $expectedCertificateSha256) {
    throw (
        "Expected certificate SHA-256 $expectedCertificateSha256, " +
        "found $actualCertificateSha256"
    )
}

Write-Output (
    "Verified package={0} versionName={1} versionCode={2} certificateSha256={3}" -f
        $actualPackage,
        $actualVersionName,
        $actualVersionCode,
        $actualCertificateSha256
)
