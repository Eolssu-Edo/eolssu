import test from 'node:test';
import assert from 'node:assert/strict';
import {appLogStatus} from '../public/live-panel.js';
test('only a successful native log read can show active collection',()=>{
 assert.deepEqual(appLogStatus({state:'waiting'}),{text:'로그 확인 중',state:'waiting'});
 assert.equal(appLogStatus({state:'unavailable'}).state,'waiting');
 assert.deepEqual(appLogStatus({state:'collecting'}),{text:'로그 수집 중',state:'observing'});
 assert.equal(appLogStatus({state:'collecting',partial:true}).text,'저장 로그 읽는 중');
});
