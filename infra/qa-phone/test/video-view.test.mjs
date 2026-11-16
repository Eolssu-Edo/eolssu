import {test} from 'node:test';
import assert from 'node:assert/strict';
import {VideoView} from '../public/video-view.js';

test('parser failure closes each fetch lease before retry, without exhausting the two-stream limit',async t=>{
  const names=['VideoDecoder','isSecureContext'],original=new Map(names.map(name=>[name,Object.getOwnPropertyDescriptor(globalThis,name)]));
  t.after(()=>{for(const [name,value]of original)if(value)Object.defineProperty(globalThis,name,value);else delete globalThis[name];});
  Object.defineProperty(globalThis,'VideoDecoder',{configurable:true,value:class{}});
  Object.defineProperty(globalThis,'isSecureContext',{configurable:true,value:true});
  const queued=[],leases=[],statuses=[],warnings=[];let cancelled=0;
  t.mock.method(globalThis,'setTimeout',(fn,ms)=>{queued.push({fn,ms});return queued.length;});
  t.mock.method(console,'warn',(...args)=>warnings.push(args.join(' ')));
  t.mock.method(globalThis,'fetch',async(_url,{signal})=>{
    if(leases.filter(lease=>lease.open).length>=2){statuses.push(429);return{status:429,ok:false,body:{}};}
    const lease={open:true,signal};leases.push(lease);signal.addEventListener('abort',()=>lease.open=false);statuses.push(200);
    return{status:200,ok:true,body:{getReader:()=>({read:async()=>({done:false,value:new Uint8Array([0,0,0,0])}),cancel:async()=>{cancelled++;lease.open=false;}})}};
  });
  const view=new VideoView({getContext:()=>({})});view.running=true;view.generation=1;
  await view.connect(1);await view.connect(1);await view.connect(1);
  assert.deepEqual(statuses,[200,200,200]);assert.equal(leases.filter(lease=>lease.open).length,0);
  assert.equal(cancelled,3);assert.ok(leases.every(lease=>lease.signal.aborted));assert.equal(queued.length,3);
  assert.ok(warnings.every(warning=>warning.includes('unsupported video codec')));
});
