import {test} from 'node:test';
import assert from 'node:assert/strict';
import {startRelay} from '../server.mjs';

const token='isolated-device-test-access-token';
const build={id:'20261102T023715Z-21-1dec028b',package:'com.ssafy.eolssu.qa',versionCode:21,versionName:'qa-21'};
async function device(t,id,basePath,width,height){
  const inputs=[]; let installed=null,finishInstall;
  const installPending=new Promise(resolve=>finishInstall=resolve);
  const data=Buffer.alloc(24);Buffer.from([137,80,78,71,13,10,26,10]).copy(data);data.writeUInt32BE(width,16);data.writeUInt32BE(height,20);
  const relay=await startRelay({port:0,token,basePath,deviceId:id,deviceName:id==='tablet'?'QA 태블릿':'QA 휴대폰',
    phone:{capture:async()=>({data,width,height}),input:async a=>inputs.push(a),installed:async()=>installed,
      install:async()=>{await installPending;installed=build;return installed;}},
    catalog:{list:()=>({builds:[build],latest:build.id}),file:async()=>'/isolated.apk'}});
  t.after(()=>relay.close());
  const request=(path,data,cookie)=>fetch(relay.publicUrl+'api/'+path,{method:data===undefined?'GET':'POST',
    headers:{Origin:relay.url,...(cookie?{Cookie:cookie}:{}),...(data===undefined?{}:{'Content-Type':'application/json'})},
    ...(data===undefined?{}:{body:JSON.stringify(data)})});
  const login=async()=>{const r=await request('login',{token,name:'Same team member'});assert.equal(r.status,200);return r.headers.get('set-cookie').split(';')[0];};
  return {relay,inputs,request,login,finishInstall};
}

test('nested tablet and parent phone cookies work in either order without cross-device access',async t=>{
  const phone=await device(t,'phone','/qa/',1080,2280),tablet=await device(t,'tablet','/qa/tablet/',2560,1600);
  const p=await phone.login(),q=await tablet.login();
  assert.match(p,/^qa_session=/);assert.match(q,/^qa_session_[a-f0-9]{16}=/);
  assert.equal((await phone.request('state',undefined,q)).status,401);
  assert.equal((await tablet.request('state',undefined,p)).status,401);
  for(const cookies of [`${p}; ${q}`,`${q}; ${p}`]){
    assert.equal((await phone.request('state',undefined,cookies)).status,200);
    assert.equal((await tablet.request('state',undefined,cookies)).status,200);
  }
  assert.deepEqual(await (await tablet.request('device')).json(),{id:'tablet',name:'QA 태블릿'});
  await phone.request('request',{},p);await tablet.request('request',{},q);
  const ps=await (await phone.request('state',undefined,p)).json(),ts=await (await tablet.request('state',undefined,q)).json();
  assert.equal(ps.owner,ps.you);assert.equal(ts.owner,ts.you);assert.notEqual(ps.epoch,ts.epoch);
  const packet={epoch:ts.epoch,seq:1,sentAt:ts.now,width:2560,height:1600,action:{kind:'tap',x:.7,y:.3}};
  assert.equal((await phone.request('input',packet,p)).status,409);
  assert.equal((await tablet.request('input',packet,q)).status,200);
  assert.equal(phone.inputs.length,0);assert.deepEqual(tablet.inputs,[packet.action]);
  await tablet.request('logout',{},q);
  assert.equal((await tablet.request('state',undefined,q)).status,401);
  assert.equal((await (await phone.request('state',undefined,p)).json()).owner,ps.you);
});

test('tablet installation locks only tablet; phone input and installed version stay independent',async t=>{
  const phone=await device(t,'phone','/qa/',1080,2280),tablet=await device(t,'tablet','/qa/tablet/',1600,2560);
  const p=await phone.login(),q=await tablet.login();
  await phone.request('request',{},p);await tablet.request('request',{},q);
  const ps=await (await phone.request('state',undefined,p)).json(),ts=await (await tablet.request('state',undefined,q)).json();
  try {
    assert.equal((await tablet.request('builds/install',{id:build.id,epoch:ts.epoch},q)).status,202);
    assert.equal((await (await tablet.request('state',undefined,q)).json()).maintenance,true);
    assert.equal((await (await phone.request('state',undefined,p)).json()).maintenance,false);
    const packet={epoch:ps.epoch,seq:1,sentAt:ps.now,width:1080,height:2280,action:{kind:'key',key:'HOME'}};
    assert.equal((await phone.request('input',packet,p)).status,200);
    assert.equal((await (await phone.request('builds',undefined,p)).json()).installed,null);
    tablet.finishInstall();
    // Await the observable result; no assumed fixed installation duration.
    let result;const deadline=Date.now()+2000;
    do{result=await (await tablet.request('builds',undefined,q)).json();}while(result.job.status==='installing'&&Date.now()<deadline);
    assert.equal(result.job.status,'success');assert.equal(result.installed.versionCode,21);
    assert.equal((await (await phone.request('builds',undefined,p)).json()).job.status,'idle');
  } finally { tablet.finishInstall(); }
});

test('device identity rejects invalid operator configuration',async()=>{
  for(const fields of [{deviceId:'tablet;id'},{deviceId:''},{deviceName:'x\ny'},{deviceName:' '}]){
    await assert.rejects(startRelay({token,...fields}),/identity/);
  }
});
