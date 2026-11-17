import test from 'node:test';
import assert from 'node:assert/strict';
import {startRelay} from '../server.mjs';

test('input failure diagnostics distinguish geometry while keeping text and error details private', async t=>{
  const warnings=[],previous=console.warn;
  console.warn=value=>warnings.push(value);
  t.after(()=>{console.warn=previous;});
  for(const [message,reason] of [
    ['screen geometry changed','screen-geometry'],
    ['selected device identity mismatch','device-identity'],
    ['private-input-canary@example.invalid password-canary','transport-or-helper'],
  ]){
    const relay=await startRelay({port:0,phone:{
      capture:async()=>({data:Buffer.from('fixture'),width:1440,height:2304}),
      input:async()=>{throw Error(message);},
    }});
    try{
      const post=(path,body,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(body)});
      const login=await post('login',{name:'Fixture'}),cookie=login.headers.get('set-cookie').split(';')[0];
      await post('request',{},cookie);
      const state=await(await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();
      const response=await post('input',{epoch:state.epoch,seq:1,sentAt:state.now,width:1440,height:2304,action:{kind:'text',text:'input-canary'}},cookie);
      assert.equal(response.status,503);
      assert.deepEqual(await response.json(),{error:'device input failed; control revoked'});
      assert.equal(warnings.at(-1),`QA input failed: kind=text; reason=${reason}; control revoked`);
      const failed=await(await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();
      assert.equal(failed.owner,null);
      assert.equal(failed.inputFault,true);
    }finally{await relay.close();}
  }
  assert.doesNotMatch(warnings.join('\n'),/canary|@example/);
});
