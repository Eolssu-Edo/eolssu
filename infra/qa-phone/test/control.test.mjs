import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Control, validateAction } from '../control.mjs';
function setup() {
  let now = 1000; const calls = [];
  const c = new Control({ now: () => now, input: async v => calls.push(v) });
  c.online = true; c.join('a', 'Alice'); c.join('b', 'Bob');
  return { c, calls, advance: ms => { now += ms; }, now: () => now };
}
const packet = (c, now, seq = 1) => ({ epoch: c.epoch, seq, sentAt: now, action: { kind: 'key', key: 'BACK' } });

test('unanswered control request transfers at 30 seconds, keeps FIFO and renews next owner response time',async()=>{
  const {c,advance,now}=setup();c.join('d','Dana');await c.request('a');await c.request('b');await c.request('d');const old=c.epoch;
  advance(29999);for(const id of ['a','b','d'])c.touch(id);await c.request('b');await c.sweep();assert.equal(c.owner,'a');
  advance(1);await c.sweep();assert.equal(c.owner,'b');assert.notEqual(c.epoch,old);assert.deepEqual(c.requests,['d']);
  await assert.rejects(c.input('a',{...packet(c,now()),epoch:old}),/control/);
  await c.sweep();assert.equal(c.owner,'b');advance(29999);for(const id of ['b','d'])c.touch(id);await c.sweep();assert.equal(c.owner,'b');advance(1);await c.sweep();assert.equal(c.owner,'d');
});
test('refused, departed, offline and maintenance requests do not auto take control',async()=>{
  for(const reason of ['reject','leave','offline','maintenance']){
    const {c,advance}=setup();await c.request('a');await c.request('b');
    if(reason==='reject')await c.reject('a','b');if(reason==='leave')await c.leave('b');if(reason==='offline')await c.setOnline(false);if(reason==='maintenance')c.beginMaintenance('a',c.epoch);
    advance(30000);c.touch('a');if(reason!=='leave')c.touch('b');await c.sweep();assert.notEqual(c.owner,'b');
  }
});
test('viewers cannot input; explicit concurrent requests grant one owner', async () => {
  const { c, calls, now } = setup(); assert.equal(c.owner, null);
  await assert.rejects(c.input('a', packet(c, now())), /control/);
  await Promise.all([c.request('a'), c.request('b')]);
  assert.equal(c.owner, 'a'); assert.deepEqual(c.requests, ['b']);
  await assert.rejects(c.input('b', packet(c, now())), /control/);
  await c.input('a', packet(c, now())); assert.equal(calls.length, 1);
});
test('only owner transfers to live requester; old lease and replay rejected', async () => {
  const { c, now, calls } = setup(); await c.request('a'); await c.request('b');
  const old = packet(c, now()); await assert.rejects(c.transfer('b', 'b'), /control/);
  await c.transfer('a', 'b'); await assert.rejects(c.input('a', old), /control/);
  await assert.rejects(c.input('b', old), /lease/);
  const current = packet(c, now()); await c.input('b', current);
  await assert.rejects(c.input('b', current), /sequence/); assert.equal(calls.length, 1);
});
test('queued former-owner input rejected after atomic transfer', async () => {
  const { c, now, calls } = setup(); await c.request('a'); await c.request('b');
  const old = packet(c, now()); const transfer = c.transfer('a', 'b'); const input = c.input('a', old);
  await transfer; await assert.rejects(input, /control/); assert.deepEqual(calls, []);
});
test('in-flight bounded gesture finishes before transfer acknowledgement', async () => {
  const { c, now } = setup(); let finish; let started;
  const entered = new Promise(resolve => { started = resolve; });
  c.sendInput = () => { started(); return new Promise(resolve => { finish = resolve; }); };
  await c.request('a'); await c.request('b'); const input = c.input('a', packet(c, now())); await entered;
  const transfer = c.transfer('a', 'b'); assert.equal(c.owner, 'a'); finish();
  await input; await transfer; assert.equal(c.owner, 'b');
});
test('release, timeout, offline and restart return to viewing-only', async () => {
  const { c, advance } = setup(); await c.request('a'); await c.release('a'); assert.equal(c.owner, null);
  await c.request('b'); advance(10001); await c.sweep(); assert.equal(c.owner, null);
  c.join('a', 'Alice'); await c.request('a'); await c.setOnline(false); assert.equal(c.owner, null);
  await assert.rejects(c.request('a'), /offline/);
  assert.equal(new Control({ input: async () => {} }).owner, null);
});
test('expired, future, malformed and shell text inputs fail closed', async () => {
  const { c, now } = setup(); await c.request('a');
  for (const p of [
    { ...packet(c, now()), sentAt: now() - 3001 }, { ...packet(c, now()), sentAt: now() + 1001 },
    { ...packet(c, now()), seq: 0 }, { ...packet(c, now()), action: { kind: 'text', text: 'invalid\ntext' } },
    { ...packet(c, now()), action: { kind: 'swipe', x: 1, y: 0, x2: 0, y2: 0, duration: 9999 } },
  ]) await assert.rejects(c.input('a', p));
});

test('transfer intent invalidates older input already waiting behind in-flight gesture', async () => {
  const { c, now, calls } = setup(); let finish; let entered;
  const started = new Promise(resolve => { entered = resolve; });
  c.sendInput = () => { calls.push('gesture'); entered(); return new Promise(resolve => { finish = resolve; }); };
  await c.request('a'); await c.request('b');
  const first = c.input('a', packet(c, now())); await started;
  const waiting = c.input('a', packet(c, now(), 2));
  const rejected = assert.rejects(waiting, /control|lease/);
  const transfer = c.transfer('a', 'b'); finish();
  await Promise.all([first, rejected, transfer]); assert.deepEqual(calls, ['gesture']); assert.equal(c.owner, 'b');
});

test('owner page reload can resume with server sequence while replay stays blocked', async () => {
  const { c, now, calls } = setup(); await c.request('a');
  await c.input('a',packet(c,now()));
  const refreshed=c.state('a'); assert.equal(refreshed.lastSeq,1);
  assert.equal(c.state('b').lastSeq,0);
  await c.input('a',{...packet(c,now()),seq:refreshed.lastSeq+1});
  await assert.rejects(c.input('a',packet(c,now())),/sequence/);
  assert.equal(calls.length,2);
});

test('owner can reject a live request without losing control; requester sees refusal and may retry',async()=>{
 const {c}=setup();await c.request('a');await c.request('b');const lease=c.epoch;
 await assert.rejects(c.reject('b','b'),/control/);
 await c.reject('a','b');assert.equal(c.owner,'a');assert.equal(c.epoch,lease);assert.deepEqual(c.requests,[]);assert.equal(c.state('b').requestResult.status,'rejected');
 await c.request('b');assert.deepEqual(c.requests,['b']);assert.equal(c.state('b').requestResult,null);
 await c.transfer('a','b');assert.equal(c.owner,'b');await assert.rejects(c.reject('a','b'),/control/);
});

test('text accepts Korean and punctuation as data while rejecting control characters',()=>{
 assert.deepEqual(validateAction({kind:'text',text:'한글 테스트; $(id)'}),{kind:'text',text:'한글 테스트; $(id)'});
 for(const text of ['','줄\n바꿈','x\u0000y','가'.repeat(129),'\ud800'])assert.throws(()=>validateAction({kind:'text',text}));
});
