import net from 'node:net';
import {spawn} from 'node:child_process';
import {randomInt,createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {setTimeout as delay} from 'node:timers/promises';
import {VideoPackets} from './public/video-wire.js';
const manifest=JSON.parse(await readFile(new URL('./scrcpy-manifest.json',import.meta.url),'utf8'));
const codec=Buffer.from('68323634','hex');
// One encoder and bounded latest GOP shared by every authenticated viewer.
export class PhoneVideo {
  constructor(phone,file){this.phone=phone;this.file=file;this.clients=new Set();this.cleanups=new Set();this.lastFrameAt=0;this.lastTransportAt=0;this.starting=null;this.active=null;this.idle=null;this.gop=[];this.gopBytes=0;this.session=null;this.config=null;}
  get healthy(){return !!this.active?.socket&&!this.active.socket.destroyed&&this.lastFrameAt>0&&Date.now()-this.lastTransportAt<6000;}
  subscribe(res,{onSession=()=>{}}={}){
    clearTimeout(this.idle);this.onSession=onSession;
    if(this.clients.size>=16)throw Error('video viewer limit');
    this.clients.add(res);
    res.writeHead(200,{'Content-Type':'application/octet-stream','X-Accel-Buffering':'no'});
    res.write(codec);res.qaVideoReady=false;
    // Replay a complete decoding boundary, never a partial/delta-only history.
    if(this.session&&this.config&&this.gop.length){res.write(this.session.raw);res.write(this.config);for(const packet of this.gop)res.write(packet);res.qaVideoReady=true;}
    res.on('close',()=>{this.clients.delete(res);if(!this.clients.size)this.idle=setTimeout(()=>void this.stop(),3000).unref();});
    if(!this.beat)this.beat=setInterval(()=>void this.heartbeat(),2000).unref();
    if(!this.starting&&!this.active)this.starting=this.start().catch(async()=>{for(const client of this.clients)client.destroy();if(this.active)await this.cleanup(this.active);}).finally(()=>{this.starting=null;});
  }
  async heartbeat(){
    const state=this.active;if(!state||state.closing||state.checking)return;
    state.checking=true;
    try{
      if((await this.phone.run(['get-state'])).trim()!=='device')throw Error('device offline');
      if(state!==this.active)return;this.lastTransportAt=Date.now();
      // Empty media packet is a relay heartbeat; it is never sent to the decoder.
      for(const client of this.clients){if(client.writableLength>256*1024)client.destroy();else client.write(Buffer.alloc(12));}
    }catch{await this.fail(state);}finally{state.checking=false;}
  }
  broadcast(bytes){for(const client of this.clients){if(client.writableLength>256*1024)client.destroy();else if(client.qaVideoReady)client.write(bytes);}}
  async start(){
    if(createHash('sha256').update(await readFile(this.file)).digest('hex')!==manifest.sha256)throw Error('scrcpy checksum mismatch');
    const sid=randomInt(0x7fffffff).toString(16).padStart(8,'0');
    const state={remote:`/data/local/tmp/qa290-${sid}.jar`,port:null,child:null,socket:null,closing:false};this.active=state;
    this.session=null;this.config=null;this.gop=[];this.gopBytes=0;this.lastFrameAt=0;
    try{
      await this.phone.run(['push',this.file,state.remote],false,10000);if(state.closing)return;
      state.port=Number((await this.phone.run(['forward','tcp:0',`localabstract:scrcpy_${sid}`])).trim());
      if(!Number.isInteger(state.port)||state.port<1||state.port>65535)throw Error('invalid video forward');
      const argv=['app_process','/','com.genymobile.scrcpy.Server',manifest.version,`scid=${sid}`,'audio=false','control=false','tunnel_forward=true','max_size=1520','max_fps=30','video_bit_rate=2000000','video_codec_options=i-frame-interval=1'];
      // Samsung Android 12 native encoder overflows long app_process argv (#2841).
      if(Buffer.byteLength(argv.join(' '))>=255)throw Error('Samsung encoder argument limit');
      if(state.closing)return;
      state.child=spawn(this.phone.adb,['-P',String(this.phone.port),'-s',this.phone.serial,'shell',`CLASSPATH=${state.remote}`,...argv],{windowsHide:true,stdio:['ignore','pipe','pipe']});
      state.child.stdout.resume();state.child.stderr.resume();
      state.child.on('error',()=>void this.fail(state));state.child.on('exit',()=>void this.fail(state));
      const parser=new VideoPackets({deviceHeader:true,onSession:s=>{
        this.session=s;this.config=null;this.gop=[];this.gopBytes=0;
        this.onSession(s);for(const client of this.clients)client.qaVideoReady=false;
      },onPacket:p=>{
        if(p.config){this.config=p.raw;this.gop=[];this.gopBytes=0;}
        else{
          this.lastFrameAt=Date.now();this.lastTransportAt=Date.now();
          if(p.key){this.gop=[];this.gopBytes=0;}
          if(p.key||this.gop.length){this.gop.push(p.raw);this.gopBytes+=p.raw.length;}
          if(this.gopBytes>4*1024*1024||this.gop.length>180){this.gop=[];this.gopBytes=0;}
        }
        if(p.key&&this.session&&this.config)for(const client of this.clients)if(!client.qaVideoReady){client.write(this.session.raw);client.write(this.config);client.qaVideoReady=true;}
        this.broadcast(p.raw);
      }});
      let header=false;
      const deadline=Date.now()+6000;
      while(!state.closing&&Date.now()<deadline){
        const received=await new Promise(resolve=>{
          let done=false;const finish=value=>{if(!done){done=true;resolve(value);}};
          state.socket=net.createConnection({host:'127.0.0.1',port:state.port});
          state.socket.setTimeout(10000,()=>state.socket.destroy());
          state.socket.on('error',()=>finish(false));
          state.socket.on('close',()=>{finish(false);if(header)void this.fail(state);});
          state.socket.on('data',chunk=>{
            try{parser.push(chunk);if(parser.codec){header=true;state.socket.setTimeout(0);finish(true);}}catch{state.socket.destroy();}
          });
        });
        if(received)return;
        if(state.child.exitCode!==null)throw Error('video encoder exited');
        await delay(100);
      }
      throw Error('video start timeout');
    }catch(error){await this.cleanup(state);throw error;}
  }
  async fail(state){if(state!==this.active||state.closing)return;for(const client of this.clients)client.destroy();await this.cleanup(state);}
  async cleanup(state){
    if(state.cleanup)return state.cleanup;
    state.closing=true;
    if(this.active===state){this.active=null;this.lastFrameAt=0;this.session=null;this.config=null;this.gop=[];this.gopBytes=0;}
    state.cleanup=(async()=>{
      state.socket?.destroy();state.child?.kill();
      if(state.port)await this.phone.run(['forward','--remove',`tcp:${state.port}`]).catch(()=>{});
      await this.phone.run(['shell','rm','-f',state.remote]).catch(()=>{});
      if(this.active===state){this.active=null;this.lastFrameAt=0;this.session=null;this.config=null;this.gop=[];this.gopBytes=0;}
    })();this.cleanups.add(state.cleanup);void state.cleanup.finally(()=>this.cleanups.delete(state.cleanup));return state.cleanup;
  }
  async stop(){clearTimeout(this.idle);clearInterval(this.beat);this.beat=null;for(const client of this.clients)client.destroy();if(this.active){const state=this.active;state.closing=true;if(this.starting)await this.starting;await this.cleanup(state);}await Promise.allSettled([...this.cleanups]);}
}
