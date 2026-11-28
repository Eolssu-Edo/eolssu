const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {execFileSync}=require('node:child_process');
const {legacyGraphExists,trashLegacyGraph}=require('../adapters.cjs');

async function fixture(t) {
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'legacy-graph-'));
 t.after(()=>fs.rm(root,{recursive:true,force:true}));
 const git=(...args)=>execFileSync('git',['-C',root,...args],{stdio:'pipe'});
 git('init','-q');
 git('remote','add','origin','git@github.com:team-edo/eolssu.git');
 const graph=path.join(root,'graphify-out');
 const commit='a'.repeat(40),release=commit+'-'+'b'.repeat(16);
 const version=path.join(graph,'versions',release);
 const manifest=JSON.stringify({format:1,project:'S15P31E102',graphify_version:'0.9.102',graph_schema:1,source_commit:commit,release});
 await fs.mkdir(version,{recursive:true});
 await fs.writeFile(path.join(graph,'active.json'),manifest);
 await fs.writeFile(path.join(graph,'.lock'),'');
 await fs.writeFile(path.join(version,'manifest.json'),manifest);
 await fs.writeFile(path.join(version,'.graphify_root'),root);
 await fs.writeFile(path.join(version,'graph.json'),'{}');
 return {root,graph,version,git};
}

test('moves a legacy download out of the project and hides the action on restart',async t=>{
 const {root,graph}=await fixture(t);
 const trash=path.join(root,'fixture-trash');
 assert.equal(legacyGraphExists(root),true);
 assert.equal(await trashLegacyGraph(root,target=>fs.rename(target,trash)),true);
 assert.equal(legacyGraphExists(root),false);
 assert.equal(await trashLegacyGraph(root,()=>assert.fail('already clean')),false);
 await assert.rejects(fs.stat(graph),{code:'ENOENT'});
 assert.equal(await fs.readFile(path.join(trash,'versions',(await fs.readdir(path.join(trash,'versions')))[0],'graph.json'),'utf8'),'{}');
});

test('preserves graph folder on trash failure and refuses user files, tracked files, and links',async t=>{
 const {root,graph,version,git}=await fixture(t);
 const trash=()=>assert.fail('unsafe folder must stay');
 await assert.rejects(trashLegacyGraph(root,async()=>{throw new Error('trash unavailable');}),/trash unavailable/);
 assert.equal(legacyGraphExists(root),true);
 const extra=path.join(graph,'notes.txt');
 await fs.writeFile(extra,'personal notes');
 await assert.rejects(trashLegacyGraph(root,trash),/확인/);
 assert.equal(await fs.readFile(extra,'utf8'),'personal notes');
 await fs.unlink(extra);
 git('add','-f','graphify-out/active.json');
 await assert.rejects(trashLegacyGraph(root,trash),/추적/);
 git('rm','--cached','-f','graphify-out/active.json');
 const file=path.join(version,'graph.json');
 await fs.unlink(file);
 if(process.platform==='win32') await fs.symlink(root,file,'junction');
 else await fs.symlink(path.join(graph,'active.json'),file);
 await assert.rejects(trashLegacyGraph(root,trash),/확인/);
});
