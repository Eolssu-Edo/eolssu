import test from 'node:test';
import assert from 'node:assert/strict';
import {AdbPhone} from '../adb.mjs';
import {startRelay} from '../server.mjs';

function fixture(){
  let replaced=false,replacementIdentity='OTHER999';const calls=[];
  const data=Buffer.alloc(24);Buffer.from([137,80,78,71,13,10,26,10]).copy(data);data.writeUInt32BE(1440,16);data.writeUInt32BE(2304,20);
  const phone=new AdbPhone({adb:'fixture-adb',serial:'192.168.1.2:40000',identity:'TABLET123',executor:async(file,args)=>{
    calls.push(args);
    if(args.includes('devices'))return {stdout:`List of devices attached\n192.168.1.2:40000 device transport_id:${replaced?12:11}\n`};
    const index=args.indexOf('-t'),transport=index<0?null:args[index+1];
    if(replaced&&transport==='11')throw Error('transport disconnected');
    if(args.includes('ro.serialno'))return {stdout:(replaced?replacementIdentity:'TABLET123')+'\n'};
    if(args.includes('screencap'))return {stdout:data};
    return {stdout:''};
  }});
  return {phone,calls,replace:(identity='OTHER999')=>{replaced=true;replacementIdentity=identity;}};
}

test('verified transport is used for device operations and address reuse never targets another device',async()=>{
  const f=fixture();assert.equal((await f.phone.capture()).width,1440);
  assert.ok(f.calls.some(a=>a.includes('ro.serialno')));
  assert.ok(f.calls.filter(a=>!a.includes('devices')).every(a=>a.includes('-t')&&!a.includes('-s')));
  f.replace();const before=f.calls.length;
  await assert.rejects(f.phone.capture());
  await assert.rejects(f.phone.input({kind:'key',key:'HOME'},{width:1440,height:2304}));
  await assert.rejects(f.phone.install('/fixture.apk',{package:'com.ssafy.eolssu.qa'}));
  assert.equal(f.calls.slice(before).some(a=>a.includes('screencap')||a.includes('input')||a.includes('install')||a.includes('monkey')),false);
});

test('the same device can reconnect at the same endpoint with a new verified transport',async()=>{
  const f=fixture();await f.phone.capture();f.replace('TABLET123');
  await assert.rejects(f.phone.capture());
  assert.equal((await f.phone.capture()).height,2304);
  assert.deepEqual(await f.phone.deviceArgs(),['-P','5037','-t','12']);
});

test('a mismatched pinned identity cannot expose a screen or acquire control after restart',async t=>{
  const f=fixture();f.replace();
  const relay=await startRelay({phone:f.phone,port:0});t.after(()=>relay.close());
  const headers={Origin:relay.url,'Content-Type':'application/json'};
  const login=await fetch(relay.url+'/api/login',{method:'POST',headers,body:JSON.stringify({name:'fixture'})});
  headers.Cookie=login.headers.get('set-cookie').split(';')[0];
  const state=await(await fetch(relay.url+'/api/state',{headers})).json();assert.equal(state.online,false);
  assert.equal((await fetch(relay.url+'/api/request',{method:'POST',headers,body:'{}'})).status,409);
  assert.ok(f.calls.some(a=>a.includes('ro.serialno')));
  assert.equal(f.calls.some(a=>a.includes('screencap')||a.includes('input')||a.includes('install')),false);
});

test('identity loss revokes a live controller before any command reaches the replacement',async t=>{
  const f=fixture();
  const relay=await startRelay({phone:f.phone,port:0});t.after(()=>relay.close());
  const headers={Origin:relay.url,'Content-Type':'application/json'};
  const request=(path,body)=>fetch(relay.url+'/api/'+path,{headers,method:body===undefined?'GET':'POST',...(body===undefined?{}:{body:JSON.stringify(body)})});
  const login=await request('login',{name:'fixture'});headers.Cookie=login.headers.get('set-cookie').split(';')[0];
  await request('request',{});const before=await(await request('state')).json();assert.equal(before.owner,before.you);
  f.replace();const count=f.calls.length;
  const input=await request('input',{epoch:before.epoch,seq:1,sentAt:before.now,width:1440,height:2304,action:{kind:'key',key:'HOME'}});
  assert.equal(input.status,503);
  const after=await(await request('state')).json();assert.equal(after.online,false);assert.equal(after.owner,null);assert.equal(after.inputFault,true);
  assert.equal(f.calls.slice(count).some(a=>a.includes('input')||a.includes('install')),false);
});

test('a timed-out read-only probe keeps verified geometry so the next tap reaches the same device',async()=>{
  let probeTimeout=true;const calls=[];
  const data=Buffer.alloc(24);Buffer.from([137,80,78,71,13,10,26,10]).copy(data);data.writeUInt32BE(1440,16);data.writeUInt32BE(2304,20);
  const phone=new AdbPhone({adb:'fixture-adb',serial:'192.168.1.2:40000',identity:'TABLET123',executor:async(file,args)=>{
    calls.push(args);
    if(args.includes('devices'))return {stdout:'List of devices attached\n192.168.1.2:40000 device transport_id:11\n'};
    if(args.includes('ro.serialno'))return {stdout:'TABLET123\n'};
    if(args.includes('screencap'))return {stdout:data};
    // PhoneVideo.heartbeat tolerates this timeout while encoded frames keep arriving.
    if(args.includes('get-state')&&probeTimeout){probeTimeout=false;throw Object.assign(Error('probe timed out'),{killed:true});}
    return {stdout:''};
  }});
  await phone.capture();
  await assert.rejects(phone.run(['get-state']));
  const before=calls.length;
  await phone.input({kind:'tap',x:0.5,y:0.5},{width:1440,height:2304});
  const sent=calls.slice(before);
  assert.ok(sent.some(a=>a.includes('ro.serialno')),'identity is verified again after the failed probe');
  assert.deepEqual(sent.at(-1),['-P','5037','-t','11','shell','input','tap','720','1152']);
});

test('an identity change still clears the verified geometry before any input',async()=>{
  const f=fixture();await f.phone.capture();f.replace();
  await assert.rejects(f.phone.deviceArgs(),/transport disconnected/);
  await assert.rejects(f.phone.deviceArgs(),/identity mismatch/);
  assert.equal(f.phone.size,null);
  await assert.rejects(f.phone.input({kind:'tap',x:0.5,y:0.5},{width:1440,height:2304}),/screen geometry changed/);
});
