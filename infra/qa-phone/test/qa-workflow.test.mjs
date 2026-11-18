import {test} from 'node:test';
import assert from 'node:assert/strict';
import {stageState} from '../public/live-panel.js';
test('only observed native stages are shown and HTTP failures remain failures',()=>{
 assert.equal(stageState({at:null,status:200}),'waiting');
 assert.equal(stageState({at:'2026-11-16T00:00:00Z',status:200}),'observed');
 assert.equal(stageState({at:'2026-11-16T00:00:00Z',status:503}),'failed');
 assert.equal(stageState({at:'2026-11-16T00:00:00Z',status:'FAILED'}),'failed');
});
