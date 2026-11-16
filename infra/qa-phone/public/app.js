import {DeviceSession,registeredDevices} from './device-session.js';
import {latestApkUpdate,apkVersionState} from './apk-update.js';
import {LivePanel} from './live-panel.js';
import {setupDashboardSplitter} from './dashboard-splitter.js';

const $=id=>document.getElementById(id);
const sessions=[];
let activeId=null,running=false,displayName=null,dialogTarget=null,dialogOpener=null,requestTarget=null;
let batchBusy=false,pairingBusy=false,portBusy=false,deciding=false,lastActivity=-Infinity;
let batchFeedback='',launchFeedback='',paintFrame=null,gridKey='',backgroundTimer;
const pairingRegistered=new Set();
const active=()=>sessions.find(device=>device.id===activeId);
const connected=()=>sessions.filter(device=>device.connected||device.authorized&&device.state?.maintenance&&Date.now()-device.stateAt<3000);
const message=value=>{$('message').textContent=value;};
const node=(tag,className,text)=>{const element=document.createElement(tag);if(className)element.className=className;if(text!==undefined)element.textContent=text;return element;};
function schedulePaint(){if(!paintFrame)paintFrame=requestAnimationFrame(()=>{paintFrame=null;paint();});}
function fitGrid(){
  const grid=$('deviceGrid'),count=connected().length;
  if(!count)return;
  const cols=Math.max(1,Math.min(count,count<=4?2:3,Math.floor(grid.clientWidth/135)||1)),rows=Math.ceil(count/cols),gap=6;
  const width=Math.max(0,Math.min((grid.clientWidth-gap*(cols-1))/cols,(grid.clientHeight-gap*(rows-1))/rows*.60));
  grid.style.setProperty('--cols',cols);grid.style.setProperty('--rows',rows);grid.style.setProperty('--tile-width',width+'px');grid.style.setProperty('--tile-height',width/.60+'px');
}
function selectDevice(id){
  if(activeId===id)return;activeId=id;livePanel.resetTarget();schedulePaint();if(running)void livePanel.refresh();
}
function addTile(device){
  const tile=node('section','deviceTile');tile.dataset.deviceId=device.id;tile.style.setProperty('--device-color',device.color);tile.tabIndex=0;tile.setAttribute('aria-label',device.name+' 제어·로그 대상으로 선택');
  const heading=node('div','tileHeading'),name=node('span','tileName'),title=node('strong','',device.name),dot=node('span','tileConnectionDot');
  dot.setAttribute('role','img');name.append(title,dot);
  const settings=node('button','tileSettings','⋯');settings.type='button';settings.dataset.settings=device.id;settings.setAttribute('aria-label',device.name+' 연결 설정');settings.setAttribute('aria-haspopup','dialog');
  settings.addEventListener('click',event=>{event.stopPropagation();openConnection(device,settings);});
  heading.append(node('span','tileId',device.number),name,settings);
  const meta=node('div','tileMeta'),cadence=node('span','tileVideo'),resolution=node('span','tileResolution');meta.append(cadence,resolution);
  const viewport=node('div','deviceViewport'),offline=node('span','tileOffline','영상 연결 중');device.canvas.setAttribute('aria-label',device.name+' 화면');device.canvas.setAttribute('role','img');device.canvas.tabIndex=0;viewport.append(device.canvas,offline);
  const footer=node('div','tileFooter'),owner=node('span','tileOwner'),selected=node('span');footer.append(owner,selected);tile.append(heading,meta,viewport,footer);
  tile.addEventListener('click',()=>selectDevice(device.id));tile.addEventListener('keydown',event=>{if(event.target===tile&&['Enter',' '].includes(event.key)){event.preventDefault();selectDevice(device.id);}});
  Object.assign(device,{tile,dot,cadenceNode:cadence,resolutionNode:resolution,offlineNode:offline,ownerNode:owner,selectedNode:selected});
  const point=event=>{const rect=device.canvas.getBoundingClientRect();return{x:Math.min(1,Math.max(0,(event.clientX-rect.left)/rect.width)),y:Math.min(1,Math.max(0,(event.clientY-rect.top)/rect.height))};};
  device.canvas.addEventListener('pointerdown',event=>{
    selectDevice(device.id);if(!device.inputReady||event.button!==0)return;
    event.preventDefault();device.canvas.focus({preventScroll:true});device.canvas.setPointerCapture(event.pointerId);device.gesture={...point(event),at:performance.now(),lease:{...device.state}};
  });
  device.canvas.addEventListener('pointerup',event=>{
    const start=device.gesture;device.gesture=null;if(!start)return;
    const end=point(event),duration=Math.round(Math.min(1000,Math.max(50,performance.now()-start.at))),moved=Math.hypot(start.x-end.x,start.y-end.y)>.015;
    void device.input(moved||duration>400?{kind:'swipe',x:start.x,y:start.y,x2:end.x,y2:end.y,duration}:{kind:'tap',x:end.x,y:end.y},start.lease);
  });
  for(const event of ['pointercancel','lostpointercapture'])device.canvas.addEventListener(event,()=>device.gesture=null);
  const keys={Enter:'ENTER',Backspace:'DEL',ArrowUp:'DPAD_UP',ArrowDown:'DPAD_DOWN',ArrowLeft:'DPAD_LEFT',ArrowRight:'DPAD_RIGHT',Escape:'BACK',Tab:'TAB'};
  device.canvas.addEventListener('keydown',event=>{
    if(!device.inputReady||event.repeat||event.isComposing)return;
    if(keys[event.key]){event.preventDefault();void device.input({kind:'key',key:keys[event.key]});}
    else if(/^[A-Za-z0-9 @._+,:/=-]$/.test(event.key)&&!event.ctrlKey&&!event.altKey&&!event.metaKey){event.preventDefault();void device.input({kind:'text',text:event.key});}
  });
}
const measure=document.createElement('canvas').getContext('2d');
function paintPeers(){
  const names=[...new Set(sessions.flatMap(device=>device.authorized&&device.state?device.state.peers.map(peer=>peer.name):[]))],summary=$('peerPreview').parentElement;
  const preview=$('peerPreview');measure.font=getComputedStyle(preview).font;
  const gap=parseFloat(getComputedStyle(summary).columnGap)||0,available=summary.clientWidth-[...summary.children].filter(child=>child!==preview).reduce((sum,child)=>sum+child.getBoundingClientRect().width,0)-gap*(summary.children.length-1);
  let text=names.join(' · ');
  if(measure.measureText(text).width>available){text='외 '+names.length+'명';for(let count=names.length-1;count>=1;count--){const candidate=names.slice(0,count).join(' · ')+' 외 '+(names.length-count)+'명';if(measure.measureText(candidate).width<=available){text=candidate;break;}}}
  preview.textContent=text;preview.title=names.join(', ');$('peerCount').textContent=names.length+'명';$('peers').replaceChildren(...names.map(name=>node('li','',name)));
}
function pendingBuild(device){
  const result=apkVersionState(device.catalog);return result.status==='outdated'?result.build:null;
}
function paintBatch(){
  const targets=connected(),pending=targets.filter(pendingBuild),serverBusy=sessions.some(device=>['scenario','run'].includes(device.liveSnapshot?.mode)),observing=sessions.some(device=>device.liveSnapshot?.mode==='observe');
  $('applyLatestBatch').disabled=batchBusy||serverBusy||observing||!pending.length;
  $('applyLatestBatch').textContent=batchBusy&&batchKind==='apk'?'APK 적용 중':'최신 APK 적용';
  const unknown=targets.filter(device=>apkVersionState(device.catalog).status==='unavailable'),checking=targets.filter(device=>apkVersionState(device.catalog).status==='checking');
  $('batchApkStatus').textContent=batchFeedback||(!targets.length?'연결 기기 없음':[pending.length?pending.map(device=>device.number).join('·')+(observing?' 로그 수집 종료 후 적용':' 적용 대기'):'',checking.length?checking.map(device=>device.number).join('·')+' 버전 확인 중':'',unknown.length?unknown.map(device=>device.number).join('·')+' 버전 확인 불가':''].filter(Boolean).join(' · ')||'모든 기기 최신');
  const launch=livePanel.query('[data-action=start]');launch.disabled=batchBusy||serverBusy||!targets.length;
  launch.textContent=batchBusy&&batchKind==='launch'?'앱 실행 중':'QA 앱 실행';$('launchAllStatus').textContent=launchFeedback;
  if(batchBusy)livePanel.nodes('[data-action],[data-scenario]').forEach(button=>button.disabled=true);
}
function paintScenarioBands(){
  for(const name of ['feedback','retry','duplicate','ownership']){
    const button=livePanel.query('[data-scenario="'+name+'"]');let rail=button.querySelector('.scenarioPassBands'),badges=button.querySelector('.scenarioDeviceBadges');
    if(!rail){rail=node('span','scenarioPassBands');rail.setAttribute('aria-hidden','true');badges=node('span','scenarioDeviceBadges');button.prepend(rail);button.append(badges);}
    rail.replaceChildren();badges.replaceChildren();
    for(const device of sessions){
      const snapshot=device.liveSnapshot,history=(snapshot?.history||[]).filter(run=>run.name===name),result=device.id===activeId&&livePanel.pendingScenario===name?{status:'running'}:snapshot?.scenario?.name===name?snapshot.scenario:history.at(-1);
      if(!result)continue;
      if(result.status==='pass'){const stripe=node('i');stripe.style.background=device.color;rail.append(stripe);}
      const badge=node('span','scenarioDeviceBadge',device.number+' '+({pass:'통과',fail:'실패',failed:'실패',running:'진행 중',cancelled:'중단'}[result.status]||result.status));badge.dataset.state=result.status;badge.style.setProperty('--device-color',device.color);badge.title=device.name+' · 서버 API 검사';badges.append(badge);
    }
    button.querySelector('[data-result]').hidden=badges.childElementCount>0;button.style.setProperty('--pass-band-width',rail.childElementCount*4+'px');
  }
}
function paint(){
  const list=connected();
  if(!sessions.some(device=>device.id===activeId)||list.length&&!list.some(device=>device.id===activeId))selectDevice(list[0]?.id||sessions[0]?.id);
  const key=list.map(device=>device.id).join(',');
  if(gridKey!==key||!$('deviceGrid').childElementCount){
    gridKey=key;$('deviceGrid').dataset.count=String(list.length);
    if(list.length)$('deviceGrid').replaceChildren(...list.map(device=>device.tile));
    else{const empty=node('div','gridEmptyState');empty.setAttribute('role','status');const action=node('button','secondary','연결 설정');action.addEventListener('click',()=>{$('pairingDetails').open=true;$('knownConnectionDevice').focus();});empty.append(node('strong','','연결된 기기 없음'),action);$('deviceGrid').replaceChildren(empty);}
    fitGrid();
  }
  for(const device of list){
    const selected=device.id===activeId;device.tile.dataset.active=String(selected);device.tile.setAttribute('aria-current',String(selected));
    device.dot.dataset.state=device.connected?'connected':'disconnected';device.dot.title=device.connected?'기기 연결됨':'기기 연결 끊김';device.dot.setAttribute('aria-label',device.dot.title);
    device.cadenceNode.textContent=device.state?.maintenance?'APK 적용 중':device.cadence||'영상 연결 중';
    device.resolutionNode.textContent=device.frameWidth?device.frameWidth+' × '+device.frameHeight:'';
    device.canvas.hidden=!device.hasFrame||!device.connected;device.offlineNode.hidden=!device.canvas.hidden;
    const owner=device.state?.peers.find(peer=>peer.id===device.state.owner);device.ownerNode.textContent=owner?'조작 '+owner.name:'제어권 없음';device.selectedNode.textContent=selected?'선택됨':'';
  }
  const device=active(),state=device?.state,owned=device?.owned;
  document.body.dataset.role=owned?'owner':'viewer';$('owner').textContent=state?.peers.find(peer=>peer.id===state.owner)?.name||'아직 없어요';$('owner').classList.toggle('ownerActive',!!state?.owner&&!!device?.connected&&!state.inputFault&&!state.maintenance);
  $('request').hidden=!!owned;$('request').disabled=!device?.connected||!!state?.maintenance||!!state?.requests.includes(state.you);
  $('request').textContent=state?.requests.includes(state.you)?'제어 승인 대기 중':'제어권 요청';
  $('release').hidden=!owned;$('release').disabled=!owned;
  document.querySelectorAll('[data-key],#textForm button,#text').forEach(element=>element.disabled=!device?.inputReady);
  $('mode').textContent=device?.inputReady?'기기 화면 조작 가능':'제어권 또는 화면 연결 대기';
  const label=livePanel.query('.activeDeviceLabel');if(label){label.textContent=device?device.number+' '+device.name:'기기 없음';label.style.color=device?.color||'';}
  paintPeers();paintRequests();paintPairing();paintBatch();
  const prompts=sessions.filter(value=>value.authorized&&value.state?.idlePromptMs!=null).map(value=>value.state.idlePromptMs);
  if(prompts.length&&running){$('idleCountdown').textContent='연결 해제까지 '+Math.ceil(Math.min(...prompts)/1000)+'초';if(!$('idlePrompt').open)$('idlePrompt').showModal();}
  else if($('idlePrompt').open)$('idlePrompt').close();
}
function paintRequests(){
  const device=sessions.find(value=>value.owned&&value.state.requests.length),state=device?.state,requester=state?.peers.find(peer=>peer.id===state.requests[0]);
  requestTarget=requester?{device,id:requester.id}:null;$('requestNotice').hidden=!requester;
  $('requestNoticeTitle').textContent=requester?device.name+' · '+requester.name+'님이 제어권을 요청했습니다.':'';
  const remaining=state?.requestDeadline?Math.max(0,Math.ceil((state.requestDeadline-state.now)/1000)):30;
  $('requestQueueInfo').textContent=requester?remaining+'초 후 자동 양도':'';$('approveRequest').disabled=$('rejectRequest').disabled=deciding||!requester;
  const selected=active()?.state;$('requestFeedback').hidden=!selected?.requestResult||selected.requests.includes(selected.you)||selected.owner===selected.you;
  $('requestFeedback').textContent=selected?.requestResult?selected.requestResult.byName+'님이 제어권 요청을 거절했습니다.':'';
}
async function deviceApi(path,data){
  const device=active();if(!device)throw Error('기기 연결 대기');
  if(path==='live/control')device.invalidateLive();
  const result=await(path.startsWith('live/status')?device.liveStatus(path):device.api(path,data));
  if(path.startsWith('live/status')){
    if(result.evidence)result.evidence={...result.evidence,records:result.evidence.records.map(record=>({...record,deviceId:device.id}))};
    if(path.includes('logs=device%2Fall')&&livePanel.logScope.value==='all'){
      const others=await Promise.all(connected().filter(value=>value!==device).map(async value=>{try{const snapshot=await value.api('live/status?logs=device%2Fall');return{...snapshot.evidence,records:(snapshot.evidence?.records||[]).map(record=>({...record,deviceId:value.id}))};}catch{return{total:0,records:[],unavailable:true};}}));
      const rows=[result.evidence,...others].filter(Boolean);
      result.evidence={id:'device/all',total:rows.reduce((sum,evidence)=>sum+evidence.total,0),records:rows.flatMap(evidence=>evidence.records).sort((a,b)=>Date.parse(a.at)-Date.parse(b.at)),unavailable:rows.filter(evidence=>evidence.unavailable).length};
    }
    if(active()===device)livePanel.get('logDevice').textContent=livePanel.logScope.value==='all'?connected().length+'대':device.name;
  }
  return result;
}
const livePanel=new LivePanel($('qaLive'),{api:deviceApi,notify:message,automationRoot:$('qaAutomation'),owner:()=>{const device=active();return{connected:!!device?.connected,allowed:!!device?.inputReady,epoch:device?.state?.epoch,frameAt:Number(device?.canvas.dataset.videoAt)||device?.state?.frameAt};}});
livePanel.get('results').querySelector('h3').append(node('span','activeDeviceLabel'));
livePanel.deviceInfo=id=>sessions.find(device=>device.id===id);
livePanel.onRender=()=>{paintScenarioBands();paintBatch();};
let batchKind=null;
async function requireControl(device){
  await device.readState();if(device.owned)return;
  await device.act('request');if(!device.owned)throw Error('제어권 승인 대기');
}
async function waitFor(device,read,ready,deadlineMs){
  const deadline=Date.now()+deadlineMs;
  while(running&&Date.now()<deadline){const value=await read();if(ready(value))return value;await new Promise(resolve=>setTimeout(resolve,1000));}
  throw Error('완료 확인 시간 초과 · 기기 상태를 확인하세요.');
}
async function applyLatestAll(){
  if($('applyLatestBatch').disabled)return;
  const targets=connected().filter(pendingBuild);batchBusy=true;batchKind='apk';batchFeedback=targets.map(device=>device.number).join('·')+' 적용 중';paintBatch();
  const results=[];
  try{for(const device of targets){try{
    await requireControl(device);await device.refreshBuilds();const update=latestApkUpdate(device.catalog,device.state);
    const version=apkVersionState(device.catalog);
    if(version.status==='latest'){results.push(device.number+' 최신');continue;}
    if(version.status!=='outdated')throw Error('설치 버전 확인 불가 · 적용 보류');
    if(!update.enabled)throw Error(update.hint);
    await device.api('builds/install',{id:update.build.id,epoch:device.state.epoch});
    await waitFor(device,async()=>{await device.refreshBuilds();return device.catalog;},catalog=>{if(catalog?.job?.status==='failed')throw Error('APK 설치 실패');return catalog?.installed?.versionCode===update.build.versionCode&&catalog?.installed?.versionName===update.build.versionName&&catalog?.installed?.package===update.build.package;},180000);
    results.push(device.number+' 적용 완료');
  }catch(error){results.push(device.number+' '+error.message);}batchFeedback=results.join(' · ');schedulePaint();}}
  finally{batchBusy=false;batchKind=null;schedulePaint();}
}
livePanel.launchAll=async()=>{
  if(batchBusy||!connected().length)return;
  const targets=connected().slice();batchBusy=true;batchKind='launch';launchFeedback=targets.map(device=>device.number).join('·')+' 실행 중';paintBatch();
  const results=await Promise.all(targets.map(async device=>{try{
    await requireControl(device);await device.refreshBuilds();
    if(device.catalog?.installed&&!device.catalog.installed.versionName.endsWith('-qa283-live')){
      await device.api('app/launch',{epoch:device.state.epoch});return device.number+' 실행 완료';
    }
    await device.api('live/control',{action:'start',epoch:device.state.epoch});
    await waitFor(device,()=>device.api('live/status'),snapshot=>{device.liveSnapshot=snapshot;if(snapshot.message)throw Error(snapshot.message);return snapshot.selected!==false&&snapshot.mode==='observe'&&snapshot.phase==='observing';},180000);
    return device.number+' 실행 완료';
  }catch(error){return device.number+' '+error.message;}}));
  batchBusy=false;batchKind=null;launchFeedback=results.join(' · ');livePanel.follow=true;livePanel.get('follow').checked=true;await livePanel.refresh();schedulePaint();
};
$('applyLatestBatch').addEventListener('click',()=>void applyLatestAll());

