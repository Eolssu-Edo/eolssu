const naiveInstant=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;
const explicitInstant=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;
const kstClock=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'});
const kstDate=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'});
export function time(value,full=false){
  if(value==null||value==='')return '—';
  const instant=new Date(typeof value==='string'&&naiveInstant.test(value)?value+'Z':value);
  if(!Number.isFinite(instant.getTime()))return '—';
  const clock=kstClock.format(instant);if(!full)return clock;
  const parts=Object.fromEntries(kstDate.formatToParts(instant).map(({type,value})=>[type,value]));
  const fraction=typeof value==='string'?(value.match(/\.\d+(?=Z|[+-]\d{2}:\d{2}|$)/)?.[0]||''):'.'+String(instant.getUTCMilliseconds()).padStart(3,'0');
  return `${parts.year}-${parts.month}-${parts.day} ${clock}${fraction} KST`;
}
export function logJson(fields){
  return JSON.stringify(fields,(key,value)=>{
    const knownTimeKey=/^(?:at|serverTime)$|(?:At|_at)$/.test(key);
    if(typeof value==='string'&&(explicitInstant.test(value)||knownTimeKey&&naiveInstant.test(value))||typeof value==='number'&&Number.isFinite(value)&&knownTimeKey){const local=time(value,true);if(local!=='—')return local;}
    return value;
  });
}
const outcomeLabel=value=>({ready:'성공',failed:'실패',invalid:'입력 검증 실패',locked:'잠김',unavailable:'사용 불가',cancelled:'취소',started:'실제 시작',completed:'완료',stopped:'중단',paused:'일시 정지',loading:'준비 중',interrupted:'인터럽트'}[value]||'');
const duration=value=>Number.isFinite(value)?`${(value/1000).toFixed(2)}초`:'—';
const sources={app:'앱',proxy:'중계',backend:'서버',ai:'AI',db:'DB 조회',observer:'수집기',test:'검사 요청'};
const stageLabels={touch_start:'터치 수신',touch_end:'터치 종료',touch_cancel:'터치 취소',click:'클릭 콜백',callback_entered:'의미 콜백',callback_returned:'콜백 반환',input_changed:'입력 변경 콜백',input_focus:'입력 포커스',input_blur:'입력 포커스 해제',input_commit:'IME 실행',input_validation:'입력 검증',screen_enter:'화면 진입',screen_leave:'화면 이탈',login_attempt:'로그인 시도',login_result:'로그인 결과',content_selected:'콘텐츠 선택',audio_requested:'재생 요청',audio_state:'재생 상태',recording_state:'녹음 상태',http_started:'앱 HTTP 요청',http_finished:'앱 HTTP 응답',modal_open:'창 열림',modal_close:'창 닫힘',action:'앱 동작',poll_summary:'조회 집계',visit_started:'화면 진입',http_attempt:'앱 HTTP 응답',data_load:'데이터 로드',flow_end:'동작 종료',request_started:'앱 요청',request_received:'서버 수신',proxy_received:'중계 수신',response_finished:'서버 응답',request_finished:'앱 수신',song_detail_drawn:'악보 표시',practice_drawn:'연습 화면',upload_accepted:'녹음 접수',ai_started:'AI 시작',ai_finished:'AI 완료',db_saved:'DB 저장',analysis_ready:'앱 결과 수신',score_drawn:'점수 표시'};
const friendlyName=value=>({instruments:'악기 목록',record_page:'최근 연주',member_me:'내 정보',session_create:'연습 시작',practice_status:'채점 상태',score:'악보 조회'}[value]||value);
const qaFeatures=[['launch','앱 실행'],['login','로그인'],['contents','기초 콘텐츠'],['history','연습 기록 보기'],['perform','연습·평가'],['sheet','악보 추가·편집']];
export function appLogStatus(log){
  if(log.state==='unsupported')return {text:'계측 버전 미지원',state:'waiting'};
  if(log.missing>0)return {text:`로그 누락 ${log.missing}건`,state:'failed'};
  if(log.state!=='collecting')return {text:log.state==='waiting'?'로그 확인 중':'로그 연결 대기',state:'waiting'};
  return {text:log.partial?'저장 로그 읽는 중':log.actions==='unsupported'?'기본 로그 수집 중 · 조작 계측 미지원':'로그 수집 중',state:'observing'};
}
export function filterAppLogs(records,kind){
  if(kind==='screen')return records.filter(row=>['screen_enter','screen_leave','modal_open','modal_close','lifecycle'].includes(row.kind));
  if(kind==='action')return records.filter(row=>['touch_start','touch_end','touch_cancel','gesture','click','callback_entered','callback_returned','input_changed','input_focus','input_blur','input_commit','input_validation','login_attempt','login_result','content_selected','audio_requested','audio_state','recording_state','key_action'].includes(row.kind));
  if(kind==='http')return records.filter(row=>['http_attempt','request_started','request_finished','http_started','http_finished'].includes(row.kind));
  if(kind==='error')return records.filter(row=>Number(row.status)>=400||['FAILED','ERROR','failed','fail','invalid','locked','unavailable'].includes(row.outcome)||['FAILED','ERROR','failed','fail','invalid','locked','unavailable'].includes(row.status));
  return records;
}
const logKey=row=>`${row.deviceId||''}/${row.runId||''}/${row.source}/${row.seq}`;
export function logWindow(records,anchor=null,size=150){
  const index=anchor?records.findIndex(row=>logKey(row)===anchor):-1,end=index<0?records.length:index+1,start=Math.max(0,end-size);
  return {records:records.slice(start,end),start,end,before:start?logKey(records[start-1]):null,anchored:index>=0};
}

