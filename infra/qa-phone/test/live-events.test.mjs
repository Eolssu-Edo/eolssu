import {test} from 'node:test';
import assert from 'node:assert/strict';
import {liveOperations} from '../live-events.mjs';

const requestId='7d23c4f1-9418-44f5-a123-7ef566103011';
const timingId='9d23c4f1-9418-44f5-a123-7ef566103011';
const event=(seq,source,kind,extra={})=>({seq,source,kind,at:new Date(1700000000000+seq*100).toISOString(),...extra});

test('actual backend receipt wins over proxy and HTTP success never implies app drawing',()=>{
  const events=[event(1,'app','request_started',{requestId,appRun:1,flow:1,visit:1,target:'song_detail',ms:0}),
    event(2,'proxy','request_received',{requestId}),event(3,'backend','request_received',{requestId}),
    event(4,'backend','response_finished',{requestId,status:200}),event(5,'app','request_finished',{requestId,appRun:1,flow:1,visit:1,target:'song_detail',ms:400,status:200})];
  const [operation]=liveOperations(events);
  assert.equal(operation.stages.find(stage=>stage.name==='request_received').source,'backend');
  assert.equal(operation.stages.find(stage=>stage.name==='song_detail_drawn').at,null);
});

test('restarted app local IDs remain separate and score latency starts at submission',()=>{
  const context={flow:1,evaluationSubmission:1,evaluationTimingId:timingId,sessionId:'12'};
  const events=[event(1,'app','request_started',{...context,appRun:1,requestId,target:'recording_upload',ms:1000}),
    event(2,'app','request_finished',{...context,appRun:1,requestId,target:'recording_upload',status:202,ms:1100}),
    event(3,'app','milestone',{...context,appRun:1,target:'analysis',phase:'analysis_ready',ms:6000,durationMs:5000}),
    event(4,'app','milestone',{flow:1,evaluationSubmission:1,appRun:2,target:'result',phase:'score_drawn',ms:200,durationMs:149}),
    event(5,'app','milestone',{...context,appRun:1,target:'result',phase:'score_drawn',ms:6500,durationMs:149})];
  const operations=liveOperations(events);
  assert.equal(operations.length,2);
  const submitted=operations.find(operation=>operation.id==='submission/1/1/1');
  assert.equal(submitted.stages.find(stage=>stage.name==='score_drawn').elapsedMs,5500);
  assert.equal(submitted.stages.find(stage=>stage.name==='db_saved').at,null);
});

test('DB observations from an earlier attempt do not complete the current retry',()=>{
  const context={appRun:1,flow:1,evaluationSubmission:2,evaluationTimingId:timingId,sessionId:'12',attempt:2};
  const [operation]=liveOperations([event(1,'app','request_started',{...context,requestId,target:'analysis_retry'}),
    event(2,'app','request_finished',{...context,requestId,target:'analysis_retry',status:202}),
    event(3,'db','state_observed',{evaluationTimingId:timingId,sessionId:'12',attempt:1,status:'DONE',evaluations:1,notes:61})]);
  assert.equal(operation.stages.find(stage=>stage.name==='db_saved').at,null);
});

test('log collection delay is not reported as server processing or submission latency',()=>{
  const [operation]=liveOperations([event(1,'app','request_started',{requestId,ms:100}),
    event(80,'backend','response_finished',{requestId,status:200,durationMs:12}),
    event(90,'app','request_finished',{requestId,ms:150})]);
  assert.equal(operation.stages.find(stage=>stage.name==='response_finished').elapsedMs,null);
  assert.equal(operation.stages.find(stage=>stage.name==='response_finished').durationMs,12);
  assert.equal(operation.stages.find(stage=>stage.name==='request_finished').elapsedMs,50);
});

test('HTTP retries within one app action keep each actual UUID and response separately',()=>{
  const nextId='8d23c4f1-9418-44f5-a123-7ef566103011',context={appRun:1,flow:1,visit:1,target:'song_detail'};
  const [operation]=liveOperations([event(1,'app','request_started',{...context,requestId}),
    event(2,'app','request_finished',{...context,requestId,status:401}),
    event(3,'app','request_started',{...context,requestId:nextId}),
    event(4,'backend','request_received',{requestId:nextId}),
    event(5,'app','request_finished',{...context,requestId:nextId,status:200})]);
  assert.deepEqual(operation.requests.map(request=>[request.requestId,request.status,request.backendReceived]),[[requestId,401,false],[nextId,200,true]]);
});