function openConnection(device,opener){
  dialogTarget=device;dialogOpener=opener;$('connectionSettingsTitle').textContent=device.name+' · 연결 설정';$('connectionSettingsStatus').textContent='';$('wirelessPort').value=device.state?.wireless?.port||'';$('connectionDialog').showModal();paintPairing();$('wirelessPort').focus();$('wirelessPort').select();
}
$('closeConnectionSettings').addEventListener('click',()=>$('connectionDialog').close());
$('connectionDialog').addEventListener('close',()=>{const opener=dialogOpener;dialogTarget=null;dialogOpener=null;if(opener?.isConnected)opener.focus();else $('openKnownConnectionSettings').focus();});
$('openKnownConnectionSettings').addEventListener('click',()=>{const device=sessions.find(value=>value.id===$('knownConnectionDevice').value);if(device)openConnection(device,$('openKnownConnectionSettings'));});
function pairingDevice(){return sessions.find(device=>device.id===$('knownConnectionDevice').value);}
function paintPairing(){
  const device=dialogTarget||pairingDevice(),state=device?.state,allowed=device?.authorized&&state?.wireless?.available&&!state.maintenance&&(!state.owner||state.owner===state.you);
  $('portForm').hidden=!state?.wireless;$('wirelessPort').disabled=$('applyPort').disabled=portBusy||!allowed;$('applyPort').textContent=portBusy?'연결 확인 중…':'포트 적용';
  const pair=pairingDevice();$('pairingCard').hidden=false;$('pairDevice').disabled=pairingBusy||!pair?.authorized||!pair.state?.wireless?.pairing||!!pair.state?.maintenance||!!(pair.state?.owner&&pair.state.owner!==pair.state.you);
  if(pair&&pairingRegistered.has(pair.id)&&pair.connected){$('pairingStatus').textContent=pair.name+' 연결 완료';pairingRegistered.delete(pair.id);$('pairingConnectPort').hidden=true;}
}
$('knownConnectionDevice').addEventListener('change',()=>{$('pairingForm').reset();$('pairingStatus').textContent='';paintPairing();});
$('portForm').addEventListener('submit',async event=>{
  event.preventDefault();const device=dialogTarget,port=Number($('wirelessPort').value);if(!device||portBusy)return;
  if(!/^\d{1,5}$/.test($('wirelessPort').value)||port<1||port>65535){$('connectionSettingsStatus').textContent='포트는 1~65535로 입력하세요.';return;}
  portBusy=true;paintPairing();
  try{await device.api('wireless',{port});$('connectionSettingsStatus').textContent='연결 확인 완료 · 화면 복구 중';}catch(error){$('connectionSettingsStatus').textContent=error.message;}finally{portBusy=false;paintPairing();}
});
$('pairingForm').addEventListener('submit',async event=>{
  event.preventDefault();const device=pairingDevice();if(!device||$('pairDevice').disabled)return;
  const data={port:Number($('pairingPort').value),code:$('pairingCode').value};$('pairingForm').reset();pairingBusy=true;$('pairingStatus').textContent='페어링 확인 중';paintPairing();
  try{await device.api('wireless/pair',data);pairingRegistered.add(device.id);$('pairingStatus').textContent='PC 페어링 완료 · 화면 연결 중';$('pairingConnectPort').hidden=false;}catch(error){$('pairingStatus').textContent=error.message;}finally{pairingBusy=false;paintPairing();}
});
$('pairingConnectPort').addEventListener('click',()=>{const device=pairingDevice();if(device)openConnection(device,$('pairingConnectPort'));});
$('pairingDetails').addEventListener('toggle',()=>{if(!$('pairingDetails').open)$('pairingForm').reset();});

