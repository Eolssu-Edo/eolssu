function Test-QaWirelessEndpoint {
    param([string]$Value)
    if($Value -notmatch '^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})$'){return $false}
    $qaHost,$qaPort=$Value.Split(':')
    return @($qaHost.Split('.') | Where-Object {[int]$_ -gt 255}).Count -eq 0 -and [int]$qaPort -ge 1 -and [int]$qaPort -le 65535
}
function Get-QaWirelessRegistration {
    param([string]$Serial,$Saved)
    if($Saved -and $Saved.identity -cmatch '^[A-Za-z0-9_-]{4,80}$' -and
       (Test-QaWirelessEndpoint $Saved.configuredSerial) -and (Test-QaWirelessEndpoint $Saved.endpoint) -and
       ($Serial -ceq $Saved.identity -or $Serial -ceq $Saved.configuredSerial)){
        return [pscustomobject]@{configuredSerial=$Saved.configuredSerial;identity=$Saved.identity;endpoint=$Saved.endpoint}
    }
    if(Test-QaWirelessEndpoint $Serial){return [pscustomobject]@{configuredSerial=$Serial;identity=$null;endpoint=$Serial}}
    return $null
}
function Get-QaRelayTransport {
    param([string]$Identity,[string]$CurrentSerial,[string]$Endpoint,[bool]$UsbConnected)
    if($Identity -cnotmatch '^[A-Za-z0-9_-]{4,80}$'){throw 'Registered Android identity required'}
    if($UsbConnected){return $Identity}
    if(Test-QaWirelessEndpoint $Endpoint){return $Endpoint}
    return $CurrentSerial
}
