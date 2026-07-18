[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $PSScriptRoot '..\..\app\src\main\AndroidManifest.xml'
[xml]$manifest = Get-Content -LiteralPath $manifestPath -Raw
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$application = $manifest.DocumentElement.SelectSingleNode('./application')
$permissions = @(
    $manifest.DocumentElement.SelectNodes('./uses-permission') |
        ForEach-Object { $_.GetAttribute('name', $androidNamespace) }
)

if ($null -eq $application -or $application.GetAttribute('allowBackup', $androidNamespace) -ne 'false') {
    throw 'Android backup must be disabled so local preferences are removed on uninstall'
}
if ($permissions -contains 'android.permission.INTERNET') {
    throw 'The privacy model forbids declaring INTERNET permission'
}
if ($permissions -notcontains 'android.permission.VIBRATE') {
    throw 'Haptic alerts require declaring VIBRATE permission'
}
if ($permissions -contains 'android.permission.ACCESS_NOTIFICATION_POLICY') {
    throw 'The privacy model forbids declaring ACCESS_NOTIFICATION_POLICY permission'
}

Write-Output 'Privacy manifest policy test passed'
