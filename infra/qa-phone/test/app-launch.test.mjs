import {test} from 'node:test';
import assert from 'node:assert/strict';
import {startRelay} from '../server.mjs';
test('ordinary QA launch uses the same current owner and epoch boundary without starting observation',async t=>{
  let launches=0;
  const relay=await startRelay({port:0,live:{command:()=>assert.fail('no isolated launch'),status:()=>assert.fail('no isolated status')},phone:{capture:async()=>({data:Buffer.alloc(24),width:10,height:20}),input:async()=>{},launchQa:async()=>{launches++;return{started:true};}}});
  t.after(()=>relay.close());
  const post=(path,data,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(data)});
  assert.equal((await post('app/launch',{})).status,401);
  const login=await post('login',{name:'QA'}),cookie=login.headers.get('set-cookie').split(';')[0];
  assert.equal((await post('app/launch',{},cookie)).status,403);
  await post('request',{},cookie);const state=await(await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();
  assert.equal((await post('app/launch',{epoch:'old'},cookie)).status,409);
  assert.equal((await post('app/launch',{epoch:state.epoch,package:'arbitrary'},cookie)).status,200);
  assert.equal(launches,1);
});
