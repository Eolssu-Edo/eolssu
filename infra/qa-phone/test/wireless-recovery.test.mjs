import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createPortDiscovery,recoverWireless,watchWireless,readWirelessState,queueWirelessPort,wirelessPortState} from '../wireless-recovery.mjs';
import {mkdtemp,rm,readFile,writeFile,readdir} from 'node:fs/promises';
import {networkInterfaces,tmpdir} from 'node:os';
import {join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import {createSocket} from 'node:dgram';
import {createHmac} from 'node:crypto';
import {createWirelessPortHint} from '../wireless-port-hint.mjs';
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
test('the registered phone provides a TLS port over USB before scanning', async () => {
  const next = '192.168.30.82:45555';
  const calls = [];
  const run = async args => {
    calls.push(args);
    if (args[0] === '-s' && args[1] === saved.identity && args[2] === 'shell') return '45555\n';
    if (args[0] === 'connect' && args[1] === next) return 'connected';
    if (args[0] === '-s' && args[1] === next) return saved.identity;
    return '';
  };
  const wirelessPortHint=async()=>assert.fail('USB hint must take priority');
  const result = await recoverWireless({configuredSerial,state:saved,run,usbPortHint:true,wirelessPortHint,probeTls:async address=>address===next});
  assert.equal(result.endpoint, next);
  assert.equal(result.status, 'connected');
  assert.ok(!calls.some(args => args[0] === 'mdns'));
});
test('Wi-Fi unicast hints require both a TLS listener and the pinned device identity',async()=>{
  const host='192.168.30.82';
  const next=`${host}:45555`,wrong=`${host}:46666`,plain=`${host}:47777`;
  const a=adb({online:{[next]:saved.identity,[wrong]:'OTHER123',[plain]:saved.identity}});
  const result=await recoverWireless({configuredSerial,state:saved,run:a.run,usbPortHint:true,
    wirelessPortHint:async address=>{assert.equal(address,host);return [47777,46666,45555];},
    probeTls:async address=>address!==plain,discover:async()=>assert.fail('unicast resolved the port')});
  assert.equal(result.endpoint,next);
  assert.deepEqual(a.connect,[wrong,next]);
  assert.ok(!a.calls.some(args=>args[0]==='mdns'));
});
test('port hint ignores forged replies and does not query public addresses',async t=>{
  const host=Object.values(networkInterfaces()).flat()
    .find(item=>item?.family==='IPv4'&&/^(?:10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.)/.test(item.address))?.address;
  if(!host){t.skip('Private IPv4 interface required');return;}
  const key='a'.repeat(64),socket=createSocket('udp4');
  await new Promise((resolve,reject)=>socket.once('error',reject).bind(0,host,resolve));
  t.after(()=>socket.close());
  socket.on('message',(request,sender)=>{
    const [protocol,nonce,mac]=request.toString().split(' ');
    const sign=value=>createHmac('sha256',key).update(value).digest('hex');
    assert.equal(mac,sign(`${protocol} ${nonce}`));
    const message=`${protocol} ${nonce} 45555`;
    socket.send(`${protocol} ${nonce} 46666 ${'0'.repeat(64)}`,sender.port,sender.address);
    socket.send(`${message} ${sign(message)}`,sender.port,sender.address);
  });
  const hint=createWirelessPortHint(key,{port:socket.address().port,timeout:1500});
  assert.equal(await hint('203.0.113.1'),null);
  assert.deepEqual(await hint(host),[45555]);
});
test('invalid, unavailable or wrong-device USB hints fall back to ordinary recovery', async () => {
  const next = '192.168.30.82:45555';
  for (const hint of [null, '70000', '46666']) {
    const a = adb({online:{[next]:saved.identity,'192.168.30.82:46666':'OTHER123'},devices:`${next}\tdevice\n`});
    const run = async args => {
      if (args[1] !== saved.identity) return a.run(args);
      if (hint === null) throw Error('USB cable disconnected');
      return hint;
    };
    const result = await recoverWireless({configuredSerial,state:saved,run,usbPortHint:true,probeTls:async()=>true});
    assert.equal(result.endpoint, next);
    assert.ok(a.calls.some(args => args[0] === 'mdns'));
  }
});
test('wrong identity, duplicate candidates, pairing advertisements and USB never switch the phone',async()=>{
  for(const options of [
    {online:{'192.168.30.82:45555':'OTHER'},services:'adb-PHONE123-x _adb-tls-connect._tcp 192.168.30.82:45555'},
    {online:{'192.168.30.82:45555':'PHONE123','192.168.30.82:45556':'PHONE123'},services:'adb-PHONE123-x _adb-tls-connect._tcp 192.168.30.82:45555\nadb-PHONE123-y _adb-tls-connect._tcp 192.168.30.82:45556'},
    {services:'adb-PHONE123-x _adb-tls-pairing._tcp 192.168.30.82:45555',devices:'PHONE123 device\nemulator-5554 device'},
  ]){const a=adb(options);const s=await recoverWireless({configuredSerial,state:saved,run:a.run});assert.equal(s.endpoint,configuredSerial);assert.notEqual(s.status,'connected');}
});
test('without mDNS, only a verified endpoint on the registered private IP is adopted',async()=>{
  const wrong='192.168.30.82:39192',pairing='192.168.30.82:39193',next='192.168.30.82:39194';
  const probed=[];
  const discover=createPortDiscovery({probe:async address=>{probed.push(address);return [wrong,pairing,next].includes(address);}});
  discover.useDeviceRange('39191 39195');
  const a=adb({online:{[wrong]:'OTHER123',[next]:'PHONE123'},services:`adb-PHONE123-x _adb-tls-pairing._tcp ${pairing}`});
  const result=await recoverWireless({configuredSerial,state:saved,run:a.run,discover});
  assert.equal(result.endpoint,next);
  assert.deepEqual(probed,[wrong,'192.168.30.82:39195',next]);
  assert.deepEqual(a.connect,[configuredSerial,wrong,next]);
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
test('pairing resumes a paused discovery sweep without a manually applied port', async t => {
  const root = await mkdtemp(join(tmpdir(), 'qa-wireless-'));
  t.after(() => rm(root, {recursive:true, force:true}));
  const file = join(root, 'state.json');
  await writeFile(file, JSON.stringify(saved));
  const controller = new AbortController();
  const next = '192.168.30.82:42207';
  const online = {};
  const a = adb({online});
  let listening = false;
  const run = async args => {
    if (args[0] === '-s' && args[1] === next && args[2] === 'wait-for-device') {
      online[next] = saved.identity;
      return '';
    }
    return a.run(args);
  };
  const discover = createPortDiscovery({now: () => 0, probe: async address => listening && address === next});
  discover.useDeviceRange('42206 42207');
  // Complete a sweep while the phone is offline and the clock is held in its cooldown.
  assert.equal((await recoverWireless({configuredSerial,state:saved,run,discover})).status, 'searching');
  listening = true;
  await writeFile(file + '.paired', '');
  const watcher = watchWireless({configuredSerial,file,run,signal:controller.signal,interval:5,discover});
  try {
    const deadline = Date.now() + 750;
    let state;
    do {
      state = await readWirelessState(file, configuredSerial);
      if (state?.endpoint === next) break;
      await delay(5);
    } while (Date.now() < deadline);
    assert.equal(state?.endpoint, next);
    assert.equal(state?.status, 'connected');
    assert.deepEqual(await readdir(root), ['state.json']);
  } finally {
    controller.abort();
    await watcher;
  }
});
test('manual port change verifies fixed phone identity, rejects unsafe input and is consumed by watcher',async t=>{
  const root=await mkdtemp(join(tmpdir(),'qa-wireless-'));t.after(()=>rm(root,{recursive:true,force:true}));const file=join(root,'state.json');await writeFile(file,JSON.stringify(saved));
  const next='192.168.30.82:37457',a=adb({online:{[next]:'PHONE123'}});
  for(const port of [0,65536,-1,'37457',3.4,null])await assert.rejects(queueWirelessPort({file,port,run:a.run}),/1~65535/);
  assert.equal(a.calls.length,0);
  await assert.rejects(queueWirelessPort({file,port:44444,run:a.run,probe:async()=>false}),/포트에 접근할 수 없습니다/);
  await assert.rejects(queueWirelessPort({file,port:44444,run:a.run,probe:async()=>true}),/다시 페어링/);
  const other=adb({online:{[next]:'OTHER123'}});
  await assert.rejects(queueWirelessPort({file,port:37457,run:other.run}),/기기 ID가 등록된 기기와 다릅니다/);
  assert.deepEqual(await readdir(root),['state.json']);
  assert.deepEqual(JSON.parse(await readFile(file,'utf8')),saved);
  await queueWirelessPort({file,port:37457,run:a.run});await assert.rejects(queueWirelessPort({file,port:37457,run:a.run}),/처리 중/);
  const controller=new AbortController();const watcher=watchWireless({configuredSerial,file,run:a.run,signal:controller.signal,interval:5});
  try{const end=Date.now()+2000;let state;do{state=await wirelessPortState(file);if(state.port===37457)break;await delay(5);}while(Date.now()<end);assert.equal(state.port,37457);assert.equal(state.available,true);}
  finally{controller.abort();await watcher;}
  assert.deepEqual(await readdir(root),['state.json']);
});
