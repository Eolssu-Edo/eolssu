import {test} from 'node:test';
import assert from 'node:assert/strict';
import {qaWorkflow,stageState} from '../public/live-panel.js';

test('the next step follows device control, app launch, login, then device QA',()=>{
  const owner={connected:false,allowed:false};
  assert.equal(qaWorkflow({available:true,operations:[]},owner),'control');
  owner.connected=true;owner.allowed=true;
  assert.equal(qaWorkflow({available:true,phase:'ready'},owner),'launch');
  const snapshot={available:true,phase:'observing',mode:'observe',operations:[]};
  assert.equal(qaWorkflow(snapshot,owner),'login');
  snapshot.operations=[{name:'로그인',requests:[{status:401}]}];
  assert.equal(qaWorkflow(snapshot,owner),'login');
  snapshot.operations[0].requests[0].status=200;
  assert.equal(qaWorkflow(snapshot,owner),'perform');
  snapshot.operations.push({stages:[{name:'score_drawn',at:'2026-11-15T00:00:00Z'}]});
  assert.equal(qaWorkflow(snapshot,owner),'perform');
});

test('only actual upload, AI, DB, app result and drawing evidence complete device QA',()=>{
  const stages=['upload_accepted','ai_finished','db_saved','analysis_ready','score_drawn'].map(name=>({name,at:'2026-11-15T00:00:00Z',status:name==='ai_finished'?'DONE':null}));
  stages[0].status=202;
  assert.equal(qaWorkflow({mode:'observe',phase:'observing',operations:[{stages}]},{connected:true,allowed:true}),'complete');
  stages[1].status='FAILED';
  assert.notEqual(qaWorkflow({mode:'observe',phase:'observing',operations:[{stages}]},{connected:true,allowed:true}),'complete');
  assert.equal(stageState(stages[1]),'failed');
  assert.equal(stageState({at:'2026-11-15T00:00:00Z',status:503}),'failed');
  assert.equal(stageState({at:null,status:200}),'waiting');
});
