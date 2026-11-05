const test = require('node:test');
const assert = require('node:assert/strict');
const {UpdateEngine} = require('../engine.cjs');
const pack = versions => ({name:'@thisis-joe/coco-skills', versions:Object.fromEntries(versions.map(v=>[v,{version:v}])), time:Object.fromEntries(versions.map((v,i)=>[v,new Date(2026,10,i+1).toISOString()])), 'dist-tags':{latest:versions.at(-1)}});
function harness() {
 const data={}; const notifications=[]; const installs=[]; let metadata=pack(['0.2.0']);
 const engine=new UpdateEngine({load:()=>data,save:value=>Object.assign(data,JSON.parse(JSON.stringify(value))),
  graph:{check:async()=>null,download:async()=>({status:'updated'})},
  coco:{check:async()=>metadata,install:async options=>{installs.push(options);return {status:'installed'};}},
  notify:async value=>{notifications.push(value);return true;},now:()=>1_000_000});
 return {engine,notifications,installs,data,setPack:value=>{metadata=value;}};
}
test('detects every new COCO version including prereleases and deduplicates after restart', async()=>{
 const h=harness(); await h.engine.checkCoco();
 h.setPack(pack(['0.2.0','1.5.1','1.6.0-beta.1']));
 await h.engine.checkCoco(); await h.engine.checkCoco();
 assert.deepEqual(h.notifications.at(-1).versions,['1.6.0-beta.1','1.5.1']);
 const restarted=new UpdateEngine(h.engine.deps);await restarted.checkCoco();
 assert.equal(h.notifications.length,1);
 assert.equal(h.installs.length,0);
});
test('graph notification is manual by default and automatic download is opt-in', async()=>{
 const data={}; let downloads=0; const notices=[]; let installed=false;
 const e=new UpdateEngine({load:()=>data,save:v=>Object.assign(data,structuredClone(v)),
  graph:{check:async()=>({release:'release1',source_commit:'abc',compatible:true,installed}),download:async()=>{downloads++;installed=true;return {status:'updated'};}},
  coco:{check:async()=>pack(['0.2.0'])},notify:async n=>{notices.push(n);},now:()=>1_000_000});
 e.configure({projectPath:'/fixture/project'});
 await e.checkGraph(); await e.checkGraph();
 assert.equal(downloads,0); assert.equal(notices.length,1);
 e.configure({autoApply:true}); await e.checkGraph();
 assert.equal(downloads,1); assert.equal(e.snapshot().graph.phase,'current');
 await e.checkGraph(); assert.equal(downloads,1);
});
test('COCO install requires a known exact version and explicit runtime/scope', async()=>{
 const h=harness(); h.setPack(pack(['0.2.0','1.6.0-beta.1'])); await h.engine.checkCoco();
 h.engine.configure({projectPath:'/fixture/project',cocoRuntime:'both',cocoScope:'project'});
 await h.engine.installCoco('1.6.0-beta.1');
 assert.deepEqual(h.installs[0],{version:'1.6.0-beta.1',runtimes:['codex','claude'],scope:'project',projectPath:'/fixture/project'});
 await assert.rejects(h.engine.installCoco('latest'),/버전/);
});
test('keeps graph errors independent from npm and never auto-downloads an incompatible graph',async()=>{
 let downloads=0; const notes=[];
 const e=new UpdateEngine({load:()=>({config:{projectPath:'/fixture',autoApply:true}}),save:()=>{},
 graph:{check:async()=>({release:'future',compatible:false,installed:false}),download:async()=>{downloads++;}},
 coco:{check:async()=>pack(['0.2.0','2.0.0-beta.1'])},notify:async n=>notes.push(n),now:()=>1});
 await e.check(); assert.equal(downloads,0);assert.equal(e.snapshot().graph.phase,'code-behind');
 assert.equal(e.snapshot().coco.latest,'2.0.0-beta.1');
});
test('restores cached npm versions when a restarted app receives HTTP 304',async()=>{
 const data={coco:{known:['1.5.1'],versions:['1.5.1'],etag:'etag'}};
 const e=new UpdateEngine({load:()=>data,save:()=>{},graph:{},coco:{check:async()=>({notModified:true})},notify:async()=>{},now:()=>1});
 await e.checkCoco();assert.deepEqual(e.snapshot().coco.versions,['1.5.1']);
});
test('parses the official COCO CLI receipt after its preserved-global warning',()=>{
 const {parseResult}=require('../adapters.cjs');
 assert.equal(parseResult('Warning: global install still applies\n{\n  "status": "installed",\n  "skills": 30\n}\n').status,'installed');
});
test('download failure keeps automatic retries bounded and preserves npm checks',async()=>{
 let attempts=0,time=1000;const e=new UpdateEngine({load:()=>({config:{projectPath:'/fixture',autoApply:true}}),save:()=>{},now:()=>time,notify:async()=>{},
 graph:{check:async()=>({release:'new',compatible:true,installed:false}),download:async()=>{attempts++;throw new Error('offline');}},coco:{check:async()=>pack(['0.2.0'])}});
 await e.check();await e.check();assert.equal(attempts,1);assert.equal(e.snapshot().coco.phase,'ready');
 time+=300001;await e.check();assert.equal(attempts,2);
});
test('builds explicit pinned npm arguments without global-removal or shell fragments',()=>{
 const {cocoArguments}=require('../adapters.cjs');
 assert.deepEqual(cocoArguments('/npm-cli.js','1.6.0-beta.1','codex','project','C:\\a project'),['/npm-cli.js','exec','--yes','--package=@thisis-joe/coco-skills@1.6.0-beta.1','--','coco-skills','install','--runtime','codex','--project','C:\\a project']);
 assert.throws(()=>cocoArguments('/npm','latest; echo leaked','codex','global',''),/버전/);
});

test('previous Graphify-only consent does not automatically install COCO',async()=>{
 const h=harness();
 h.data.config={projectPath:'/fixture/project',autoDownload:true};
 h.setPack(pack(['0.2.0','2.0.0-beta.2']));
 const restarted=new UpdateEngine(h.engine.deps);
 await restarted.check();
 assert.equal(restarted.snapshot().config.autoApply,false);
 assert.equal(h.installs.length,0);
});

test('auto apply uses the highest COCO version in the saved scope, even with cached metadata',async()=>{
 const h=harness();
 h.setPack(pack(['0.2.0','2.0.0-beta.11','2.0.0-beta.2','1.9.0']));
 await h.engine.checkCoco();
 assert.equal(h.installs.length,0);
 h.engine.configure({projectPath:'/fixture/project',cocoRuntime:'both',cocoScope:'project',autoApply:true});
 h.setPack({notModified:true});
 await h.engine.checkCoco();
 await h.engine.checkCoco();
 assert.deepEqual(h.installs,[{version:'2.0.0-beta.11',runtimes:['codex','claude'],scope:'project',projectPath:'/fixture/project'}]);
});
