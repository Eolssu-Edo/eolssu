param([Parameter(Mandatory)][string]$OutputDirectory,[switch]$IncludeLive)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'public') -Force | Out-Null
$destination=(Resolve-Path -LiteralPath $OutputDirectory).Path
if($destination -eq (Resolve-Path -LiteralPath $PSScriptRoot).Path){throw 'Choose a separate release directory'}
Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {$_.Extension -in @('.mjs','.ps1','.json')} | Copy-Item -Destination $destination -Force
Copy-Item -Path (Join-Path $PSScriptRoot 'public\*') -Destination (Join-Path $destination 'public') -Force
if($IncludeLive){
  $jar=Join-Path $PSScriptRoot '..\..\backend\build\libs\eolssu-0.0.1-SNAPSHOT.jar'
  if(-not (Test-Path -LiteralPath $jar)){throw 'Build the QA backend before packaging'}
  New-Item -ItemType Directory -Path (Join-Path $destination 'live\fixtures') -Force | Out-Null
  Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'live') -File | Where-Object {$_.Extension -in @('.py','.java','.txt','.Dockerfile')} | Copy-Item -Destination (Join-Path $destination 'live') -Force
  Copy-Item -Path (Join-Path $PSScriptRoot 'live\fixtures\*') -Destination (Join-Path $destination 'live\fixtures') -Force
  Copy-Item -LiteralPath $jar -Destination (Join-Path $destination 'live\backend.jar') -Force
}
foreach($required in @('server.mjs','supervise.ps1','scrcpy-manifest.json','public\app.js','public\live-panel.js')){
  if(-not (Test-Path -LiteralPath (Join-Path $destination $required) -PathType Leaf)){throw "Missing release file: $required"}
}
$hashes=Get-ChildItem -LiteralPath $destination -File -Recurse | Where-Object {$_.Name -ne 'release-manifest.json' -and $_.Extension -ne '.pyc' -and $_.FullName -notmatch '[\\/]__pycache__[\\/]'} | ForEach-Object {
  @{path=[IO.Path]::GetRelativePath($destination,$_.FullName);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
}
[IO.File]::WriteAllText((Join-Path $destination 'release-manifest.json'),(@{packagedUtc=[DateTime]::UtcNow.ToString('o');files=@($hashes)}|ConvertTo-Json -Depth 5))
Write-Output "QA release prepared: $destination"
