import {test} from 'node:test';
import assert from 'node:assert/strict';
import {recoverWireless,watchWireless,readWirelessState,queueWirelessPort,wirelessPortState} from '../wireless-recovery.mjs';
import {mkdtemp,rm,readFile,writeFile,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
const configuredSerial='192.168.30.82:39191';
const saved={configuredSerial,identity:'PHONE123',endpoint:configuredSerial};
function adb({online={},services='',devices='',connect=[]}={}){
  const calls=[];
  const run=async args=>{calls.push(args);
    if(args[0]==='-s'){if(online[args[1]])return online[args[1]];throw Error('offline');}
    if(args[0]==='mdns')return services;
    if(args[0]==='devices')return devices;
    if(args[0]==='connect'){connect.push(args[1]);return 'connected';}
    throw Error('unexpected command');
  };return {run,calls,connect};
}
test('explicit initial endpoint pins the hardware identity; failed bootstrap never guesses',async()=>{
  const a=adb({online:{[configuredSerial]:'PHONE123\n'}});
  const s=await recoverWireless({configuredSerial,run:a.run});assert.equal(s.identity,'PHONE123');assert.equal(s.status,'connected');
  const b=adb({services:'adb-OTHER-x _adb-tls-connect._tcp 192.168.30.99:33333'});
  assert.equal((await recoverWireless({configuredSerial,run:b.run})).status,'identity-required');assert.equal(b.connect.length,0);
});
test('changed mDNS port is connected only after exact identity verification',async()=>{
  const next='192.168.30.82:45555';const a=adb({online:{[next]:'PHONE123'},services:`adb-PHONE123-abc _adb-tls-connect._tcp ${next}\nadb-OTHER-abc _adb-tls-connect._tcp 192.168.30.83:44444`});
  const s=await recoverWireless({configuredSerial,state:saved,run:a.run});assert.equal(s.endpoint,next);assert.equal(s.status,'connected');assert.deepEqual(a.connect,[next,configuredSerial]);
});
test('already connected TLS endpoint can recover when multicast discovery is unavailable',async()=>{
  const next='192.168.30.82:45555';const a=adb({online:{[next]:'PHONE123'},devices:`List of devices attached\n${next}\tdevice\n`});
  assert.equal((await recoverWireless({configuredSerial,state:saved,run:a.run})).endpoint,next);
});
test('wrong identity, duplicate candidates, pairing advertisements and USB never switch the phone',async()=>{
  for(const options of [
    {online:{'192.168.30.82:45555':'OTHER'},services:'adb-PHONE123-x _adb-tls-connect._tcp 192.168.30.82:45555'},
    {online:{'192.168.30.82:45555':'PHONE123','192.168.30.82:45556':'PHONE123'},services:'adb-PHONE123-x _adb-tls-connect._tcp 192.168.30.82:45555\nadb-PHONE123-y _adb-tls-connect._tcp 192.168.30.82:45556'},
    {services:'adb-PHONE123-x _adb-tls-pairing._tcp 192.168.30.82:45555',devices:'PHONE123 device\nemulator-5554 device'},
  ]){const a=adb(options);const s=await recoverWireless({configuredSerial,state:saved,run:a.run});assert.equal(s.endpoint,configuredSerial);assert.notEqual(s.status,'connected');}
});
test('live configured phone is preferred; old cache cannot override a different configured target',async()=>{
  const a=adb({online:{[configuredSerial]:'PHONE123'}});assert.equal((await recoverWireless({configuredSerial,state:saved,run:a.run})).endpoint,configuredSerial);
  assert.equal(a.calls.length,1);
  const b=adb();const s=await recoverWireless({configuredSerial:'192.168.30.83:33333',state:saved,run:b.run});assert.equal(s.identity,undefined);assert.equal(s.endpoint,'192.168.30.83:33333');
});
test('watcher persists initial identity and changed port, then reloads safely after restart',async t=>{
  const root=await mkdtemp(join(tmpdir(),'qa-wireless-'));t.after(()=>rm(root,{recursive:true,force:true}));
  const file=join(root,'state.json'),controller=new AbortController();let changed=false;
  const next='192.168.30.82:45555';
  const run=async args=>{
    if(args[0]==='-s'){if(args[1]===(changed?next:configuredSerial))return 'PHONE123';throw Error('offline');}
    if(args[0]==='mdns')return `adb-PHONE123-xyz _adb-tls-connect._tcp ${next}`;
    return '';
  };
  const watcher=watchWireless({configuredSerial,file,run,signal:controller.signal,interval:5});
  const until=async predicate=>{const end=Date.now()+2000;while(Date.now()<end){try{const s=JSON.parse(await readFile(file,'utf8'));if(predicate(s))return s;}catch{}await delay(5);}throw Error('Expected saved state not reached');};
  try{
    await until(s=>s.identity==='PHONE123');changed=true;
    await until(s=>s.endpoint===next);controller.abort();await watcher;
    const loaded=await readWirelessState(file,configuredSerial);assert.equal(loaded.endpoint,next);
    assert.equal((await recoverWireless({configuredSerial,state:loaded,run})).endpoint,next);
    assert.equal(await readWirelessState(file,'192.168.30.83:33333'),null);
    assert.deepEqual(await readdir(root),['state.json']);
    await writeFile(file,'broken');await assert.rejects(readWirelessState(file,configuredSerial),/unreadable/);
  }finally{controller.abort();await watcher;}
});
test('manual port change verifies fixed phone identity, rejects unsafe input and is consumed by watcher',async t=>{
  const root=await mkdtemp(join(tmpdir(),'qa-wireless-'));t.after(()=>rm(root,{recursive:true,force:true}));const file=join(root,'state.json');await writeFile(file,JSON.stringify(saved));
  const next='192.168.30.82:37457',a=adb({online:{[next]:'PHONE123'}});
  for(const port of [0,65536,-1,'37457',3.4,null])await assert.rejects(queueWirelessPort({file,port,run:a.run}),/1~65535/);
  assert.equal(a.calls.length,0);
  await assert.rejects(queueWirelessPort({file,port:44444,run:a.run}),/등록된 기기/);
  assert.deepEqual(JSON.parse(await readFile(file,'utf8')),saved);
  await queueWirelessPort({file,port:37457,run:a.run});await assert.rejects(queueWirelessPort({file,port:37457,run:a.run}),/처리 중/);
  const controller=new AbortController();const watcher=watchWireless({configuredSerial,file,run:a.run,signal:controller.signal,interval:5});
  try{const end=Date.now()+2000;let state;do{state=await wirelessPortState(file);if(state.port===37457)break;await delay(5);}while(Date.now()<end);assert.equal(state.port,37457);assert.equal(state.available,true);}
  finally{controller.abort();await watcher;}
  assert.deepEqual(await readdir(root),['state.json']);
});
