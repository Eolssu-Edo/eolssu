'use strict';
const fs=require('node:fs');
const fsp=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const {VERSION}=require('./engine.cjs');
const exec=promisify(execFile);
const PACKAGE='@thisis-joe/coco-skills';
const REGISTRY='https://registry.npmjs.org/@thisis-joe%2Fcoco-skills';
const PUBLISH_HOST='edo1';
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
const legacyGraphPath=root=>path.join(root,'graphify-out');

function legacyGraphExists(root) {
 return path.isAbsolute(root)&&fs.existsSync(legacyGraphPath(root));
}

async function trashLegacyGraph(selectedRoot,trash) {
 if(!legacyGraphExists(selectedRoot)) return false;
 const root=await project(selectedRoot),folder=legacyGraphPath(root);
 const unexpected=()=>new Error('앱이 받은 Graphify 폴더인지 확인할 수 없어 그대로 두었습니다.');
 const folderStat=await fsp.lstat(folder);
 if(!folderStat.isDirectory()) throw unexpected();
 const tracked=await run(program('git'),['-C',root,'ls-files','-z','--',':(top,icase)graphify-out']);
 if(tracked.stdout) throw new Error('Git이 추적 중인 파일이 있어 Graphify 폴더를 그대로 두었습니다.');
 const contents=await fsp.readdir(folder,{withFileTypes:true});
 const expected={'.lock':'file','active.json':'file',versions:'directory'};
 if(contents.some(entry=>!(Object.hasOwn(expected,entry.name)&&
    (expected[entry.name]==='file'?entry.isFile():entry.isDirectory())))) throw unexpected();
 if(!contents.some(entry=>entry.name==='active.json')||!contents.some(entry=>entry.name==='versions')) throw unexpected();
 const readRelease=async file=>{
  const stat=await fsp.lstat(file);
  if(!stat.isFile()||stat.size>16384) throw unexpected();
  let meta;
  try{meta=JSON.parse(await fsp.readFile(file,'utf8'));}catch{throw unexpected();}
  if(meta?.format!==1||meta.project!=='S15P31E102'||meta.graphify_version!=='0.9.102'||
    meta.graph_schema!==1||!RELEASE.test(meta.release||'')||meta.source_commit!==meta.release.slice(0,40)) throw unexpected();
  return meta.release;
 };
 const active=await readRelease(path.join(folder,'active.json'));
 const versions=path.join(folder,'versions');
 const releases=await fsp.readdir(versions,{withFileTypes:true});
 if(!releases.some(entry=>entry.name===active)) throw unexpected();
 for(const entry of releases) {
  if(!entry.isDirectory()||!RELEASE.test(entry.name)) throw unexpected();
  const version=path.join(versions,entry.name);
  const files=await fsp.readdir(version,{withFileTypes:true});
  if(files.length!==3||files.some(file=>!file.isFile()||!['graph.json','manifest.json','.graphify_root'].includes(file.name))) throw unexpected();
  if(await readRelease(path.join(version,'manifest.json'))!==entry.name) throw unexpected();
 }
 const current=await fsp.lstat(folder);
 if(!current.isDirectory()||current.dev!==folderStat.dev||current.ino!==folderStat.ino) throw unexpected();
 await trash(folder);
 return true;
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
function adapters() {
 return {
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
module.exports={PUBLISH_HOST,adapters,JsonStore,project,run,program,environment,cocoArguments,parseResult,npmRuntime,legacyGraphExists,trashLegacyGraph};
