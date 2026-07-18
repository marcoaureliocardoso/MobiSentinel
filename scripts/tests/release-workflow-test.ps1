[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$workflowPath = Join-Path $PSScriptRoot '..\..\.github\workflows\release-please.yml'
$workflow = Get-Content -LiteralPath $workflowPath -Raw
$ciPath = Join-Path $PSScriptRoot '..\..\.github\workflows\ci.yml'
$ci = Get-Content -LiteralPath $ciPath -Raw

$requiredPatterns = [ordered]@{
    'protected production environment' = '(?m)^\s{4}environment:\s*production\s*$'
    'base64 keystore secret' = 'secrets\.ANDROID_SIGNING_KEY_BASE64'
    'store password secret' = 'secrets\.ANDROID_SIGNING_STORE_PASSWORD'
    'key alias secret' = 'secrets\.ANDROID_SIGNING_KEY_ALIAS'
    'key password secret' = 'secrets\.ANDROID_SIGNING_KEY_PASSWORD'
    'release build' = '\bassembleRelease\b'
    'repeated debug unit tests' = '\btestDebugUnitTest\b'
    'repeated debug lint' = '\blintDebug\b'
    'repeated debug build' = '\bassembleDebug\b'
    'release APK input' = 'outputs/apk/release/app-release\.apk'
    'pinned certificate verification' = '-CertificateSha256Path\s+signing/release-certificate\.sha256'
    'Android 35 emulator' = '(?m)^\s+api-level:\s*35\s*$'
    'instrumented tests' = '\bconnectedDebugAndroidTest\b'
    'production APK asset' = 'MobiSentinel-\$VERSION\.apk'
    'checksum asset' = 'MobiSentinel-\$VERSION\.apk\.sha256'
    'published checksum verification' = 'sha256sum\s+--check\s+"MobiSentinel-\$VERSION\.apk\.sha256"'
    'always cleanup signing key' = '(?ms)- name: Remove signing key.*?if:\s*\$\{\{\s*always\(\)\s*\}\}'
    'final production promotion' = 'gh release edit "\$TAG" --prerelease=false --latest'
    'repository context for GitHub CLI' = 'GH_REPO:\s*\$\{\{\s*github\.repository\s*\}\}'
}

if ($ci -notmatch 'scripts/tests/release-workflow-test\.ps1') {
    throw 'CI does not execute the release workflow policy test'
}

foreach ($entry in $requiredPatterns.GetEnumerator()) {
    if ($workflow -notmatch $entry.Value) {
        throw "Release workflow is missing $($entry.Key)"
    }
}

$forbiddenPatterns = [ordered]@{
    'debug APK publication' = 'MobiSentinel-\$VERSION-debug\.apk'
    'debug release build label' = 'Build verified debug APK'
    'debug APK release input' = 'outputs/apk/debug/app-debug\.apk'
}

foreach ($entry in $forbiddenPatterns.GetEnumerator()) {
    if ($workflow -match $entry.Value) {
        throw "Release workflow still contains $($entry.Key)"
    }
}

$assetCheck = [regex]::Match(
    $workflow,
    '(?ms)- name: Verify exact release assets.*?gh release edit "\$TAG" --prerelease=false --latest'
)
if (-not $assetCheck.Success) {
    throw 'Production promotion must follow the exact release asset check in one step'
}
if ($assetCheck.Value -notmatch '\$\{#assets\[@\]\}\s*-ne\s*2') {
    throw 'Production promotion must require exactly two release assets'
}

Write-Output 'Release workflow policy test passed'
