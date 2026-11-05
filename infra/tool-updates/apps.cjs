'use strict';
const fs=require('node:fs');const fsp=require('node:fs/promises');const path=require('node:path');const crypto=require('node:crypto');const {Transform}=require('node:stream');const {pipeline}=require('node:stream/promises');const {spawn}=require('node:child_process');
const {run,program,environment}=require('./adapters.cjs');
const BUILD=/^\d{8}T\d{6}Z-\d+-[0-9a-f]{8}$/;
function latestBuild(index) {
 if(!Array.isArray(index?.builds)||!index.builds.length)throw new Error('게시된 앱 빌드가 없습니다.');
 const build=[...index.builds].sort((a,b)=>Date.parse(b.builtAt)-Date.parse(a.builtAt))[0];
 if(!BUILD.test(build.id||'')||!Number.isFinite(Date.parse(build.builtAt))||!/^[0-9a-f]{64}$/.test(build.sha256||'')||!Number.isInteger(build.size)||build.size<=0||build.size>512*1024*1024)throw new Error('앱 빌드 정보가 올바르지 않습니다.');
 return build;
}
async function fileHash(file){const hash=crypto.createHash('sha256');for await(const c of fs.createReadStream(file))hash.update(c);return hash.digest('hex');}
async function saveVerified(stream,destination,meta,{completion=async()=>{},progress=()=>{}}={}) {
 const temporary=path.join(path.dirname(destination),'.'+path.basename(destination)+'.'+crypto.randomUUID()+'.partial');
 const hash=crypto.createHash('sha256');let size=0,last=0;
 try {
  const verify=new Transform({transform(chunk,_,callback){size+=chunk.length;if(size>meta.size){callback(new Error('다운로드 크기 검증 실패'));return;}hash.update(chunk);if(Date.now()-last>250||size===meta.size){last=Date.now();progress(Math.round(size/meta.size*100));}callback(null,chunk);}});
  await pipeline(stream,verify,fs.createWriteStream(temporary,{flags:'wx',mode:0o600,flush:true}));
  await completion();
  if(size!==meta.size||hash.digest('hex')!==meta.sha256)throw new Error('다운로드 크기·SHA-256 검증 실패');
  await fsp.rename(temporary,destination);
  return {status:'downloaded',file:destination,sha256:meta.sha256,size};
 }finally{await fsp.rm(temporary,{force:true});}
}
function sshArgs(host,command){if(!/^[A-Za-z0-9][A-Za-z0-9_.@-]*$/.test(host))throw new Error('SSH 별칭을 확인해주세요.');return ['-o','ClearAllForwardings=yes','-o','BatchMode=yes','-o','ConnectTimeout=5',host,command];}
async function listBuilds(host) {
 const result=await Promise.allSettled(['apks','ipas'].map(async kind=>{
  const {stdout}=await run(program('ssh'),sshArgs(host,'cat -- /opt/eolssu/'+kind+'/index.json'),{maxBuffer:4*1024*1024});
  return latestBuild(JSON.parse(stdout));
 }));
 const apk=result[0].status==='fulfilled'?result[0].value:null,ipa=result[1].status==='fulfilled'?result[1].value:null;
 return {phone:apk,tablet:apk,iphone:ipa,error:result.filter(r=>r.status==='rejected').map(r=>r.reason.message).join('\n')};
}
async function downloadBuild(kind,host,destination,meta,progress) {
 if(!['phone','tablet','iphone'].includes(kind))throw new Error('지원하지 않는 앱 종류입니다.');
 latestBuild({builds:[meta]});
 try{if((await fsp.stat(destination)).size===meta.size&&await fileHash(destination)===meta.sha256)return {status:'unchanged',file:destination};}catch{}
 const ios=kind==='iphone',source='/opt/eolssu/'+(ios?'ipas':'apks')+'/'+meta.id+'/app.'+(ios?'ipa':'apk');
 const child=spawn(program('ssh'),sshArgs(host,'cat -- '+source),{env:environment(),windowsHide:true,stdio:['ignore','pipe','pipe']});
 let stderr='';child.stderr.on('data',d=>{stderr=(stderr+d.toString()).slice(-4000);});
 const closed=new Promise(resolve=>{child.once('error',error=>resolve({error}));child.once('close',code=>resolve({code}));});
 const timeout=setTimeout(()=>child.kill(),300000);timeout.unref();
 try{return await saveVerified(child.stdout,destination,meta,{progress,completion:async()=>{const ended=await closed;if(ended.error||ended.code!==0)throw new Error('앱 파일 수신 실패: '+(stderr||ended.error?.message||ended.code));}});}
 finally{clearTimeout(timeout);if(child.exitCode===null)child.kill();}
}
module.exports={latestBuild,saveVerified,listBuilds,downloadBuild};
