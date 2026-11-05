'use strict';
const fs=require('node:fs');
const fsp=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const crypto=require('node:crypto');
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const {VERSION}=require('./engine.cjs');
const exec=promisify(execFile);
const PACKAGE='@thisis-joe/coco-skills';
const REGISTRY='https://registry.npmjs.org/@thisis-joe%2Fcoco-skills';
const PUBLISH_HOST='edo1';
const GRAPH_INDEX='/opt/eolssu/graphify/data/published/index.json';
const RELEASE=/^[0-9a-f]{40}-[0-9a-f]{16}$/;

function environment() {
 const dirs=[path.join(os.homedir(),'.local/bin'),'/opt/homebrew/bin','/usr/local/bin'];
 if(process.platform==='win32') dirs.push(path.join(process.env.ProgramFiles||'C:\\Program Files','nodejs'));
 return {...process.env,PATH:[...dirs,process.env.PATH||process.env.Path||''].join(path.delimiter)};
}
function program(name) {
 const env=environment();
 for(const dir of env.PATH.split(path.delimiter)) for(const ext of process.platform==='win32'?['.exe','.cmd','']:['']) {
  const p=path.join(dir,name+ext);
  try{fs.accessSync(p,fs.constants.X_OK);return p;}catch{}
 }
 throw new Error(name+' 실행 파일을 찾지 못했습니다. 설치 후 앱을 다시 열어주세요.');
}
async function run(command,args,options={}) {
 try{return await exec(command,args,{env:environment(),timeout:30000,maxBuffer:2*1024*1024,windowsHide:true,...options});}
 catch(e){const error=new Error((e.stderr||e.message||'명령 실행 실패').trim().slice(-1400));error.code=e.code;throw error;}
}
function normalizeOrigin(url){return url.trim().replace(/\.git$/,'').replace(/^https:\/\//,'').replace(/^ssh:\/\/git@/,'').replace(/^git@/,'').replace(':','/');}
async function project(root) {
 if(!root||!path.isAbsolute(root)) throw new Error('프로젝트 루트의 절대경로를 선택해주세요.');
 const actual=await fsp.realpath(root);
 const top=(await run(program('git'),['-C',actual,'rev-parse','--show-toplevel'])).stdout.trim();
 if(await fsp.realpath(top)!==actual) throw new Error('Git 프로젝트의 최상위 폴더를 선택해주세요.');
 const origin=(await run(program('git'),['-C',actual,'remote','get-url','origin'])).stdout;
 if(normalizeOrigin(origin)!=='lab.ssafy.com/s15-final/S15P31E102') throw new Error('S15P31E102 프로젝트를 선택해주세요.');
 return actual;
}
function graphMetadata(m) {
 if(m?.format!==1||m.project!=='S15P31E102'||m.graphify_version!=='0.9.102'||m.graph_schema!==1||!RELEASE.test(m.release||'')||!m.release.startsWith(m.source_commit+'-')||!/^[a-f0-9]{64}$/.test(m.sha256||'')||!Number.isInteger(m.size)||m.size<=0||m.size>128*1024*1024) throw new Error('지원하지 않거나 손상된 Graphify 버전 정보입니다.');
 return m;
}
async function installedGraph(root,m) {
 try {
  const local=JSON.parse(await fsp.readFile(path.join(root,'graphify-out/active.json'),'utf8'));
  if(local.release!==m.release||local.sha256!==m.sha256) return false;
  const graphRoot=await fsp.realpath(path.join(root,'graphify-out'));
  const file=await fsp.realpath(path.join(graphRoot,'versions',m.release,'graph.json'));
  if(!file.startsWith(graphRoot+path.sep)) return false;
  const stat=await fsp.stat(file);if(stat.size!==m.size) return false;
  const hash=crypto.createHash('sha256');for await(const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex')===m.sha256;
 }catch{return false;}
}
function python() {
 if(process.platform==='win32') {try{return {command:program('py'),prefix:['-3']};}catch{}}
 return {command:program(process.platform==='win32'?'python':'python3'),prefix:[]};
}
function parseResult(text) {
 let result;
 const jsonStart=text.search(/^\s*\{/m);
 if(jsonStart>=0) {try{result=JSON.parse(text.slice(jsonStart));}catch{}}
 try{result=result||JSON.parse(text);}catch{for(const line of text.split(/\r?\n/)){try{result=JSON.parse(line);}catch{}}}
 if(!result||!['installed','unchanged','updated'].includes(result.status)) throw new Error('실행은 끝났지만 성공 영수증을 확인하지 못했습니다.\n'+text.slice(-600));
 return result;
}
function cocoArguments(npmCli,version,runtime,scope,root) {
 if(!VERSION.test(version)||!['codex','claude'].includes(runtime)||!['project','global'].includes(scope)) throw new Error('설치 버전과 대상을 확인해주세요.');
 return [npmCli,'exec','--yes','--package='+PACKAGE+'@'+version,'--','coco-skills','install','--runtime',runtime,...(scope==='global'?['--global']:['--project',root])];
}
function npmRuntime() {
 const node=program('node'),base=path.dirname(node),npm=program('npm');
 const candidates=[fs.realpathSync(npm),path.join(base,'node_modules/npm/bin/npm-cli.js'),path.join(base,'../lib/node_modules/npm/bin/npm-cli.js'),path.join(path.dirname(npm),'node_modules/npm/bin/npm-cli.js')];
 const cli=candidates.find(p=>p.endsWith('npm-cli.js')&&fs.existsSync(p));
 if(!cli) throw new Error('npm CLI 경로를 확인하지 못했습니다. Node.js 22 이상을 설치해주세요.');
 return {node,cli};
}
function adapters(contextPath) {
 return {
  graph:{
   async check(c) {
    const root=await project(c.projectPath);
    const {stdout}=await run(program('ssh'),['-o','ClearAllForwardings=yes','-o','BatchMode=yes','-o','ConnectTimeout=5',PUBLISH_HOST,'cat -- '+GRAPH_INDEX],{timeout:15000,maxBuffer:1024*1024});
    const index=JSON.parse(stdout);
    if(index.format!==1||index.project!=='S15P31E102'||!Array.isArray(index.releases)||!index.releases.length) throw new Error('발행된 그래프가 아직 없습니다.');
    const m=graphMetadata(index.releases[0]);let compatible=false;
    try{await run(program('git'),['-C',root,'merge-base','--is-ancestor',m.source_commit,'HEAD']);compatible=true;}catch{}
    return {...m,compatible,installed:await installedGraph(root,m)};
   },
   async download(c) {
    const root=await project(c.projectPath),py=python();
    const {stdout}=await run(py.command,[...py.prefix,'-B',contextPath,'sync','--project',root,'--host',PUBLISH_HOST],{timeout:120000});
    return parseResult(stdout);
   }
  },
  coco:{
   async check(etag) {
    const response=await fetch(REGISTRY,{headers:{Accept:'application/json',...(etag?{'If-None-Match':etag}:{})},signal:AbortSignal.timeout(15000),redirect:'error'});
    if(response.status===304)return {notModified:true};
    if(!response.ok)throw new Error('npm 배포 조회 실패: HTTP '+response.status);
    const reader=response.body.getReader();let size=0;const chunks=[];
    while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>8*1024*1024){await reader.cancel();throw new Error('npm 응답 크기가 제한을 넘었습니다.');}chunks.push(Buffer.from(value));}
    return {data:JSON.parse(Buffer.concat(chunks).toString()),etag:response.headers.get('etag')||''};
   },
   async install({version,runtimes,scope,projectPath}) {
    const {node,cli}=npmRuntime();
    if(Number((await run(node,['-p','process.versions.node'])).stdout.split('.')[0])<22) throw new Error('COCO에는 Node.js 22 이상이 필요합니다.');
    if(scope==='project') await project(projectPath);
    const results=[];
    for(const runtime of runtimes) {
     try{
      const {stdout}=await run(node,cocoArguments(cli,version,runtime,scope,projectPath),{cwd:scope==='project'?projectPath:os.homedir(),timeout:300000});
      results.push({runtime,...parseResult(stdout)});
     }catch(e){throw new Error((results.length?'일부 대상은 완료했습니다: '+results.map(r=>r.runtime).join(', ')+'.\n':'')+runtime+' 설치 실패: '+e.message);}
    }
    return {status:'installed',results};
   }
  }
 };
}
class JsonStore {
 constructor(file){this.file=file;}
 load(){try{return JSON.parse(fs.readFileSync(this.file,'utf8'));}catch(e){if(e.code==='ENOENT')return {};throw new Error('설정 파일을 읽지 못했습니다: '+this.file);}}
 save(value){fs.mkdirSync(path.dirname(this.file),{recursive:true});const temp=this.file+'.pending';fs.writeFileSync(temp,JSON.stringify(value,null,2),{mode:0o600});fs.renameSync(temp,this.file);}
}
module.exports={PUBLISH_HOST,adapters,JsonStore,project,run,program,environment,cocoArguments,parseResult,npmRuntime,graphMetadata};
