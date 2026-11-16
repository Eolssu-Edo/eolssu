import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {mkdtempSync,readFileSync,unlinkSync,rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {startRelay} from '../server.mjs';
import {InstalledAppLogs} from '../installed-app-logs.mjs';
import {decodeAppDatabase,appDatabaseReader} from '../read-only-app-db.mjs';
import {AdbPhone} from '../adb.mjs';
const version='qa-20261116.022919-59e0e700',stream='a'.repeat(32),run='b'.repeat(32);
const event=(seq,target='song_list',v=version)=>({seq,event:{kind:seq===1?'visit_started':'http_attempt',ms:seq*10,flow:1,visit:3,target,status:200,duration_ms:20,run_id:run,version_name:v,password:'must-not-leak',url:'https://private.invalid',source:'backend'}});
function bytesFor(rows){
  const directory=mkdtempSync(join(tmpdir(),'qa-log-test-')),file=join(directory,'data.db');let db;
  try{db=new DatabaseSync(file);db.exec('CREATE TABLE state(stream_id TEXT,ack INTEGER,last_seq INTEGER);CREATE TABLE events(seq INTEGER PRIMARY KEY,event TEXT)');db.exec('BEGIN');db.prepare('INSERT INTO state VALUES(?,0,?)').run(stream,rows.length);for(const row of rows)db.prepare('INSERT INTO events VALUES(?,?)').run(row.seq,JSON.stringify(row.event));db.exec('COMMIT');db.close();db=null;return readFileSync(file);}
  finally{db?.close();try{unlinkSync(file);}finally{rmdirSync(directory);}}
}
test('latest installed APK logs reach authenticated QA status without app launch, broadcast or ack',async t=>{
  const bytes=bytesFor([event(1),event(2)]),calls=[];
  const phone={identity:'TABLET994',capture:async()=>({data:Buffer.alloc(24),width:10,height:20}),installed:async()=>({versionName:version}),
    readPerformanceLog:async()=>{calls.push('read');return bytes;},applog:()=>assert.fail('no broadcast or ack')};
  const live={status:async()=>({available:true,busy:false,phase:'ready',device:{os:'Android 16',appVersion:'stale'},operations:[],history:[{id:'kept'}]}),command:()=>assert.fail('no app action')};
  const relay=await startRelay({port:0,phone,live});t.after(()=>relay.close());
  assert.equal((await fetch(relay.url+'/api/live/status')).status,401);assert.deepEqual(calls,[]);
  const login=await fetch(relay.url+'/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json'},body:JSON.stringify({name:'QA'})});const cookie=login.headers.get('set-cookie').split(';')[0];
  let value;for(let i=0;i<50;i++){value=await(await fetch(relay.url+'/api/live/status?logs=device%2Fall',{headers:{Cookie:cookie}})).json();if(value.evidence?.total)break;await new Promise(resolve=>setTimeout(resolve,10));}
  assert.equal(value.evidence?.total,2);assert.equal(value.operations.length,1);assert.equal(value.device.appVersion,version);assert.equal(value.api,null);assert.deepEqual(value.history,[]);
  assert.ok(value.evidence.records.every(row=>row.source==='app'));assert.ok(!JSON.stringify(value).includes('must-not-leak'));assert.ok(!JSON.stringify(value).includes('private.invalid'));
  assert.ok(value.operations[0].stages.some(stage=>stage.name==='http_attempt'&&stage.status===200));assert.ok(!value.operations[0].stages.some(stage=>stage.source==='backend'||stage.name==='db_saved'));
});
test('stable DB copies return incremental records and leave original ack and bytes intact',async()=>{
  const bytes=bytesFor([event(1),event(2),event(3)]),copy=Buffer.from(bytes);let reads=0;
  const reader=appDatabaseReader({readPerformanceLog:async()=>{reads++;return bytes;}});
  assert.deepEqual((await reader(0,null)).events.map(x=>x.seq),[1,2,3]);assert.equal(reads,2);
  const next=await reader(2,stream);assert.deepEqual(next.events.map(x=>x.seq),[3]);assert.equal(next.ack,0);assert.equal(reads,3);assert.deepEqual(bytes,copy);
  let index=0;const changing=appDatabaseReader({readPerformanceLog:async()=>++index===1?bytes:bytesFor([event(1),event(2)])});
  await assert.rejects(changing(0,null),/changing/);await assert.rejects(decodeAppDatabase(Buffer.alloc(128)),/Unsupported/);
});

