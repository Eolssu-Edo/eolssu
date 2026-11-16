function Get-QaRelayTransport {
    param([string]$Identity,[string]$CurrentSerial,[string]$Endpoint,[bool]$UsbConnected)
    if($Identity -cnotmatch '^[A-Za-z0-9_-]{4,80}$'){throw 'Registered Android identity required'}
    if($UsbConnected){return $Identity}
    if($Endpoint -match '^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$'){return $Endpoint}
    return $CurrentSerial
}
