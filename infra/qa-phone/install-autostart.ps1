#requires -Version 7.4
param(
  [Parameter(Mandatory)][string]$RunAsUser,
  [Parameter(Mandatory)][string]$PowerShell,
  [Parameter(Mandatory)][string]$Config
)
$ErrorActionPreference='Stop'
if(-not [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Administrator permission required to register boot task'}
$qaAction=New-ScheduledTaskAction -Execute $PowerShell -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -File `"$PSScriptRoot/supervise.ps1`" -Config `"$Config`"" -WorkingDirectory $PSScriptRoot
$qaBoot=New-ScheduledTaskTrigger -AtStartup
$qaBoot.Delay='PT30S'
$qaLogin=New-ScheduledTaskTrigger -AtLogOn -User $RunAsUser
# Local files/explicit SSH key auth; no password saved, no administrator runtime.
$qaPrincipal=New-ScheduledTaskPrincipal -UserId $RunAsUser -LogonType S4U -RunLevel Limited
$qaSettings=New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName 'EolssuQA-PhoneRelay' -Action $qaAction -Trigger @($qaBoot,$qaLogin) -Principal $qaPrincipal -Settings $qaSettings -Description 'QA relay and SSH recovery; owned by SSAFY. See infra/qa-phone/OPERATIONS.md.' -Force | Out-Null
Start-ScheduledTask -TaskName 'EolssuQA-PhoneRelay'
