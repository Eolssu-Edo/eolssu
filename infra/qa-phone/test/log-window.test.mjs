import test from 'node:test';
import assert from 'node:assert/strict';
import {logWindow,filterAppLogs} from '../public/live-panel.js';
test('log pages retain their anchor during live arrivals and can reach every record',()=>{
 const rows=Array.from({length:866},(_,seq)=>({deviceId:'phone',source:'app',seq,at:'2026-11-16T00:00:00Z'}));
 const latest=logWindow(rows);assert.equal(latest.records.at(-1).seq,865);
 const previous=logWindow(rows,latest.before);assert.equal(previous.records.at(-1).seq,715);
 const growing=logWindow([...rows,{deviceId:'phone',source:'app',seq:866,at:'2026-11-16T00:00:01Z'}],latest.before);
 assert.deepEqual(growing.records,previous.records);
 const reached=new Set();let anchor=null;do{const page=logWindow(rows,anchor);page.records.forEach(row=>reached.add(row.seq));anchor=page.before;}while(anchor);
 assert.equal(reached.size,866);
});

test('native filters preserve all records and distinguish HTTP evidence from failures',()=>{
 const rows=[{kind:'run_started'},{kind:'http_attempt',status:200},{kind:'http_attempt',status:503},{kind:'flow_end',outcome:'failed'}];
 assert.equal(filterAppLogs(rows,'all').length,4);
 assert.deepEqual(filterAppLogs(rows,'http'),[rows[1],rows[2]]);
 assert.deepEqual(filterAppLogs(rows,'error'),[rows[2],rows[3]]);
});
