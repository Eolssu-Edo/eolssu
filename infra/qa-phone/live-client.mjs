import {readFile} from 'node:fs/promises';
import {Rejected} from './control.mjs';
import {liveOperations,liveEvidence,deviceEvidence,safeLogRecords} from './live-events.mjs';

function withoutLogs(value){
  if(Array.isArray(value))return value.map(withoutLogs);
  if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).filter(([key])=>!['logs','deviceIdentity'].includes(key)).map(([key,item])=>[key,withoutLogs(item)]));
  return value;
}

const actions=new Set(['start','launch','stop','fixture','email','password','scenario','run','cancel']);
const scenarios=new Set(['feedback','retry','duplicate','ownership']);
const sizes=new Set([6,150,300,500,750,1000]);

export class LiveClient {
  constructor({origin,token,identity}) {
    const url=new URL(origin);
    if(url.protocol!=='http:'||url.hostname!=='127.0.0.1'||url.username||url.password||url.pathname!=='/'||url.search||url.hash||!url.port)throw Error('Live control must use a fixed loopback origin');
    if(!/^[0-9a-f]{64}$/.test(token)||!/^[A-Za-z0-9_-]{4,80}$/.test(identity))throw Error('Invalid live control configuration');
    this.origin=url.origin;this.token=token;this.identity=identity;
  }
  async request(path,data) {
    let response;
    try {
      response=await fetch(this.origin+path,{method:data===undefined?'GET':'POST',redirect:'error',signal:AbortSignal.timeout(['email','password','stop','launch'].includes(data?.action)?30000:8000),
        headers:{Origin:this.origin,'X-QA-Token':this.token,'X-QA-Device':this.identity,...(data===undefined?{}:{'Content-Type':'application/json'})},
        ...(data===undefined?{}:{body:JSON.stringify(data)})});
    }catch{throw new Rejected('격리 관측 서비스를 연결하지 못했습니다.',503);}
    if(!response.ok){
      const errors={400:'QA 동작의 입력을 확인하세요.',403:'이 실행을 시작한 QA 참여자만 중단할 수 있습니다.',409:'같은 격리 환경에서 다른 실행이 진행 중입니다.'};
      throw new Rejected(errors[response.status]||'격리 QA 요청을 완료하지 못했습니다.',errors[response.status]?response.status:503);
    }
    return response.json();
  }
  async status({logsFor}={}) {
    const raw=await this.request('/status');
    const device=Object.fromEntries(['name','os','appVersion','connected'].map(key=>[key,raw.device?.[key]??null]));
    const result={available:true,busy:!!raw.mode,mode:raw.mode,phase:raw.state,selected:raw.observingSelected!==false,api:'http://127.0.0.1:18180',device,lastEventAt:raw.lastEventAt,activeDevice:raw.activeDevice,message:raw.message,
      operations:liveOperations(raw.events||[]),scenario:withoutLogs(raw.scenario),load:withoutLogs(raw.load),history:withoutLogs(raw.history||[]),inputMode:raw.inputMode};
    if(typeof logsFor==='string'&&logsFor.length<=120){
      let records;
      if(logsFor==='device/all'){result.evidence={id:logsFor,...deviceEvidence(raw.events||[])};return result;}
      if(logsFor==='server/scenario')records=raw.scenario?.logs;
      else if(logsFor==='server/load')records=raw.load?.logs;
      else if(logsFor.startsWith('history/')){
        const run=(raw.history||[]).find(value=>value.id===logsFor.slice(8));
        records=run?.result?.logs||run?.result?.result?.logs;
      }else {result.evidence={id:logsFor,...liveEvidence(raw.events||[],logsFor)};return result;}
      result.evidence={id:logsFor,...safeLogRecords(records)};
    }
    return result;
  }
  async command(data,identity,sessionId) {
    if(identity!==this.identity)throw new Rejected('선택한 기기가 격리 QA 대상과 다릅니다.',409);
    if(!actions.has(data.action))throw new Rejected('허용되지 않은 QA 동작입니다.',400);
    const body={action:data.action,sessionId};
    if(data.action==='scenario'){
      if(!scenarios.has(data.scenario))throw new Rejected('검사 종류를 확인하세요.',400);
      body.scenario=data.scenario;
    }
    if(data.action==='run'){
      if(!sizes.has(data.size))throw new Rejected('동시 제출 수를 확인하세요.',400);
      body.size=data.size;
    }
    if(data.action==='fixture'){
      if(!['microphone','synthetic'].includes(data.input))throw new Rejected('녹음 입력을 확인하세요.',400);
      body.input=data.input;
    }
    return this.request('/control',body);
  }
}

export async function loadLiveClient(file) {
  if(!file)return null;
  const current=async()=>{
    try{return new LiveClient(JSON.parse(await readFile(file,'utf8')));}
    catch{throw new Rejected('격리 관측 서비스 설정을 확인하세요.',503);}
  };
  // The isolated service may restart while the shared QA relay keeps running.
  // Read its operator-owned token again; no browser reload or relay restart is needed.
  return {status:async(...args)=>(await current()).status(...args),command:async(...args)=>(await current()).command(...args)};
}
