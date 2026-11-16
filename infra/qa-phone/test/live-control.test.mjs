import http from 'node:http';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {startRelay} from '../server.mjs';
import {LiveClient,loadLiveClient} from '../live-client.mjs';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';

test('QA live commands require the current control lease and preserve ordinary input',async t=>{
  const commands=[],inputs=[];
  const relay=await startRelay({port:0,phone:{identity:'PHONE936',capture:async()=>({data:Buffer.alloc(24),width:10,height:20}),input:async value=>inputs.push(value)},
    live:{status:async()=>({available:true}),command:async(data,identity,id)=>{commands.push({data,identity,id});return {accepted:true};}}});
  t.after(()=>relay.close());
  const post=(path,data,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(data)});
  assert.equal((await fetch(relay.url+'/api/live/status')).status,401);
  const login=await post('login',{name:'QA'}),cookie=login.headers.get('set-cookie').split(';')[0];
  assert.equal((await post('live/control',{action:'start'},cookie)).status,403);
  await post('request',{},cookie);
  const state=await (await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();
  assert.equal((await post('live/control',{action:'start',epoch:'old'},cookie)).status,409);
  assert.equal((await post('live/control',{action:'start',epoch:state.epoch},cookie)).status,202);
  assert.equal(commands[0].identity,'PHONE936');
  assert.equal(commands[0].id,state.you);
  assert.equal((await post('input',{epoch:state.epoch,seq:1,sentAt:Date.now(),action:{kind:'text',text:'한글 QA'}},cookie)).status,200);
  assert.deepEqual(inputs,[{kind:'text',text:'한글 QA'}]);
});

test('loopback bridge sends no secrets to the browser and rejects arbitrary targets or commands',async t=>{
  assert.throws(()=>new LiveClient({origin:'https://example.com',token:'a'.repeat(64),identity:'PHONE936'}));
  const requests=[];
  const server=http.createServer((req,res)=>{
    requests.push(req);
    assert.equal(req.headers['x-qa-token'],'a'.repeat(64));
    assert.equal(req.headers['x-qa-device'],'PHONE936');
    res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({state:'ready',device:{os:'Android 12',password:'must-not-reach-browser'},events:[],history:[]}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=new LiveClient({origin:`http://127.0.0.1:${server.address().port}`,token:'a'.repeat(64),identity:'PHONE936'});
  assert.ok(!JSON.stringify(await client.status()).includes('must-not-reach-browser'));
  await assert.rejects(client.command({action:'shell'},'PHONE936','session'),/허용되지/);
  await assert.rejects(client.command({action:'start'},'OTHER936','session'),/다릅니다/);
  await assert.rejects(client.command({action:'run',size:999},'PHONE936','session'),/제출 수/);
  assert.equal(requests.length,1);
});

test('an already running QA relay picks up the isolated service token after restart',async t=>{
  const directory=await mkdtemp(join(tmpdir(),'qa-live-rotation-'));
  t.after(()=>rm(directory,{recursive:true,force:true}));
  const file=join(directory,'live-control.json'),received=[];
  const server=http.createServer((req,res)=>{
    received.push(req.headers['x-qa-token']);res.setHeader('Content-Type','application/json');res.end('{"state":"ready","events":[]}');
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=await loadLiveClient(file),origin=`http://127.0.0.1:${server.address().port}`;
  await assert.rejects(client.status(),/설정/);
  await writeFile(file,JSON.stringify({origin,identity:'PHONE936',token:'a'.repeat(64)}));await client.status();
  await writeFile(file,JSON.stringify({origin,identity:'PHONE936',token:'b'.repeat(64)}));await client.status();
  assert.deepEqual(received,['a'.repeat(64),'b'.repeat(64)]);
});
