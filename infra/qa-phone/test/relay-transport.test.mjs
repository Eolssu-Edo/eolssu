import {test} from 'node:test';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {fileURLToPath} from 'node:url';
test('a registered USB transport stays preferred while TLS discovery continues, then falls back when unplugged',async()=>{
  const source=fileURLToPath(new URL('../relay-transport.ps1',import.meta.url)).replaceAll("'","''");
  const script=". '"+source+"'; @((Get-QaRelayTransport -Identity PHONE936 -CurrentSerial 192.0.2.1:40000 -Endpoint 192.0.2.1:45000 -UsbConnected $true),(Get-QaRelayTransport -Identity PHONE936 -CurrentSerial PHONE936 -Endpoint 192.0.2.1:45000 -UsbConnected $false),(Get-QaRelayTransport -Identity PHONE936 -CurrentSerial PHONE936 -Endpoint '' -UsbConnected $false)) | ConvertTo-Json -Compress";
  const {stdout}=await promisify(execFile)('pwsh',['-NoProfile','-NonInteractive','-Command',script]);
  assert.deepEqual(JSON.parse(stdout),['PHONE936','192.0.2.1:45000','PHONE936']);
});

test('USB startup reuses only its own saved TLS registration and keeps recovery enabled',async()=>{
  const source=fileURLToPath(new URL('../relay-transport.ps1',import.meta.url)).replaceAll("'","''");
  const script=". '"+source+"'; $saved=[pscustomobject]@{configuredSerial='192.168.30.82:40000';endpoint='192.168.30.82:45000';identity='PHONE936'}; @((Get-QaWirelessRegistration -Serial PHONE936 -Saved $saved),(Get-QaWirelessRegistration -Serial OTHER936 -Saved $saved),(Get-QaWirelessRegistration -Serial 192.168.30.82:40000 -Saved $saved)) | ConvertTo-Json -Compress";
  const {stdout}=await promisify(execFile)('pwsh',['-NoProfile','-NonInteractive','-Command',script]);
  const [usb,other,wireless]=JSON.parse(stdout);
  assert.equal(usb.configuredSerial,'192.168.30.82:40000');assert.equal(usb.identity,'PHONE936');
  assert.equal(other,null);assert.deepEqual(wireless,usb);
});
