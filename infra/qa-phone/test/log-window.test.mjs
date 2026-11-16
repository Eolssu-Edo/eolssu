import test from 'node:test';
import assert from 'node:assert/strict';
import {logWindow} from '../public/live-panel.js';
test('log pages retain their anchor during live arrivals and can reach every record',()=>{
 const rows=Array.from({length:866},(_,seq)=>({deviceId:'phone',source:'app',seq,at:'2026-11-16T00:00:00Z'}));
 const latest=logWindow(rows);assert.equal(latest.records.at(-1).seq,865);
 const previous=logWindow(rows,latest.before);assert.equal(previous.records.at(-1).seq,715);
 const growing=logWindow([...rows,{deviceId:'phone',source:'app',seq:866,at:'2026-11-16T00:00:01Z'}],latest.before);
 assert.deepEqual(growing.records,previous.records);
 const reached=new Set();let anchor=null;do{const page=logWindow(rows,anchor);page.records.forEach(row=>reached.add(row.seq));anchor=page.before;}while(anchor);
 assert.equal(reached.size,866);
});
