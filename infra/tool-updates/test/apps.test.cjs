const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs/promises');const path=require('node:path');const os=require('node:os');const crypto=require('node:crypto');const {Readable}=require('node:stream');
const {latestBuild,saveVerified}=require('../apps.cjs');
const meta=(id,builtAt,data)=>({id,builtAt,sha256:crypto.createHash('sha256').update(data).digest('hex'),size:data.length});
test('selects the newest actual published artifact from an ascending index',()=>{
 const old=meta('20261101T000000Z-1-aaaaaaaa','2026-11-01T00:00:00Z',Buffer.from('old'));
 const recent=meta('20261105T000000Z-2-bbbbbbbb','2026-11-05T00:00:00Z',Buffer.from('new'));
 assert.equal(latestBuild({builds:[old,recent]}).id,recent.id);
 assert.throws(()=>latestBuild({builds:[{...recent,id:'../../escape'}]}),/빌드/);
});
test('checksum failure preserves an existing download and clears only its partial file',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'updates-apk-'));try{
  const dest=path.join(dir,'existing.apk');await fs.writeFile(dest,'previous');
  const good=Buffer.from('PK new apk');const m=meta('20261105T000000Z-2-bbbbbbbb','2026-11-05T00:00:00Z',good);
  await assert.rejects(saveVerified(Readable.from([Buffer.from('corrupt')]),dest,m),/검증/);
  assert.equal(await fs.readFile(dest,'utf8'),'previous');assert.deepEqual(await fs.readdir(dir),['existing.apk']);
  await saveVerified(Readable.from([good]),dest,m);assert.equal(await fs.readFile(dest,'utf8'),'PK new apk');
 }finally{await fs.rm(dir,{recursive:true,force:true});}
});

const {terminalShortcut}=require('../ssh.cjs');
test('terminal shortcuts allow only the two fixed aliases and preserve SSH config',async()=>{
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'notifier-ssh-'));
 try{
  const mac=await terminalShortcut('edo1',directory,'darwin');
  assert.match(await fs.readFile(mac,'utf8'),/exec \/usr\/bin\/ssh -o ClearAllForwardings=yes edo1/);
  const win=await terminalShortcut('edo2',directory,'win32');
  assert.match(await fs.readFile(win,'utf8'),/ssh -o ClearAllForwardings=yes edo2/);
  await assert.rejects(()=>terminalShortcut('edo1;exit',directory,'darwin'));
 }finally{await fs.rm(directory,{recursive:true,force:true});}
});
