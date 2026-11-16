import {VideoPackets,avcCodec} from './video-wire.js';
export class VideoView {
  constructor(canvas,{onFrame=()=>{},onStatus=()=>{},onAuth=()=>{},url='./api/video'}={}){
    this.canvas=canvas;this.context=canvas.getContext('2d',{alpha:false});this.onFrame=onFrame;this.onStatus=onStatus;this.onAuth=onAuth;this.url=url;this.running=false;this.active=false;this.total=0;this.generation=0;
  }
  start(){if(this.running)return;this.running=true;const generation=++this.generation;this.watch=setInterval(()=>{if(document.visibilityState==='visible'&&this.active&&(Date.now()-(this.lastRead||this.connectedAt)>6000||(!this.lastPaint&&Date.now()-this.connectedAt>6000)))this.abort?.abort();if(this.active&&this.lastPaint&&Date.now()-this.lastPaint>2000)this.onStatus('실시간 영상 · 화면 변화 대기');},1000);void this.connect(generation);}
  async connect(generation){
    if(!this.running||generation!==this.generation)return;
    if(!globalThis.VideoDecoder||!globalThis.isSecureContext){this.onStatus('이미지 모드 · 응답 후 0.9초 간격');return;}
    const controller=new AbortController();this.abort=controller;this.active=true;this.connectedAt=Date.now();this.lastPaint=0;this.onStatus('실시간 영상 연결 중');
    this.lastRead=0;let config=null,needKey=true,failed=false,reader=null,stage='request';
    const fail=error=>{failed=true;if(generation===this.generation)this.decodeFailure=error?.name||'decoder';controller.abort();};
    const reset=()=>{if(this.decoder&&this.decoder.state!=='closed')this.decoder.close();this.decoder=null;needKey=true;};
    const parser=new VideoPackets({onSession:()=>{reset();config=null;},onPacket:p=>{
      if(!p.payload.length)return;
      if(p.config){
        reset();config=p.payload;
        this.decoder=new VideoDecoder({error:fail,output:frame=>{if(!this.running||generation!==this.generation){frame.close();return;}this.latest?.close();this.latest=frame;if(!this.raf)this.raf=requestAnimationFrame(()=>this.paint());}});
        stage='configure';
        this.decoder.configure({codec:avcCodec(config),optimizeForLatency:true});return;
      }
      if(!this.decoder||this.decoder.state!=='configured')return;
      if(this.decoder.decodeQueueSize>45){this.decoder.reset();this.decoder.configure({codec:avcCodec(config),optimizeForLatency:true});needKey=true;}
      if(needKey&&!p.key)return;
      let data=p.payload;
      if(p.key){data=new Uint8Array(config.length+p.payload.length);data.set(config);data.set(p.payload,config.length);needKey=false;}
      stage='decode';this.decoder.decode(new EncodedVideoChunk({type:p.key?'key':'delta',timestamp:p.timestamp,data}));
    }});
    try{
      const response=await fetch(this.url,{signal:controller.signal});
      if(!this.running||generation!==this.generation)return;
      if(response.status===401){this.onAuth();throw Error('video authentication changed');}
      if(!response.ok||!response.body)throw Error('video unavailable');
      reader=response.body.getReader();
      while(this.running&&generation===this.generation){stage='read';const {value,done}=await reader.read();if(done||!this.running||generation!==this.generation)break;this.lastRead=Date.now();stage='packet';parser.push(value);}
      if(this.running)throw Error('video ended');
    }catch(error){
      if(!this.running||generation!==this.generation)return;
      const known=/^(video unavailable|video ended|video buffer limit|unsupported video codec|invalid video geometry|video packet limit|missing AVC configuration|video authentication changed)$/;
      this.lastFailure=stage+':'+(this.decodeFailure||(known.test(error?.message)?error.message:error?.name||'transport'));this.decodeFailure=null;
      console.warn('[QA video retry]',this.lastFailure);
      this.failures=(this.failures||0)+1;this.active=false;
      this.onStatus(this.failures<4?'영상 재연결 중 · 이미지로 표시':'이미지 모드 · 응답 후 0.9초 간격');
      this.retry=setTimeout(()=>void this.connect(generation),this.failures<4?1000:15000);
    }finally{
      controller.abort();if(reader)void reader.cancel().catch(()=>{});
      if(generation===this.generation){reset();this.latest?.close();this.latest=null;if(failed)this.active=false;}
    }
  }
  paint(){
    this.raf=null;const frame=this.latest;this.latest=null;if(!frame)return;
    try{
      if(!this.running)return;
      this.lastPaint=Date.now();if(this.canvas.width!==frame.displayWidth)this.canvas.width=frame.displayWidth;if(this.canvas.height!==frame.displayHeight)this.canvas.height=frame.displayHeight;this.context.drawImage(frame,0,0);this.total++;
      this.failures=0;this.canvas.dataset.videoFrames=String(this.total);this.canvas.dataset.videoAt=String(Date.now());this.onFrame(frame.displayWidth,frame.displayHeight);
      const now=performance.now();if(!this.fpsAt){this.fpsAt=now;this.fpsFrames=this.total;}
      if(now-this.fpsAt>=1000){const fps=(this.total-this.fpsFrames)*1000/(now-this.fpsAt);this.onStatus(`실시간 영상 · ${fps.toFixed(1)} FPS`);this.fpsAt=now;this.fpsFrames=this.total;}
    }finally{frame.close();}
  }
  stop(){this.running=false;this.active=false;this.generation++;this.failures=0;clearTimeout(this.retry);clearInterval(this.watch);this.abort?.abort();if(this.decoder&&this.decoder.state!=='closed')this.decoder.close();this.decoder=null;this.latest?.close();this.latest=null;cancelAnimationFrame(this.raf);this.raf=null;this.fpsAt=0;}
}
