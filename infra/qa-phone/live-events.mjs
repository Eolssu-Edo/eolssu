const names={sign_in:'로그인',auth_sign_in:'로그인',auth_sign_up:'회원가입',auth_refresh:'로그인 갱신',auth_sign_out:'로그아웃',terms:'약관 조회',api:'API 요청',song_list:'곡 목록',song_detail:'곡 상세 악보',practice:'연습 악보',recording_upload:'녹음 제출',analysis_retry:'채점 재시도',evaluation:'본인 결과 조회'};
const validUuid=value=>typeof value==='string'&&/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
const localKey=event=>Number.isSafeInteger(event.interactionId)&&event.interactionId>0&&Number.isSafeInteger(event.appRun)?`interaction/${event.appRun}/${event.interactionId}`:
  Number.isSafeInteger(event.appRun)&&Number.isSafeInteger(event.flow)
  ? event.evaluationSubmission!==undefined?`submission/${event.appRun}/${event.flow}/${event.evaluationSubmission}`
    :event.visit!==undefined?`visit/${event.appRun}/${event.flow}/${event.visit}`:null:null;

// Logs are a separate, opt-in projection. Never forward raw bodies or headers.
export function safeLogRecords(events,{limit=500}={}) {
  const tokens=new Set(['kind','phase','target','outcome','method','modelVersion','failReason','state','scoringVersion','versionName','reason','dataSource','screenId','correlation','gesture','contentId']);
  const numbers=new Set(['seq','durationMs','queueMs','processingMs','threadCpuMs','ms','attempt','visit','flow','appRun','evaluationSubmission','count','evaluations','notes','total','pitch','timing','duration','bytes','noteCount','load','resource','revision','failed','missing','versionCode','schemaVersion','interactionId','x','y','dx','dy','pointers','elapsedMs']);
  const records=[];
  for(const event of Array.isArray(events)?events:[]){
    if(!event||!['app','proxy','backend','ai','db','observer','test'].includes(event.source)||!/^\d{4}-\d\d-\d\dT[\d:.+-]+Z?$/.test(event.at)||!Number.isFinite(Date.parse(event.at)))continue;
    const row={at:event.at,source:event.source};
    for(const [key,value] of Object.entries(event)){
      if(['dx','dy'].includes(key)&&Number.isInteger(value)&&value>=-1000&&value<=1000)row[key]=value;
      else if(tokens.has(key)&&typeof value==='string'&&/^[A-Za-z0-9_.:-]{1,100}$/.test(value))row[key]=value;
      else if(numbers.has(key)&&Number.isFinite(value)&&value>=0&&value<=1e12)row[key]=value;
      else if(['requestId','evaluationTimingId'].includes(key)&&validUuid(value))row[key]=value;
      else if(key==='runId'&&/^[a-f0-9]{32}$/.test(value))row[key]=value;
      else if(['sessionId','evaluationId'].includes(key)&&/^[1-9][0-9]{0,18}$/.test(String(value)))row[key]=String(value);
      else if(key==='status'&&((Number.isInteger(value)&&value>=100&&value<=599)||['RECORDING','PENDING','DONE','FAILED'].includes(value)))row[key]=value;
    }
    if(row.kind)records.push(row);
  }
  return {total:records.length,records:limit===null?records:records.slice(-limit)};
}

export function deviceEvidence(events) {
  const local=events.filter(event=>['app','proxy','observer'].includes(event.source));
  const requests=new Set(local.map(event=>event.requestId).filter(validUuid));
  const timings=new Set(local.map(event=>event.evaluationTimingId).filter(validUuid));
  return safeLogRecords(events.filter(event=>['app','proxy','observer'].includes(event.source)||requests.has(event.requestId)||timings.has(event.evaluationTimingId)));
}

export function liveEvidence(events,operationId) {
  const operation=liveOperations(events).find(value=>value.id===operationId);
  if(!operation)return {total:0,records:[]};
  const requests=new Set(operation.requests.map(value=>value.requestId));
  return safeLogRecords(events.filter(event=>{
    if(event.source==='app')return localKey(event)===operation.id||requests.has(event.requestId);
    if(operation.attempt!=null&&event.attempt!=null&&operation.attempt!==event.attempt)return false;
    return requests.has(event.requestId)||(!!operation.timingId&&event.evaluationTimingId===operation.timingId);
  }));
}

