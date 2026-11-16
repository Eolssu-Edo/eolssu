import {safeLogRecords,liveOperations,liveEvidence} from './live-events.mjs';

// Reads a stable, read-only copy of the APK performance DB. No broadcast, ack or app action.
export class InstalledAppLogs {
  constructor({snapshot,installed,now=Date.now}) {
    Object.assign(this,{snapshot,installed,now});
    this.records=[];this.runs=new Map();this.nextRun=1;this.version=null;this.stream=null;this.highWater=0;
    this.round=null;this.pending=null;this.nextAt=0;this.state='waiting';this.closed=false;
  }
  refresh(known) {
    if(this.closed||this.pending||this.now()<this.nextAt)return;
    this.nextAt=this.now()+400;
    this.pending=this.collect(known).catch(()=>{this.state='unavailable';this.round=null;})
      .finally(()=>{this.pending=null;});
  }
  async collect(known) {
    const installed=known||await this.installed();
    const version=installed?.versionName;
    if(typeof version!=='string'||!/^[A-Za-z0-9._+-]{1,100}$/.test(version))throw Error('App version unavailable');
    if(this.version!==version){this.version=version;this.records=[];this.runs.clear();this.highWater=0;this.round=null;this.stream=null;this.lastEventAt=null;}
    const value=await this.snapshot(this.highWater,this.stream);
    if(this.stream!==value.stream_id){this.stream=value.stream_id;this.highWater=0;this.records=[];this.runs.clear();this.lastEventAt=null;}
    this.round=(value.events.at(-1)?.seq??value.after??this.highWater)<value.end;
    this.records=this.records.filter(record=>record.seq>(value.ack??0));
    for(const entry of value.events){
        if(entry.seq<=this.highWater)continue;
        this.highWater=entry.seq;
        const event=entry.event;
        if(event.version_name!==this.version||!/^[a-f0-9]{32}$/.test(event.run_id)||
          ['flow','visit'].some(key=>event[key]!==undefined&&(!Number.isSafeInteger(event[key])||event[key]<0)))continue;
        if(!this.runs.has(event.run_id))this.runs.set(event.run_id,this.nextRun++);
        const candidate={...event,source:'app',at:new Date(this.now()).toISOString(),seq:entry.seq,
          appRun:this.runs.get(event.run_id),runId:event.run_id,versionName:this.version,versionCode:event.version_code,dataSource:event.source,
          durationMs:event.duration_ms,evaluationTimingId:event.evaluation_timing_id,evaluationSubmission:event.evaluation_submission};
        const safe=safeLogRecords([candidate]).records[0];
        if(safe)this.records.push(safe);
      }
    this.lastEventAt=this.records.at(-1)?.at||null;
    this.state='collecting';
  }
  project(snapshot,logsFor) {
    if(!this.version)return snapshot;
    const result={...snapshot,api:null,device:{...snapshot.device,appVersion:this.version},
      appLog:{state:this.state,count:this.records.length,partial:!!this.round},
      operations:liveOperations(this.records),lastEventAt:this.lastEventAt||null};
    if(logsFor==='device/all')result.evidence={id:logsFor,...safeLogRecords(this.records,{limit:null})};
    else if(logsFor)result.evidence={id:logsFor,...liveEvidence(this.records,logsFor)};
    return result;
  }
  async close(){this.closed=true;await this.pending;}
}
