#requires -Version 7.4
param([string]$ToolRoot = (Join-Path $env:LOCALAPPDATA 'EolssuQA'))
$ErrorActionPreference = 'Stop'
$packagedRoot = Join-Path $env:LOCALAPPDATA 'Packages/OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/EolssuQA'
if (!$PSBoundParameters.ContainsKey('ToolRoot') -and (Test-Path -LiteralPath $packagedRoot)) { $ToolRoot = $packagedRoot }
$root = (Resolve-Path -LiteralPath $ToolRoot).Path
$target = Join-Path $root 'ssh_config'
$candidate = Join-Path $root ('ssh-config-' + [guid]::NewGuid().ToString('N') + '.tmp')
$ssh = (Get-Command ssh -ErrorAction Stop).Source
$lines = foreach ($alias in @('edo2','edo1')) {
  $effective = & $ssh -G $alias
  if ($LASTEXITCODE -ne 0) { throw "SSH 별칭 $alias 설정을 확인하세요." }
  $required = $effective | Where-Object { $_ -match '^(hostname|user|port|identityfile|identitiesonly|hostkeyalias) ' }
  if (!($required -match '^hostname ') -or !($required -match '^user ')) { throw "SSH 별칭 $alias 연결 정보가 없습니다." }
  "Host $alias"
  foreach ($line in $required) {
    if ($line -match '^identityfile (.+)$') { '  IdentityFile "' + $Matches[1].Replace('\','/') + '"' }
    else { '  ' + $line }
  }
  '  StrictHostKeyChecking yes'
}
try {
  $lines | Set-Content -LiteralPath $candidate -Encoding utf8NoBOM
  foreach ($alias in @('edo2','edo1')) {
    & $ssh -F $candidate -o BatchMode=yes -o ConnectTimeout=8 $alias true
    if ($LASTEXITCODE -ne 0) { throw "QA 전용 설정으로 $alias 연결을 확인하지 못했습니다." }
  }
  [IO.File]::Move($candidate,$target,$true)
  Write-Host "QA SSH 설정 준비 완료 $target"
} finally {
  if (Test-Path -LiteralPath $candidate) { Remove-Item -LiteralPath $candidate }
}
