#requires -Version 7.4
param([string]$ToolRoot=(Join-Path $env:LOCALAPPDATA 'EolssuQA/scrcpy'))
$ErrorActionPreference='Stop'
$qaManifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'scrcpy-manifest.json') -Raw | ConvertFrom-Json
$qaFolder=Join-Path $ToolRoot $qaManifest.version
$qaTarget=Join-Path $qaFolder ('scrcpy-server-v'+$qaManifest.version)
if(Test-Path -LiteralPath $qaTarget){
  if((Get-FileHash -LiteralPath $qaTarget -Algorithm SHA256).Hash.ToLowerInvariant() -ne $qaManifest.sha256){throw '기존 scrcpy 파일의 SHA256이 일치하지 않습니다.'}
  Write-Host "영상 도구 준비됨: $qaTarget"; return
}
New-Item -ItemType Directory -Path $qaFolder -Force | Out-Null
$qaDownload=Join-Path $qaFolder ('download-'+[guid]::NewGuid().ToString('N')+'.tmp')
try{
  Invoke-WebRequest -Uri $qaManifest.url -OutFile $qaDownload
  if((Get-FileHash -LiteralPath $qaDownload -Algorithm SHA256).Hash.ToLowerInvariant() -ne $qaManifest.sha256){throw 'scrcpy SHA256 확인 실패'}
  Move-Item -LiteralPath $qaDownload -Destination $qaTarget
}finally{if(Test-Path -LiteralPath $qaDownload){Remove-Item -LiteralPath $qaDownload}}
Write-Host "영상 도구 준비됨: $qaTarget"
