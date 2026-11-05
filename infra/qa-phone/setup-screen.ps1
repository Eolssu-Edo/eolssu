#requires -Version 7.4
param([string]$SshHost='edo1',[string]$ToolRoot=(Join-Path $env:LOCALAPPDATA 'EolssuQA/screen'))
$ErrorActionPreference='Stop'
if($SshHost -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$'){throw 'Invalid SSH alias'}
$qaSource=Join-Path $PSScriptRoot 'android/BrightScreen.java'
$qaTextSource=Join-Path $PSScriptRoot 'android/UnicodeText.java'
$qaTextHash=(Get-FileHash -LiteralPath $qaTextSource -Algorithm SHA256).Hash.ToLowerInvariant()
$qaSourceHash=(Get-FileHash -LiteralPath $qaSource -Algorithm SHA256).Hash.ToLowerInvariant()
$qaRoot=[IO.Path]::GetFullPath($ToolRoot)
$qaJar=Join-Path $qaRoot 'bright-screen.jar';$qaMeta=Join-Path $qaRoot 'bright-screen.json'
if((Test-Path -LiteralPath $qaJar) -and (Test-Path -LiteralPath $qaMeta)){
  $qaSaved=Get-Content -LiteralPath $qaMeta -Raw | ConvertFrom-Json
  if($qaSaved.sourceSha256 -eq $qaSourceHash -and $qaSaved.textSourceSha256 -eq $qaTextHash -and $qaSaved.sha256 -eq (Get-FileHash -LiteralPath $qaJar -Algorithm SHA256).Hash.ToLowerInvariant()){
    Write-Host "밝기 유지 도구 준비됨: $qaJar";return
  }
}
$qaOptions=@('-o','BatchMode=yes','-o','ClearAllForwardings=yes')
$qaRemote=(& ssh @qaOptions $SshHost 'mktemp -d /tmp/qa290-screen-build.XXXXXX' | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $qaRemote -notmatch '^/tmp/qa290-screen-build\.[A-Za-z0-9]+$'){throw '빌드 임시 폴더 생성 실패'}
$qaStaging=Join-Path $qaRoot ('.staging-'+[guid]::NewGuid().ToString('N'))
try{
  New-Item -ItemType Directory -Path $qaStaging -Force | Out-Null
  & scp @qaOptions $qaSource $qaTextSource (Join-Path $PSScriptRoot 'android/build.sh') "${SshHost}:${qaRemote}/"
  if($LASTEXITCODE -ne 0){throw '빌드 소스 복사 실패'}
  & ssh @qaOptions $SshHost "sed -i 's/\r$//' ${qaRemote}/build.sh && ANDROID_HOME=/opt/eolssu/android-sdk bash ${qaRemote}/build.sh ${qaRemote}/output"
  if($LASTEXITCODE -ne 0){throw '밝기 도구 빌드 실패'}
  & scp @qaOptions "${SshHost}:${qaRemote}/output/bright-screen.jar" "${SshHost}:${qaRemote}/output/bright-screen.json" $qaStaging
  if($LASTEXITCODE -ne 0){throw '빌드 결과 복사 실패'}
  $qaBuilt=Get-Content -LiteralPath (Join-Path $qaStaging 'bright-screen.json') -Raw | ConvertFrom-Json
  if($qaBuilt.sourceSha256 -ne $qaSourceHash -or $qaBuilt.textSourceSha256 -ne $qaTextHash -or $qaBuilt.leaseMs -ne 15000 -or $qaBuilt.sha256 -ne (Get-FileHash -LiteralPath (Join-Path $qaStaging 'bright-screen.jar') -Algorithm SHA256).Hash.ToLowerInvariant()){throw '빌드 무결성 확인 실패'}
  Move-Item -LiteralPath (Join-Path $qaStaging 'bright-screen.jar') -Destination $qaJar -Force
  Move-Item -LiteralPath (Join-Path $qaStaging 'bright-screen.json') -Destination $qaMeta -Force
}finally{
  & ssh @qaOptions $SshHost "rm -rf -- $qaRemote"
  $qaChecked=[IO.Path]::GetFullPath($qaStaging)
  if(-not $qaChecked.StartsWith($qaRoot+[IO.Path]::DirectorySeparatorChar)){throw 'Unexpected staging path'}
  if(Test-Path -LiteralPath $qaChecked){Remove-Item -LiteralPath $qaChecked -Recurse -Force}
}
Write-Host "밝기 유지 도구 준비됨: $qaJar"