test('initial collection starts at the retained cursor and continues beyond a 1600-event chunk',async()=>{
  const bytes=bytesFor(Array.from({length:1602},(_,index)=>event(index+1))),first=await decodeAppDatabase(bytes,0,null);
  assert.equal(first.events.length,1600);assert.equal(first.events[0].seq,1);
  const rest=await decodeAppDatabase(bytes,first.events.at(-1).seq,stream);
  assert.deepEqual(rest.events.map(row=>row.seq),[1601,1602]);
});
test('polling deduplicates, separates devices and excludes another installed version',async()=>{
  let clock=0,count=2;
  const make=target=>new InstalledAppLogs({now:()=>clock,installed:async()=>({versionName:version}),snapshot:async(after)=>({stream_id:stream,end:count,events:[event(1,'old','qa-old'),event(2,target),...(count>2?[event(3,target)]:[])].filter(row=>row.seq>after)})});
  const phone=make('song_list'),tablet=make('record_page');phone.refresh();tablet.refresh();await Promise.all([phone.pending,tablet.pending]);
  assert.equal(phone.project({},'device/all').evidence.records[0].target,'song_list');assert.equal(tablet.project({},'device/all').evidence.records[0].target,'record_page');
  clock=500;count=3;phone.refresh();await phone.pending;assert.deepEqual(phone.project({},'device/all').evidence.records.map(x=>x.seq),[2,3]);
  assert.equal(phone.project({evidence:{total:7}},'server/load').evidence.total,0);await Promise.all([phone.close(),tablet.close()]);
});

test('all retained native event kinds and storage markers remain visible beyond 500 rows',async()=>{
  const rows=Array.from({length:600},(_,index)=>event(index+1));
  rows[0].event={kind:'run_started',ms:0,run_id:run,version_name:version};
  rows[1].event={kind:'log_gap',ms:0,missing:2,reason:'overflow',run_id:run,version_name:version};
  rows[2].event={...rows[2].event,kind:'action',source:'network'};
  rows[3].event={...rows[3].event,kind:'poll_summary',count:3,failed:1};
  const logs=new InstalledAppLogs({installed:async()=>({versionName:version}),snapshot:async()=>({stream_id:stream,ack:0,end:600,events:rows})});
  logs.refresh();await logs.pending;const value=logs.project({},'device/all');
  assert.equal(value.evidence.total,600);assert.equal(value.evidence.records.length,600);
  assert.equal(value.evidence.records[1].missing,2);assert.equal(value.evidence.records[2].dataSource,'network');
  assert.ok(value.operations[0].stages.some(stage=>stage.name==='action'));
  assert.ok(value.operations[0].stages.some(stage=>stage.name==='poll_summary'));await logs.close();
});

test('read-only collection does not depend on video or the isolated test server being healthy',async t=>{
  const bytes=bytesFor([event(1)]);
  const relay=await startRelay({port:0,phone:{capture:async()=>{throw Error('video offline');},installed:async()=>({versionName:version}),readPerformanceLog:async()=>bytes},live:{status:async()=>{throw Error('server offline');}}});t.after(()=>relay.close());
  const login=await fetch(relay.url+'/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json'},body:JSON.stringify({name:'QA'})}),cookie=login.headers.get('set-cookie').split(';')[0];
  let value;for(let i=0;i<50;i++){const response=await fetch(relay.url+'/api/live/status?logs=device%2Fall',{headers:{Cookie:cookie}});assert.equal(response.status,200);value=await response.json();if(value.evidence?.total)break;await new Promise(resolve=>setTimeout(resolve,10));}
  assert.equal(value.evidence.total,1);assert.equal(value.available,true);
});

