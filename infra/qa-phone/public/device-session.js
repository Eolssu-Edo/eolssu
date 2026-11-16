import {VideoView} from './video-view.js';

export const deviceColors=['#365ea5','#7653a0','#ad6a29','#247a70','#a34f70','#637a30'];
export function registeredDevices(value, currentOrigin=location.origin){
  if(!Array.isArray(value)||value.length<1||value.length>6)throw Error('등록 기기는 1~6대여야 합니다.');
  const ids=new Set(),paths=new Set();
  return value.map((entry,index)=>{
    const url=new URL(entry.basePath,currentOrigin);
    if(!/^[a-z][a-z0-9-]{0,31}$/.test(entry.id)||typeof entry.name!=='string'||!entry.name.trim()||entry.name.length>60||/[\p{C}]/u.test(entry.name)||
       url.origin!==currentOrigin||url.search||url.hash||url.username||url.password||!/^\/(?:[A-Za-z0-9_-]+\/)+$/.test(entry.basePath)||ids.has(entry.id)||paths.has(url.pathname))throw Error('등록된 기기 경로를 확인하세요.');
    ids.add(entry.id);paths.add(url.pathname);
    return {...entry,basePath:url.pathname,number:String(index+1).padStart(2,'0'),color:deviceColors[index]};
  });
}

