param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]{4,100}$')][string]$Serial,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]{4,80}$')][string]$Identity,
    [Parameter(Mandatory)][string]$Adb,
    [Parameter(Mandatory)][string]$Python,
    [Parameter(Mandatory)][string]$RuntimeDirectory,
    [Parameter(Mandatory)][string]$RelayRuntime,
    [hashtable]$AdditionalRelays = @{},
    [string]$AiImage = 'eolssu-qa936-ai:20261115',
    [string]$BackendImage = 'eolssu-qa936-backend:20261115'
)
$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -State Listen -LocalPort 18785 -ErrorAction SilentlyContinue) { throw 'Live QA control already running' }
$actual = & $Adb -P 5037 -s $Serial shell getprop ro.serialno
if ($LASTEXITCODE -ne 0 -or $actual.Trim() -cne $Identity) { throw 'Registered Android identity mismatch' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $PSScriptRoot 'live\backend.jar'
if(-not (Test-Path -LiteralPath $jar)){$jar = Join-Path $repo 'backend\build\libs\eolssu-0.0.1-SNAPSHOT.jar'}
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the QA backend first' }
New-Item -ItemType Directory -Path $RuntimeDirectory -Force | Out-Null
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$token = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
$origin = 'http://127.0.0.1:18785'
$config = @{origin=$origin;token=$token;identity=$Identity;serial=$Serial;adb=(Resolve-Path $Adb).Path;adbPort=5037;jar=$jar;aiImage=$AiImage;backendImage=$BackendImage;runId=[guid]::NewGuid().ToString('N')}
$config.device=@{name=((& $Adb -P 5037 -s $Serial shell getprop ro.product.model).Trim());os=('Android '+(& $Adb -P 5037 -s $Serial shell getprop ro.build.version.release).Trim());connected=$true}
$appInfo=& $Adb -P 5037 -s $Serial shell dumpsys package com.ssafy.eolssu.qa
$version=$appInfo | Select-String '^\s*versionName=([A-Za-z0-9._+-]{1,100})\s*$' | Select-Object -First 1
if($version){$config.device.appVersion=$version.Matches[0].Groups[1].Value}
$config.devices=@{}
$config.devices[$Identity]=@{serial=$Serial;device=$config.device}
foreach($registered in $AdditionalRelays.Keys){
    if($registered -cnotmatch '^[A-Za-z0-9_-]{4,80}$'){throw 'Invalid additional Android identity'}
    if(-not (Test-Path -LiteralPath $AdditionalRelays[$registered] -PathType Container)){throw 'Additional relay directory missing'}
    $config.devices[$registered]=@{serial=$registered;device=@{name=$registered;connected=$false}}
    $rows=& $Adb -P 5037 devices
    if($rows | Where-Object {$_ -match ('^'+[regex]::Escape($registered)+'\s+device\s*$')}){
        $model=(& $Adb -P 5037 -s $registered shell getprop ro.product.model).Trim()
        $os=(& $Adb -P 5037 -s $registered shell getprop ro.build.version.release).Trim()
        $installed=& $Adb -P 5037 -s $registered shell dumpsys package com.ssafy.eolssu.qa
        $app=$installed|Select-String '^\s*versionName=([A-Za-z0-9._+-]{1,100})\s*$'|Select-Object -First 1
        $config.devices[$registered].device=@{name=$model;os=('Android '+$os);connected=$true}
        if($app){$config.devices[$registered].device.appVersion=$app.Matches[0].Groups[1].Value}
    }
}
$configFile = Join-Path $RuntimeDirectory 'control-config.json'
[IO.File]::WriteAllText($configFile, ($config | ConvertTo-Json -Depth 8))
$relayFile = Join-Path $RelayRuntime 'live-control.json'
[IO.File]::WriteAllText($relayFile, (@{origin=$origin;token=$token;identity=$Identity} | ConvertTo-Json))
foreach($registered in $AdditionalRelays.Keys){
    [IO.File]::WriteAllText((Join-Path $AdditionalRelays[$registered] 'live-control.json'), (@{origin=$origin;token=$token;identity=$registered} | ConvertTo-Json))
}
$service = Join-Path $PSScriptRoot 'live\control_service.py'
$runPath = Join-Path $RuntimeDirectory 'runtime'
$argsList = @('-u', ('"'+$service+'"'), '--config', ('"'+$configFile+'"'), '--runtime', ('"'+$runPath+'"'))
$child = Start-Process -FilePath $Python -ArgumentList $argsList -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $RuntimeDirectory 'control.stdout.log') -RedirectStandardError (Join-Path $RuntimeDirectory 'control.stderr.log')
[IO.File]::WriteAllText((Join-Path $RuntimeDirectory 'process.json'), (@{pid=$child.Id;config=$configFile;service=$service;identity=$Identity} | ConvertTo-Json))
Write-Output 'Isolated QA runtime starting. Check execution status in the QA page.'
