import http from 'node:http';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startRelay } from '../server.mjs';

test('name-only entry needs no configured password and duplicate names have independent control',async t=>{
 const relay=await startRelay({port:0,phone:{capture:async()=>({data:Buffer.from('fixture'),width:1,height:1}),input:async()=>{}}});t.after(()=>relay.close());
 const post=(path,body,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(body)});
 assert.equal((await fetch(relay.url+'/api/state')).status,401);
 const first=await post('login',{name:'은서'});assert.equal(first.status,200);const a=first.headers.get('set-cookie').split(';')[0];
 const second=await post('login',{name:'은서'});assert.equal(second.status,200);const b=second.headers.get('set-cookie').split(';')[0];assert.notEqual(a,b);
 assert.equal((await post('request',{},a)).status,200);
 assert.equal((await post('release',{},b)).status,403);
 for(const name of ['', '   ', 'a'.repeat(33), 'a\nb', null])assert.equal((await post('login',{name})).status,400);
 assert.equal((await post('login',{name:' 영민 '})).status,200);
});
const data = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==', 'base64');
async function fixture(t, failInput = false, extra = {}) {
  const calls = [];
  const relay = await startRelay({ port: 0, ...extra, phone: {
    ...extra.phone, capture: async () => ({ data, width: 1, height: 1 }), input: async a => { if (failInput) throw new Error('test transport failure'); calls.push(a); },
  }});
  t.after(() => relay.close());
  async function request(path, body, cookie, origin = relay.url) {
    return fetch(relay.url + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { Origin: origin, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(cookie ? { Cookie: cookie } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  }
  async function login(name) {
    const r = await request('/api/login', { name }); assert.equal(r.status, 200);
    return r.headers.get('set-cookie').split(';')[0];
  }
  return { relay, calls, request, login };
}
test('Wi-Fi pairing requires a session and the operator key before invoking ADB', async t => {
  const key = 'a'.repeat(64);
  const calls = [];
  const {request, login} = await fixture(t, false, {
    pairingKey:key,
    wireless:{
      state:async()=>({available:true,port:40519}),
      pair:async(port,code)=>{calls.push([port,code]);return {paired:true};},
    },
  });
  const path = '/api/wireless/pair';
  const payload = {key,port:37631,code:'012345'};
  assert.equal((await request(path,payload)).status,401);
  const viewer = await login('참여자');
  assert.equal((await request(path,payload,viewer,'https://other.example')).status,403);
  assert.equal((await request(path,{...payload,key:'b'.repeat(64)},viewer)).status,403);
  assert.deepEqual(calls,[]);
  const state = await (await request('/api/state',undefined,viewer)).json();
  assert.equal(state.wireless.pairing,true);
  assert.equal(JSON.stringify(state).includes(key),false);
  assert.deepEqual(await (await request(path,payload,viewer)).json(),{paired:true});
  assert.deepEqual(calls,[[37631,'012345']]);
  assert.equal((await request(path,payload,viewer)).status,429);
});
test('unauthenticated screen/API and cross-origin writes rejected', async t => {
  const { request, login } = await fixture(t);
  for (const path of ['/api/state', '/api/frame']) assert.equal((await request(path)).status, 401);
  assert.equal((await request('/api/login', { name: '' })).status, 400);
  assert.equal((await request('/api/login', { name: 'a' }, null, 'https://evil.test')).status, 403);
  const a = await login('Alice');
  assert.equal((await request('/api/request', {}, a, 'https://evil.test')).status, 403);
  assert.equal((await request('/api/frame', undefined, a)).headers.get('content-type'), 'image/png');
});
test('header port recovery requires login and current operator, accepts offline recovery and validates numeric port',async t=>{
  const changes=[];const f=await fixture(t,false,{wireless:{state:async()=>({available:true,port:44185}),change:async port=>{changes.push(port);return {port,restarting:true};}}});
  assert.equal((await f.request('/api/wireless',{port:37457})).status,401);
  const a=await f.login('Alice'),b=await f.login('Bob');
  await f.request('/api/request',{},a);
  assert.equal((await f.request('/api/wireless',{port:37457},b)).status,403);
  for(const port of [0,65536,'37457',3.4])assert.equal((await f.request('/api/wireless',{port},a)).status,400);
  assert.equal((await f.request('/api/wireless',{port:37457},a)).status,202);
  await f.relay.control.setOnline(false);
  assert.equal((await f.request('/api/wireless',{port:37458},b)).status,202);
  assert.deepEqual(changes,[37457,37458]);
  assert.equal((await (await f.request('/api/state',undefined,b)).json()).wireless.port,44185);
});
test('two independent HTTP clients view same frame; owner transfers; old input refused', async t => {
  const { calls, request, login } = await fixture(t); const a = await login('Alice'), b = await login('Bob');
  assert.deepEqual(Buffer.from(await (await request('/api/frame', undefined, a)).arrayBuffer()), data);
  assert.deepEqual(Buffer.from(await (await request('/api/frame', undefined, b)).arrayBuffer()), data);
  const state = async cookie => (await request('/api/state', undefined, cookie)).json();
  assert.equal((await state(a)).owner, null);
  await request('/api/request', {}, a); await request('/api/request', {}, b);
  const first = await state(a), second = await state(b);
  const packet = { epoch: first.epoch, seq: 1, sentAt: first.now, width: 1, height: 1, action: { kind: 'key', key: 'BACK' } };
  assert.equal((await request('/api/input', packet, b)).status, 403);
  assert.equal((await request('/api/input', packet, a)).status, 200);
  assert.equal((await request('/api/transfer', { target: second.you }, a)).status, 200);
  assert.equal((await request('/api/input', { ...packet, seq: 2 }, a)).status, 403);
  const updated = await state(b);
  assert.equal((await request('/api/input', { ...packet, epoch: updated.epoch, seq: 1, sentAt: updated.now }, b)).status, 200);
  assert.equal(calls.length, 2);
  await request('/api/logout', {}, b); assert.equal((await state(a)).owner, null);
  assert.equal((await request('/api/state', undefined, b)).status, 401);
});
test('unknown routes, oversized bodies and invalid JSON return controlled errors', async t => {
  const { request, relay } = await fixture(t);
  assert.equal((await request('/../server.mjs')).status, 404);
  assert.equal((await request('/api/login', { name: 'a'.repeat(5000) })).status, 413);
  const r = await fetch(relay.url + '/api/login', { method: 'POST', headers: { Origin: relay.url, 'Content-Type': 'application/json' }, body: '{' });
  assert.equal(r.status, 400);
});

test('public peer identifier cannot be used as another client session cookie', async t => {
  const { request, login } = await fixture(t); const a = await login('Alice'), b = await login('Bob');
  const state = await (await request('/api/state', undefined, b)).json();
  const alice = state.peers.find(p => p.name === 'Alice');
  assert.equal((await request('/api/request', {}, `qa_session=${alice.id}`)).status, 401);
  assert.ok(!JSON.stringify(state).includes(a.split('=')[1]));
});

test('input failure revokes control; transport recovery requires idle input and a fresh screen, never replaying input', async t => {
  let idle=false,screen=true,attempts=0,probes=0;
  const relay=await startRelay({port:0,phone:{
    capture:async()=>{if(!screen)throw Error('screen unavailable');return {data,width:1,height:1};},
    input:async()=>{attempts++;if(attempts===1)throw Error('transient transport failure');},
    inputReady:async()=>{probes++;return idle;},
  }});t.after(()=>relay.close());
  const send=(path,body,cookie)=>fetch(relay.url+path,{method:body===undefined?'GET':'POST',headers:{Origin:relay.url,...(body===undefined?{}:{'Content-Type':'application/json'}),...(cookie?{Cookie:cookie}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
  const login=await send('/api/login',{name:'Alice'}),cookie=login.headers.get('set-cookie').split(';')[0];
  const state=async()=>(await send('/api/state',undefined,cookie)).json();
  const until=async check=>{const deadline=Date.now()+6000;while(Date.now()<deadline){if(await check())return;await new Promise(r=>setTimeout(r,25));}assert.fail('recovery condition not reached');};
  await send('/api/request',{},cookie);const before=await state();
  const packet={epoch:before.epoch,seq:1,sentAt:before.now,width:1,height:1,action:{kind:'key',key:'BACK'}};
  assert.equal((await send('/api/input',packet,cookie)).status,503);
  let failed=await state();assert.equal(failed.owner,null);assert.equal(failed.online,false);assert.equal(failed.inputFault,true);
  await until(()=>probes>0);assert.equal((await state()).inputFault,true);assert.equal((await send('/api/request',{},cookie)).status,409);
  screen=false;idle=true;const previous=probes;await until(()=>probes>previous);assert.equal((await state()).inputFault,true);
  screen=true;await until(async()=>!(await state()).inputFault);
  const recovered=await state();assert.equal(recovered.online,true);assert.equal(recovered.owner,null);assert.notEqual(recovered.epoch,before.epoch);assert.equal(attempts,1);
  assert.equal((await send('/api/input',packet,cookie)).status,403);
  await send('/api/request',{},cookie);const fresh=await state();
  assert.equal((await send('/api/input',packet,cookie)).status,409);
  assert.equal((await send('/api/input',{...packet,epoch:fresh.epoch,sentAt:fresh.now},cookie)).status,200);assert.equal(attempts,2);
});

test('input stays blocked when the phone adapter cannot prove old input has stopped',async t=>{
 const {request,login}=await fixture(t,true);const cookie=await login('Alice');await request('/api/request',{},cookie);
 const state=await(await request('/api/state',undefined,cookie)).json();
 assert.equal((await request('/api/input',{epoch:state.epoch,seq:1,sentAt:state.now,width:1,height:1,action:{kind:'key',key:'BACK'}},cookie)).status,503);
 const failed=await(await request('/api/state',undefined,cookie)).json();assert.equal(failed.online,false);assert.equal(failed.owner,null);assert.equal(failed.inputFault,true);
 assert.equal((await request('/api/request',{},cookie)).status,409);
});

test('Host binding and browser security headers are enforced', async t => {
  const { relay } = await fixture(t);
  const wrongStatus = await new Promise((resolve,reject) => {
    http.get(relay.url + '/', {headers:{Host:'other.test'}}, res => { res.resume(); resolve(res.statusCode); }).on('error',reject);
  });
  assert.equal(wrongStatus,403);
  const page = await fetch(relay.url + '/'); assert.equal(page.status,200);
  assert.ok(page.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
  assert.ok(page.headers.get('permissions-policy').includes('microphone=()'));
  assert.equal(page.headers.get('cache-control'),'no-store');
  for (const asset of ['/app.js','/style.css','/apk-update.js']) assert.equal((await fetch(relay.url+asset)).status,200);
});
test('invalid names are throttled without unlimited login work', async t => {
  const { request } = await fixture(t);
  for (let i=0;i<10;i++) assert.equal((await request('/api/login',{name:''})).status,400);
  assert.equal((await request('/api/login',{name:''})).status,429);
});

test('subpath deployment scopes API, assets and session cookie', async t => {
  const relay=await startRelay({port:0,basePath:'/qa/',phone:{capture:async()=>({data,width:1,height:1}),input:async()=>{}}});
  t.after(()=>relay.close());
  assert.equal((await fetch(relay.url+'/api/state')).status,404);
  assert.equal((await fetch(relay.url+'/qa/')).status,200);
  const html=await (await fetch(relay.url+'/qa/')).text();
  assert.doesNotMatch(html.match(/<form id="loginForm">[\s\S]*?<\/form>/)[0],/type="password"/);
  assert.ok(html.includes('href="./style.css"'));assert.ok(html.includes('src="./app.js"'));
  const r=await fetch(relay.url+'/qa/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json'},body:JSON.stringify({name:'Team member'})});
  assert.equal(r.status,200);assert.ok(r.headers.get('set-cookie').includes('Path=/qa/'));
  const cookie=r.headers.get('set-cookie').split(';')[0];
  assert.equal((await fetch(relay.url+'/qa/api/frame',{headers:{Cookie:cookie}})).status,200);
});
test('only configured proxy mode trusts validated real client address for login limits', async t => {
  const relay=await startRelay({port:0,trustProxy:true,phone:{capture:async()=>({data,width:1,height:1}),input:async()=>{}}});
  t.after(()=>relay.close());
  const login=(ip,name)=>fetch(relay.url+'/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json','X-Real-IP':ip},body:JSON.stringify({name})});
  for(let i=0;i<10;i++)assert.equal((await login('192.0.2.1','')).status,400);
  assert.equal((await login('192.0.2.1','Test')).status,429);
  assert.equal((await login('192.0.2.2','Test')).status,200);
});

test('direct mode ignores forged proxy headers when limiting login attempts', async t => {
  const { relay }=await fixture(t);
  for(let i=0;i<11;i++){
    const r=await fetch(relay.url+'/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json','X-Real-IP':`192.0.2.${i+1}`},body:JSON.stringify({name:''})});
    assert.equal(r.status,i<10?400:429);
  }
});
test('deployment base path rejects ambiguous or unsafe prefixes', async () => {
  for(const basePath of ['/qa','//','/../','/qa/?x=1','/qa space/'])await assert.rejects(startRelay({basePath}),/BASE_PATH/);
});


test('APK installation requires fresh owner lease and blocks input while preserving viewers', async t => {
 let complete,started; const active=new Promise(r=>started=r),done=new Promise(r=>complete=r);
 const build={id:'20261030T031000Z-12-abcdef01',package:'com.ssafy.eolssu.qa',versionCode:12,versionName:'qa-12'};
 const catalog={list:()=>({builds:[build],latest:build.id}),file:async()=>'/fixture.apk',alias:async()=>{}};
 const {request,login}=await fixture(t,false,{catalog,phone:{install:async()=>{started(); await done;return build;},installed:async()=>null}});
 const a=await login('Alice'),b=await login('Bob');
 assert.equal((await request('/api/builds')).status,401);
 assert.equal((await request('/api/builds',undefined,b)).status,200);
 assert.equal((await request('/api/builds/install',{id:build.id},b)).status,403);
 await request('/api/request',{},a); const state=await (await request('/api/state',undefined,a)).json();
 assert.equal((await request('/api/builds/install',{id:build.id,epoch:'old'},a)).status,409);
 assert.equal((await request('/api/builds/install',{id:build.id,epoch:state.epoch},a)).status,202);
 await active;
 assert.equal((await request('/api/input',{},a)).status,409);
 assert.equal((await request('/api/release',{},a)).status,409);
 assert.equal((await request('/api/builds',undefined,b)).status,200);
 assert.equal((await request('/api/state',undefined,a)).status,200);
 complete();
});


test('failed APK apply releases maintenance and returns a useful status without removing archive',async t=>{
 let observed; const failed=new Promise(r=>observed=r);
 const b={id:'20261030T031000Z-12-abcdef01',package:'com.ssafy.eolssu.qa',versionCode:12,versionName:'qa-12'};
 const catalog={list:()=>({builds:[b],latest:b.id}),file:async()=>'/fixture.apk',alias:async()=>{}};
 const {request,login}=await fixture(t,false,{catalog,phone:{installed:async()=>null,install:async()=>{observed();throw new Error('서명이 달라 설치할 수 없습니다. 기존 앱과 데이터는 보존했습니다.');}}});
 const a=await login('Alice');await request('/api/request',{},a);
 const state=await(await request('/api/state',undefined,a)).json();
 assert.equal((await request('/api/builds/install',{id:b.id,epoch:state.epoch},a)).status,202);await failed;
 const result=await(await request('/api/builds',undefined,a)).json();assert.equal(result.job.status,'failed');assert.match(result.job.error,/서명이 달라/);assert.equal(result.builds.length,1);
 const next=await(await request('/api/state',undefined,a)).json();assert.equal(next.maintenance,false);assert.notEqual(next.epoch,state.epoch);
 assert.equal((await request('/api/release',{},a)).status,200);
});

test('video is authenticated, same-origin and closed on logout without closing other viewers',async t=>{
 const subscribers=new Set();const video={healthy:true,lastFrameAt:Date.now(),subscribe(res){res.writeHead(200,{'Content-Type':'application/octet-stream'});res.write('frame');subscribers.add(res);res.on('close',()=>subscribers.delete(res));},async stop(){for(const res of subscribers)res.destroy();}};
 const {request,login}=await fixture(t,false,{video});
 assert.equal((await request('/api/video')).status,401);
 const a=await login('Alice'),b=await login('Bob');
 assert.equal((await request('/api/video',undefined,a,'https://evil.test')).status,403);
 const one=await request('/api/video',undefined,a),two=await request('/api/video',undefined,b);
 assert.equal(one.status,200);assert.equal(two.status,200);assert.equal(subscribers.size,2);
 const reader=one.body.getReader();await reader.read();const closed=new Promise(resolve=>[...subscribers][0].once('close',resolve));await request('/api/logout',{},a);
 await assert.rejects(reader.read());await closed;assert.equal(subscribers.size,1);
 await two.body.cancel();
});
test('stream rotation invalidates controller before recapturing physical touch geometry',async t=>{
 let rotate;
 const video={healthy:true,lastFrameAt:Date.now(),subscribe(res,options){rotate=options.onSession;res.writeHead(200);res.write('frame');},async stop(){}};
 const {request,login}=await fixture(t,false,{video});const a=await login('Alice');
 await request('/api/request',{},a);const old=await(await request('/api/state',undefined,a)).json();
 const stream=await request('/api/video',undefined,a);rotate({width:2,height:1});
 const next=await(await request('/api/state',undefined,a)).json();assert.notEqual(next.epoch,old.epoch);assert.equal(next.owner,null);
 assert.equal((await request('/api/input',{epoch:old.epoch},a)).status,403);await stream.body.cancel();
});

test('10 minutes of inactivity prompts every participant, then only unconfirmed sessions disconnect', {timeout:5000},async t=>{
 const start=Date.now();let now=start;
 const video={healthy:true,lastFrameAt:Date.now(),subscribe(res){res.writeHead(200);res.write('frame');},async stop(){}};
 const {request,login}=await fixture(t,false,{video,sessionNow:()=>now});
 const owner=await login('Owner'),viewer=await login('Viewer'),present=await login('Present'),absent=await login('Absent');
 const state=async cookie=>(await request('/api/state',undefined,cookie)).json();
 await request('/api/request',{},owner);
 const stream=await request('/api/video',undefined,owner),reader=stream.body.getReader();await reader.read();
 now=start+599_999;
 assert.equal((await state(owner)).idlePromptMs,null);
 assert.equal((await request('/api/activity',{},present)).status,200);
 now=start+600_000;
 const warning=await state(owner);
 assert.equal(warning.idlePromptMs,60_000);assert.equal(warning.owner,warning.you);
 assert.equal((await state(viewer)).idlePromptMs,60_000);
 assert.equal((await state(present)).idlePromptMs,null);
 now=start+659_999;
 assert.equal((await state(owner)).idlePromptMs,1);
 await request('/api/activity',{},viewer); // Routine browser events cannot answer the prompt.
 assert.equal((await state(viewer)).idlePromptMs,1);
 assert.equal((await request('/api/activity',{confirm:true},viewer)).status,200);
 assert.equal((await state(viewer)).idlePromptMs,null);
 now=start+660_000;
 await assert.rejects(reader.read()); // The server sweep closes the stream without a new owner request.
 assert.equal((await request('/api/activity',{confirm:true},owner)).status,401);
 assert.equal((await request('/api/activity',{confirm:true},absent)).status,401);
 const remaining=await state(viewer);
 assert.equal(remaining.owner,null);
 assert.deepEqual(remaining.peers.map(peer=>peer.name),['Viewer','Present']);
});

test('eight-hour session cap cannot be extended by activity',async t=>{
 const start=Date.now();let now=start;
 const active=new Set();const video={healthy:true,lastFrameAt:Date.now(),subscribe(res){res.writeHead(200);res.write('frame');active.add(res);res.on('close',()=>active.delete(res));},async stop(){for(const res of active)res.destroy();}};
 const {request,login}=await fixture(t,false,{video,sessionNow:()=>now});const a=await login('Alice');
 const stream=await request('/api/video',undefined,a),reader=stream.body.getReader();await reader.read();
 for (now=start+540_000;now<start+8*3600000;now+=540_000) {
   assert.equal((await request('/api/activity',{},a)).status,200);
 }
 now=start+8*3600000;
 assert.equal((await request('/api/state',undefined,a)).status,401);await assert.rejects(reader.read());
});
test('healthy video retains physical touch geometry across PNG fallback failure',async t=>{
 let rotate,captures=0;
 const video={healthy:true,lastFrameAt:Date.now(),subscribe(res,options){rotate=options.onSession;res.writeHead(200);res.write('frame');},async stop(){}};
 const relay=await startRelay({port:0,video,phone:{capture:async()=>{captures++;throw Error('PNG timeout');},geometry:async()=>({width:1080,height:2280}),input:async()=>{}}});t.after(()=>relay.close());
 const send=(path,body,cookie)=>fetch(relay.url+path,{method:body===undefined?'GET':'POST',headers:{Origin:relay.url,...(body===undefined?{}:{'Content-Type':'application/json'}),...(cookie?{Cookie:cookie}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const login=await send('/api/login',{name:'Alice'});const cookie=login.headers.get('set-cookie').split(';')[0];
 const stream=await send('/api/video',undefined,cookie);rotate({width:720,height:1520});
 let state=await(await send('/api/state',undefined,cookie)).json();assert.equal(state.width,1080);assert.equal(state.height,2280);assert.equal(state.online,true);
 const before=captures;await send('/api/state',undefined,cookie);assert.equal(captures,before);
 video.healthy=false;await send('/api/state',undefined,cookie);video.healthy=true;
 state=await(await send('/api/state',undefined,cookie)).json();assert.equal(state.online,true);assert.equal(state.width,1080);await stream.body.cancel();
});

test('only authenticated QA presence holds screen brightness until the last participant leaves',async t=>{
 let present=false,closed=false;const screenKeeper={async setPresent(value){present=value;},state:()=>({active:present}),async close(){closed=true;present=false;}};
 const {request,login,relay}=await fixture(t,false,{screenKeeper});
 await request('/api/state');assert.equal(present,false);
 const a=await login('Alice'),b=await login('Bob');assert.equal(present,true);
 assert.equal((await(await request('/api/state',undefined,a)).json()).screenAwake.active,true);
 await request('/api/logout',{},a);assert.equal(present,true);
 await request('/api/logout',{},b);assert.equal(present,false);
 await relay.close();assert.equal(closed,true);
});

test('lost heartbeat releases brightness after existing disconnect grace', {timeout:3000},async t=>{
 t.mock.timers.enable({apis:['Date'],now:Date.now()});let present=false,release;
 const released=new Promise(r=>release=r),screenKeeper={async setPresent(v){if(present&&!v)release();present=v;},state:()=>({active:present}),async close(){present=false;}};
 const {login}=await fixture(t,false,{screenKeeper});await login('Alice');assert.equal(present,true);
 t.mock.timers.setTime(Date.now()+11000);await released;assert.equal(present,false);
});
test('authenticated open video keeps brightness in a background tab; closing last stream releases after expired heartbeat',{timeout:3000},async t=>{
 t.mock.timers.enable({apis:['Date'],now:Date.now()});let present=false,observing=false,observed,release;
 const renewed=new Promise(r=>observed=r),released=new Promise(r=>release=r);
 const screenKeeper={async setPresent(v){if(observing&&v)observed();if(present&&!v)release();present=v;},state:()=>({active:present}),async close(){present=false;}};
 const video={healthy:true,lastFrameAt:Date.now(),subscribe(res){res.writeHead(200);res.write('frame');},async stop(){}};
 const {request,login}=await fixture(t,false,{screenKeeper,video});const a=await login('Alice');const stream=await request('/api/video',undefined,a);
 t.mock.timers.setTime(Date.now()+11000);observing=true;await renewed;assert.equal(present,true);
 await stream.body.cancel();await released;assert.equal(present,false);
});

test('request refusal is owner-only and returns feedback without changing the controller',async t=>{
 const {request,login}=await fixture(t);const a=await login('Alice'),b=await login('Bob');
 await request('/api/request',{},a);await request('/api/request',{},b);const before=await(await request('/api/state',undefined,b)).json();
 assert.equal((await request('/api/reject',{target:before.you},b)).status,403);
 assert.equal((await request('/api/reject',{target:before.you},a)).status,200);
 const rejected=await(await request('/api/state',undefined,b)).json();assert.equal(rejected.owner,before.owner);assert.equal(rejected.epoch,before.epoch);assert.equal(rejected.requestResult.status,'rejected');assert.equal(rejected.requests.includes(before.you),false);
 await request('/api/request',{},b);assert.equal((await(await request('/api/state',undefined,b)).json()).requestResult,null);
});


test('failed installation lookup is unknown rather than a confirmed absent app',async t=>{
 const catalog={list:()=>({builds:[],latest:null})};
 const f=await fixture(t,false,{catalog,phone:{installed:async()=>{throw Error('temporary ADB error');}}});
 const cookie=await f.login('viewer');const b=await(await f.request('/api/builds',undefined,cookie)).json();
 assert.equal(b.installedStatus,'unavailable');assert.equal(b.installed,null);
 const absent=await fixture(t,false,{catalog,phone:{installed:async()=>null}});
 const c=await absent.login('viewer');const a=await(await absent.request('/api/builds',undefined,c)).json();
 assert.equal(a.installedStatus,'absent');
});