async function actSelected(path,data){const device=active();if(!device)return;try{await device.act(path,data);message('');}catch(error){message(error.message);}}
$('request').addEventListener('click',()=>void actSelected('request'));
$('release').addEventListener('click',()=>void actSelected('release'));
for(const [id,path]of [['approveRequest','transfer'],['rejectRequest','reject']])$(id).addEventListener('click',async()=>{const target=requestTarget;if(!target||deciding)return;deciding=true;paintRequests();try{await target.device.act(path,{target:target.id});message('');}catch(error){message(error.message);}finally{deciding=false;schedulePaint();if($('requestNotice').hidden)($('request').hidden?$('release'):$('request')).focus();}});
document.querySelectorAll('[data-key]').forEach(button=>button.addEventListener('click',()=>void active()?.input({kind:'key',key:button.dataset.key})));
$('textForm').addEventListener('submit',async event=>{event.preventDefault();const text=$('text').value;if(!text.length||text.length>128||/[\p{C}]/u.test(text)){message('제어문자 없이 1~128자로 입력하세요.');return;}await active()?.input({kind:'text',text});});
window.addEventListener('blur',()=>sessions.forEach(device=>device.gesture=null));

async function reportActivity(confirm=false){
  if(!running)return;
  await Promise.all(sessions.filter(device=>device.authorized).map(device=>device.api('activity',confirm?{confirm:true}:{}).catch(()=>{})));
}
for(const type of ['pointerdown','click','keydown','wheel'])document.addEventListener(type,event=>{if(!event.isTrusted||event.repeat||$('idlePrompt').open||performance.now()-lastActivity<1000)return;lastActivity=performance.now();void reportActivity();},{capture:true,passive:true});
$('idlePrompt').addEventListener('cancel',event=>event.preventDefault());
$('continueSession').addEventListener('click',async()=>{$('continueSession').disabled=true;try{await reportActivity(true);$('idlePrompt').close();for(const device of sessions)if(device.state)device.state.idlePromptMs=null;}finally{$('continueSession').disabled=false;}});
async function disconnect(text=''){
  running=false;clearTimeout(backgroundTimer);livePanel.setActive(false);await Promise.allSettled(sessions.map(device=>device.logout()));displayName=null;
  $('workspace').hidden=true;$('login').hidden=false;$('logout').hidden=true;document.body.classList.remove('workspaceOpen');$('pairingForm').reset();$('pairingDetails').open=false;if($('connectionDialog').open)$('connectionDialog').close();if($('idlePrompt').open)$('idlePrompt').close();message(text);
}
$('logout').addEventListener('click',()=>void disconnect());
function begin(name){displayName=name;running=true;$('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;document.body.classList.add('workspaceOpen');for(const device of sessions)device.start(name);livePanel.setActive(true);schedulePaint();void pollBackground();}
$('loginForm').addEventListener('submit',async event=>{
  event.preventDefault();const name=$('name').value.trim(),button=$('loginForm').querySelector('button');button.disabled=true;
  try{const results=await Promise.allSettled(sessions.map(device=>device.join(name)));if(!results.some(result=>result.status==='fulfilled'))throw Error('기기 중계를 연결하지 못했습니다. 잠시 후 다시 연결하세요.');message('');begin(name);}catch(error){message(error.message);}finally{button.disabled=false;}
});
async function pollBackground(){
  if(!running)return;
  await Promise.all(sessions.filter(device=>device.id!==activeId&&device.authorized).map(device=>device.liveStatus().catch(()=>{})));
  paintScenarioBands();if(running)backgroundTimer=setTimeout(()=>void pollBackground(),1500);
}
document.querySelectorAll('.brand').forEach(link=>link.addEventListener('click',event=>{if(running){event.preventDefault();const first=connected()[0];if(first)selectDevice(first.id);}}));
document.addEventListener('click',event=>document.querySelectorAll('.controlHeader details[open]').forEach(menu=>{if(!menu.contains(event.target))menu.open=false;}));
document.addEventListener('keydown',event=>{const open=document.querySelector('.controlHeader details[open]');if(event.key==='Escape'&&open){event.preventDefault();open.open=false;open.querySelector('summary').focus();}},true);
new ResizeObserver(paintPeers).observe($('peerPreview').parentElement);new ResizeObserver(fitGrid).observe($('deviceGrid'));document.fonts.ready.then(schedulePaint);
setupDashboardSplitter();
async function initialize(){
  try{
    const response=await fetch('./devices.json',{cache:'no-store'});if(!response.ok)throw Error('기기 목록을 불러오지 못했습니다.');
    for(const descriptor of registeredDevices(await response.json())){
      const device=new DeviceSession(descriptor,{canvas:node('canvas'),onChange:schedulePaint,onMessage:message,onExpired:()=>{if(running)void disconnect('연결이 종료됐습니다. 이름을 입력해 다시 연결하세요.');}});sessions.push(device);addTile(device);
      const option=node('option','',device.number+' '+device.name);option.value=device.id;$('knownConnectionDevice').append(option);
    }
    activeId=sessions[0]?.id;const restored=await Promise.allSettled(sessions.map(device=>device.readState({rejoin:false})));
    const joined=sessions.find(device=>device.authorized);if(joined)begin(joined.sessionName);else schedulePaint();
  }catch(error){message(error.message);}
}
setInterval(()=>{if(running)schedulePaint();},1000);
void initialize();
