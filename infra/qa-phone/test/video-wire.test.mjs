import {test} from 'node:test';
import assert from 'node:assert/strict';
import {VideoPackets,avcCodec} from '../public/video-wire.js';
function packet(flag,payload){const b=Buffer.alloc(12+payload.length);b.writeBigUInt64BE(flag);b.writeUInt32BE(payload.length,8);Buffer.from(payload).copy(b,12);return b;}
const session=Buffer.from('80000000000002d0000005f0','hex');
const codec=Buffer.from('68323634','hex');
test('scrcpy5 fragmented stream strips private device header and preserves frame flags',()=>{
 const out=[],sessions=[];const parser=new VideoPackets({deviceHeader:true,onSession:s=>sessions.push([s.width,s.height]),onPacket:p=>out.push(p)});
 const config=packet(1n<<62n,[0,0,0,1,103,66,192,31]),key=packet((1n<<61n)|1000n,[0,0,1,101,1]);
 const input=Buffer.concat([Buffer.alloc(65),codec,session,config,key]);
 for(const byte of input)parser.push(Uint8Array.of(byte));
 assert.deepEqual(sessions,[[720,1520]]);assert.equal(out.length,2);assert.equal(out[0].config,true);assert.equal(out[1].key,true);assert.equal(out[1].timestamp,1000);assert.deepEqual(Buffer.from(out[1].raw),key);assert.equal(avcCodec(out[0].payload),'avc1.42c01f');
});
test('invalid codec, geometry and excessive packet length fail before payload allocation',()=>{
 assert.throws(()=>new VideoPackets().push(Buffer.from('00000000','hex')));
 assert.throws(()=>new VideoPackets().push(Buffer.concat([codec,Buffer.from('800000000000000000000001','hex')])));
 const huge=Buffer.alloc(12);huge.writeUInt32BE(5000000,8);assert.throws(()=>new VideoPackets().push(Buffer.concat([codec,huge])));
 assert.throws(()=>avcCodec(Uint8Array.of(1,2,3)));
});
