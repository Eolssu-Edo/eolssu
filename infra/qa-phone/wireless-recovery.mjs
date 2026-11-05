import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,rename,rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {Rejected} from './control.mjs';
const execute=promisify(execFile);
const endpoint=value=>typeof value==='string'&&/^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$/.test(value)&&value.split(':')[0].split('.').every(x=>Number(x)<=255)&&Number(value.split(':')[1])>0&&Number(value.split(':')[1])<=65535;
const validIdentity=value=>typeof value==='string'&&/^[A-Za-z0-9_-]{4,80}$/.test(value);
export async function recoverWireless({configuredSerial,state,run}){
  if(!endpoint(configuredSerial))throw Error('Explicit wireless IPv4 endpoint required');
  const pinned=state?.configuredSerial===configuredSerial&&validIdentity(state.identity)&&endpoint(state.endpoint);
  const current=pinned?{configuredSerial,identity:state.identity,endpoint:state.endpoint}:{configuredSerial,endpoint:configuredSerial};
  const identity=async address=>{try{const value=(await run(['-s',address,'shell','getprop','ro.serialno'])).trim();return validIdentity(value)?value:null;}catch{return null;}};
  const actual=await identity(current.endpoint);
  if(actual&&(!current.identity||actual===current.identity))return {...current,identity:actual,status:'connected'};
  if(!current.identity)return {...current,status:'identity-required'};
  const safe=async args=>{try{return await run(args);}catch{return '';}};
  // A connection attempt uses existing ADB TLS credentials. Never pair, scan ports or run input.
  const services=await safe(['mdns','services']),devices=await safe(['devices']);
  const candidates=new Set();
  for(const line of services.split(/\r?\n/)){
    const parts=line.trim().split(/\s+/);
    if(parts.length===3&&parts[0].startsWith(`adb-${current.identity}-`)&&parts[1]==='_adb-tls-connect._tcp'&&endpoint(parts[2]))candidates.add(parts[2]);
  }
  for(const line of devices.split(/\r?\n/)){
    const [address,status]=line.trim().split(/\s+/);
    // Inspect only existing wireless connections on the configured phone's last IP.
    if(status==='device'&&endpoint(address)&&address.split(':')[0]===current.endpoint.split(':')[0])candidates.add(address);
  }
  // Retrying the last address also handles Wi-Fi loss without any port change.
  candidates.add(current.endpoint);
  if(candidates.size>16)return {...current,status:'ambiguous'};
  const matches=[];
  for(const address of candidates){await safe(['connect',address]);if(await identity(address)===current.identity)matches.push(address);}
  if(matches.length!==1)return {...current,status:matches.length?'ambiguous':'searching'};
  return {...current,endpoint:matches[0],status:'connected'};
}
export async function readWirelessState(file,configuredSerial){
  try{const state=JSON.parse(await readFile(file,'utf8'));return state.configuredSerial===configuredSerial?state:null;}
  catch(e){if(e.code==='ENOENT')return null;throw Error('Wireless state unreadable');}
}
async function save(file,state){
  const temp=file+'.'+randomUUID()+'.tmp';
  try{
    await writeFile(temp,JSON.stringify(state,null,2),{flag:'wx'});
    // Windows readers can briefly hold a handle without FILE_SHARE_DELETE.
    for(let attempt=0;;attempt++){
      try{await rename(temp,file);break;}
      catch(e){if(!['EPERM','EACCES'].includes(e.code)||attempt>=9)throw e;await delay(25);}
    }
  }finally{await rm(temp,{force:true});}
}
export async function queueWirelessPort({file,port,run}){
  if(!Number.isInteger(port)||port<1||port>65535)throw new Rejected('포트는 1~65535 사이 숫자로 입력하세요',400);
  const state=JSON.parse(await readFile(file,'utf8'));
  if(!validIdentity(state.identity)||!endpoint(state.endpoint)||!endpoint(state.configuredSerial))throw new Rejected('먼저 운영 PC에서 기기를 등록해 주세요',503);
  try{await readFile(file+'.request');throw new Rejected('포트 변경을 처리 중입니다',409);}catch(e){if(e.code!=='ENOENT')throw e;}
  const address=state.endpoint.split(':')[0]+':'+port;
  try{
    await run(['connect',address]);
    if((await run(['-s',address,'shell','getprop','ro.serialno'])).trim()!==state.identity)throw Error('identity');
  }catch{throw new Rejected('해당 포트에서 등록된 기기에 연결할 수 없습니다. 폰의 무선 디버깅 포트를 확인하세요',400);}
  await save(file+'.request',{configuredSerial:state.configuredSerial,identity:state.identity,endpoint:address,status:'connected'});
  return {port,restarting:true};
}
export async function wirelessPortState(file){
  try{const s=JSON.parse(await readFile(file,'utf8'));return {available:validIdentity(s.identity)&&endpoint(s.endpoint),port:endpoint(s.endpoint)?Number(s.endpoint.split(':')[1]):null};}
  catch{return {available:false,port:null};}
}
export async function watchWireless({configuredSerial,file,run,signal,interval=5000}){
  let state=await readWirelessState(file,configuredSerial);
  while(!signal.aborted){
    const requested=await readWirelessState(file+'.request',configuredSerial);
    if(requested){
      if(state?.identity&&requested.identity===state.identity&&endpoint(requested.endpoint)){
        const verified=await recoverWireless({configuredSerial,state:requested,run});
        if(verified.status==='connected'&&verified.endpoint===requested.endpoint){await save(file,verified);state=verified;}
      }
      await rm(file+'.request',{force:true});
    }
    const next=await recoverWireless({configuredSerial,state,run});
    if(signal.aborted)return;
    if(JSON.stringify(next)!==JSON.stringify(state)){await save(file,next);state=next;}
    try{await delay(interval,undefined,{signal});}catch(e){if(e.name!=='AbortError')throw e;}
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const [adb,port,configuredSerial,file]=process.argv.slice(2);
  if(!adb||!/^\d{1,5}$/.test(port)||Number(port)<1||Number(port)>65535||!file||!endpoint(configuredSerial))throw Error('Invalid wireless watcher arguments');
  const run=async args=>(await execute(adb,['-P',port,...args],{windowsHide:true,timeout:2500,maxBuffer:256*1024})).stdout;
  const controller=new AbortController(),parent=process.ppid;
  const monitor=setInterval(()=>{try{process.kill(parent,0);}catch(e){if(e.code==='ESRCH')controller.abort();}},5000).unref();
  process.once('SIGTERM',()=>controller.abort());
  try{await watchWireless({configuredSerial,file,run,signal:controller.signal});}finally{clearInterval(monitor);}
}
