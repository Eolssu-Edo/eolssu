import {spawn} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {randomUUID,createHash} from 'node:crypto';
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');

// One phone-side bright wake lock, renewed only while authenticated QA viewers exist.
export class ScreenKeeper {
  constructor(phone,file,{launch=spawn}={}) {
    this.phone=phone;this.file=file;this.launch=launch;this.present=false;this.current=null;
    this.task=null;this.timer=null;this.closed=false;this.error='';this.retryAt=0;this.cleanups=new Set();
  }
  state(){return {requested:this.present,active:!!this.current?.ready,error:this.error};}
  setPresent(value){
    if(this.closed)return Promise.resolve();
    this.present=!!value;
    if(!this.present){clearInterval(this.timer);this.timer=null;}
    else if(!this.timer)this.timer=setInterval(()=>this.refresh(),5000).unref();
    if(this.task)return this.task;
    this.task=(async()=>{
      try{
        if(this.present&&!this.current&&Date.now()>=this.retryAt)await this.start();
        if(!this.present&&this.current)await this.cleanup(this.current);
      }catch{
        this.error='화면 밝기 유지 연결을 확인하세요';this.retryAt=Date.now()+10000;
        if(this.current)await this.cleanup(this.current);
      }
    })().finally(()=>{this.task=null;});
    return this.task;
  }
  refresh(){
    if(!this.present||this.closed)return;
    const state=this.current;
    if(state?.ready){
      if(state.child.stdin.writable)state.child.stdin.write('ping\n');
      else this.lost(state);
    }else if(!this.task)void this.setPresent(true);
  }
  lost(state){
    if(state.stopping)return;
    state.ready=false;
    if(this.present&&!this.closed){this.error='화면 밝기 유지 연결을 확인하세요';this.retryAt=Date.now()+10000;}
    void this.cleanup(state);
  }
  async start(){
    const metadata=JSON.parse(await readFile(join(dirname(this.file),'bright-screen.json'),'utf8'));
    const binary=await readFile(this.file),source=await readFile(new URL('./android/BrightScreen.java',import.meta.url));
    if(metadata.leaseMs!==15000||metadata.sha256!==sha(binary)||metadata.sourceSha256!==sha(source))throw Error('screen helper integrity mismatch');
    if(!this.present||this.closed)return;
    const state={remote:`/data/local/tmp/qa290-bright-${randomUUID().replaceAll('-','')}.jar`,child:null,ready:false,exited:false,stopping:false};
    this.current=state;
    await this.phone.run(['push',this.file,state.remote],false,10000);
    if(!this.present||this.closed){await this.cleanup(state);return;}
    state.child=this.launch(this.phone.adb,[...await this.phone.deviceArgs(),'shell',`CLASSPATH=${state.remote}`,'app_process','/','com.ssafy.qa.BrightScreen'],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    const ready=new Promise((resolve,reject)=>{
      let output='';const timeout=setTimeout(()=>reject(Error('screen helper ready timeout')),5000);
      state.child.stdout.on('data',chunk=>{output=(output+chunk.toString()).slice(-4096);if(/(?:^|\r?\n)READY\r?\n/.test(output)){clearTimeout(timeout);state.ready=true;resolve();}});
      state.child.stderr.resume();
      state.child.once('error',()=>{clearTimeout(timeout);reject(Error('screen helper launch failed'));this.lost(state);});
      state.child.once('exit',()=>{state.exited=true;clearTimeout(timeout);reject(Error('screen helper exited'));this.lost(state);});
      state.child.stdin.on('error',()=>this.lost(state));
    });
    await ready;this.error='';state.child.stdin.write('ping\n');
  }
  cleanup(state){
    if(state.cleanup)return state.cleanup;
    state.stopping=true;state.ready=false;if(this.current===state)this.current=null;
    state.cleanup=(async()=>{
      const child=state.child;
      if(child&&!state.exited){
        await new Promise(resolve=>{
          const timeout=setTimeout(()=>{child.kill();resolve();},2000);
          child.once('exit',()=>{clearTimeout(timeout);resolve();});
          if(child.stdin.writable)child.stdin.end('stop\n');else child.kill();
        });
      }
      await this.phone.run(['shell','rm','-f',state.remote]).catch(()=>{});
    })();
    this.cleanups.add(state.cleanup);void state.cleanup.finally(()=>this.cleanups.delete(state.cleanup));return state.cleanup;
  }
  async close(){
    this.present=false;this.closed=true;clearInterval(this.timer);this.timer=null;
    if(this.task)await this.task;
    if(this.current)await this.cleanup(this.current);
    await Promise.allSettled([...this.cleanups]);
  }
}