test('a stalled isolated status response cannot delay native APK log reads',async t=>{
  const bytes=bytesFor([event(1)]);let release;const gate=new Promise(resolve=>{release=()=>resolve({available:true,operations:[],history:[]});});
  const relay=await startRelay({port:0,phone:{capture:async()=>{throw Error('video offline');},installed:async()=>({versionName:version}),readPerformanceLog:async()=>bytes},live:{status:()=>gate}});
  t.after(async()=>{release();await relay.close();});
  const login=await fetch(relay.url+'/api/login',{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json'},body:JSON.stringify({name:'QA'})}),cookie=login.headers.get('set-cookie').split(';')[0];
  let value;for(let i=0;i<20;i++){const response=await fetch(relay.url+'/api/live/status?logs=device%2Fall',{headers:{Cookie:cookie},signal:AbortSignal.timeout(600)});value=await response.json();if(value.evidence?.total)break;await new Promise(resolve=>setTimeout(resolve,10));}
  assert.equal(value.evidence.total,1);assert.equal(value.available,true);
});

test('a failed read stays unavailable while a retry is pending',async()=>{
  let clock=0;
  const logs=new InstalledAppLogs({now:()=>clock,installed:async()=>({versionName:version}),snapshot:async()=>{throw Error('device offline');}});
  logs.refresh();await logs.pending;assert.equal(logs.project({}).appLog.state,'unavailable');
  clock=500;logs.refresh();assert.equal(logs.project({}).appLog.state,'unavailable');await logs.close();
});
test('APK installation cannot overlap a read-only app log snapshot',async t=>{
  const bytes=bytesFor([event(1)]);let finish,blocked=true;const build={id:'20261116T000000Z-1-aaaaaaaa',versionName:'qa-next'};
  const relay=await startRelay({port:0,catalog:{list:()=>({builds:[build],latest:build.id}),tail:Promise.resolve()},phone:{identity:'PHONE994',capture:async()=>({data:Buffer.alloc(24),width:10,height:20}),installed:async()=>({versionName:version}),readPerformanceLog:()=>blocked?new Promise(resolve=>{finish=()=>resolve(bytes);}):Promise.resolve(bytes),install:()=>assert.fail('no installation')}});
  t.after(async()=>{blocked=false;finish?.();await relay.close();});const post=(path,data,cookie)=>fetch(relay.url+'/api/'+path,{method:'POST',headers:{Origin:relay.url,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(data)});
  const login=await post('login',{name:'QA'}),cookie=login.headers.get('set-cookie').split(';')[0];await post('request',{},cookie);const state=await(await fetch(relay.url+'/api/state',{headers:{Cookie:cookie}})).json();await fetch(relay.url+'/api/live/status',{headers:{Cookie:cookie}});assert.equal((await post('builds/install',{id:build.id,epoch:state.epoch},cookie)).status,409);
});
test('native log transport only reads the fixed QA performance database on the verified device',async()=>{
  const calls=[];const phone=new AdbPhone({serial:'192.168.30.82:44709',identity:'PHONE994',executor:async(_,args)=>{calls.push(args);if(args.includes('devices'))return{stdout:'192.168.30.82:44709 device transport_id:12\n'};if(args.includes('getprop'))return{stdout:'PHONE994\n'};return{stdout:Buffer.alloc(0)};}});
  await phone.readPerformanceLog();assert.deepEqual(calls.at(-1),['-P','5037','-t','12','exec-out','run-as','com.ssafy.eolssu.qa','--user','0','cat','no_backup/qa-performance.db']);
});
