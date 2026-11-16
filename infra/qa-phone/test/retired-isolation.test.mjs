import test from 'node:test';
import assert from 'node:assert/strict';
import {startRelay} from '../server.mjs';

test('retired isolated commands never start apps or issue requests to any backend',async t=>{
  const inputs=[];
  const relay=await startRelay({port:0,phone:{capture:async()=>({data:Buffer.alloc(24),width:10,height:20}),input:async action=>inputs.push(action),launchQa:()=>assert.fail('no app launch')},live:{status:()=>assert.fail('no isolated server'),command:()=>assert.fail('no backend mutations')}});
  t.after(()=>relay.close());
  const post=(path,data,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(data)});
  assert.equal((await post('live/control',{action:'run'})).status,401);
  const login=await post('login',{name:'QA'}),cookie=login.headers.get('set-cookie').split(';')[0];await post('request',{},cookie);
  const state=await(await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();
  for(const action of ['start','scenario','run','fixture','email','password','stop','cancel'])assert.equal((await post('live/control',{action,epoch:state.epoch},cookie)).status,410);
  assert.equal((await post('input',{epoch:state.epoch,seq:1,sentAt:Date.now(),action:{kind:'text',text:'한글 QA'}},cookie)).status,200);
  assert.deepEqual(inputs,[{kind:'text',text:'한글 QA'}]);
});
