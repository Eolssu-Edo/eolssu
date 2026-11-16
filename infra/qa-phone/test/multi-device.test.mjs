import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DeviceSession,registeredDevices} from '../public/device-session.js';
import {deviceEvidence} from '../live-events.mjs';
import {apkVersionState} from '../public/apk-update.js';

const origin='https://qa.example.test';
const descriptor=(id='one')=>({id,name:id,basePath:'/qa/'+id+'/'});
const canvas=()=>({width:10,height:20,hidden:false,dataset:{},getContext:()=>({clearRect(){}})});
const state=(epoch='epoch')=>({online:true,owner:'me',you:'me',epoch,now:Date.now(),width:100,height:200,peers:[{id:'me',name:'QA'}],requests:[]});
const response=(value,status=200,instance='relay-one')=>new Response(JSON.stringify(value),{status,headers:{'X-QA-Instance':instance,'Content-Type':'application/json'}});

test('the registry accepts up to six unique same-origin routes and rejects external or duplicate targets',()=>{
  assert.equal(registeredDevices(Array.from({length:6},(_,index)=>descriptor('d'+index)),origin).length,6);
  assert.throws(()=>registeredDevices(Array.from({length:7},(_,index)=>descriptor('d'+index)),origin));
  for(const basePath of ['https://other.test/qa/','//other.test/qa/','/qa/../admin/','/qa/?token=x'])assert.throws(()=>registeredDevices([{...descriptor(),basePath}],origin));
  assert.throws(()=>registeredDevices([descriptor(),{...descriptor('two'),basePath:'/qa/one/'}],origin));
});

test('inputs use the target device route, geometry, epoch and independent sequence',async t=>{
  const inputs=[];
  t.mock.method(globalThis,'fetch',async(url,options)=>{if(url.endsWith('/input'))inputs.push({url,packet:JSON.parse(options.body)});return response(url.endsWith('/state')?state():{ok:true});});
  const devices=['one','two'].map(id=>new DeviceSession(descriptor(id),{canvas:canvas()}));
  for(const device of devices){device.state=state();device.stateAt=Date.now();device.authorized=true;device.hasFrame=true;device.running=true;}
  await Promise.all(devices.map((device,index)=>device.input({kind:'tap',x:index/2,y:.5})));
  assert.deepEqual(inputs.map(value=>value.url),['/qa/one/api/input','/qa/two/api/input']);
  assert.deepEqual(inputs.map(value=>[value.packet.seq,value.packet.epoch,value.packet.width,value.packet.height]),[[1,'epoch',100,200],[1,'epoch',100,200]]);
  const before=inputs.length;await devices[0].input({kind:'tap',x:0,y:0},{...state(),epoch:'old'});assert.equal(inputs.length,before);
});

test('relay restart rejoins the existing name; idle expiry on the same instance cannot silently rejoin',async t=>{
  const device=new DeviceSession(descriptor(),{canvas:canvas(),onExpired:()=>expired++});let expired=0,calls=[];
  device.sessionName='QA';device.authorized=true;device.instance='before';device.running=true;
  t.mock.method(globalThis,'fetch',async(url,options)=>{calls.push(url);return calls.length===1?response({error:'expired'},401,'after'):response(url.endsWith('/state')?state():{ok:true},200,'after');});
  await device.readState();assert.deepEqual(calls,['/qa/one/api/state','/qa/one/api/login','/qa/one/api/state']);assert.equal(expired,0);assert.equal(device.name,'one');
  calls=[];t.mock.method(globalThis,'fetch',async url=>{calls.push(url);return response({error:'expired'},401,'after');});
  await assert.rejects(device.readState());assert.deepEqual(calls,['/qa/one/api/state']);assert.equal(expired,1);
});

test('device-wide logs include only local events and correlated server/AI/DB evidence',()=>{
  const own='00000000-0000-4000-8000-000000000001',other='00000000-0000-4000-8000-000000000002',at='2026-11-16T00:00:00Z';
  const evidence=deviceEvidence([{at,source:'app',kind:'request_started',requestId:own},{at,source:'backend',kind:'request_received',requestId:own},{at,source:'backend',kind:'request_received',requestId:other},{at,source:'observer',kind:'started',password:'secret'}]);
  assert.equal(evidence.total,3);assert.equal(evidence.records.filter(row=>row.source==='backend').length,1);assert.ok(!JSON.stringify(evidence).includes(other));assert.ok(!JSON.stringify(evidence).includes('secret'));
});

test('unknown installed version or failed catalog sync never claims that a device is latest',()=>{
  const build={id:'latest',package:'qa.test',versionCode:2,versionName:'two'},catalog={latest:'latest',builds:[build],installedStatus:'installed',installed:{...build}};
  assert.equal(apkVersionState(catalog).status,'latest');
  assert.equal(apkVersionState({...catalog,installedStatus:'absent',installed:null}).status,'outdated');
  assert.equal(apkVersionState({...catalog,installedStatus:'checking'}).status,'checking');
  assert.equal(apkVersionState({...catalog,installedStatus:'unavailable'}).status,'unavailable');
  assert.equal(apkVersionState({...catalog,sync:{error:'not reachable'}}).status,'unavailable');
  assert.equal(apkVersionState({...catalog,builds:[]}).status,'unavailable');
});

test('a late foreground or background status cannot restore an older pass badge',async t=>{
  const device=new DeviceSession(descriptor(),{canvas:canvas()}),pending=[];
  t.mock.method(globalThis,'fetch',()=>new Promise(resolve=>pending.push(resolve)));
  const old=device.liveStatus(),fresh=device.liveStatus();
  pending[1](response({scenario:{status:'running'}}));await fresh;
  pending[0](response({scenario:{status:'pass'}}));await old;
  assert.equal(device.liveSnapshot.scenario.status,'running');
});

test('a transient offline state keeps the authenticated video alive while geometry and transport recover',async t=>{
  const device=new DeviceSession(descriptor(),{canvas:canvas()});let starts=0,stops=0;
  device.video={start:()=>starts++,stop:()=>stops++,active:true};device.running=true;device.buildChecked=Date.now();
  t.after(()=>device.stop());t.mock.method(globalThis,'fetch',async()=>response({...state(),online:false}));
  await device.poll();assert.equal(starts,1);assert.equal(stops,0);assert.equal(device.hasFrame,false);assert.equal(device.inputReady,false);
});
