import http from 'node:http';
import test from 'node:test';
import assert from 'node:assert/strict';
import {ApplogHost,ApplogPath} from '../applog.mjs';
import {startRelay,InstallWaitMessage} from '../server.mjs';

const serviceToken='fixture-app-log-service-key-32-chars';
const build={id:'20261117T000000Z-1-abcdef01',package:'com.ssafy.eolssu.qa',versionCode:12,versionName:'qa-12'};
const begin={op:'begin',request_id:'1'.repeat(32)};
const snapshot={...begin,stream_id:'2'.repeat(32),ack:5,end:6};

async function fixture(t,{installWaitMs}={}){
  let entered,release,fail;const reading=new Promise(resolve=>entered=resolve);
  const logRead=new Promise((resolve,reject)=>{release=resolve;fail=reject;});
  const installs=[],applogCalls=[];
  const catalog={list:()=>({builds:[build],latest:build.id}),file:async()=>'/fixture.apk',alias:async()=>{}};
  const relay=await startRelay({port:0,basePath:'/qa/',catalog,installWaitMs,applog:{token:serviceToken,identity:'PHONE_FIXTURE'},phone:{
    capture:async()=>({data:Buffer.alloc(24),width:1,height:1}),installed:async()=>null,
    applog:async()=>{applogCalls.push(Date.now());entered();await logRead;return JSON.stringify(snapshot);},
    install:async()=>{installs.push(build.id);return build;},
  }});
  t.after(()=>relay.close());
  const privateRequest=payload=>new Promise((resolve,reject)=>{
    const req=http.request(relay.url+ApplogPath,{method:'POST',headers:{Host:ApplogHost,'Content-Type':'application/json',Authorization:`Bearer ${serviceToken}`}},res=>{
      const chunks=[];res.on('data',chunk=>chunks.push(chunk));res.on('end',()=>resolve({status:res.statusCode,data:JSON.parse(Buffer.concat(chunks))}));res.on('error',reject);
    });req.on('error',reject);req.end(JSON.stringify(payload));
  });
  const request=(path,body,cookie)=>fetch(relay.url+'/qa/api/'+path,{method:body===undefined?'GET':'POST',
    headers:{Origin:relay.url,...(body===undefined?{}:{'Content-Type':'application/json'}),...(cookie?{Cookie:cookie}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
  const login=async name=>(await request('login',{name})).headers.get('set-cookie').split(';')[0];
  const builds=async cookie=>(await request('builds',undefined,cookie)).json();
  const settled=async cookie=>{for(;;){const value=await builds(cookie);if(!['waiting','installing'].includes(value.job.status))return value;await new Promise(resolve=>setTimeout(resolve,10));}};
  return {relay,reading,release,fail,installs,applogCalls,privateRequest,request,login,builds,settled};
}

async function owner(f){
  const cookie=await f.login('Alice');await f.request('request',{},cookie);
  return {cookie,state:await(await f.request('state',undefined,cookie)).json()};
}

test('an install requested during an app log read waits and then installs once without another click',async t=>{
  const f=await fixture(t),{cookie,state}=await owner(f);
  const read=f.privateRequest(begin);await f.reading;
  const accepted=await f.request('builds/install',{id:build.id,epoch:state.epoch},cookie);
  assert.equal(accepted.status,202);
  assert.deepEqual(await accepted.json(),{ok:true,waiting:true,message:InstallWaitMessage});
  assert.equal(InstallWaitMessage,'앱 로그를 읽고 있습니다. 잠시 후 설치됩니다.');
  const waiting=await f.builds(cookie);
  assert.equal(waiting.job.status,'waiting');assert.equal(waiting.job.message,InstallWaitMessage);assert.equal(waiting.installedStatus,'installing');
  // The pending install already holds the device: no second install, no new log read, no input.
  assert.equal((await f.request('builds/install',{id:build.id,epoch:state.epoch},cookie)).status,409);
  assert.deepEqual(await f.privateRequest(begin),{status:409,data:{error:'app_log_installing'}});
  assert.equal((await f.request('input',{epoch:state.epoch,seq:1,sentAt:state.now,width:1,height:1,action:{kind:'key',key:'HOME'}},cookie)).status,409);
  assert.deepEqual(f.installs,[]);
  f.release();assert.equal((await read).status,200);
  const done=await f.settled(cookie);
  assert.equal(done.job.status,'success');assert.equal(done.job.message,undefined);
  assert.deepEqual(f.installs,[build.id]);assert.equal(f.applogCalls.length,1);
  assert.equal((await(await f.request('state',undefined,cookie)).json()).maintenance,false);
});

test('a failed app log read still lets the waiting install continue',async t=>{
  const f=await fixture(t),{cookie,state}=await owner(f);
  const read=f.privateRequest(begin);await f.reading;
  assert.equal((await f.request('builds/install',{id:build.id,epoch:state.epoch},cookie)).status,202);
  f.fail(Error('fixture log failure'));assert.equal((await read).status,503);
  assert.equal((await f.settled(cookie)).job.status,'success');assert.deepEqual(f.installs,[build.id]);
});

test('a log read that outlasts the wait limit cancels only the install and releases the device',async t=>{
  const f=await fixture(t,{installWaitMs:30}),{cookie,state}=await owner(f);
  const read=f.privateRequest(begin);await f.reading;
  assert.equal((await f.request('builds/install',{id:build.id,epoch:state.epoch},cookie)).status,202);
  const done=await f.settled(cookie);
  assert.equal(done.job.status,'failed');assert.match(done.job.error,/앱 로그 읽기가 끝나지 않아 설치하지 않았습니다/);
  assert.deepEqual(f.installs,[]);
  assert.equal((await(await f.request('state',undefined,cookie)).json()).maintenance,false);
  f.release();assert.equal((await read).status,200);
});

test('without a pending log read the install starts immediately as before',async t=>{
  const f=await fixture(t),{cookie,state}=await owner(f);
  const accepted=await f.request('builds/install',{id:build.id,epoch:state.epoch},cookie);
  assert.equal(accepted.status,202);assert.deepEqual(await accepted.json(),{ok:true});
  assert.equal((await f.settled(cookie)).job.status,'success');assert.deepEqual(f.installs,[build.id]);
});
