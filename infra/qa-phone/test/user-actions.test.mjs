import test from 'node:test';
import assert from 'node:assert/strict';
import {safeLogRecords,liveOperations} from '../live-events.mjs';
import {filterAppLogs,appLogStatus} from '../public/live-panel.js';
const base={source:'app',at:'2026-11-16T12:00:00.000Z',appRun:1,runId:'b'.repeat(32),schemaVersion:2,interactionId:7,correlation:'exact',screenId:'auth_login',target:'login_submit'};
test('QA user actions survive the allowlist, group by interaction and never invent success',()=>{
 const rows=safeLogRecords(['touch_end','click','callback_returned','login_result','screen_enter'].map((kind,i)=>({...base,kind,seq:i+1,outcome:kind==='login_result'?'failed':undefined,password:'secret',length:8,value:'private'}))).records;
 assert.equal(rows[0].interactionId,7);assert.equal(rows[0].screenId,'auth_login');assert.ok(!JSON.stringify(rows).includes('secret'));assert.ok(!JSON.stringify(rows).includes('length'));
 const ops=liveOperations(rows);assert.equal(ops.length,1);assert.equal(ops[0].stages.length,5);assert.equal(ops[0].stages[0].status,null);assert.equal(ops[0].stages[0].correlation,'exact');assert.equal(ops[0].stages[3].status,'failed');
 assert.equal(filterAppLogs(rows,'action').length,4);assert.equal(filterAppLogs(rows,'screen').length,1);assert.equal(filterAppLogs(rows,'error').length,1);
});
test('unsupported versions and retention loss are distinct from normal idle collection',()=>{
 assert.match(appLogStatus({state:'unsupported'}).text,/미지원/);
 assert.match(appLogStatus({state:'collecting',missing:20}).text,/누락/);
 assert.equal(appLogStatus({state:'collecting',partial:false}).state,'observing');
});

import {DatabaseSync} from 'node:sqlite';
import {mkdtempSync,readFileSync,unlinkSync,rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';import {join} from 'node:path';
import {decodeAppDatabase} from '../read-only-app-db.mjs';
import {InstalledAppLogs} from '../installed-app-logs.mjs';
test('retention floor recovers sequence without changing ack and reports missing prefix',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'qa1051-retention-')),file=join(dir,'db');let db;
 try {db=new DatabaseSync(file);db.exec('CREATE TABLE state(stream_id TEXT,ack INTEGER,last_seq INTEGER,retained_after INTEGER,missing_retention INTEGER);CREATE TABLE events(seq INTEGER PRIMARY KEY,event TEXT);PRAGMA user_version=2');
 db.prepare('INSERT INTO state VALUES(?,0,102,100,100)').run('a'.repeat(32));
 for(const seq of [101,102])db.prepare('INSERT INTO events VALUES(?,?)').run(seq,JSON.stringify({kind:'click',schema_version:2,interaction_id:seq,screen_id:'home',target:'button',ms:seq,run_id:'b'.repeat(32),version_name:'qa-next'}));
 db.close();db=null;const bytes=readFileSync(file);const value=await decodeAppDatabase(bytes,0,null);
 assert.equal(value.ack,0);assert.equal(value.retained_after,100);assert.deepEqual(value.events.map(x=>x.seq),[101,102]);
 const logs=new InstalledAppLogs({installed:async()=>({versionName:'qa-next'}),snapshot:async()=>value});logs.refresh();await logs.pending;
 const view=logs.project({},'device/all');assert.equal(view.appLog.missing,100);assert.equal(view.evidence.records[0].interactionId,101);await logs.close();
 }finally{db?.close();unlinkSync(file);rmdirSync(dir);}
});
