const time=value=>value?new Date(value).toLocaleTimeString('en-GB',{hour12:false,timeZone:'Asia/Seoul'}):'—';
const duration=value=>Number.isFinite(value)?`${(value/1000).toFixed(2)}초`:'—';
const scenarioNames={feedback:'평가 결과 비교',retry:'평가 재시도',duplicate:'중복 제출 방지',ownership:'계정 접근 차단'};
const statuses={requesting:'요청 중',preparing:'준비 중',running:'진행 중',pass:'통과',fail:'실패',failed:'실패',cancelled:'중단'};
const sources={app:'앱',proxy:'중계',backend:'서버',ai:'AI',db:'DB 조회',observer:'수집기',test:'검사 요청'};
const stageLabels={action:'앱 동작',poll_summary:'조회 집계',visit_started:'화면 진입',http_attempt:'앱 HTTP 응답',data_load:'데이터 로드',flow_end:'동작 종료',request_started:'앱 요청',request_received:'서버 수신',proxy_received:'중계 수신',response_finished:'서버 응답',request_finished:'앱 수신',song_detail_drawn:'악보 표시',practice_drawn:'연습 화면',upload_accepted:'녹음 접수',ai_started:'AI 시작',ai_finished:'AI 완료',db_saved:'DB 저장',analysis_ready:'앱 결과 수신',score_drawn:'점수 표시'};
const friendlyName=value=>({instruments:'악기 목록',record_page:'최근 연주',member_me:'내 정보',session_create:'연습 시작',practice_status:'채점 상태',score:'악보 조회'}[value]||value);
const qaFeatures=[['launch','앱 실행'],['login','로그인'],['contents','기초 콘텐츠'],['history','연습 기록 보기'],['perform','연습·평가'],['sheet','악보 추가·편집'],['load','부하 테스트']];
const logKey=row=>`${row.deviceId||''}/${row.source}/${row.seq}/${row.at}`;
export function logWindow(records,anchor=null,size=150){
  const index=anchor?records.findIndex(row=>logKey(row)===anchor):-1,end=index<0?records.length:index+1,start=Math.max(0,end-size);
  return {records:records.slice(start,end),start,end,before:start?logKey(records[start-1]):null,anchored:index>=0};
}

export function stageState(stage){
  if(!stage?.at)return 'waiting';
  if((typeof stage.status==='number'&&stage.status>=400)||['FAILED','ERROR','SERVER_ERROR','failed','fail'].includes(stage.status))return 'failed';
  return 'observed';
}
export function qaWorkflow(snapshot,owner){
  if(!owner.connected||!owner.allowed)return 'control';
  if(snapshot.mode!=='observe'||snapshot.phase!=='observing'||snapshot.selected===false)return 'launch';
  const operations=snapshot.operations||[];
  const required=['upload_accepted','ai_finished','db_saved','analysis_ready','score_drawn'];
  if(operations.some(operation=>required.every(name=>{
    const stage=operation.stages?.find(value=>value.name===name);
    return stageState(stage)==='observed'&&(name!=='ai_finished'||stage.status==='DONE')&&(name!=='upload_accepted'||stage.status===202);
  })))return 'complete';
  const signedIn=operations.some(operation=>
    (['로그인','record_page','member_me'].includes(operation.name)&&operation.requests?.some(request=>request.status===200))||
    operation.stages?.some(stage=>['song_detail_drawn','practice_drawn'].includes(stage.name)&&stageState(stage)==='observed'));
  return signedIn?'perform':'login';
}
const logView=target=>`<details class="logDetails" data-log="${target}"><summary>로그 보기</summary><div class="logToolbar"><div class="logSourceButtons" role="group" aria-label="로그 출처"><button type="button" data-log-source="all" aria-pressed="true">전체 출처</button><button type="button" data-log-source="app" aria-pressed="false">앱</button><button type="button" data-log-source="backend" aria-pressed="false">서버</button><button type="button" data-log-source="ai" aria-pressed="false">AI</button><button type="button" data-log-source="db" aria-pressed="false">DB 조회</button><button type="button" data-log-source="test" aria-pressed="false">검사 요청</button><button type="button" data-log-source="proxy" aria-pressed="false">중계</button><button type="button" data-log-source="observer" aria-pressed="false">수집기</button></div><span class="logCount"></span></div><div class="logPaging"><button type="button" data-log-page="previous">이전 기록</button><button type="button" data-log-page="latest">최신 기록</button></div><pre class="logRecords" tabindex="0">기록 없음</pre></details>`;

