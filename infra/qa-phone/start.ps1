param(
  [Parameter(Mandatory=$true)][string]$Serial,
  [string]$Adb = 'adb',
  [ValidateRange(1,65535)][int]$AdbPort = 5037,
  [ValidateRange(1,65535)][int]$Port = 17680
)
$ErrorActionPreference = 'Stop'
if ((& node -p 'process.versions.node.split(".")[0]') -ne '24') { throw 'Node.js 24 is required.' }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$' -or $Serial.StartsWith('emulator-')) { throw '실제 휴대폰 serial을 지정하세요.' }
& $Adb -P $AdbPort -s $Serial get-state
if ($LASTEXITCODE -ne 0) { throw 'USB 연결과 휴대폰의 디버깅 허용을 확인하세요.' }
$qaSecureToken = Read-Host '팀원에게 별도로 공유할 QA 접속 키 (24자 이상)' -AsSecureString
$qaTokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($qaSecureToken)
$qaPrevious = @{}
foreach ($qaName in @('QA_TOKEN','QA_SERIAL','QA_ADB','QA_ADB_PORT','QA_PORT','QA_SCREEN_SERVER','QA_VIDEO_SERVER')) { $qaPrevious[$qaName] = [Environment]::GetEnvironmentVariable($qaName, 'Process') }
try {
  $env:QA_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($qaTokenPointer)
  if ($env:QA_TOKEN.Length -lt 24) { throw 'QA 접속 키는 24자 이상이어야 합니다.' }
  $env:QA_SERIAL = $Serial; $env:QA_ADB = $Adb; $env:QA_ADB_PORT = [string]$AdbPort; $env:QA_PORT = [string]$Port
  $env:QA_SCREEN_SERVER=Join-Path $env:LOCALAPPDATA 'EolssuQA/screen/bright-screen.jar'
  if(-not (Test-Path -LiteralPath $env:QA_SCREEN_SERVER)){throw 'setup-screen.ps1을 먼저 실행하세요.'}
  $env:QA_VIDEO_SERVER=Join-Path $env:LOCALAPPDATA 'EolssuQA/scrcpy/5.0.1/scrcpy-server-v5.0.1'
  if (-not (Test-Path -LiteralPath $env:QA_VIDEO_SERVER)) { throw 'setup-video.ps1을 먼저 실행하세요.' }
  & node (Join-Path $PSScriptRoot 'server.mjs')
  if ($LASTEXITCODE -ne 0) { throw '중계 서버 시작 실패' }
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($qaTokenPointer)
  foreach ($qaName in $qaPrevious.Keys) { [Environment]::SetEnvironmentVariable($qaName, $qaPrevious[$qaName], 'Process') }
}
