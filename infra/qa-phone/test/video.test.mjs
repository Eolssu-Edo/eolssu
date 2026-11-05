import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PhoneVideo} from '../video.mjs';
import {VideoPackets} from '../public/video-wire.js';
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

test('shutdown waits for cleanup already started by a dropped transport',async()=>{
 let release,entered;const pending=new Promise(r=>release=r),started=new Promise(r=>entered=r);
 const phone={run:async args=>{if(args[0]==='get-state')throw Error('offline');if(args[0]==='forward'){entered();await pending;}return '';}};
 const video=new PhoneVideo(phone,null);video.active={remote:'/data/local/tmp/qa290-test.jar',port:12345,socket:{destroy(){}},closing:false};
 const failing=video.heartbeat();await started;assert.equal(video.active,null);
 let finished=false;const stopping=video.stop().then(()=>finished=true);await Promise.resolve();assert.equal(finished,false);
 release();await failing;await stopping;assert.equal(finished,true);
});
