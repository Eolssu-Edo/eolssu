#requires -Version 7.4
param([string]$Config = (Join-Path $env:LOCALAPPDATA 'EolssuQA/relay/supervisor.json'))
$ErrorActionPreference='Stop'
# Packaged Codex virtualizes AppData. Prefer its real path, usable by ordinary terminals too.
$qaNormal=Join-Path $env:LOCALAPPDATA 'EolssuQA/relay/supervisor.json'
$qaPhysical=Join-Path $env:LOCALAPPDATA 'Packages/OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/EolssuQA/relay/supervisor.json'
if([IO.Path]::GetFullPath($Config) -eq [IO.Path]::GetFullPath($qaNormal) -and (Test-Path -LiteralPath $qaPhysical)){$Config=$qaPhysical}
if(-not (Test-Path -LiteralPath $Config)){throw "QA 설정 파일이 없습니다: $Config"}
$qaRuntime=Split-Path -Parent $Config
. (Join-Path $PSScriptRoot 'relay-settings.ps1')
$qaConfig=Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
if($qaConfig.PSObject.Properties['releaseDirectory']){
  $qaRelease=(Resolve-Path -LiteralPath $qaConfig.releaseDirectory).Path
  $qaRoot=[IO.Path]::GetFullPath((Split-Path -Parent $qaRuntime)).TrimEnd('\')+'\'
  if(-not $qaRelease.StartsWith($qaRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'QA 릴리스는 QA 도구 폴더 안에 있어야 합니다.'}
  if($qaRelease -cne (Resolve-Path -LiteralPath $PSScriptRoot).Path){
    & (Join-Path $qaRelease 'supervise.ps1') -Config $Config
    return
  }
}
$qaParameters=Get-QaRelaySettings -Value $qaConfig -Runtime $qaRuntime
$qaStop=Join-Path $qaRuntime 'supervisor.disabled'
if(Test-Path -LiteralPath $qaStop){throw "QA가 중지 설정 상태입니다. supervisor.disabled 파일을 확인하세요: $qaStop"}
# Windows releases this exclusive handle on crashes and reboot; no stale PID lock.
try {$qaLock=[IO.File]::Open((Join-Path $qaRuntime 'supervisor.lock'),'OpenOrCreate','ReadWrite','None')}
catch {throw "QA 감독 잠금을 열 수 없습니다. 다른 감독 실행 여부 또는 파일 접근 권한을 확인하세요. 원인: $($_.Exception.GetBaseException().Message)"}
try {
  Write-Host 'QA 감독 실행 시작 · 종료: Ctrl+C'
  $qaLog=Join-Path $qaRuntime 'supervisor.log'
  while(-not (Test-Path -LiteralPath $qaStop)){
    $qaWirelessRestart=$false
    try {
      # Reconcile children left by an abrupt supervisor termination. Never kill arbitrary PIDs.
      $qaRecord=Join-Path $qaRuntime 'processes.json'
      if(Test-Path -LiteralPath $qaRecord){
        $qaOld=Get-Content -LiteralPath $qaRecord -Raw | ConvertFrom-Json
        if ((Split-Path -Parent ([IO.Path]::GetFullPath($qaOld.stopFile))) -ne [IO.Path]::GetFullPath($qaRuntime) -or (Split-Path -Leaf $qaOld.stopFile) -notmatch '^stop-[a-f0-9]{32}$') { throw '다른 기기의 종료 기록입니다.' }
        $qaNode=Get-CimInstance Win32_Process -Filter "ProcessId=$($qaOld.nodePid)" -ErrorAction SilentlyContinue
        $qaServer=Join-Path $PSScriptRoot 'server.mjs'
        if($qaNode -and $qaNode.Name -eq 'node.exe' -and $qaNode.CommandLine.Contains($qaServer)){
          [IO.File]::WriteAllText($qaOld.stopFile,'')
          Wait-Process -Id $qaOld.nodePid -Timeout 25 -ErrorAction SilentlyContinue
          if(Get-Process -Id $qaOld.nodePid -ErrorAction SilentlyContinue){throw 'Previous owned relay still stopping'}
        }
        $qaSsh=Get-CimInstance Win32_Process -Filter "ProcessId=$($qaOld.sshPid)" -ErrorAction SilentlyContinue
        $qaOldPort=if($qaOld.port){[int]$qaOld.port}else{17680}
        $qaOldReversePort=if($qaOld.reversePort){[int]$qaOld.reversePort}else{27890}
        $qaOldHost=if($qaOld.sshHost){$qaOld.sshHost}else{'edo2'}
        $qaOldForward="127.0.0.1:${qaOldReversePort}:127.0.0.1:${qaOldPort}"
        if($qaSsh -and $qaSsh.Name -eq 'ssh.exe' -and $qaSsh.CommandLine.Contains($qaOldForward) -and $qaSsh.CommandLine -match ('\b'+[regex]::Escape($qaOldHost)+'\s*$')){
          Stop-Process -Id $qaSsh.ProcessId -ErrorAction SilentlyContinue
        }
      }
      Add-Content $qaLog "$([DateTime]::UtcNow.ToString('o')) starting relay"
      & (Join-Path $PSScriptRoot 'start-public.ps1') @qaParameters -AutoRecover -SupervisorStopFile $qaStop -RestartForWirelessAddress ([ref]$qaWirelessRestart)
    } catch {
      # Avoid raw exceptions/command lines: they may contain credentials.
      Add-Content $qaLog "$([DateTime]::UtcNow.ToString('o')) relay stopped or unavailable; retry in 10 seconds"
      $qaWirelessRestart=$false
    }
    if($qaWirelessRestart){continue}
    for($qaWait=0;$qaWait -lt 10 -and -not (Test-Path -LiteralPath $qaStop);$qaWait++){Start-Sleep -Seconds 1}
  }
} finally {$qaLock.Dispose()}