// Each relay retains its own HttpOnly cookie, lease/epoch, input sequence and video.
export class DeviceSession{
  constructor(device,{canvas,onChange=()=>{},onExpired=()=>{},onMessage=()=>{}}){
    Object.assign(this,device);this.canvas=canvas;this.onChange=onChange;this.onExpired=onExpired;this.onMessage=onMessage;
    this.state=null;this.stateAt=0;this.seq=0;this.offset=0;this.inputTail=Promise.resolve();this.inputPending=0;
    this.running=false;this.authorized=false;this.sessionName=null;this.instance=null;this.hasFrame=false;this.catalog=null;this.buildChecked=0;this.generation=0;
    this.video=new VideoView(canvas,{url:this.basePath+'api/video',onStatus:text=>{this.cadence=text;this.onChange(this);},onFrame:(width,height)=>{if(!this.running)return;this.hasFrame=true;this.frameWidth=width;this.frameHeight=height;this.onChange(this);}});
  }
  get connected(){return this.authorized&&!!this.state?.online&&Date.now()-this.stateAt<3000;}
  get owned(){return this.connected&&!this.state.maintenance&&!this.state.inputFault&&this.state.owner===this.state.you;}
  get inputReady(){return this.running&&this.owned&&this.hasFrame;}
  async request(path,data){
    const response=await fetch(this.basePath+'api/'+path,{method:data===undefined?'GET':'POST',credentials:'same-origin',redirect:'error',signal:AbortSignal.timeout(45000),headers:data===undefined?{}:{'Content-Type':'application/json'},...(data===undefined?{}:{body:JSON.stringify(data)})});
    let result;try{result=await response.json();}catch{throw Error('기기 중계에 다시 연결하고 있습니다.');}
    if(!response.ok){const error=Error(result.error||'기기 요청을 완료하지 못했습니다.');error.status=response.status;error.instance=response.headers.get('X-QA-Instance');throw error;}
    return {value:result,instance:response.headers.get('X-QA-Instance')};
  }
  async api(path,data){return (await this.request(path,data)).value;}
  invalidateLive(){this.liveRequestVersion=(this.liveRequestVersion||0)+1;}
  async liveStatus(path='live/status'){
    const version=this.liveRequestVersion=(this.liveRequestVersion||0)+1;
    try{const value=await this.api(path);if(version===this.liveRequestVersion)this.liveSnapshot=value;return value;}
    catch(error){if(version===this.liveRequestVersion)this.liveSnapshot=null;throw error;}
  }
  async readState({rejoin=true}={}){
    const generation=this.generation;let response;
    try{response=await this.request('state');}
    catch(error){
      if(generation!==this.generation)throw Error('기기 연결이 변경되었습니다.');
      if(error.status!==401)throw error;
      const restarted=this.instance&&error.instance&&this.instance!==error.instance;
      if(!rejoin||!this.sessionName||this.authorized&&!restarted){this.authorized=false;if(this.running)this.onExpired(this);throw error;}
      await this.api('login',{name:this.sessionName});response=await this.request('state');
    }
    if(generation!==this.generation)throw Error('기기 연결이 변경되었습니다.');
    const next=response.value;
    if(this.state?.epoch!==next.epoch)this.seq=0;
    this.seq=Math.max(this.seq,next.lastSeq||0);this.state=next;this.stateAt=Date.now();this.offset=next.now-Date.now();this.instance=response.instance;this.authorized=true;
    this.sessionName=next.peers.find(peer=>peer.id===next.you)?.name||this.sessionName;
    return next;
  }
  async join(name){
    this.sessionName=name;
    try{await this.readState({rejoin:false});if(this.sessionName!==name)await this.api('logout',{});else return;}
    catch(error){if(error.status!==401)throw error;}
    this.sessionName=name;await this.api('login',{name});await this.readState({rejoin:false});
  }
  start(name){if(name)this.sessionName=name;if(this.running)return;this.running=true;void this.poll();}
  stop(){this.running=false;this.generation++;clearTimeout(this.timer);this.video.stop();this.hasFrame=false;this.gesture=null;}
  async logout(){this.stop();this.sessionName=null;try{if(this.authorized)await this.api('logout',{});}finally{this.authorized=false;this.state=null;this.catalog=null;this.clearFrame();}}
  clearFrame(){this.hasFrame=false;this.canvas.hidden=true;this.canvas.getContext('2d').clearRect(0,0,this.canvas.width,this.canvas.height);}
  async refreshBuilds(){
    if(this.buildChecking)return;this.buildChecking=true;this.buildChecked=Date.now();
    try{this.catalog=await this.api('builds');}catch{this.catalog=null;}finally{this.buildChecking=false;this.onChange(this);}
  }
  async poll(){
    if(!this.running)return;
    try{
      const state=await this.readState();if(!this.running)return;
      this.error='';
      this.video.start();
      if(state.online){
        if(!this.video.active){
          const response=await fetch(this.basePath+'api/frame',{credentials:'same-origin',signal:AbortSignal.timeout(7000)});
          if(!response.ok)throw Error('화면을 다시 연결하고 있습니다.');
          const frame=await createImageBitmap(await response.blob());
          try{if(this.running&&!this.video.active){this.canvas.width=frame.width;this.canvas.height=frame.height;this.canvas.getContext('2d').drawImage(frame,0,0);this.hasFrame=true;this.frameWidth=frame.width;this.frameHeight=frame.height;}}finally{frame.close();}
        }
      }else{this.clearFrame();}
      if(Date.now()-this.buildChecked>5000)void this.refreshBuilds();
    }catch(error){this.error=error.message;this.stateAt=0;this.clearFrame();if(!this.authorized)this.video.stop();}
    finally{this.onChange(this);if(this.running)this.timer=setTimeout(()=>void this.poll(),900);}
  }
  async act(path,data={}){const result=await this.api(path,data);await this.readState();this.onChange(this);return result;}
  async input(action,start=this.state){
    if(!this.inputReady||!start||start.epoch!==this.state.epoch)return;
    if(this.inputPending>=8){this.onMessage('입력 처리 중입니다.');return;}
    const queuedAt=Date.now(),packet={epoch:start.epoch,seq:++this.seq,sentAt:queuedAt+this.offset,width:start.width,height:start.height,action};
    this.inputPending++;
    this.inputTail=this.inputTail.then(async()=>{
      if(!this.inputReady||this.state.epoch!==packet.epoch||Date.now()-queuedAt>3000)return;
      await this.act('input',packet);
    }).catch(error=>this.onMessage(error.message)).finally(()=>this.inputPending--);
    return this.inputTail;
  }
}
