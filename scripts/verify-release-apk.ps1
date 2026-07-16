[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Tag,

    [Parameter(Mandatory)]
    [string]$ApkPath
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

function Resolve-Aapt {
    $command = Get-Command aapt, aapt.exe -ErrorAction SilentlyContinue |
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
        foreach ($name in @('aapt', 'aapt.exe')) {
            $candidate = Join-Path $directory.FullName $name
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }
    throw "aapt was not found under '$buildTools'"
}

$aapt = Resolve-Aapt
$badging = & $aapt dump badging $resolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "aapt failed to inspect '$resolvedApk'"
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

if ($actualPackage -ne 'com.mobisentinel.app') {
    throw "Expected package com.mobisentinel.app, found $actualPackage"
}
if ($actualVersionName -ne $versionName) {
    throw "Expected versionName $versionName, found $actualVersionName"
}
if ($actualVersionCode -ne $expectedVersionCode.ToString()) {
    throw "Expected versionCode $expectedVersionCode, found $actualVersionCode"
}

Write-Output (
    "Verified package={0} versionName={1} versionCode={2}" -f
        $actualPackage,
        $actualVersionName,
        $actualVersionCode
)
