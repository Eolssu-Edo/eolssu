param(
    [Parameter(Mandatory)][string]$Adb,
    [Parameter(Mandatory)][string]$StateFile,
    [int]$AdbPort = 5037,
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = 'Stop'
function Invoke-Checked($Executable, $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Executable" }
}

$state = Get-Content -Raw -LiteralPath $StateFile | ConvertFrom-Json
if ($state.identity -notmatch '^[A-Za-z0-9_-]{4,80}$' -or
    $state.endpoint -notmatch '^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$') { throw 'Registered wireless device required' }
$target = @('-P', "$AdbPort", '-s', $state.endpoint)
$identity = Invoke-Checked $Adb ($target + @('shell', 'getprop', 'ro.serialno'))
if ($identity.Trim() -cne $state.identity) { throw 'Registered device identity mismatch' }

$work = (New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) "eolssu-port-hint-$([guid]::NewGuid())")).FullName
try {
    $source = Join-Path $PSScriptRoot 'android\WirelessPortHint.java'
    Invoke-Checked 'javac' @('--release', '8', '-d', $work, $source)
    $class = Join-Path $work 'com\ssafy\qa\WirelessPortHint.class'
    $d8 = Join-Path $AndroidSdk 'build-tools\37.0.0\d8.bat'
    $androidJar = Join-Path $AndroidSdk 'platforms\android-37.0\android.jar'
    $helper = Join-Path $work 'wireless-port-hint.jar'
    Invoke-Checked $d8 @('--min-api', '26', '--lib', $androidJar, '--output', $helper, $class)

    $keyFile = "$StateFile.hint-key"
    if (Test-Path -LiteralPath $keyFile) {
        $key = (Get-Content -Raw -LiteralPath $keyFile).Trim()
    } else {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $key = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    }
    if ($key -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid local port hint key' }
    $dir = '/data/local/tmp/eolssu-wireless-port'
    Invoke-Checked $Adb ($target + @('shell', 'umask 077; mkdir -p /data/local/tmp/eolssu-wireless-port; chmod 700 /data/local/tmp/eolssu-wireless-port'))
    Invoke-Checked $Adb ($target + @('push', $helper, "$dir/helper.jar.new"))
    $key | & $Adb @target shell -T 'umask 077; cat > /data/local/tmp/eolssu-wireless-port/key'
    if ($LASTEXITCODE -ne 0) { throw 'Could not provision device port hint key' }
    $launch = @'
set -e
cd /data/local/tmp/eolssu-wireless-port
if [ -f pid ]; then
  pid=$(cat pid)
  case "$pid" in
    ''|*[!0-9]*) ;;
    *) if tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q '^eolssu-wireless-port '; then kill "$pid"; fi ;;
  esac
fi
mv helper.jar.new helper.jar
chmod 400 helper.jar
CLASSPATH=$PWD/helper.jar setsid app_process /system/bin --nice-name=eolssu-wireless-port com.ssafy.qa.WirelessPortHint "$PWD/key" </dev/null >helper.log 2>&1 &
echo $! >pid
'@
    Invoke-Checked $Adb ($target + @('shell', '-T', $launch.Replace("`r", '')))
    [IO.File]::WriteAllText($keyFile, $key)
    Write-Output 'Wireless port hint helper provisioned on registered device.'
} finally {
    if ((Split-Path -Leaf $work) -match '^eolssu-port-hint-[a-f0-9-]{36}$' -and
        (Split-Path -Parent $work).TrimEnd('\') -eq ([IO.Path]::GetTempPath()).TrimEnd('\')) {
        Remove-Item -LiteralPath $work -Recurse -Force
    }
}
