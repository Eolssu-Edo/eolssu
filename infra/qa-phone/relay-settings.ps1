# Pure configuration resolution; no service or file mutation.
function Get-QaRelaySettings {
  param([Parameter(Mandatory)]$Value, [Parameter(Mandatory)][string]$Runtime)
  $qaResult = @{
    Serial=$Value.serial; Adb=$Value.adb; AccessKeyFile=$Value.accessKeyFile
    RuntimeDirectory=$Runtime; DeviceId='phone'; DeviceName='휴대폰'
    Port=17680; ReversePort=27890; BasePath='/qa/'
    SshHost='edo2'; ApkSshHost='edo1'; PublicOrigin='https://15.165.144.7'; AdbPort=5037
  }
  foreach ($qaField in @('DeviceId','DeviceName','Port','ReversePort','BasePath','SshHost','ApkSshHost','PublicOrigin','AdbPort','ToolRoot')) {
    $qaProperty=$Value.PSObject.Properties[$qaField]
    if ($null -ne $qaProperty) { $qaResult[$qaField]=$qaProperty.Value }
  }
  if ($qaResult.Serial -notmatch '^[A-Za-z0-9._:-]+$' -or $qaResult.Serial.StartsWith('emulator-')) { throw '실제 기기 serial이 필요합니다.' }
  if (-not $qaResult.Adb -or -not $qaResult.AccessKeyFile) { throw 'ADB와 접속 키 경로가 필요합니다.' }
  if ($qaResult.DeviceId -notmatch '^[a-z][a-z0-9-]{0,31}$' -or -not $qaResult.DeviceName.Trim() -or $qaResult.DeviceName.Length -gt 60 -or $qaResult.DeviceName -match '\p{C}') { throw '잘못된 기기 이름입니다.' }
  foreach ($qaField in @('Port','ReversePort','AdbPort')) {
    if ([string]$qaResult[$qaField] -notmatch '^\d{1,5}$' -or [int]$qaResult[$qaField] -lt 1 -or [int]$qaResult[$qaField] -gt 65535) { throw '잘못된 포트입니다.' }
    $qaResult[$qaField]=[int]$qaResult[$qaField]
  }
  if ($qaResult.BasePath -notmatch '^/(?:[A-Za-z0-9_-]+/)*$' -or $qaResult.PublicOrigin -notmatch '^https://[^/]+$') { throw '잘못된 공개 경로입니다.' }
  foreach ($qaField in @('SshHost','ApkSshHost')) { if ($qaResult[$qaField] -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') { throw '잘못된 SSH alias입니다.' } }
  if ($qaResult.DeviceId -ne 'phone' -and ($qaResult.BasePath -eq '/qa/' -or $qaResult.Port -eq 17680 -or $qaResult.ReversePort -eq 27890)) { throw '추가 기기는 전용 경로와 포트가 필요합니다.' }
  return $qaResult
}
