import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {liveEvidence} from '../live-events.mjs';
import {LiveClient} from '../live-client.mjs';

const id='7d23c4f1-9418-44f5-a123-7ef566103011';
const other='8d23c4f1-9418-44f5-a123-7ef566103011';
const event=(seq,source,kind,extra={})=>({seq,source,kind,at:'2026-11-15T10:00:00.000Z',...extra});
const events=[event(1,'app','request_started',{requestId:id,target:'auth_sign_in',password:'secret-value',body:{password:'hidden'}}),
  event(2,'backend','request_received',{requestId:id,authorization:'Bearer hidden'}),
  event(3,'backend','response_finished',{requestId:id,status:200,durationMs:12}),
  event(4,'app','request_finished',{requestId:id,status:200}),
  event(5,'app','request_started',{requestId:other,target:'song_detail'}),
  event(6,'backend','response_finished',{requestId:other,status:500})];

test('log evidence contains only the selected operation and safe fields from collected records',()=>{
  const evidence=liveEvidence(events,'request/'+id);
  assert.equal(evidence.total,4);
  assert.deepEqual(evidence.records.map(row=>[row.source,row.kind]),[
    ['app','request_started'],['backend','request_received'],['backend','response_finished'],['app','request_finished']]);
  assert.equal(evidence.records[2].durationMs,12);
  assert.ok(evidence.records.every(row=>row.requestId===id));
  assert.ok(!JSON.stringify(evidence).includes('secret'));
  assert.ok(!JSON.stringify(evidence).includes('password'));
  assert.ok(!JSON.stringify(evidence).includes('Bearer'));
  assert.equal(liveEvidence(events,'../../private').records.length,0);
});

test('a successful HTTP response does not manufacture a DB or app drawing log',()=>{
  const evidence=liveEvidence(events,'request/'+id);
  assert.equal(evidence.records.filter(row=>row.source==='db').length,0);
  assert.equal(evidence.records.filter(row=>row.phase==='score_drawn').length,0);
});

test('ordinary status excludes log bodies; explicit selection returns scoped evidence',async t=>{
  const raw={state:'ready',events,history:[{id:other,result:{logs:[event(1,'test','response_finished',{requestId:id,status:409,password:'never'})]}}]};
  const server=http.createServer((_req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(raw));});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=new LiveClient({origin:`http://127.0.0.1:${server.address().port}`,token:'a'.repeat(64),identity:'PHONE936'});
  const ordinary=await client.status();
  assert.equal(ordinary.evidence,undefined);
  assert.equal(ordinary.history[0].result.logs,undefined);
  const selected=await client.status({logsFor:'request/'+id});
  assert.equal(selected.evidence.records.length,4);
  const history=await client.status({logsFor:'history/'+other});
  assert.equal(history.evidence.records[0].status,409);
  assert.ok(!JSON.stringify(history).includes('never'));
});
