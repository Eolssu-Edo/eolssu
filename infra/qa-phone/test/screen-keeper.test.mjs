import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {EventEmitter} from 'node:events';
import {PassThrough,Writable} from 'node:stream';
import {ScreenKeeper} from '../screen-keeper.mjs';
const sha=b=>createHash('sha256').update(b).digest('hex');
async function fixture(t,{run=async()=>'',valid=true}={}){
 const root=await mkdtemp(join(tmpdir(),'qa290-screen-'));t.after(()=>rm(root,{recursive:true,force:true}));
 const binary=Buffer.from('test-only-native-boundary'),source=await readFile(new URL('../android/BrightScreen.java',import.meta.url));
 await writeFile(join(root,'bright-screen.jar'),binary);await writeFile(join(root,'bright-screen.json'),JSON.stringify({leaseMs:15000,sha256:valid?sha(binary):'bad',sourceSha256:sha(source)}));
 const adb=[],launches=[],commands=[];
 const phone={deviceArgs:async()=>['-P','5037','-t','42'],adb:'fixture-adb',port:5037,serial:'fixture-phone',async run(args){adb.push(args);return run(args);}};
 const keeper=new ScreenKeeper(phone,join(root,'bright-screen.jar'),{launch(...args){
  const child=new EventEmitter();child.stdout=new PassThrough();child.stderr=new PassThrough();
  const exit=()=>queueMicrotask(()=>child.emit('exit',0));child.kill=exit;
  child.stdin=new Writable({write(chunk,encoding,done){commands.push(chunk.toString());if(chunk.toString().includes('stop'))exit();done();}});
  launches.push(args);queueMicrotask(()=>child.stdout.write('READY\n'));return child;
 }});t.after(()=>keeper.close());return {keeper,adb,launches,commands};
}
test('one native brightness lease covers repeated viewers and releases without changing timeout or injecting input',async t=>{
 const {keeper,adb,launches,commands}=await fixture(t);
 await keeper.setPresent(true);assert.equal(keeper.state().active,true);
 for(let i=0;i<20;i++)await keeper.setPresent(true);
 keeper.refresh();assert.equal(launches.length,1);assert.deepEqual(launches[0][1].slice(0,4),['-P','5037','-t','42']);assert.ok(commands.includes('ping\n'));
 await keeper.setPresent(false);assert.equal(keeper.state().active,false);assert.ok(commands.includes('stop\n'));
 assert.equal(adb.filter(a=>a[0]==='push').length,1);assert.equal(adb.filter(a=>a[1]==='rm').length,1);
 assert.equal(adb.some(a=>a.includes('settings')||a.includes('input')),false);
});
test('last viewer leaving during startup cancels acquisition and cleans its own file',async t=>{
 let entered,release;const pushed=new Promise(r=>entered=r),gate=new Promise(r=>release=r);
 const {keeper,launches,adb}=await fixture(t,{run:async args=>{if(args[0]==='push'){entered();await gate;}return '';}});
 const starting=keeper.setPresent(true);await pushed;keeper.setPresent(false);const closing=keeper.close();release();await starting;await closing;
 assert.equal(launches.length,0);assert.equal(keeper.state().active,false);assert.equal(adb.at(-1)[1],'rm');
});
test('integrity failure is visible and never launches an unverified phone helper',async t=>{
 const {keeper,launches}=await fixture(t,{valid:false});await keeper.setPresent(true);
 assert.equal(keeper.state().active,false);assert.ok(keeper.state().error);assert.equal(launches.length,0);
});