export function stageState(stage){
  if(!stage?.at)return 'waiting';
  if((typeof stage.status==='number'&&stage.status>=400)||['FAILED','ERROR','SERVER_ERROR','failed','fail','invalid','locked','unavailable'].includes(stage.status))return 'failed';
  return 'observed';
}
const logView=target=>`<details class="logDetails" data-log="${target}"><summary>로그 보기</summary><div class="logToolbar"><div class="logSourceButtons" role="group" aria-label="로그 종류"><button type="button" data-log-source="all" aria-pressed="true">전체</button><button type="button" data-log-source="action" aria-pressed="false">조작</button><button type="button" data-log-source="screen" aria-pressed="false">화면</button><button type="button" data-log-source="http" aria-pressed="false">HTTP</button><button type="button" data-log-source="error" aria-pressed="false">오류</button></div><span class="logCount"></span></div><div class="logPaging"><button type="button" data-log-page="previous">이전 기록</button><button type="button" data-log-page="latest">최신 기록</button></div><pre class="logRecords" tabindex="0">기록 없음</pre></details>`;

export class LivePanel{
  constructor(root,{api,owner,notify,automationRoot}){
    this.root=root;this.automation=automationRoot;this.roots=[root,automationRoot];this.api=api;this.owner=owner;this.notify=notify;
    this.active=false;this.follow=true;this.selected=null;this.qaStep='launch';this.context=0;this.requestVersion=0;this.logSourceSelections=new Map();
    this.query=selector=>this.roots.map(node=>node.querySelector(selector)).find(Boolean);
    this.nodes=selector=>this.roots.flatMap(node=>[...node.querySelectorAll(selector)]);
    this.get=name=>this.query(`[data-live="${name}"]`);
    root.innerHTML=`
      <ol class="qaSteps" role="tablist" aria-label="실기기 QA 항목">${qaFeatures.map(([key,label],index)=>`<li role="presentation"><button type="button" role="tab" id="qaTab-${key}" data-step="${key}" aria-controls="qaPanel-${key}" aria-selected="false" tabindex="-1"><span>${index+1}</span><strong>${label}</strong></button></li>`).join('')}</ol>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-launch" aria-labelledby="qaTab-launch" data-step-pane="launch" hidden><div class="launchActions"><div class="launchAction"><button id="applyLatestBatch" class="secondary" type="button">최신 APK 적용</button><p id="batchApkStatus" class="launchStatus" role="status"></p></div><div class="launchAction"><button data-action="start" class="primary">QA 앱 실행</button><p id="launchAllStatus" class="launchStatus" role="status"></p></div></div></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-login" aria-labelledby="qaTab-login" data-step-pane="login" hidden><ul class="qaCheckList"><li>로그인·로그아웃</li><li>입력 검증</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-contents" aria-labelledby="qaTab-contents" data-step-pane="contents" hidden><ul class="qaCheckList"><li>기초부터 배우기</li><li>운지법 보기</li><li>정간보 읽기</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-history" aria-labelledby="qaTab-history" data-step-pane="history" hidden><ul class="qaCheckList"><li>기록 목록·필터</li><li>기록 상세·녹음 재생</li><li>이전 연주 비교</li><li>메모·기록 삭제</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-perform" aria-labelledby="qaTab-perform" data-step-pane="perform" hidden><ul class="qaCheckList"><li>곡·악보 선택</li><li>모범 연주·구간 연습</li><li>마이크 녹음·다시 연주</li><li>평가 점수·틀린 부분</li></ul>
      </section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-sheet" aria-labelledby="qaTab-sheet" data-step-pane="sheet" hidden><ul class="qaCheckList"><li>사진·PDF 등록</li><li>변환·검수</li><li>음·빠르기·장단 편집</li><li>저장·다시 열기·연습</li></ul></section>
      <section class="card liveResults" data-live="results" aria-labelledby="observationTitle"><div class="panelHeading"><h3 id="observationTitle">앱 처리 기록</h3><span data-live="operationCount" class="subtle">0건</span><div class="logCollectionControls"><span class="chip" data-live="status" role="status">로그 수집 전</span></div></div>
        <div class="liveSelection"><label class="srOnly" for="qaOperation">확인할 앱 동작</label><select id="qaOperation" data-live="operations" aria-label="확인할 앱 동작"><option value="">기록 없음</option></select><label class="liveFollow"><input type="checkbox" data-live="follow" checked>최신</label></div>
        <ol class="liveTimeline" data-live="timeline" aria-label="실제 확인된 처리 단계"></ol>${logView('operation')}
        <details class="executionDetails"><summary>실행 정보</summary><dl class="liveMeta"><div><dt>앱 버전</dt><dd data-live="device">—</dd></div><div><dt>화면 수신</dt><dd data-live="frame">—</dd></div><div><dt>로그 수신</dt><dd data-live="event">—</dd></div></dl><p class="identifiers" data-live="identifiers"></p><details class="liveHttp"><summary data-live="httpCount">HTTP 요청 0개</summary><ul data-live="httpRequests"></ul></details></details>
      </section><p class="liveNotice" data-live="message" role="status"></p>`;
    automationRoot.replaceChildren(this.get('results'));
    const scopeBar=document.createElement('div');scopeBar.className='logDeviceToolbar';
    scopeBar.innerHTML='<label>기기 로그 <select aria-label="로그 기기 범위"><option value="operation">선택 동작</option><option value="active">선택 기기</option><option value="all">연결된 모든 기기</option></select></label><span data-live="logDevice"></span>';
    this.query('[data-log=operation] .logToolbar').before(scopeBar);this.logScope=scopeBar.querySelector('select');this.logScope.value='all';this.logScope.addEventListener('change',()=>void this.refresh());
    this.query('[data-log=operation]').open=true;
    this.nodes('[data-action]').forEach(button=>button.disabled=true);
    this.nodes('[data-step]').forEach((button,index)=>{
      button.addEventListener('click',()=>{this.qaStep=button.dataset.step;this.render(this.snapshot);void this.refresh();});
      button.addEventListener('keydown',event=>{
        const tabs=this.nodes('[data-step]');let next;
        if(event.key==='ArrowRight')next=(index+1)%tabs.length;
        else if(event.key==='ArrowLeft')next=(index-1+tabs.length)%tabs.length;
        else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;
        event.preventDefault();tabs[next].focus();tabs[next].click();
      });
    });
    this.nodes('[data-action]').forEach(button=>button.addEventListener('click',()=>void this.command(button.dataset.action)));
    this.get('follow').addEventListener('change',()=>{this.follow=this.get('follow').checked;this.render(this.snapshot);});
    this.get('operations').addEventListener('change',()=>{this.follow=false;this.get('follow').checked=false;this.selected=this.get('operations').value;this.render(this.snapshot);void this.refresh();});
    this.roots.forEach(node=>node.addEventListener('toggle',event=>{
      const details=event.target;
      if(!details.matches('.logDetails')){if(details.querySelector('.logDetails[open]'))void this.refresh();return;}
      if(!details.open){void this.refresh();return;}
      this.nodes('.logDetails[open]').forEach(other=>{if(other!==details)other.open=false;});void this.refresh();
    },true));
    this.roots.forEach(node=>node.addEventListener('click',event=>{const button=event.target.closest('[data-log-source],[data-log-page]');if(!button||!node.contains(button))return;const details=button.closest('.logDetails');if(button.dataset.logSource)this.logSourceSelections.set(details.dataset.log,button.dataset.logSource);else details.logAnchor=button.dataset.logPage==='previous'?details.logBefore:null;this.renderLogs(this.snapshot);}));
  }
  async command(action){if(action==='start'&&this.launchAll)await this.launchAll();}
  openLog(){return this.nodes('.logDetails[open]').find(details=>!details.closest('details:not([open]),[hidden]'));}
  logTarget(){const details=this.openLog();return details?.dataset.log==='operation'?(this.logScope.value==='operation'?this.selected:'device/all'):details?.dataset.log;}
  resetTarget(){this.context++;this.requestVersion++;this.selected=null;this.follow=true;this.operationsKey=null;this.get('follow').checked=true;this.logSourceSelections.clear();this.get('message').textContent='';this.render({appLog:{state:'waiting'},operations:[]});}
  setActive(active){if(this.active===active)return;this.active=active;clearTimeout(this.timer);if(active)void this.refresh();}
  async refresh(){
    if(!this.active)return;
    const context=this.context,version=++this.requestVersion;
    try{
      const target=this.logTarget();let snapshot=await this.api('live/status'+(target?'?logs='+encodeURIComponent(target):''));
      if(context===this.context&&version===this.requestVersion&&this.active){
        const previous=this.snapshot,sameVersion=!snapshot.device?.appVersion||!previous?.device?.appVersion||snapshot.device.appVersion===previous.device.appVersion;
        if(snapshot.appLog?.state!=='collecting'&&sameVersion&&previous?.evidence?.records?.length&&previous.evidence.id===target&&!snapshot.evidence?.records?.length){
          snapshot={...snapshot,evidence:previous.evidence,operations:snapshot.operations?.length?snapshot.operations:previous.operations};
        }
        this.render(snapshot);
      }
    }
    catch(error){if(context===this.context&&version===this.requestVersion){
      const previous=[401,403].includes(error.status)?null:this.snapshot;
      this.render({...previous,appLog:{...previous?.appLog,state:'unavailable'},operations:previous?.operations||[]});
      if(!previous?.evidence?.records?.length||previous.evidence.id!==this.logTarget())this.nodes('.logDetails[open] .logRecords').forEach(pre=>pre.textContent='로그 연결 대기');
    }}
    finally{if(context===this.context&&version===this.requestVersion){clearTimeout(this.timer);if(this.active)this.timer=setTimeout(()=>void this.refresh(),500);}}
  }
  render(snapshot){
    if(!snapshot)return;this.snapshot=snapshot;
    const owned=this.owner(),status=appLogStatus(snapshot.appLog||{state:'waiting'});
    this.get('status').textContent=status.text;this.get('status').dataset.state=status.state;
    this.root.closest('.realQa').dataset.next=owned.allowed&&['login','contents','history','perform','sheet'].includes(this.qaStep)?'device':'';
    this.nodes('[data-step]').forEach(item=>{const selected=item.dataset.step===this.qaStep;item.setAttribute('aria-selected',String(selected));item.tabIndex=selected?0:-1;item.dataset.state=selected?'current':'waiting';});
    this.nodes('[data-step-pane]').forEach(pane=>pane.hidden=pane.dataset.stepPane!==this.qaStep);
    this.get('device').textContent=snapshot.device?.appVersion||'—';this.get('frame').textContent=owned.frameAt?time(owned.frameAt)+' KST':'—';this.get('frame').title=time(owned.frameAt,true);this.get('event').textContent=snapshot.lastEventAt?time(snapshot.lastEventAt)+' KST':'—';this.get('event').title=time(snapshot.lastEventAt,true);
    const operations=snapshot.operations||[];if(this.follow)this.selected=operations.at(-1)?.id??null;
    const key=JSON.stringify(operations.map(({id,name,startedAt})=>({id,name,startedAt})));
    if(key!==this.operationsKey){this.operationsKey=key;this.get('operations').replaceChildren(...operations.map(operation=>{const option=document.createElement('option');option.value=operation.id;option.textContent=`${time(operation.startedAt)} KST ${friendlyName(operation.name)}`;option.title=time(operation.startedAt,true);return option;}));if(!operations.length){const option=document.createElement('option');option.value='';option.textContent='기록 없음';this.get('operations').append(option);}}
    this.get('operations').value=this.selected??'';this.get('operationCount').textContent=`${operations.length}건`;
    const operation=operations.find(value=>value.id===this.selected),visible=operation?.stages||[];
    this.get('timeline').replaceChildren(...visible.map(stage=>{const item=document.createElement('li');item.dataset.state=stageState(stage);const label=document.createElement('strong');label.textContent=stage.source==='proxy'&&stage.name==='response_finished'?'중계 응답':stageLabels[stage.name]||stage.name;const detail=document.createElement('span');detail.textContent=stage.at?Number.isFinite(stage.elapsedMs)?duration(stage.elapsedMs):Number.isFinite(stage.durationMs)?duration(stage.durationMs):'확인':'—';if(outcomeLabel(stage.status))detail.textContent+=' · '+outcomeLabel(stage.status);if(stage.correlation==='estimated')detail.textContent+=' · 조작 연결 추정';item.title=stage.at?`${sources[stage.source]||stage.source} · ${time(stage.at,true)}${stage.status!=null?' · '+stage.status:''}`:'';item.append(label,detail);return item;}));
    this.get('identifiers').textContent=operation?[operation.requestId,operation.timingId,operation.sessionId&&`세션 ${operation.sessionId}`,operation.attempt&&`시도 ${operation.attempt}`,operation.evaluationId&&`평가 ${operation.evaluationId}`].filter(Boolean).join(' · '):'';
    const requests=operation?.requests||[];this.get('httpCount').textContent=`HTTP 요청 ${requests.length}개`;this.get('httpRequests').replaceChildren(...requests.map(request=>{const li=document.createElement('li');li.textContent=[request.target,request.requestId,request.status?`HTTP ${request.status}`:'응답 대기',Number.isFinite(request.durationMs)?duration(request.durationMs):null].filter(Boolean).join(' · ');return li;}));
    this.renderLogs(snapshot);this.onRender?.(snapshot);
  }
  renderLogs(snapshot){
    const details=this.openLog();if(!details)return;const target=this.logTarget(),evidence=snapshot?.evidence,pre=details.querySelector('.logRecords'),count=details.querySelector('.logCount');
    if(!target){pre.textContent='기록 없음';count.textContent='';return;}if(evidence?.id!==target){pre.textContent='불러오는 중';count.textContent='';return;}
    details.querySelector('summary').textContent='로그 보기 · KST';
    const source=this.logSourceSelections.get(details.dataset.log)||'all';details.querySelectorAll('[data-log-source]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.logSource===source)));
    const context=target+'|'+source;if(details.logContext!==context){details.logContext=context;details.logAnchor=null;}
    const scrollTop=pre.scrollTop;
    const all=filterAppLogs(evidence.records||[],source),page=logWindow(all,details.logAnchor),records=page.records;
    details.logBefore=page.before;details.querySelector('[data-log-page=previous]').disabled=!page.before;details.querySelector('[data-log-page=latest]').disabled=!page.anchored;
    count.textContent=`${page.end?page.start+1+'–'+page.end:0} / ${all.length}건${evidence.total>evidence.records.length?' · 전체 '+evidence.total+'건':''}${evidence.unavailable?' · '+evidence.unavailable+'대 연결 대기':''}`;
    pre.classList.add('hasDeviceRows');pre.replaceChildren(...records.map(({at,source,deviceId,...fields})=>{const device=this.deviceInfo?.(deviceId),row=document.createElement('div');row.className='deviceLogRow';row.style.setProperty('--device-color',device?.color||'#69734f');row.dataset.level=(Number(fields.status)>=400||fields.status==='FAILED'||['FAILED','failed','invalid','locked','unavailable'].includes(fields.outcome))?'failed':'observed';const when=document.createElement('time');when.textContent=time(at);when.title=time(at,true);when.dateTime=at;const tag=document.createElement('span');tag.className='deviceTag';tag.textContent=device?`${device.number} ${device.name}`:'서버';const body=document.createElement('span');body.className='logBody';const origin=document.createElement('span');origin.className='sourceChip';origin.textContent=sources[source]||source;const name=document.createElement('strong');name.textContent=fields.kind||'기록';const data=document.createElement('small');data.textContent=logJson(fields);body.append(origin,name,data);row.append(when,tag,body);return row;}));if(!records.length)pre.textContent='기록 없음';pre.scrollTop=scrollTop;
  }
}

/** Upgrade existing module instances in the same document, retaining video and control ownership. */
export function installLivePanelUpgrade(previous) {
  if(typeof previous!=='function'||!previous.prototype?.renderLogs)throw Error('Incompatible QA panel');
  for(const key of Object.getOwnPropertyNames(LivePanel.prototype)) {
    if(key!=='constructor')Object.defineProperty(previous.prototype,key,Object.getOwnPropertyDescriptor(LivePanel.prototype,key));
  }
  for(const group of document.querySelectorAll('.logSourceButtons')) {
    for(const [kind,label] of [['action','조작'],['screen','화면']]) {
      if(group.querySelector(`[data-log-source="${kind}"]`))continue;
      const button=document.createElement('button');button.type='button';button.dataset.logSource=kind;
      button.setAttribute('aria-pressed','false');button.textContent=label;
      group.insertBefore(button,group.querySelector('[data-log-source="http"]'));
    }
  }
  return {schemaVersion:2,documentPreserved:true,panels:document.querySelectorAll('.logSourceButtons').length};
}
