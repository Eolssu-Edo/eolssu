import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,rename,rm,unlink} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {createConnection} from 'node:net';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {Rejected} from './control.mjs';
import {createWirelessPortHint} from './wireless-port-hint.mjs';
const execute=promisify(execFile);
const PAIRING_RETRY_MS = 250;
const PAIRING_RECOVERY_MS = 90_000;
// Samsung's S10 runs adbd as the shell UID and exposes its listening sockets.
// Probe the ADB TLS handshake before treating any of these ports as a hint.
const READ_TLS_PORT = `printf 'qa-usb\\n'
if [ "$(settings get global adb_wifi_enabled)" = 1 ]; then
  port=$(getprop service.adb.tls.port)
  case "$port" in
    ''|*[!0-9]*|0) awk -v uid="$(id -u)" '$4=="0A" && $8==uid {print $2}' /proc/net/tcp /proc/net/tcp6 | while IFS=: read -r ip hex; do printf '%d\\n' "0x$hex"; done ;;
    *) printf '%s\\n' "$port" ;;
  esac
fi`;
const endpoint=value=>typeof value==='string'&&/^\d{1,3}(?:\.\d{1,3}){3}:\d{1,5}$/.test(value)&&value.split(':')[0].split('.').every(x=>Number(x)<=255)&&Number(value.split(':')[1])>0&&Number(value.split(':')[1])<=65535;
const validIdentity=value=>typeof value==='string'&&/^[A-Za-z0-9_-]{4,80}$/.test(value);
export async function recoverWireless({configuredSerial,state,run,discover,usbPortHint=false,wirelessPortHint,deferScan=false,probeTls=isAdbTlsPort}){
  if(!endpoint(configuredSerial))throw Error('Explicit wireless IPv4 endpoint required');
  const pinned=state?.configuredSerial===configuredSerial&&validIdentity(state.identity)&&endpoint(state.endpoint);
  const current=pinned?{configuredSerial,identity:state.identity,endpoint:state.endpoint}:{configuredSerial,endpoint:configuredSerial};
  const identity=async address=>{try{const value=(await run(['-s',address,'shell','getprop','ro.serialno'])).trim();return validIdentity(value)?value:null;}catch{return null;}};
  const actual=await identity(current.endpoint);
  if(actual&&(!current.identity||actual===current.identity))return {...current,identity:actual,status:'connected'};
  if(!current.identity)return {...current,status:'identity-required'};
  const safe=async args=>{try{return await run(args);}catch{return '';}};
  const verify=async address=>{
    await safe(['connect',address]);
    return await identity(address)===current.identity;
  };
  const host=current.endpoint.split(':')[0];
  const verifiedHint=async ports=>{
    const addresses=[...new Set(ports.map(port=>`${host}:${port}`).filter(endpoint))];
    if(addresses.length>16)return null;
    const tls=await Promise.all(addresses.map(probeTls));
    for(const address of addresses.filter((_,i)=>tls[i])){
      if(await verify(address))return address;
    }
    return null;
  };
  let usbReadable=false;
  if(usbPortHint){
    const output=await safe(['-s',current.identity,'shell','-T',READ_TLS_PORT]);
    usbReadable=output.split(/\r?\n/,1)[0]==='qa-usb';
    const ports=output.trim().split(/\s+/).filter(port=>/^\d+$/.test(port));
    const address=await verifiedHint(ports);
    if(address){
      if(address!==current.endpoint)console.log(`Registered phone TLS port found over USB: ${Number(address.split(':')[1])}`);
      return {...current,endpoint:address,status:'connected'};
    }
    // Let the listener start before a broad sweep competes for ADB commands.
    if(deferScan&&usbReadable&&!ports.length)return {...current,status:'searching'};
  }
  if(wirelessPortHint){
    const ports=await wirelessPortHint(host);
    const address=ports?.length?await verifiedHint(ports):null;
    if(address){
      if(address!==current.endpoint)console.log(`Registered phone TLS port found over Wi-Fi unicast: ${Number(address.split(':')[1])}`);
      return {...current,endpoint:address,status:'connected'};
    }
    if(deferScan&&ports)return {...current,status:'searching'};
  }
  // A connection attempt uses the existing ADB credentials.
  const services=await safe(['mdns','services']),devices=await safe(['devices']);
  const candidates=new Set();
  const pairingAddresses=new Set();
  for(const line of services.split(/\r?\n/)){
    const parts=line.trim().split(/\s+/);
    if(!endpoint(parts[2]))continue;
    if(parts[1]==='_adb-tls-pairing._tcp')pairingAddresses.add(parts[2]);
    if(parts[0].startsWith(`adb-${current.identity}-`)&&parts[1]==='_adb-tls-connect._tcp')candidates.add(parts[2]);
  }
  for(const line of devices.split(/\r?\n/)){
    const [address,status]=line.trim().split(/\s+/);
    // Inspect only existing wireless connections on the configured phone's last IP.
    if(status==='device'&&endpoint(address)&&address.split(':')[0]===current.endpoint.split(':')[0])candidates.add(address);
  }
  // Retrying the last address also handles Wi-Fi loss without any port change.
  candidates.add(current.endpoint);
  candidates.add(configuredSerial);
  if(candidates.size>16)return {...current,status:'ambiguous'};
  const matches=[];
  for(const address of candidates){if(await verify(address))matches.push(address);}
  if(!matches.length&&discover&&!(deferScan&&usbReadable)){
    const found=await discover(current.endpoint,async address=>{
      if(await verify(address))return true;
      // An open TLS port may still be negotiating the ADB transport after connect returns.
      await safe(['-s',address,'wait-for-device']);
      return await identity(address)===current.identity;
    },new Set([...candidates,...pairingAddresses]));
    if(found)matches.push(found);
  }
  if(matches.length!==1)return {...current,status:matches.length?'ambiguous':'searching'};
  return {...current,endpoint:matches[0],status:'connected'};
}
export async function readWirelessState(file,configuredSerial){
  try{const state=JSON.parse(await readFile(file,'utf8'));return state.configuredSerial===configuredSerial?state:null;}
  catch(e){if(e.code==='ENOENT')return null;throw Error('Wireless state unreadable');}
}
export async function pairWireless({file,port,code,adb,adbPort,resumeAfterPairing=false}){
  if(!Number.isInteger(port)||port<1||port>65535||typeof code!=='string'||!/^\d{6}$/.test(code)){
    throw new Rejected('페어링 팝업의 포트와 6자리 코드를 입력하세요.',400);
  }
  const state=await readFile(file,'utf8').then(JSON.parse,error=>{
    if(error.code==='ENOENT')return null;
    throw error;
  });
  if(!validIdentity(state?.identity)||!endpoint(state.endpoint)){
    throw new Rejected('먼저 운영 PC에서 기기를 등록해 주세요.',503);
  }
  const address=state.endpoint.split(':')[0]+':'+port;
  const success=await new Promise(resolve=>{
    const child=execFile(adb,['-P',String(adbPort),'pair',address],{
      windowsHide:true,timeout:8000,maxBuffer:16*1024,
    },(error,stdout)=>resolve(!error&&stdout.includes(`Successfully paired to ${address}`)));
    child.stdin.on('error',()=>{});
    child.stdin.end(code+'\n');
  });
  if(!success)throw new Rejected('페어링에 실패했습니다. 기기의 팝업을 열어 둔 채 현재 포트와 코드를 확인하세요.',400);
  if(resumeAfterPairing)await writeFile(file+'.paired','',{mode:0o600});
  return {paired:true};
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
function canReachPort(address,timeout=750){
  const [host,port]=address.split(':');
  return new Promise(resolve=>{
    const socket=createConnection({host,port:Number(port)});
    const finish=reachable=>{socket.destroy();resolve(reachable);};
    socket.once('connect',()=>finish(true));
    socket.once('error',()=>finish(false));
    socket.setTimeout(timeout,()=>finish(false));
  });
}
function isAdbTlsPort(address){
  const [host,port]=address.split(':');
  const banner=Buffer.from('host::\0');
  const request=Buffer.alloc(24+banner.length);
  const command=0x4e584e43; // ADB CNXN
  request.writeUInt32LE(command);
  request.writeUInt32LE(0x01000001,4);
  request.writeUInt32LE(4096,8);
  request.writeUInt32LE(banner.length,12);
  request.writeUInt32LE(banner.reduce((sum,byte)=>sum+byte,0),16);
  request.writeUInt32LE((command^0xffffffff)>>>0,20);
  banner.copy(request,24);
  return new Promise(resolve=>{
    const socket=createConnection({host,port:Number(port)});
    const finish=result=>{clearTimeout(timer);socket.destroy();resolve(result);};
    const timer=setTimeout(()=>finish(false),500);
    let response=Buffer.alloc(0);
    socket.once('connect',()=>socket.write(request));
    socket.once('error',()=>finish(false));
    socket.once('end',()=>finish(false));
    socket.on('data',chunk=>{
      response=Buffer.concat([response,chunk]);
      if(response.length<24)return;
      const tls=0x534c5453; // ADB STLS
      finish(response.readUInt32LE()===tls&&response.readUInt32LE(12)===0&&response.readUInt32LE(20)===((tls^0xffffffff)>>>0));
    });
  });
}
export function createPortDiscovery({probe=canReachPort,signal,now=Date.now}={}){
  const defaultRange=[32768,60999];
  let range=defaultRange, lastAddress, checked=0, retryAt=0, pairingUntil=0;
  const discover=async (address,verify,skip)=>{
    const [host,lastPort]=address.split(':');
    const [a,b]=host.split('.').map(Number);
    if(!(a===10||(a===172&&b>=16&&b<=31)||(a===192&&b===168)))return;
    if(lastAddress!==address){lastAddress=address;checked=0;retryAt=0;}
    if(now()<retryAt)return;
    const [start,end]=range;
    const count=end-start+1;
    const previous=Number(lastPort);
    const nearby=previous>=start&&previous<=end;
    const total=nearby?count-1:count;
    const pairing=now()<pairingUntil;
    const limit=Math.min(checked+2048,total);
    let verificationCount=0;
    while(checked<limit&&!signal?.aborted&&verificationCount<8){
      const batch=[];
      for(let n=0;n<64&&checked<limit;n++,checked++){
        const distance=Math.floor(checked/2)+1;
        const offset=checked%2===0?distance:-distance;
        const port=nearby?start+(previous-start+offset+count)%count:start+checked;
        const candidate=host+':'+port;
        if(!skip.has(candidate))batch.push(candidate);
      }
      const open=await Promise.all(batch.map(candidate=>probe(candidate,160)));
      for(const [index,candidate] of batch.entries()){
        if(signal?.aborted)return;
        if(!open[index]||verificationCount>=8)continue;
        verificationCount++;
        if(await verify(candidate))return candidate;
      }
    }
    if(checked===total){checked=0;retryAt=now()+(pairing?1000:5000);}
  };
  discover.useDeviceRange=value=>{
    const [start,end,...rest]=value.trim().split(/\s+/).map(Number);
    if(!rest.length&&Number.isInteger(start)&&Number.isInteger(end)&&start>=1024&&end<=65535&&end>=start&&end-start<=32767){
      range=[start,end];
    }
  };
  discover.resume=()=>{checked=0;retryAt=0;pairingUntil=now()+PAIRING_RECOVERY_MS;};
  return discover;
}
export async function queueWirelessPort({file,port,run,probe=canReachPort}){
  if(!Number.isInteger(port)||port<1||port>65535)throw new Rejected('포트는 1~65535 사이 숫자로 입력하세요',400);
  const state=JSON.parse(await readFile(file,'utf8'));
  if(!validIdentity(state.identity)||!endpoint(state.endpoint)||!endpoint(state.configuredSerial))throw new Rejected('먼저 운영 PC에서 기기를 등록해 주세요',503);
  try{await readFile(file+'.request');throw new Rejected('포트 변경을 처리 중입니다',409);}catch(e){if(e.code!=='ENOENT')throw e;}
  const address=state.endpoint.split(':')[0]+':'+port;
  let identity;
  try{
    await run(['connect',address]);
    identity=(await run(['-s',address,'shell','getprop','ro.serialno'])).trim();
  }catch{}
  if(validIdentity(identity)&&identity!==state.identity){
    throw new Rejected('해당 포트의 기기 ID가 등록된 기기와 다릅니다. 대상 기기를 확인하세요.',400);
  }
  if(identity!==state.identity){
    if(await probe(address)){
      throw new Rejected('포트는 열려 있지만 ADB 연결을 확인하지 못했습니다. 무선 디버깅의 페어링된 기기에서 운영 PC를 확인하세요. 필요하면 운영 PC를 다시 페어링한 뒤 무선 디버깅 기본 화면의 포트를 입력하세요.',400);
    }
    throw new Rejected(`등록된 주소(${address})의 포트에 접근할 수 없습니다. 기기와 운영 PC가 같은 Wi-Fi에 있는지, 무선 디버깅 기본 화면의 IP 주소와 포트가 맞는지 확인하세요.`,400);
  }
  await save(file+'.request',{configuredSerial:state.configuredSerial,identity:state.identity,endpoint:address,status:'connected'});
  return {port,restarting:true};
}
export async function wirelessPortState(file){
  try{const s=JSON.parse(await readFile(file,'utf8'));return {available:validIdentity(s.identity)&&endpoint(s.endpoint),port:endpoint(s.endpoint)?Number(s.endpoint.split(':')[1]):null};}
  catch{return {available:false,port:null};}
}
export async function watchWireless({configuredSerial,file,run,signal,interval=5000,discover,usbPortHint=false,wirelessPortHint}){
  let state=await readWirelessState(file,configuredSerial);
  let rangeReadFor;
  let pairingDeadline=0;
  let offlineSince=null;
  while(!signal.aborted){
    if(discover){
      try{
        await unlink(file+'.paired');
        discover.resume();
        pairingDeadline=Date.now()+PAIRING_RECOVERY_MS;
        console.log('Wireless pairing succeeded; checking the registered phone.');
      }catch(error){if(error.code!=='ENOENT')throw error;}
    }
    const requested=await readWirelessState(file+'.request',configuredSerial);
    if(requested){
      if(state?.identity&&requested.identity===state.identity&&endpoint(requested.endpoint)){
        const verified=await recoverWireless({configuredSerial,state:requested,run});
        if(verified.status==='connected'&&verified.endpoint===requested.endpoint){await save(file,verified);state=verified;}
      }
      await rm(file+'.request',{force:true});
    }
    const deferScan=offlineSince===null||Date.now()-offlineSince<8000;
    const next=await recoverWireless({configuredSerial,state,run,discover,usbPortHint,wirelessPortHint,deferScan});
    offlineSince=next.status==='connected'?null:(offlineSince??Date.now());
    if(signal.aborted)return;
    if(discover&&next.status==='connected'&&rangeReadFor!==next.endpoint){
      rangeReadFor=next.endpoint;
      try{discover.useDeviceRange(await run(['-s',next.endpoint,'shell','cat','/proc/sys/net/ipv4/ip_local_port_range']));}catch{}
    }
    if(JSON.stringify(next)!==JSON.stringify(state)){await save(file,next);state=next;}
    if(pairingDeadline&&next.status==='connected'){
      pairingDeadline=0;
      console.log('Registered phone confirmed after pairing.');
    }
    const retryDelay=Date.now()<pairingDeadline?Math.min(interval,PAIRING_RETRY_MS):interval;
    try{await delay(retryDelay,undefined,{signal});}catch(e){if(e.name!=='AbortError')throw e;}
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const [adb,port,configuredSerial,file]=process.argv.slice(2);
  if(!adb||!/^\d{1,5}$/.test(port)||Number(port)<1||Number(port)>65535||!file||!endpoint(configuredSerial))throw Error('Invalid wireless watcher arguments');
  const run=async args=>(await execute(adb,['-P',port,...args],{windowsHide:true,timeout:args[4]===READ_TLS_PORT?4500:2500,maxBuffer:256*1024})).stdout;
  const controller=new AbortController(),parent=process.ppid;
  const monitor=setInterval(()=>{try{process.kill(parent,0);}catch(e){if(e.code==='ESRCH')controller.abort();}},5000).unref();
  process.once('SIGTERM',()=>controller.abort());
  const discover=createPortDiscovery({signal:controller.signal});
  const usbPortHint=true;
  let wirelessPortHint;
  if(usbPortHint){
    try{wirelessPortHint=createWirelessPortHint((await readFile(file+'.hint-key','utf8')).trim());}
    catch(error){if(error.code!=='ENOENT')console.error('Wireless port hint key unavailable; using existing recovery.');}
  }
  try{await watchWireless({configuredSerial,file,run,signal:controller.signal,discover,usbPortHint,wirelessPortHint,interval:usbPortHint?1000:5000});}finally{clearInterval(monitor);}
}
