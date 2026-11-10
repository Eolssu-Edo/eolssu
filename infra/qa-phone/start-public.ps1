#requires -Version 7.4
param(
  [Parameter(Mandatory=$true)][string]$Serial,
  [string]$Adb = 'adb',
  [ValidateRange(1,65535)][int]$AdbPort = 5037,
  [string]$SshHost = 'edo2',
  [string]$ApkSshHost = 'edo1',
  [string]$PublicOrigin = 'https://15.165.144.7',
  [string]$BasePath = '/qa/',
  [ValidatePattern('^[a-z][a-z0-9-]{0,31}$')][string]$DeviceId = 'phone',
  [ValidateLength(1,60)][string]$DeviceName = '휴대폰',
  [string]$RuntimeDirectory,
  [string]$ToolRoot,
  [ValidateRange(1,65535)][int]$Port = 17680,
  [ValidateRange(1,65535)][int]$ReversePort = 27890,
  [switch]$AutoRecover,
  [string]$SupervisorStopFile,
  [string]$ApplogTokenFile,
  [string]$ApplogDeviceIdentity,
  [string]$AccessKeyFile = (Join-Path $env:LOCALAPPDATA 'EolssuQA/relay/access-key.txt')
)
$ErrorActionPreference = 'Stop'
if ($PublicOrigin -notmatch '^https://[^/]+$') { throw '공개 주소는 후행 슬래시 없는 HTTPS origin이어야 합니다.' }
if ($SshHost -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') { throw 'Invalid SSH alias' }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$' -or $Serial.StartsWith('emulator-')) { throw '실제 폰 serial이 필요합니다.' }
if ($BasePath -notmatch '^/(?:[A-Za-z0-9_-]+/)*$') { throw '잘못된 QA 경로입니다.' }
if ($DeviceName -match '\p{C}' -or -not $DeviceName.Trim()) { throw '잘못된 기기 이름입니다.' }
if ($DeviceId -ne 'phone' -and ($BasePath -eq '/qa/' -or $Port -eq 17680 -or $ReversePort -eq 27890)) { throw '추가 기기는 전용 경로와 포트를 지정하세요.' }
if (-not (Test-Path -LiteralPath $AccessKeyFile)) { throw '사용자 전용 접속 키 파일이 필요합니다.' }
if ($ApplogTokenFile -or $ApplogDeviceIdentity) {
  if ($DeviceId -ne 'phone' -or $BasePath -ne '/qa/' -or $Port -ne 17680 -or $ReversePort -ne 27890 -or
      $ApplogDeviceIdentity -cnotmatch '^[A-Za-z0-9_-]{4,80}$' -or -not $ApplogTokenFile -or
      -not (Test-Path -LiteralPath $ApplogTokenFile -PathType Leaf)) {
    throw '앱 로그는 지정한 휴대폰의 접속 키 파일과 실제 기기 ID가 필요합니다.'
  }
}
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw '기존 QA 중계를 먼저 종료하세요.' }
$qaKey = [IO.File]::ReadAllText($AccessKeyFile).Trim()
if ($qaKey.Length -lt 24) { throw '접속 키는 24자 이상이어야 합니다.' }
$qaKeyDirectory = Split-Path -Parent (Resolve-Path -LiteralPath $AccessKeyFile)
$qaRuntime = if ($RuntimeDirectory) { (Resolve-Path -LiteralPath $RuntimeDirectory).Path } else { $qaKeyDirectory }
if ($DeviceId -ne 'phone' -and $qaRuntime -eq $qaKeyDirectory) { throw '추가 기기는 별도 RuntimeDirectory를 지정하세요.' }
$qaToolRoot = if ($ToolRoot) { (Resolve-Path -LiteralPath $ToolRoot).Path } else { Split-Path -Parent $qaKeyDirectory }
$qaPrevious = @{}
foreach ($qaName in @('QA_APPLOG_TOKEN','QA_APPLOG_DEVICE_IDENTITY','QA_EXPECTED_IDENTITY','QA_TOKEN','QA_SERIAL','QA_ADB','QA_ADB_PORT','QA_PORT','QA_ORIGIN','QA_BASE_PATH','QA_DEVICE_ID','QA_DEVICE_NAME','QA_TRUST_PROXY','QA_APK_DIR','QA_APK_SSH_HOST','QA_SCREEN_SERVER','QA_VIDEO_SERVER','QA_STOP_FILE','QA_WIRELESS_STATE')) { $qaPrevious[$qaName] = [Environment]::GetEnvironmentVariable($qaName,'Process') }
$qaNodeProcess = $null; $qaTunnelProcess = $null; $qaAdbChild=$null; $qaWirelessChild=$null
$qaConfiguredSerial=$Serial
$qaWirelessState=Join-Path $qaRuntime 'wireless-state.json'
try {
  $env:QA_APPLOG_TOKEN=$null
  $env:QA_APPLOG_DEVICE_IDENTITY=$null
  $env:QA_EXPECTED_IDENTITY=$null
  if($AutoRecover -and $Serial -match '^\d{1,3}(?:\.\d{1,3}){3}:\d+$' -and (Test-Path -LiteralPath $qaWirelessState)){
    $qaSavedWireless=Get-Content -LiteralPath $qaWirelessState -Raw | ConvertFrom-Json
    if($qaSavedWireless.configuredSerial -eq $qaConfiguredSerial -and $qaSavedWireless.identity -match '^[A-Za-z0-9_-]{4,80}$' -and $qaSavedWireless.endpoint -match '^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$'){$Serial=$qaSavedWireless.endpoint;$env:QA_EXPECTED_IDENTITY=$qaSavedWireless.identity}
  }
  $env:QA_STOP_FILE=Join-Path $qaRuntime ('stop-'+[guid]::NewGuid().ToString('N'))
  $env:QA_TOKEN=$qaKey; $env:QA_SERIAL=$Serial; $env:QA_ADB=$Adb; $env:QA_ADB_PORT=[string]$AdbPort; $env:QA_PORT=[string]$Port
  $env:QA_DEVICE_ID=$DeviceId; $env:QA_DEVICE_NAME=$DeviceName
  $env:QA_WIRELESS_STATE=if($AutoRecover -and $qaConfiguredSerial -match '^\d{1,3}(?:\.\d{1,3}){3}:\d+$'){$qaWirelessState}else{$null}
  $env:QA_SCREEN_SERVER=Join-Path $qaToolRoot 'screen/bright-screen.jar'
  if(-not (Test-Path -LiteralPath $env:QA_SCREEN_SERVER)){throw 'setup-screen.ps1을 먼저 실행하세요.'}
  $env:QA_VIDEO_SERVER=Join-Path $qaToolRoot 'scrcpy/5.0.1/scrcpy-server-v5.0.1'
  if (-not (Test-Path -LiteralPath $env:QA_VIDEO_SERVER)) { throw 'setup-video.ps1을 실행해 영상 도구를 준비하세요.' }
  $env:QA_APK_DIR=Join-Path $qaRuntime 'apks'; $env:QA_APK_SSH_HOST=$ApkSshHost
  $env:QA_ORIGIN=$PublicOrigin; $env:QA_BASE_PATH=$BasePath; $env:QA_TRUST_PROXY='1'
  $qaNode = (Get-Command node -ErrorAction Stop).Source
  $qaSsh = (Get-Command ssh -ErrorAction Stop).Source
  $qaServer = Join-Path $PSScriptRoot 'server.mjs'
  if($AutoRecover -and $qaConfiguredSerial -match '^\d{1,3}(?:\.\d{1,3}){3}:\d+$'){
    $qaWatcher=Join-Path $PSScriptRoot 'wireless-recovery.mjs'
    $qaWirelessArgs=@($qaWatcher,$Adb,[string]$AdbPort,$qaConfiguredSerial,$qaWirelessState) | ForEach-Object { '"'+$_+'"' }
    $qaWirelessChild=Start-Process -FilePath $qaNode -ArgumentList $qaWirelessArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $qaRuntime 'wireless.stdout.log') -RedirectStandardError (Join-Path $qaRuntime 'wireless.stderr.log')
  }
  try {
    if ($ApplogTokenFile) {
      $env:QA_APPLOG_TOKEN=[IO.File]::ReadAllText($ApplogTokenFile).Trim()
      if ($env:QA_APPLOG_TOKEN -cnotmatch '^[A-Za-z0-9_-]{32,128}$' -or $env:QA_APPLOG_TOKEN -ceq $qaKey) {
        throw '앱 로그 전용 접속 키 설정이 올바르지 않습니다.'
      }
      $env:QA_APPLOG_DEVICE_IDENTITY=$ApplogDeviceIdentity
    }
    $qaNodeProcess = Start-Process -FilePath $qaNode -ArgumentList @(('"'+$qaServer+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $qaRuntime 'relay.stdout.log') -RedirectStandardError (Join-Path $qaRuntime 'relay.stderr.log')
  } finally {
    $env:QA_APPLOG_TOKEN=$null
    $env:QA_APPLOG_DEVICE_IDENTITY=$null
  }
  $qaTunnelProcess = Start-Process -FilePath $qaSsh -ArgumentList @('-NT','-o','BatchMode=yes','-o','ConnectTimeout=10','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=20','-o','ServerAliveCountMax=3','-R',"127.0.0.1:${ReversePort}:127.0.0.1:${Port}",$SshHost) -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $qaRuntime 'tunnel.stderr.log')
  @{nodePid=$qaNodeProcess.Id;sshPid=$qaTunnelProcess.Id;stopFile=$env:QA_STOP_FILE;origin=$PublicOrigin;basePath=$BasePath;deviceId=$DeviceId;serial=$Serial;port=$Port;reversePort=$ReversePort;sshHost=$SshHost;startedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $qaRuntime 'processes.json') -Encoding utf8
  Write-Host "QA 접속 주소: $PublicOrigin$BasePath"
  Write-Host '팀원은 본인 이름과 접속 키로 로그인합니다. 이 실행을 유지하고 PC 절전을 해제하세요. 종료: Ctrl+C'
  $qaReconnectAt=[DateTime]::MinValue; $qaAdbAt=[DateTime]::MinValue; $qaAdbChild=$null
  while (-not $qaNodeProcess.HasExited) {
    if($SupervisorStopFile -and (Test-Path -LiteralPath $SupervisorStopFile)){break}
    if($qaWirelessChild){
      $qaWirelessChild.Refresh()
      if($qaWirelessChild.HasExited){throw 'Wireless discovery stopped; supervisor will retry'}
      if(Test-Path -LiteralPath $qaWirelessState){
        $qaWireless=Get-Content -LiteralPath $qaWirelessState -Raw | ConvertFrom-Json
        if($qaWireless.configuredSerial -eq $qaConfiguredSerial -and $qaWireless.status -eq 'connected' -and $qaWireless.identity -match '^[A-Za-z0-9_-]{4,80}$' -and $qaWireless.endpoint -match '^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$' -and $qaWireless.endpoint -ne $Serial){
          Write-Host '같은 휴대폰의 새 무선 주소를 확인했습니다. 중계를 정상 재시작합니다.'
          break
        }
      }
    }
    $qaTunnelProcess.Refresh()
    if($qaTunnelProcess.HasExited){
      if(-not $AutoRecover){throw 'SSH tunnel stopped'}
      if([DateTime]::UtcNow -ge $qaReconnectAt){
        $qaReconnectAt=[DateTime]::UtcNow.AddSeconds(10)
        $qaTunnelProcess=Start-Process -FilePath $qaSsh -ArgumentList @('-NT','-o','BatchMode=yes','-o','ConnectTimeout=10','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=20','-o','ServerAliveCountMax=3','-R',"127.0.0.1:${ReversePort}:127.0.0.1:${Port}",$SshHost) -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $qaRuntime 'tunnel.stderr.log')
        @{nodePid=$qaNodeProcess.Id;sshPid=$qaTunnelProcess.Id;stopFile=$env:QA_STOP_FILE;origin=$PublicOrigin;basePath=$BasePath;deviceId=$DeviceId;serial=$Serial;port=$Port;reversePort=$ReversePort;sshHost=$SshHost;startedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $qaRuntime 'processes.json') -Encoding utf8
        Write-Host 'SSH tunnel reconnect started'
      }
    }
    if($AutoRecover -and $Serial -match '^\d{1,3}(?:\.\d{1,3}){3}:\d+$' -and [DateTime]::UtcNow -ge $qaAdbAt){
      if($qaAdbChild -and -not $qaAdbChild.HasExited){$qaAdbChild.Kill();$qaAdbChild.WaitForExit()}
      $qaAdbChild=Start-Process -FilePath $Adb -ArgumentList @('-P',[string]$AdbPort,'connect',$Serial) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $qaRuntime 'adb-connect.log') -RedirectStandardError (Join-Path $qaRuntime 'adb-connect.stderr.log')
      $qaAdbAt=[DateTime]::UtcNow.AddSeconds(15)
    }
    Start-Sleep -Seconds 1
    $qaNodeProcess.Refresh()
  }
  if($qaNodeProcess.HasExited){throw 'QA relay stopped'}

} finally {
  if($qaWirelessChild -and -not $qaWirelessChild.HasExited){$qaWirelessChild.Kill();$qaWirelessChild.WaitForExit()}
  if($qaAdbChild -and -not $qaAdbChild.HasExited){$qaAdbChild.Kill()}
  if($null -ne $qaNodeProcess -and -not $qaNodeProcess.HasExited -and $env:QA_STOP_FILE){
    [IO.File]::WriteAllText($env:QA_STOP_FILE,'')
    Wait-Process -Id $qaNodeProcess.Id -Timeout 20 -ErrorAction SilentlyContinue
  }
  foreach ($qaProcess in @($qaNodeProcess,$qaTunnelProcess)) {
    if ($null -ne $qaProcess) { $qaProcess.Refresh(); if (-not $qaProcess.HasExited) { Stop-Process -Id $qaProcess.Id -ErrorAction SilentlyContinue } }
  }
  foreach ($qaName in $qaPrevious.Keys) { [Environment]::SetEnvironmentVariable($qaName,$qaPrevious[$qaName],'Process') }
  $qaKey=$null
}