export function liveOperations(events) {
  const groups=new Map(),requests=new Map(),timings=new Map();
  const ensure=(key,event)=>{
    if(!groups.has(key))groups.set(key,{id:key,events:[],name:names[event.target]||event.target||'앱 동작'});
    return groups.get(key);
  };
  for(const event of events.filter(event=>event.source==='app')){
    const key=localKey(event)||(validUuid(event.requestId)?`request/${event.requestId}`:null);
    if(!key)continue;
    const group=ensure(key,event);group.events.push(event);
    if(['login_attempt','login_result'].includes(event.kind))group.name='로그인';
    else if(event.kind==='content_selected')group.name=event.contentId?`콘텐츠 ${event.contentId}`:'콘텐츠 선택';
    else if(event.kind==='callback_entered'&&!group.events.some(row=>['login_attempt','content_selected'].includes(row.kind)))group.name=event.target;
    if(validUuid(event.requestId))requests.set(event.requestId,group);
    if(validUuid(event.evaluationTimingId))timings.set(event.evaluationTimingId,group);
    if(event.target==='recording_upload'||event.target==='analysis_retry')group.name=names[event.target];
  }
  for(const event of events.filter(event=>event.source!=='app')){
    const group=timings.get(event.evaluationTimingId)||requests.get(event.requestId);
    if(group)group.events.push(event);
  }
  return [...groups.values()].map(group=>{
    group.events.sort((a,b)=>a.seq-b.seq);
    const all=group.events;
    const upload=all.find(event=>event.source==='app'&&event.kind==='request_started'&&['recording_upload','analysis_retry'].includes(event.target));
    const begin=upload||all.find(event=>event.kind==='request_started')||all[0];
    const first=(kind,source)=>all.find(event=>event.kind===kind&&event.source===source&&(!upload||event.requestId===upload.requestId));
    const milestone=phase=>all.find(event=>event.source==='app'&&event.kind==='milestone'&&event.phase===phase);
    const stage=(name,event)=>({name,at:event?.at??null,source:event?.source??null,
      elapsedMs:event?.source==='app'&&Number.isFinite(event.ms)&&Number.isFinite(begin.ms)?event.ms-begin.ms:null,
      durationMs:Number.isFinite(event?.durationMs)?event.durationMs:null,
      status:event?.status??event?.outcome??null,requestId:event?.requestId??null,correlation:event?.correlation??null});
    const received=first('request_received','backend')||first('request_received','proxy');
    const stages=[stage('request_started',begin.kind==='request_started'?begin:null),stage(received?.source==='proxy'?'proxy_received':'request_received',received),
      stage('response_finished',first('response_finished','backend')||first('response_finished','proxy')),stage('request_finished',first('request_finished','app'))];
    if(upload){
      const accepted=all.find(event=>event.kind==='request_finished'&&event.source==='app'&&event.requestId===upload.requestId&&event.status===202);
      const tid=accepted?.evaluationTimingId||upload.evaluationTimingId;
      const attempt=all.find(event=>event.evaluationTimingId===tid&&event.attempt!==undefined)?.attempt;
      const matching=all.filter(event=>event.evaluationTimingId===tid&&(attempt===undefined||event.attempt===undefined||event.attempt===attempt));
      stages.push(stage('upload_accepted',accepted),stage('ai_started',matching.find(event=>event.source==='ai'&&event.kind==='analysis_started')),
        stage('ai_finished',matching.find(event=>event.source==='ai'&&event.kind==='analysis_finished')),
        stage('db_saved',matching.find(event=>event.source==='db'&&event.status==='DONE'&&event.evaluations>0&&event.notes>0)),
        stage('analysis_ready',milestone('analysis_ready')),stage('score_drawn',milestone('score_drawn')));
    }else{
      for(const phase of ['song_detail_drawn','practice_drawn']){
        if(all.some(event=>event.target===(phase==='practice_drawn'?'practice':'song_detail')))stages.push(stage(phase,milestone(phase)));
      }
    }
    // Standard QA APKs record completed HTTP attempts/resource loads, without
    // live-only request UUIDs. Show that actual evidence; never infer server/AI/DB stages.
    if(!all.some(event=>event.kind==='request_started')){
      stages.length=0;
      for(const event of all.filter(event=>['visit_started','http_attempt','data_load','milestone','flow_end','action','poll_summary','touch_start','touch_end','touch_cancel','gesture','click','callback_entered','callback_returned','input_changed','input_focus','input_blur','input_commit','input_validation','screen_enter','screen_leave','lifecycle','key_action','login_attempt','login_result','content_selected','audio_requested','audio_state','recording_state','modal_open','modal_close','http_started','http_finished'].includes(event.kind)))
        stages.push(stage(event.kind==='milestone'?(event.phase||'milestone'):event.kind,event));
    }
    const context=all.find(event=>event.evaluationTimingId&&event.sessionId)||all.find(event=>event.sessionId)||{};
    const requestEvidence=all.filter(event=>event.source==='app'&&['request_started','http_started'].includes(event.kind)&&validUuid(event.requestId)).map(start=>{
      const received=all.find(event=>event.source==='backend'&&event.kind==='request_received'&&event.requestId===start.requestId);
      const response=all.find(event=>event.source==='backend'&&event.kind==='response_finished'&&event.requestId===start.requestId);
      const finished=all.find(event=>event.source==='app'&&['request_finished','http_finished'].includes(event.kind)&&event.requestId===start.requestId);
      return {requestId:start.requestId,target:names[start.target]||start.target,backendReceived:!!received,status:finished?.status??null,durationMs:response?.durationMs??null};
    });
    for(const event of all.filter(event=>event.source==='app'&&event.kind==='http_attempt'))
      requestEvidence.push({requestId:null,target:names[event.target]||event.target,backendReceived:false,status:event.status??null,durationMs:event.durationMs??null});
    return {id:group.id,name:group.name,startedAt:begin.at,requestId:begin.requestId??null,timingId:context.evaluationTimingId??null,
      sessionId:context.sessionId??null,attempt:context.attempt??null,evaluationId:all.find(event=>event.evaluationId)?.evaluationId??null,stages,requests:requestEvidence};
  }).sort((a,b)=>Date.parse(a.startedAt)-Date.parse(b.startedAt)).slice(-80);
}
