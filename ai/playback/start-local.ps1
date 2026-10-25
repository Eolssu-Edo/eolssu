param(
    [Parameter(Mandatory=$true)][string]$Assets,
    [Parameter(Mandatory=$true)][string]$Storage,
    [string]$Model = 'qwen3-vl:4b-instruct-q4_K_M',
    [string]$OllamaExe = "$env:LOCALAPPDATA\score-playback\ollama-0.40.10\ollama.exe",
    [string]$Models = "$env:LOCALAPPDATA\score-playback\models",
    [string]$Audiveris = "$env:LOCALAPPDATA\score-playback\audiveris-5.11.0\Audiveris.exe",
    [string]$JeongganAtlas = $env:PLAYBACK_JEONGGAN_ATLAS,
    [int]$Port = 8765
)
$ErrorActionPreference = 'Stop'
$aiDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$python = Join-Path $aiDirectory '..\.venv\Scripts\python.exe'
if (!(Test-Path -LiteralPath $python)) { throw 'Install requirements-service.txt in the repository .venv first.' }
if (!(Test-Path -LiteralPath $OllamaExe)) { throw 'Set -OllamaExe to an installed Ollama executable. See playback/README.md.' }
$assetFile = (Resolve-Path -LiteralPath $Assets).Path
$outputDirectory = [System.IO.Path]::GetFullPath($Storage)
if (![string]::IsNullOrWhiteSpace($JeongganAtlas)) {
    if (!(Test-Path -LiteralPath $JeongganAtlas -PathType Leaf)) { throw 'Set -JeongganAtlas to a local symbol-atlas manifest.json file.' }
    $env:PLAYBACK_JEONGGAN_ATLAS = (Resolve-Path -LiteralPath $JeongganAtlas).Path
}
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 2 }
catch {
    # Child-process settings only: no persistent user environment or startup registration.
    $env:OLLAMA_HOST = '127.0.0.1:11434'
    $env:OLLAMA_MODELS = $Models
    $env:OLLAMA_NUM_PARALLEL = '1'
    $env:OLLAMA_MAX_LOADED_MODELS = '1'
    $env:OLLAMA_NO_CLOUD = '1'
    Start-Process -FilePath $OllamaExe -ArgumentList 'serve' -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $outputDirectory 'ollama.stdout.log') `
        -RedirectStandardError (Join-Path $outputDirectory 'ollama.stderr.log') | Out-Null
    $ready = $false
    for ($attempt=0; $attempt -lt 20; $attempt++) {
        try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 1; $ready=$true; break }
        catch { Start-Sleep -Milliseconds 250 }
    }
    if (!$ready) { throw 'Ollama did not start; inspect ollama.stderr.log.' }
}
$installed = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags'
if ($Model -notin $installed.models.name) { throw "Install the local vision model first: ollama pull $Model" }
Push-Location $aiDirectory
try {
    $serviceArgs = @('-m','playback.service','--assets',$assetFile,'--storage',$outputDirectory,'--model',$Model,'--port',$Port)
    if (Test-Path -LiteralPath $Audiveris) { $serviceArgs += @('--audiveris',$Audiveris) }
    & $python @serviceArgs
}
finally { Pop-Location }
