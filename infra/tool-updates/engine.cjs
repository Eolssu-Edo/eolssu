'use strict';
const {EventEmitter} = require('node:events');
const crypto = require('node:crypto');
const VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$/;
const defaults = {projectPath:'',autoApply:false,startAtLogin:true,cocoRuntime:'both',cocoScope:'project',setupComplete:false};
const copy = value => structuredClone(value);
const order = (a,b) => a===b?0:a>b?1:-1;

function compareVersions(a,b) {
  const left=VERSION.exec(a),right=VERSION.exec(b);
  for(let i=1;i<=3;i++){
    const diff=order(BigInt(left[i]),BigInt(right[i]));
    if(diff)return diff;
  }
  if(!left[4]||!right[4])return order(!left[4],!right[4]);
  const x=left[4].split('.'),y=right[4].split('.');
  for(let i=0;i<Math.min(x.length,y.length);i++){
    if(x[i]===y[i])continue;
    const nx=/^\d+$/.test(x[i]),ny=/^\d+$/.test(y[i]);
    if(nx&&ny){const diff=order(BigInt(x[i]),BigInt(y[i]));if(diff)return diff;}
    else return nx!==ny?order(ny,nx):order(x[i],y[i]);
  }
  return order(x.length,y.length);
}

class UpdateEngine extends EventEmitter {
  constructor(deps) {
    super(); this.deps=deps;
    const saved=deps.load()||{};
    const config={...defaults,...saved.config};
    delete config.sshHost;
    // Graphify-only consent does not authorize automatic COCO installation.
    delete config.autoDownload;
    this.data={format:1,config,graph:saved.graph||{},coco:saved.coco||{}};
    this.view={graph:{phase:'setup'},coco:{phase:saved.coco?.versions?.length?'ready':'waiting',versions:saved.coco?.versions||[],latest:saved.coco?.versions?.[0],installed:saved.coco?.installed},busyGraph:false,busyCoco:false};
  }
  snapshot() { return copy({config:this.data.config,...this.view}); }
  persist() { this.deps.save(copy(this.data)); this.emit('change',this.snapshot()); }
  configure(input) {
    if(this.view.busyGraph||this.view.busyCoco) throw new Error('진행 중인 작업이 끝난 뒤 설정을 바꿔주세요.');
    const c={...this.data.config};
    for(const key of ['projectPath','cocoRuntime','cocoScope']) if(input[key]!==undefined) c[key]=String(input[key]).trim();
    for(const key of ['autoApply','startAtLogin']) if(input[key]!==undefined) {if(typeof input[key]!=='boolean') throw new Error('잘못된 설정입니다.');c[key]=input[key];}
    if(!['codex','claude','both'].includes(c.cocoRuntime)||!['project','global'].includes(c.cocoScope)) throw new Error('설정 값을 확인해주세요.');
    c.setupComplete=true;this.data.config=c; this.epoch=(this.epoch||0)+1; this.persist(); return this.snapshot();
  }
  async notice(value) {
    try {await this.deps.notify(value);} catch(error) {this.view.notificationError=error.message;this.persist();}
  }
  async check() {await Promise.allSettled([this.checkGraph(),this.checkCoco()]); return this.snapshot();}
  async checkGraph() {
    if(this.graphCheck) return this.graphCheck;
    this.graphCheck=this._checkGraph().finally(()=>{this.graphCheck=null;}); return this.graphCheck;
  }
  async _checkGraph() {
    const config=copy(this.data.config),epoch=this.epoch;
    if(!config.projectPath) {this.view.graph={phase:'setup'};this.persist();return;}
    try {
      const result=await this.deps.graph.check(config);
      if(epoch!==this.epoch||!result) return;
      this.view.graph={...result,phase:result.installed?'current':'available',lastChecked:this.deps.now(),error:''};
      this.persist();
      if(!result.installed&&config.autoApply) {
        if(this.data.graph.failedRelease!==result.release||this.deps.now()>=(this.data.graph.retryAfter||0)) await this.downloadGraph(true);
      } else if(!result.installed&&this.data.graph.notified!==result.release) {
        this.data.graph.notified=result.release;this.persist();
        await this.notice({kind:'graph',...result});
      }
    } catch(error) {this.view.graph={...this.view.graph,phase:'error',error:error.message,lastChecked:this.deps.now()};this.persist();}
  }
  async downloadGraph(automatic=false) {
    if(this.view.busyGraph) return;
    const config=copy(this.data.config);
    if(!config.projectPath) throw new Error('프로젝트를 먼저 선택해주세요.');
    this.view.busyGraph=true;this.persist();
    let release;
    try {
      const current=await this.deps.graph.check(config);release=current.release;
      const result=current.installed?{status:'unchanged'}:await this.deps.graph.download(config);
      const applied={...current,release:result.release??release,source_commit:result.source_commit??current.source_commit,
                     compatible:result.compatible??current.compatible};
      if(applied.release!==release)delete applied.archive_size;
      this.data.graph={...this.data.graph,notified:applied.release,failedRelease:'',retryAfter:0};
      this.view.graph={...applied,installed:true,phase:'current',lastChecked:this.deps.now(),error:''};
      if(result.status==='updated') await this.notice({kind:'graph-downloaded',...applied});
      return result;
    } catch(error) {
      this.data.graph.failedRelease=release;this.data.graph.retryAfter=this.deps.now()+300000;
      this.view.graph={...this.view.graph,phase:'error',error:error.message};
      if(automatic&&this.data.graph.failureNotified!==release) {this.data.graph.failureNotified=release;await this.notice({kind:'graph-failed',release,error:error.message});}
      if(!automatic) throw error;
    } finally {this.view.busyGraph=false;this.persist();}
  }
  async installCoco(version) {
    if(this.view.busyCoco) throw new Error('COCO 설치가 진행 중입니다.');
    if(!VERSION.test(version)||!this.data.coco.versions?.includes(version)) throw new Error('확인된 배포 버전을 선택해주세요.');
    const c=copy(this.data.config);
    if(c.cocoScope==='project'&&!c.projectPath) throw new Error('프로젝트를 먼저 선택해주세요.');
    this.view.busyCoco=true;this.view.coco.installError='';this.persist();
    try {
      const result=await this.deps.coco.install({version,runtimes:c.cocoRuntime==='both'?['codex','claude']:[c.cocoRuntime],scope:c.cocoScope,projectPath:c.projectPath});
      this.data.coco.installed={version,runtime:c.cocoRuntime,scope:c.cocoScope,projectPath:c.projectPath};
      this.view.coco={...this.view.coco,installed:this.data.coco.installed,installResult:result,installError:''};
      await this.notice({kind:'coco-installed',version});return result;
    } catch(error) {this.view.coco.installError=error.message;throw error;}
    finally {this.view.busyCoco=false;this.persist();}
  }
  async applyCoco() {
    const c=this.data.config,versions=this.data.coco.versions;
    if(!c.autoApply||this.view.busyCoco||!versions?.length||(c.cocoScope==='project'&&!c.projectPath))return;
    const version=versions.reduce((latest,v)=>compareVersions(v,latest)>0?v:latest);
    const applied=this.data.coco.installed,root=c.cocoScope==='project'?c.projectPath:'';
    if(applied?.scope===c.cocoScope&&(applied.runtime===c.cocoRuntime||applied.runtime==='both')&&
       (!root||applied.projectPath===root)&&VERSION.test(applied.version)&&compareVersions(applied.version,version)>=0)return;
    const target=JSON.stringify([version,c.cocoScope,c.cocoRuntime,root]),retry=this.data.coco.autoRetry;
    if(retry?.target===target&&this.deps.now()<retry.after)return;
    try{await this.installCoco(version);}
    catch{
      this.data.coco.autoRetry={target,after:this.deps.now()+300000};
      this.persist();
    }
  }
  async checkCoco() {
    if(this.cocoCheck) return this.cocoCheck;
    this.cocoCheck=this._checkCoco().finally(()=>{this.cocoCheck=null;});
    return this.cocoCheck;
  }
  async _checkCoco() {
    try {
      const response=await this.deps.coco.check(this.data.coco.etag);
      if(response.notModified) {
        this.view.coco={...this.view.coco,phase:'ready',lastChecked:this.deps.now(),error:''};
        this.persist();
        await this.applyCoco();
        return;
      }
      const p=response.data||response;
      if(p.name!=='@thisis-joe/coco-skills'||!p.versions||typeof p.versions!=='object') throw new Error('COCO npm 응답이 올바르지 않습니다.');
      const versions=Object.keys(p.versions).filter(v=>v.length<100&&VERSION.test(v)).sort((a,b)=>(Date.parse(p.time?.[b])||0)-(Date.parse(p.time?.[a])||0)||b.localeCompare(a,undefined,{numeric:true}));
      if(!versions.length||versions.length>5000) throw new Error('COCO 배포 버전 목록을 확인할 수 없습니다.');
      const known=this.data.coco.known;
      // First launch offers the newest existing release once, then watches all versions.
      const fresh=known ? versions.filter(v=>!known.includes(v)) : (versions[0]!=='0.2.0'?[versions[0]]:[]);
      this.data.coco={...this.data.coco,known:versions,etag:response.etag||'',versions};
      this.view.coco={...this.view.coco,phase:'ready',versions,latest:versions[0],newVersions:fresh,lastChecked:this.deps.now(),error:''};
      this.persist();
      if(fresh.length) await this.notice({kind:'coco',versions:fresh,version:fresh[0]});
      await this.applyCoco();
    } catch(error) {
      this.view.coco={...this.view.coco,phase:'error',error:error.message,lastChecked:this.deps.now()}; this.persist();
    }
  }
}
module.exports={UpdateEngine,VERSION,defaults};