export class LivePanel{
  constructor(root,{api,owner,notify,automationRoot}){
    this.root=root;this.automation=automationRoot;this.roots=[root,automationRoot];this.api=api;this.owner=owner;this.notify=notify;
    this.active=false;this.follow=true;this.selected=null;this.pending=false;this.loginNext='email';this.qaStep='launch';this.context=0;this.requestVersion=0;this.logSourceSelections=new Map();this.loadTracking=false;
    this.query=selector=>this.roots.map(node=>node.querySelector(selector)).find(Boolean);
    this.nodes=selector=>this.roots.flatMap(node=>[...node.querySelectorAll(selector)]);
    this.get=name=>this.query(`[data-live="${name}"]`);
    root.innerHTML=`
      <ol class="qaSteps" role="tablist" aria-label="실기기 QA 항목">${qaFeatures.map(([key,label],index)=>`<li role="presentation"><button type="button" role="tab" id="qaTab-${key}" data-step="${key}" aria-controls="qaPanel-${key}" aria-selected="false" tabindex="-1"><span>${index+1}</span><strong>${label}</strong></button></li>`).join('')}</ol>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-launch" aria-labelledby="qaTab-launch" data-step-pane="launch" hidden><div class="launchActions"><div class="launchAction"><button id="applyLatestBatch" class="secondary" type="button">최신 APK 적용</button><p id="batchApkStatus" class="launchStatus" role="status"></p></div><div class="launchAction"><button data-action="start" class="primary">QA 앱 실행</button><p id="launchAllStatus" class="launchStatus" role="status"></p></div></div></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-login" aria-labelledby="qaTab-login" data-step-pane="login" hidden><div class="liveActions loginActions"><button data-action="email">이메일 입력</button><button data-action="password">비밀번호 입력</button></div><ul class="qaCheckList"><li>로그인·로그아웃</li><li>입력 검증</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-contents" aria-labelledby="qaTab-contents" data-step-pane="contents" hidden><ul class="qaCheckList"><li>기초부터 배우기</li><li>운지법 보기</li><li>정간보 읽기</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-history" aria-labelledby="qaTab-history" data-step-pane="history" hidden><ul class="qaCheckList"><li>기록 목록·필터</li><li>기록 상세·녹음 재생</li><li>이전 연주 비교</li><li>메모·기록 삭제</li></ul></section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-perform" aria-labelledby="qaTab-perform" data-step-pane="perform" hidden><ul class="qaCheckList"><li>곡·악보 선택</li><li>모범 연주·구간 연습</li><li>마이크 녹음·다시 연주</li><li>평가 점수·틀린 부분</li></ul><span class="chip" data-live="inputApplied">다음 녹음: 기기 마이크</span>
        <details class="inputSettings"><summary>녹음 입력 변경</summary><div class="liveInput"><label><span class="srOnly">녹음 입력</span><select data-live="input"><option value="microphone">기기 마이크</option><option value="synthetic">QA 테스트 소리</option></select></label><button data-action="fixture">입력 적용</button></div></details>
      </section>
      <section class="card qaStepPane" role="tabpanel" id="qaPanel-sheet" aria-labelledby="qaTab-sheet" data-step-pane="sheet" hidden><ul class="qaCheckList"><li>사진·PDF 등록</li><li>변환·검수</li><li>음·빠르기·장단 편집</li><li>저장·다시 열기·연습</li></ul></section>
      <section class="card liveResults" data-live="results" aria-labelledby="observationTitle"><div class="panelHeading"><h3 id="observationTitle">앱 처리 기록</h3><span data-live="operationCount" class="subtle">0건</span><div class="logCollectionControls"><span class="chip" data-live="status" role="status">로그 수집 전</span><button data-action="stop" class="secondary" hidden>로그 수집 종료</button></div></div>
        <div class="liveSelection"><label class="srOnly" for="qaOperation">확인할 앱 동작</label><select id="qaOperation" data-live="operations" aria-label="확인할 앱 동작"><option value="">기록 없음</option></select><label class="liveFollow"><input type="checkbox" data-live="follow" checked>최신</label></div>
        <ol class="liveTimeline" data-live="timeline" aria-label="실제 확인된 처리 단계"></ol>${logView('operation')}
        <details class="executionDetails"><summary>실행 정보</summary><dl class="liveMeta"><div><dt>OS · 앱</dt><dd data-live="device">—</dd></div><div><dt>QA API</dt><dd data-live="api">—</dd></div><div><dt>화면 수신</dt><dd data-live="frame">—</dd></div><div><dt>로그 수신</dt><dd data-live="event">—</dd></div></dl><p class="identifiers" data-live="identifiers"></p><details class="liveHttp"><summary data-live="httpCount">HTTP 요청 0개</summary><ul data-live="httpRequests"></ul></details></details>
      </section><p class="liveNotice" data-live="message" role="status"></p>`;
    automationRoot.innerHTML=`<div class="workspaceHeading"><h2>서버 자동 검사</h2><span class="scopeBadge">QA 서버</span></div><div class="automationGrid">
      <section class="card scenarioCard" aria-labelledby="scenarioTitle"><div class="panelHeading"><h3 id="scenarioTitle">기능 검사</h3></div>
        <div class="scenarioButtons">${Object.entries(scenarioNames).map(([name,label])=>`<button data-scenario="${name}" aria-pressed="false"><span>${label}</span><em data-result="${name}">미실행</em></button>`).join('')}</div>
        <details class="scenarioDetails" hidden open><summary><strong data-live="scenarioName">검사 결과</strong><span class="chip" data-live="scenarioStatus" role="status">대기</span></summary><ol class="scenarioChecks" data-live="scenarioEvidence"></ol><div class="tableScroll"><table class="liveFeedback" data-live="feedback" hidden></table></div>${logView('server/scenario')}</details>
      </section>
      <section class="card loadCard" aria-label="부하 테스트"><span class="srOnly" data-live="loadStatus" role="status">미실행</span>
        <div class="loadControls"><label>제출 수<select data-live="size"><option>6</option><option>150</option><option>300</option><option>500</option><option>750</option><option>1000</option></select></label><button data-action="run" class="primary">동시 제출 시작</button><button data-action="cancel">중단</button></div>
        <dl class="liveMetrics"><div><dt>성공 / 전체</dt><dd data-live="success">—</dd></div><div><dt>실패</dt><dd data-live="failed">—</dd></div><div><dt>5초 이내</dt><dd data-live="within5">—</dd></div></dl><div class="loadTiles" data-live="loadTiles" role="list" aria-label="제출별 결과"></div><p class="loadTiming" data-live="loadTiming"></p>${logView('server/load')}
      </section><section class="card liveHistory"><details><summary>이전 실행 결과</summary><ul data-live="history"><li>기록 없음</li></ul></details></section></div>`;
    automationRoot.prepend(this.get('results'));
    const loadPane=document.createElement('section');loadPane.className='qaStepPane loadStepPane';loadPane.id='qaPanel-load';loadPane.dataset.stepPane='load';loadPane.hidden=true;loadPane.setAttribute('role','tabpanel');loadPane.setAttribute('aria-labelledby','qaTab-load');loadPane.append(this.query('.loadCard'));root.append(loadPane);
    const scopeBar=document.createElement('div');scopeBar.className='logDeviceToolbar';
    scopeBar.innerHTML='<label>기기 로그 <select aria-label="로그 기기 범위"><option value="operation">선택 동작</option><option value="active">선택 기기</option><option value="all">연결된 모든 기기</option></select></label><span data-live="logDevice"></span>';
    this.query('[data-log=operation] .logToolbar').before(scopeBar);this.logScope=scopeBar.querySelector('select');this.logScope.value='all';this.logScope.addEventListener('change',()=>void this.refresh());
    this.query('[data-log=operation]').open=true;
    this.get('size').addEventListener('change',()=>this.render(this.snapshot));
    this.nodes('[data-action],[data-scenario]').forEach(button=>button.disabled=true);
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
    this.nodes('[data-scenario]').forEach(button=>button.addEventListener('click',()=>{this.query('.scenarioDetails').open=true;void this.command('scenario',{scenario:button.dataset.scenario});}));
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
  async command(action,extra={}){
    if(action==='start'&&this.launchAll){await this.launchAll();return;}
    const owned=this.owner();if((!owned.allowed&&!['stop','cancel'].includes(action))||this.pending)return;
    const context=this.context;
    this.requestVersion++;
    const data={action,epoch:owned.epoch,...extra};if(action==='run')data.size=Number(this.get('size').value);if(action==='fixture')data.input=this.get('input').value;
    this.pending=true;this.pendingAction=action;if(action==='run'){this.loadTracking=true;if(this.snapshot)this.snapshot={...this.snapshot,load:null};}this.pendingScenario=action==='scenario'?extra.scenario:null;if(this.pendingScenario){this.query('.scenarioDetails').open=true;this.nodes('.scenarioDetails .logDetails').forEach(details=>details.open=false);}this.render(this.snapshot);
    try{await this.api('live/control',data);if(context!==this.context)return;if(action==='start')this.loginNext='email';if(action==='email')this.loginNext='password';if(action==='password')this.loginNext='device';this.get('message').textContent='';await this.refresh();}
    catch(error){if(context===this.context){this.get('message').textContent=error.message;if(action==='run')this.loadTracking=false;}}finally{if(context===this.context){this.pending=false;this.pendingAction=null;this.pendingScenario=null;this.render(this.snapshot);clearTimeout(this.timer);if(this.active)this.timer=setTimeout(()=>void this.refresh(),1000);}}
  }
  openLog(){return this.nodes('.logDetails[open]').find(details=>!details.closest('details:not([open]),[hidden]'));}
  logTarget(){const details=this.openLog();return details?.dataset.log==='operation'?(this.logScope.value==='operation'?this.selected:'device/all'):details?.dataset.log;}
  resetTarget(){this.context++;this.selected=null;this.follow=true;this.pending=false;this.pendingAction=null;this.pendingScenario=null;this.operationsKey=null;this.historyKey=null;this.loginNext='email';this.loadTracking=false;this.get('follow').checked=true;this.logSourceSelections.clear();this.get('message').textContent='';this.render({available:false,phase:'ready',operations:[],history:[]});}
  setActive(active){if(this.active===active)return;this.active=active;clearTimeout(this.timer);if(active)void this.refresh();}
  async refresh(){
    if(!this.active)return;
    const context=this.context,version=++this.requestVersion;
    try{const target=this.logTarget(),snapshot=await this.api('live/status'+(target?'?logs='+encodeURIComponent(target):''));if(context===this.context&&version===this.requestVersion&&this.active)this.render(snapshot);}
    catch{if(context===this.context&&version===this.requestVersion){this.render({available:false,phase:'failed',message:'관측 서비스 연결 대기',operations:[],history:[]});this.nodes('.logDetails[open] .logRecords').forEach(pre=>pre.textContent='로그 연결 대기');}}
    finally{if(context===this.context&&version===this.requestVersion){clearTimeout(this.timer);if(this.active)this.timer=setTimeout(()=>void this.refresh(),500);}}
  }
  render(snapshot){
    if(!snapshot)return;this.snapshot=snapshot;
    const owned=this.owner(),busy=this.pending||!owned.allowed||!snapshot.available;
    const phases={preparing:'QA 서버 준비 중',ready:'로그 수집 전',starting:'앱 실행 중',stopping:'로그 수집 종료 중',observing:'로그 수집 중',running:'서버 검사 중',failed:'시작 실패',stopped:'로그 수집 전'};
    this.get('status').textContent=snapshot.message?'실행 오류':snapshot.selected===false&&snapshot.busy?'다른 기기 사용 중':phases[snapshot.phase]||'대기';this.get('status').dataset.state=snapshot.message?'failed':snapshot.phase;
    if(snapshot.appLog){this.get('status').textContent=snapshot.appLog.state==='unavailable'?'로그 연결 대기':snapshot.appLog.partial?'저장 로그 읽는 중':'로그 수집 중';this.get('status').dataset.state=snapshot.appLog.state==='unavailable'?'waiting':'observing';}
    const step=qaWorkflow(snapshot,owned);
    this.root.closest('.realQa').dataset.next=owned.allowed&&snapshot.mode==='observe'&&(['contents','history','perform','sheet'].includes(this.qaStep)||(this.qaStep==='login'&&this.loginNext==='device'))?'device':'';
    this.nodes('[data-step]').forEach(item=>{const selected=item.dataset.step===this.qaStep;item.setAttribute('aria-selected',String(selected));item.tabIndex=selected?0:-1;item.dataset.state=selected?'current':'waiting';});
    this.nodes('[data-step-pane]').forEach(pane=>pane.hidden=pane.dataset.stepPane!==this.qaStep);
    this.get('results').hidden=false;
    this.query('[data-action=stop]').hidden=snapshot.mode!=='observe'||snapshot.phase!=='observing';

    this.nodes('[data-action],[data-scenario]').forEach(button=>{button.disabled=busy;button.classList.remove('nextAction');});
    this.nodes('[data-scenario],[data-action="start"],[data-action="run"]').forEach(button=>button.disabled=busy||snapshot.busy||snapshot.phase==='preparing');
    this.query('[data-action="stop"]').disabled=this.pending||!snapshot.available||snapshot.mode!=='observe'||snapshot.phase!=='observing';
    this.query('[data-action="cancel"]').disabled=this.pending||!snapshot.available||!['scenario','run'].includes(snapshot.mode);
    this.nodes('[data-action="fixture"],[data-action="email"],[data-action="password"]').forEach(button=>button.disabled=busy||snapshot.mode!=='observe'||snapshot.phase!=='observing'||snapshot.selected===false);
    const next=step==='launch'?'start':step==='login'&&this.loginNext!=='device'?this.loginNext:null;if(next)this.query(`[data-action="${next}"]`).classList.add('nextAction');
    this.get('device').textContent=[snapshot.device?.os,snapshot.device?.appVersion].filter(Boolean).join(' · ')||'—';this.get('api').textContent=snapshot.api||'—';this.get('frame').textContent=time(owned.frameAt);this.get('event').textContent=time(snapshot.lastEventAt);this.get('inputApplied').textContent=snapshot.inputMode==='synthetic'?'다음 녹음: 테스트 소리':'다음 녹음: 기기 마이크';
    const operations=snapshot.operations||[];if(this.follow)this.selected=operations.at(-1)?.id??null;
    const key=JSON.stringify(operations.map(({id,name,startedAt})=>({id,name,startedAt})));
    if(key!==this.operationsKey){this.operationsKey=key;this.get('operations').replaceChildren(...operations.map(operation=>{const option=document.createElement('option');option.value=operation.id;option.textContent=`${time(operation.startedAt)} ${friendlyName(operation.name)}`;return option;}));if(!operations.length){const option=document.createElement('option');option.value='';option.textContent='기록 없음';this.get('operations').append(option);}}
    this.get('operations').value=this.selected??'';this.get('operationCount').textContent=`${operations.length}건`;
    const operation=operations.find(value=>value.id===this.selected),grading=operation?.stages?.some(stage=>stage.name==='upload_accepted');
    const stages=operation?.stages||(this.qaStep==='perform'?['upload_accepted','ai_finished','db_saved','analysis_ready','score_drawn'].map(name=>({name})):[]);
    const visible=grading?stages.filter(stage=>['upload_accepted','ai_finished','db_saved','analysis_ready','score_drawn'].includes(stage.name)):stages;
    this.get('timeline').replaceChildren(...visible.map(stage=>{const item=document.createElement('li');item.dataset.state=stageState(stage);const label=document.createElement('strong');label.textContent=stage.source==='proxy'&&stage.name==='response_finished'?'중계 응답':stageLabels[stage.name]||stage.name;const detail=document.createElement('span');detail.textContent=stage.at?Number.isFinite(stage.elapsedMs)?duration(stage.elapsedMs):Number.isFinite(stage.durationMs)?duration(stage.durationMs):'확인':'—';item.title=stage.at?`${sources[stage.source]||stage.source} · ${time(stage.at)}${stage.status!=null?' · '+stage.status:''}`:'';item.append(label,detail);return item;}));
    this.get('identifiers').textContent=operation?[operation.requestId,operation.timingId,operation.sessionId&&`세션 ${operation.sessionId}`,operation.attempt&&`시도 ${operation.attempt}`,operation.evaluationId&&`평가 ${operation.evaluationId}`].filter(Boolean).join(' · '):'';
    const requests=operation?.requests||[];this.get('httpCount').textContent=`HTTP 요청 ${requests.length}개`;this.get('httpRequests').replaceChildren(...requests.map(request=>{const li=document.createElement('li');li.textContent=[request.target,request.requestId,request.backendReceived?'서버 수신 확인':'서버 수신 미확인',request.status?`HTTP ${request.status}`:'응답 대기',Number.isFinite(request.durationMs)?duration(request.durationMs):null].filter(Boolean).join(' · ');return li;}));
    const scenario=this.pendingScenario?{name:this.pendingScenario,status:'requesting',checks:[]}:snapshot.scenario;
    this.query('.scenarioDetails').hidden=!scenario;
    this.get('scenarioName').textContent=`${scenarioNames[scenario?.name]||'검사'} 결과`;
    this.get('scenarioStatus').textContent=statuses[scenario?.status]||'미실행';this.get('scenarioStatus').dataset.state=scenario?.status||'waiting';
    for(const [name,label]of Object.entries(scenarioNames)){const latest=(snapshot.history||[]).filter(run=>run.name===name).at(-1),badge=this.query(`[data-result="${name}"]`),current=scenario?.name===name;badge.textContent=current?statuses[scenario.status]||scenario.status:latest?`이전 ${statuses[latest.status]||latest.status}`:'미실행';badge.dataset.state=(current?scenario.status:latest?.status)||'waiting';badge.title=latest?`${label} · ${new Date(latest.startedAt).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'})}`:'';this.query(`[data-scenario="${name}"]`).setAttribute('aria-pressed',String(current));}
    this.get('scenarioEvidence').replaceChildren(...(scenario?.checks||[]).map(check=>{const li=document.createElement('li');li.dataset.state=check.status||(check.passed?'pass':'fail');const label=document.createElement('strong');label.textContent=`${statuses[check.status]||(check.passed?'통과':'실패')} · ${check.name}`;li.append(label);if(check.value!==undefined){const value=document.createElement('span');value.textContent=String(check.value);li.append(value);}return li;}));
    const feedback=scenario?.result?.feedback,table=this.get('feedback');table.hidden=!feedback;table.replaceChildren();
    if(feedback){const head=document.createElement('thead'),body=document.createElement('tbody'),header=document.createElement('tr');for(const value of feedback.columns){const th=document.createElement('th');th.textContent=value;header.append(th);}head.append(header);for(const row of feedback.rows){const tr=document.createElement('tr');for(const value of row){const td=document.createElement('td');td.textContent=value;tr.append(td);}body.append(tr);}table.append(head,body);}
    const load=this.pendingAction==='run'?null:this.loadTracking||snapshot.mode==='run'?snapshot.load:null;this.get('loadStatus').textContent=load?.status==='pass'?'제출 완료':statuses[load?.status]||'미실행';this.get('loadStatus').dataset.state=load?.status||'waiting';this.get('success').textContent=load?`${load.success??0} / ${load.total??0}`:'—';this.get('failed').textContent=load?String(load.failed??0):'—';this.get('within5').textContent=load?`${load.within5??0} / ${load.total??0}`:'—';
    const run=this.query('[data-action=run]'),submitting=this.pendingAction==='run'||snapshot.mode==='run',complete=load?.status==='pass'&&load.total===Number(this.get('size').value)&&load.success===load.total;
    run.setAttribute('aria-busy',String(submitting));run.replaceChildren();
    if(submitting){const spinner=document.createElement('span');spinner.className='buttonSpinner';spinner.setAttribute('aria-hidden','true');run.append(spinner,document.createTextNode('제출 중'));run.disabled=true;}
    else run.textContent=complete?'✓ 제출 완료':'동시 제출 시작';
    run.title=complete?'같은 조건으로 다시 제출합니다.':'';
    const slots=load?.results||[],times=slots.filter(slot=>slot.status==='verified'&&Number.isFinite(slot.elapsedMs)).map(slot=>slot.elapsedMs);this.get('loadTiming').textContent=times.length?`${duration(Math.min(...times))} ~ ${duration(Math.max(...times))}`:'';
    if(JSON.stringify(slots)!==this.slotsKey){this.slotsKey=JSON.stringify(slots);this.get('loadTiles').replaceChildren(...slots.slice().sort((a,b)=>a.label-b.label).map(slot=>{const tile=document.createElement('span');tile.dataset.state=slot.status;tile.textContent=`${slot.status==='verified'?'✓':['failed','cancelled'].includes(slot.status)?'×':'…'} ${slot.label}`;tile.title=`제출 ${slot.label} · ${slot.status==='verified'?'저장 확인':statuses[slot.status]||slot.status}${Number.isFinite(slot.elapsedMs)?' · '+duration(slot.elapsedMs):''}`;tile.setAttribute('aria-label',tile.title);tile.setAttribute('role','listitem');return tile;}));}
    const history=snapshot.history||[],historyKey=JSON.stringify(history);
    if(historyKey!==this.historyKey){this.historyKey=historyKey;const expanded=new Set(this.nodes('.liveHistory details[open][data-id],.liveHistory .logDetails[open]').map(details=>details.dataset.id||details.dataset.log));this.get('history').replaceChildren(...history.slice().reverse().map(run=>{const li=document.createElement('li'),details=document.createElement('details'),summary=document.createElement('summary'),result=document.createElement('pre');details.dataset.id=run.id;details.dataset.state=run.status;details.open=expanded.has(run.id);summary.textContent=`${new Date(run.startedAt).toLocaleString('ko-KR',{hour12:false,timeZone:'Asia/Seoul'})} · ${scenarioNames[run.name]||run.name} · ${run.deviceId?'기기 '+run.deviceId+' · ':''}${statuses[run.status]||run.status}`;result.className='liveEvidence';result.textContent=JSON.stringify(run.result,null,2);details.append(summary,result);const holder=document.createElement('div');holder.innerHTML=logView('history');const logs=holder.firstElementChild;logs.dataset.log='history/'+run.id;logs.open=expanded.has(logs.dataset.log);details.append(logs);li.append(details);return li;}));if(!history.length){const li=document.createElement('li');li.className='emptyState';li.textContent='기록 없음';this.get('history').append(li);}}
    this.renderLogs(snapshot);this.onRender?.(snapshot);
  }
  renderLogs(snapshot){
    const details=this.openLog();if(!details)return;const target=this.logTarget(),evidence=snapshot?.evidence,pre=details.querySelector('.logRecords'),count=details.querySelector('.logCount');
    if(!target){pre.textContent='기록 없음';count.textContent='';return;}if(evidence?.id!==target){pre.textContent='불러오는 중';count.textContent='';return;}
    const source=this.logSourceSelections.get(details.dataset.log)||'all';details.querySelectorAll('[data-log-source]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.logSource===source)));
    const context=target+'|'+source;if(details.logContext!==context){details.logContext=context;details.logAnchor=null;}
    const all=(evidence.records||[]).filter(row=>source==='all'||row.source===source),page=logWindow(all,details.logAnchor),records=page.records;
    details.logBefore=page.before;details.querySelector('[data-log-page=previous]').disabled=!page.before;details.querySelector('[data-log-page=latest]').disabled=!page.anchored;
    count.textContent=`${page.end?page.start+1+'–'+page.end:0} / ${all.length}건${evidence.total>evidence.records.length?' · 전체 '+evidence.total+'건':''}${evidence.unavailable?' · '+evidence.unavailable+'대 연결 대기':''}`;
    pre.classList.add('hasDeviceRows');pre.replaceChildren(...records.map(({at,source,deviceId,...fields})=>{const device=this.deviceInfo?.(deviceId),row=document.createElement('div');row.className='deviceLogRow';row.style.setProperty('--device-color',device?.color||'#69734f');row.dataset.level=(Number(fields.status)>=400||fields.status==='FAILED'||fields.outcome==='FAILED')?'failed':'observed';const when=document.createElement('time');when.textContent=time(at);const tag=document.createElement('span');tag.className='deviceTag';tag.textContent=device?`${device.number} ${device.name}`:'서버';const body=document.createElement('span');body.className='logBody';const origin=document.createElement('span');origin.className='sourceChip';origin.textContent=sources[source]||source;const name=document.createElement('strong');name.textContent=fields.kind||'기록';const data=document.createElement('small');data.textContent=JSON.stringify(fields);body.append(origin,name,data);row.append(when,tag,body);return row;}));if(!records.length)pre.textContent='기록 없음';
  }
}
