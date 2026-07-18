[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $PSScriptRoot '..\..\app\src\main\AndroidManifest.xml'
$manifest = Get-Content -LiteralPath $manifestPath -Raw

if ($manifest -notmatch 'android:allowBackup="false"') {
    throw 'Android backup must be disabled so local preferences are removed on uninstall'
}
if ($manifest -match 'android\.permission\.INTERNET') {
    throw 'The privacy model forbids declaring INTERNET permission'
}

Write-Output 'Privacy manifest policy test passed'
