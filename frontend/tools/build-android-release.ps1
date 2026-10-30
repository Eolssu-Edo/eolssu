param(
    [ValidateRange(1, 2100000000)][int]$VersionCode = 2,
    [ValidateNotNullOrEmpty()][string]$VersionName = '1.0.0'
)
$ErrorActionPreference = 'Stop'
$required = 'EOLSSU_STORE_FILE', 'EOLSSU_STORE_PASSWORD', 'EOLSSU_KEY_ALIAS', 'EOLSSU_KEY_PASSWORD'
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing $name. Supply the approved upload key through the build environment."
    }
}
if (-not (Test-Path -LiteralPath $env:EOLSSU_STORE_FILE -PathType Leaf)) { throw 'Signing keystore does not exist.' }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # Do not store signing secrets in Gradle configuration-cache snapshots.
    & ./gradlew.bat :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:bundleRelease "-Peolssu.versionCode=$VersionCode" "-Peolssu.versionName=$VersionName" --no-configuration-cache --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw "Android checks/build failed ($LASTEXITCODE)." }
} finally { Pop-Location }
