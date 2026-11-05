#requires -Version 7.4
param([string]$Runtime=(Join-Path $env:LOCALAPPDATA 'EolssuQA/relay'))
$ErrorActionPreference='Stop'
$qaRoot=[IO.Path]::GetFullPath($Runtime).TrimEnd([IO.Path]::DirectorySeparatorChar)
$qaState=Get-Content -LiteralPath (Join-Path $qaRoot 'processes.json') -Raw | ConvertFrom-Json
$qaStop=[IO.Path]::GetFullPath($qaState.stopFile)
if((Split-Path -Parent $qaStop) -ne $qaRoot -or (Split-Path -Leaf $qaStop) -notmatch '^stop-[a-f0-9]{32}$'){throw '현재 중계의 종료 파일이 아닙니다.'}
$qaNode=Get-CimInstance Win32_Process -Filter ('ProcessId = '+[int]$qaState.nodePid)
if(-not $qaNode){Write-Host 'QA 중계가 이미 종료됐습니다.';return}
if($qaNode.Name -ne 'node.exe' -or $qaNode.CommandLine -notmatch 'infra[\\/]+qa-phone[\\/]+server\.mjs'){throw 'PID가 QA 중계와 일치하지 않습니다.'}
[IO.File]::WriteAllText($qaStop,'')
Wait-Process -Id $qaState.nodePid -Timeout 20 -ErrorAction SilentlyContinue
if(Get-Process -Id $qaState.nodePid -ErrorAction SilentlyContinue){throw '정상 종료를 기다리고 있습니다. 강제 종료하지 않았습니다.'}
Write-Host 'QA 중계가 정상 종료됐습니다. 영상 세션과 소유 ADB 전달을 정리했습니다.'
