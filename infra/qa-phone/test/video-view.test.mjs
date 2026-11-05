import test from 'node:test';
import assert from 'node:assert/strict';
import {VideoView} from '../public/video-view.js';

for(const hasFrame of [false,true])test(hasFrame?'a painted static frame survives healthy video heartbeats':'heartbeat without a first frame cannot leave the viewer waiting forever',t=>{
  t.mock.timers.enable({apis:['Date','setInterval'],now:100000});
  const previous=globalThis.document;
  globalThis.document={visibilityState:'visible'};
  const view=new VideoView({getContext:()=>({})});
  const abort=new AbortController();view.abort=abort;
  // A transport can deliver heartbeats without a decodable picture.
  view.connect=()=>{view.active=true;view.connectedAt=Date.now();view.lastRead=Date.now();view.lastPaint=hasFrame?Date.now():0;};
  t.after(()=>{view.running=false;clearInterval(view.watch);if(previous===undefined)delete globalThis.document;else globalThis.document=previous;});
  view.start();t.mock.timers.tick(5000);view.lastRead=Date.now();t.mock.timers.tick(2000);
  assert.equal(abort.signal.aborted,!hasFrame);
});
