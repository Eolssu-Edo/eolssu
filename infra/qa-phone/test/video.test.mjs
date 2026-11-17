import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PhoneVideo} from '../video.mjs';
import {VideoPackets} from '../public/video-wire.js';
import {EventEmitter} from 'node:events';

test('a keyframe burst drains in order instead of disconnecting before the socket can drain',()=>{
 const video=new PhoneVideo({},null),viewer=new EventEmitter(),chunks=[];
 viewer.qaVideoReady=true;viewer.writableLength=0;viewer.write=packet=>{chunks.push(packet);viewer.writableLength+=packet.length;return viewer.writableLength<16384;};viewer.destroy=()=>viewer.destroyed=true;video.clients.add(viewer);
 const key=Buffer.alloc(300000),delta=Buffer.alloc(1024);video.broadcast(key);video.broadcast(delta);
 assert.equal(viewer.destroyed,undefined);assert.equal(chunks.length,1);assert.equal(viewer.qaVideoQueued,1024);
 viewer.writableLength=0;viewer.emit('drain');assert.deepEqual(chunks,[key,delta]);assert.equal(viewer.qaVideoQueued,0);
});

test('a blocked viewer still disconnects when its queued packets exceed the existing 256 KiB bound',t=>{
 const reasons=[];t.mock.method(console,'warn',value=>reasons.push(value));
 const video=new PhoneVideo({},null),viewer=new EventEmitter();viewer.qaVideoReady=true;viewer.write=()=>false;viewer.destroy=()=>viewer.destroyed=true;video.clients.add(viewer);
 video.broadcast(Buffer.alloc(1024));video.broadcast(Buffer.alloc(256*1024+1));assert.equal(viewer.destroyed,true);assert.equal(viewer.qaVideoQueued,0);assert.ok(reasons[0].includes('queue-limit'));
});
test('static screen stays live through transport heartbeat; slow viewer is disconnected',async()=>{
 const calls=[],chunks=[];
 const phone={run:async args=>{calls.push(args);return 'device\n';}};
 const video=new PhoneVideo(phone,null);
 video.active={socket:{destroyed:false},closing:false};video.lastFrameAt=Date.now()-60000;
 const viewer={writableLength:0,write:b=>chunks.push(b)},slow={writableLength:300000,destroy(){this.destroyed=true;}};
 video.clients.add(viewer);video.clients.add(slow);
 await video.heartbeat();assert.equal(video.healthy,true);assert.equal(slow.destroyed,true);assert.deepEqual(calls,[['get-state']]);
 const packets=[];const parser=new VideoPackets({onPacket:p=>packets.push(p)});parser.push(Buffer.from('68323634','hex'));parser.push(chunks[0]);
 assert.equal(packets.length,1);assert.equal(packets[0].payload.length,0);
 video.lastTransportAt=Date.now()-7000;assert.equal(video.healthy,false);
});
test('transport loss ends streams and removes only its own phone file and forward',async()=>{
 const calls=[];const phone={run:async args=>{calls.push(args);if(args[0]==='get-state')throw Error('offline');return '';}};
 const video=new PhoneVideo(phone,null),client={destroy(){this.destroyed=true;}};
 const socket={destroyed:false,destroy(){this.destroyed=true;}};
 video.active={remote:'/data/local/tmp/qa290-test.jar',port:12345,socket,closing:false};video.clients.add(client);
 await video.heartbeat();assert.equal(video.active,null);assert.equal(client.destroyed,true);assert.equal(socket.destroyed,true);
 assert.deepEqual(calls,[['get-state'],['forward','--remove','tcp:12345'],['shell','rm','-f','/data/local/tmp/qa290-test.jar']]);
});

test('an ADB probe timeout cannot discard a currently arriving video, but a silent transport still closes',async()=>{
 const phone={run:async args=>{if(args[0]==='get-state')throw Error('timeout');return '';}};
 const video=new PhoneVideo(phone,null),client={destroy(){this.destroyed=true;}};
 video.active={remote:'/data/local/tmp/qa290-test.jar',port:12345,socket:{destroyed:false,destroy(){this.destroyed=true;}},closing:false};
 video.clients.add(client);video.lastFrameAt=Date.now();video.lastTransportAt=Date.now();
 await video.heartbeat();assert.ok(video.active);assert.equal(client.destroyed,undefined);assert.equal(video.healthy,true);
 video.lastFrameAt=Date.now()-4000;video.lastTransportAt=Date.now()-7000;
 await video.heartbeat();assert.equal(video.active,null);assert.equal(client.destroyed,true);
});

test('shutdown waits for cleanup already started by a dropped transport',async()=>{
 let release,entered;const pending=new Promise(r=>release=r),started=new Promise(r=>entered=r);
 const phone={run:async args=>{if(args[0]==='get-state')throw Error('offline');if(args[0]==='forward'){entered();await pending;}return '';}};
 const video=new PhoneVideo(phone,null);video.active={remote:'/data/local/tmp/qa290-test.jar',port:12345,socket:{destroy(){}},closing:false};
 const failing=video.heartbeat();await started;assert.equal(video.active,null);
 let finished=false;const stopping=video.stop().then(()=>finished=true);await Promise.resolve();assert.equal(finished,false);
 release();await failing;await stopping;assert.equal(finished,true);
});
