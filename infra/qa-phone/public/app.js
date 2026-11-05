import {VideoView} from './video-view.js';
import {latestApkUpdate} from './apk-update.js';
const $ = id => document.getElementById(id);
const REFRESH_DELAY_MS = 900;
async function loadDevice() {
  try {
    const response = await fetch('./api/device');
    if (!response.ok) throw new Error('기기 정보 조회 실패');
    const device = await response.json();
    $('deviceName').textContent = device.name;
    $('loginDeviceName').textContent = device.name;
    document.title = `얼쑤 QA · ${device.name}`;
    document.querySelectorAll('[data-device]').forEach(link => {
      if (link.dataset.device === device.id) link.setAttribute('aria-current','page');
      else link.removeAttribute('aria-current');
    });
  } catch {
    // Legacy phone process may still serve this shared UI until its next planned restart.
    const name = location.pathname === '/qa/' ? '휴대폰' : '기기 정보 확인 필요';
    $('deviceName').textContent = name; $('loginDeviceName').textContent = name;
  }
}
void loadDevice();
let inputTail = Promise.resolve(), inputPending = 0, refreshError = '';
let lastStateAt=0,requestTarget=null,deciding=false,hasFrame=false;
let portChanging=false,portEdited=false;
function disableInputs() { document.querySelectorAll('[data-key], #textForm button, #text, #request, #release, #approveRequest, #rejectRequest, #installLatest').forEach(e => e.disabled = true);$('owner').classList.remove('ownerActive'); }
let state = null, seq = 0, offset = 0, gesture = null, running = false, refreshTimer;
const message = text => { $('message').textContent = text; };
const isOwner = () => state?.online && !state.maintenance && state.owner === state.you;
async function api(path, data) {
  const response = await fetch('./api/' + path, { method: data === undefined ? 'GET' : 'POST', headers: data === undefined ? {} : { 'Content-Type': 'application/json' }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
  const result = await response.json();
  if (!response.ok) { if (response.status === 401) showLogin(); throw new Error(result.error); }
  return result;
}
function clearFrame(erase=false) {
  $('screen').hidden = true; if(erase){$('screen').getContext('2d').clearRect(0,0,$('screen').width,$('screen').height);hasFrame=false;} $('offline').hidden = false; gesture = null;
}
function showLogin() {
  buildChecked=0; running = false; video.stop(); clearTimeout(refreshTimer); state = null; clearFrame(true);disableInputs();
  $('workspace').hidden = true; $('login').hidden = false; $('logout').hidden = true; document.body.dataset.role = 'viewer';
}
function render(next) {
  if (state?.epoch !== next.epoch) { seq = 0; gesture = null; }
  seq = Math.max(seq, next.lastSeq || 0);
  state = next;lastStateAt=Date.now(); offset = next.now - Date.now();
  $('portForm').hidden=!next.wireless;
  if(!portEdited&&document.activeElement!==$('wirelessPort'))$('wirelessPort').value=next.wireless?.port||'';
  const portAllowed=!!next.wireless?.available&&!next.maintenance&&(!next.owner||next.owner===next.you)&&!portChanging;
  $('wirelessPort').disabled=$('applyPort').disabled=!portAllowed;
  $('applyPort').textContent=portChanging?'연결 확인 중…':'포트 적용';
  $('applyPort').title=portAllowed?'등록된 기기의 연결을 확인한 뒤 중계를 재시작합니다':'현재 조작자 또는 조작자가 없을 때만 변경할 수 있습니다';
  document.body.dataset.role = isOwner() ? 'owner' : 'viewer';
  const owner = next.peers.find(p => p.id === next.owner);
  $('owner').textContent = owner ? owner.name : '아직 없어요';
  $('owner').title = owner ? owner.name : '현재 조작자가 없습니다';
  $('owner').classList.toggle('ownerActive',!!owner&&next.online&&!next.maintenance&&!next.inputFault);
  $('peerCount').textContent = `${next.peers.length}명`;
  const names = next.peers.map(p => p.name);
  $('peerPreview').textContent = names.slice(0, 2).join(', ') + (names.length > 2 ? ` 외 ${names.length - 2}명` : '');
  $('peerPreview').title = names.join(', ');
  $('connection').textContent = next.inputFault ? '입력 연결 복구 중' : next.online ? '기기 연결됨' : '기기 연결 대기';
  $('connection').dataset.state = next.online ? 'online' : 'offline';
  $('mode').textContent = next.maintenance ? 'APK 적용 중 · 터치는 잠시 멈춥니다' : next.inputFault ? '입력 연결 확인 중 · 복구 후 제어권을 다시 요청해 주세요' : isOwner() ? '클릭 = 터치 · 드래그 = 스와이프' : !next.online ? '기기 연결 후 제어할 수 있어요' : next.requests.includes(next.you) ? '승인을 기다리는 중 · 승인 후 화면을 터치할 수 있어요' : '제어권을 받으면 화면을 터치할 수 있어요';
  $('request').hidden = next.owner === next.you; $('request').disabled = next.maintenance || !next.online || next.requests.includes(next.you);
  const remaining=next.requestDeadline?Math.max(0,Math.ceil((next.requestDeadline-next.now)/1000)):30;
  $('request').textContent = next.requests.includes(next.you) ? next.requests[0]===next.you?`자동 양도까지 ${remaining}초`:'제어 승인 대기 중' : '제어권 요청';
  $('release').hidden = next.owner !== next.you; $('release').disabled = !isOwner();
  document.querySelectorAll('[data-key], #textForm button, #text').forEach(e => e.disabled = !isOwner());
  $('peers').replaceChildren(...next.peers.map(p => { const e = document.createElement('li'); e.textContent = `${p.name}${p.id === next.you ? ' (나)' : ''}${p.id === next.owner ? ' · 조작 중' : ''}`; return e; }));
  requestTarget=isOwner()?next.requests[0]||null:null;
  const requester=next.peers.find(p=>p.id===requestTarget);
  $('requestNotice').hidden=!requester;
  const requestTitle=requester?`${requester.name}님이 제어권을 요청했습니다.`:'';
  if($('requestNoticeTitle').textContent!==requestTitle)$('requestNoticeTitle').textContent=requestTitle;
  $('requestQueueInfo').textContent=`응답이 없으면 ${remaining}초 후 자동 양도됩니다.${next.requests.length>1?` 다음 요청 ${next.requests.length-1}명 대기`:''}`;
  $('approveRequest').disabled=$('rejectRequest').disabled=deciding||!isOwner()||!requester;
  $('requestFeedback').hidden=!next.requestResult||next.requests.includes(next.you)||next.owner===next.you||!next.online;
  $('requestFeedback').textContent=next.requestResult?`${next.requestResult.byName}님이 제어권 요청을 거절했습니다. 다시 요청할 수 있습니다.`:'';
  $('screenAwake').textContent=next.screenAwake?.active?'접속 중 기기 화면 밝기 유지':next.screenAwake?.error?'화면 밝기 유지 재연결 중':next.screenAwake?'화면 밝기 유지 준비 중':'화면 밝기 유지 도구 설정 필요';
  if (next.maintenance) $('connection').textContent = 'APK 적용 중';
  if (!next.online) clearFrame();else if(video.active&&hasFrame){$('screen').hidden=false;$('offline').hidden=true;}
  renderApkUpdate();
}
async function refresh() {
  if (!running) return;
  try {
    const next = await api('state'); if (!running) return; render(next);
    if (next.online && !video.active) {
      const response = await fetch('./api/frame');
      if (!response.ok) { clearFrame(); throw new Error('기기 화면을 다시 연결하고 있습니다.'); }
      const blob = await response.blob();
      if (!running) return;
      const bitmap=await createImageBitmap(blob);
      if(running&&!video.active){hasFrame=true;$('screen').width=bitmap.width;$('screen').height=bitmap.height;$('screen').getContext('2d').drawImage(bitmap,0,0);$('screen').hidden=false;$('offline').hidden=true;}bitmap.close();
      $('frameInfo').textContent = `${next.width} × ${next.height}`;

    }
    if(Date.now()-buildChecked>5000) { await refreshBuilds(); }
    if (refreshError && $('message').textContent === refreshError) message(''); refreshError = '';
  } catch (e) { refreshError = e.message; message(e.message); state = null; clearFrame(); disableInputs(); }
  finally { if (running) refreshTimer = setTimeout(refresh, REFRESH_DELAY_MS); }
}
const video=new VideoView($('screen'),{onAuth:showLogin,onStatus:text=>{$('cadence').textContent=text;$('cadence').title='영상은 최대 30 FPS로 전송하며 표시된 FPS는 이 브라우저에서 실제로 그린 프레임 수입니다. 정지 화면·통신·기기 상태에 따라 달라집니다.';},onFrame:(width,height)=>{if(running){hasFrame=true;$('screen').hidden=false;$('offline').hidden=true;$('frameInfo').textContent=`${width} × ${height}`;}}});
async function begin() { disableInputs(); running = true; video.start(); $('login').hidden = true; $('workspace').hidden = false; $('logout').hidden = false; await refresh(); }
async function act(path, data = {}) { try { await api(path, data); message(''); render(await api('state')); } catch (e) { message(e.message); } }
$('wirelessPort').addEventListener('input',()=>{portEdited=true;});
$('portForm').addEventListener('submit',async event=>{
  event.preventDefault();if(portChanging)return;
  const value=$('wirelessPort').value.trim(),port=Number(value);
  if(!/^\d{1,5}$/.test(value)||port<1||port>65535){message('포트는 1~65535 사이 숫자로 입력하세요.');return;}
  portChanging=true;if(state)render(state);
  try{
    await api('wireless',{port});portEdited=false;showLogin();
    message(`포트 ${port} 연결을 확인했습니다. 중계가 재시작된 뒤 다시 로그인해 주세요.`);
  }catch(error){message(error.message);}finally{portChanging=false;if(state)render(state);}
});
async function input(action, start = state) {
  if (!isOwner() || !start || start.epoch !== state.epoch) return;
  if (inputPending >= 8) { message('입력 처리 중입니다. 잠시 후 다시 입력해 주세요.'); return; }
  const queuedAt = Date.now(), packet = { epoch: start.epoch, seq: ++seq, sentAt: queuedAt + offset, width: start.width, height: start.height, action };
  inputPending++;
  inputTail = inputTail.then(async () => {
    if (!isOwner() || state.epoch !== packet.epoch || Date.now() - queuedAt > 3000) return;
    await act('input', packet);
  }).catch(e => message(e.message)).finally(() => inputPending--);
  await inputTail;
}
$('loginForm').addEventListener('submit', async e => {
  e.preventDefault(); try { await api('login', { name: $('name').value, token: $('token').value }); $('token').value = ''; message(''); await begin(); } catch (e) { message(e.message); }
});
$('logout').addEventListener('click', async () => { try { await api('logout', {}); } finally { showLogin(); } });
$('request').addEventListener('click', () => act('request'));
$('release').addEventListener('click', () => act('release'));
document.querySelectorAll('[data-key]').forEach(b => b.addEventListener('click', () => input({ kind: 'key', key: b.dataset.key })));
$('textForm').addEventListener('submit', async e => { e.preventDefault(); const text = $('text').value; if (!text.length||text.length>128||/[\p{C}]/u.test(text)) { message('제어문자 없이 1~128자로 입력해 주세요.'); return; } await input({ kind: 'text', text }); });
const point = e => { const r = $('screen').getBoundingClientRect(); return { x: Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)) }; };
$('screen').addEventListener('pointerdown', e => { if (!isOwner() || e.button !== 0) return; e.preventDefault(); $('screen').focus({ preventScroll: true }); $('screen').setPointerCapture(e.pointerId); gesture = { ...point(e), at: performance.now(), lease: { ...state } }; });
$('screen').addEventListener('pointerup', e => {
  if (!gesture) return; const start = gesture; gesture = null; const end = point(e);
  const duration = Math.round(Math.min(1000, Math.max(50, performance.now() - start.at)));
  const moved = Math.hypot(start.x - end.x, start.y - end.y) > 0.015;
  input(moved || duration > 400 ? { kind: 'swipe', x: start.x, y: start.y, x2: end.x, y2: end.y, duration } : { kind: 'tap', x: end.x, y: end.y }, start.lease);
});
$('screen').addEventListener('pointercancel', () => { gesture = null; });
$('screen').addEventListener('lostpointercapture', () => { gesture = null; });
const keys = { Enter: 'ENTER', Backspace: 'DEL', ArrowUp: 'DPAD_UP', ArrowDown: 'DPAD_DOWN', ArrowLeft: 'DPAD_LEFT', ArrowRight: 'DPAD_RIGHT', Escape: 'BACK', Tab: 'TAB' };
$('screen').addEventListener('keydown', e => { if (!isOwner() || e.repeat || e.isComposing) return; if (keys[e.key]) { e.preventDefault(); input({ kind: 'key', key: keys[e.key] }); } else if (/^[A-Za-z0-9 @._+,:/=-]$/.test(e.key) && !e.ctrlKey && !e.altKey && !e.metaKey) { e.preventDefault(); input({ kind: 'text', text: e.key }); } });
window.addEventListener('blur', () => { gesture = null; });
document.addEventListener('click', e => {
  document.querySelectorAll('.controlHeader details[open]').forEach(menu => { if (!menu.contains(e.target)) menu.open = false; });
});
document.querySelectorAll('.controlHeader details').forEach(menu => menu.addEventListener('toggle', () => {
  if (menu.open) document.querySelectorAll('.controlHeader details').forEach(other => { if (other !== menu) other.open = false; });
}));
document.addEventListener('keydown', e => {
  const openMenu = document.querySelector('.controlHeader details[open]');
  if (e.key === 'Escape' && openMenu) {
    e.preventDefault(); e.stopPropagation(); openMenu.open = false; openMenu.querySelector('summary').focus(); return;
  }
}, true);
if (window.matchMedia('(max-width: 700px)').matches) {
  document.querySelectorAll('aside details').forEach(e => e.open = false);
}




let builds=[],buildChecked=0,buildCatalog=null,installingLatest=false;
const buildName=build=>build?.mergeRequest?`MR !${build.mergeRequest} - ${build.authorName||'작성자 확인 중'} - ${build.mergeRequestTitle||'제목 확인 중'}`:'MR 정보 없음';
function renderApkUpdate(){
  const fresh=running&&Date.now()-lastStateAt<5000;
  const update=latestApkUpdate(buildCatalog,fresh?state:null,installingLatest);
  $('installLatest').disabled=!update.enabled;$('installLatest').textContent=update.label;
  const job=buildCatalog?.job;
  $('apkUpdateHint').textContent=job?.status==='failed'?`설치 실패: ${job.error} · 연결 상태를 확인한 뒤 다시 시도해 주세요.`:job?.status==='success'&&job.id===buildCatalog.latest?'최신 APK 설치가 완료되었습니다.':update.hint;
}
function selectedBuild(){return builds.find(build=>build.id===$('apkVersion').value);}
function showBuild(){
  const b=selectedBuild();
  $('apkVersion').disabled=!builds.length;
  $('apkMeta').textContent=b?`${buildName(b)} · ${b.versionName} · ${b.commit.slice(0,8)} · ${(b.size/1024/1024).toFixed(1)} MB`:'';
}
async function refreshBuilds(){
  buildChecked=Date.now();
  try{
    const result=await api('builds');if(!running)return;buildCatalog=result;builds=result.builds;
    const selected=$('apkVersion').value;
    const options=builds.map(b=>{const o=document.createElement('option');o.value=b.id;o.textContent=`${buildName(b)} · #${b.build}${b.id===result.latest?' · 최신':''}`;return o;});
    if(!options.length){const o=document.createElement('option');o.value='';o.textContent='아직 보관된 APK가 없습니다';options.push(o);}
    if(document.activeElement!==$('apkVersion')){$('apkVersion').replaceChildren(...options);if(builds.some(b=>b.id===selected))$('apkVersion').value=selected;}
    const installed=result.installed,current=installed&&builds.find(b=>b.package===installed.package&&b.versionCode===installed.versionCode&&b.versionName===installed.versionName);
    $('installedVersion').textContent=installed?`설치됨: ${current?buildName(current):installed.versionName} (${installed.versionCode})`:result.installedStatus==='absent'?'이 기기에 얼쑤 QA가 설치되어 있지 않습니다':result.installedStatus==='installing'?'기기에 앱을 설치하고 있습니다':result.installedStatus==='unavailable'?'설치 정보를 불러오지 못했습니다. 다시 확인 중입니다':'기기의 설치 정보를 확인하고 있습니다';
    $('installedVersion').title=installed?`${installed.package} · ${installed.versionName}`:'';
    $('apkStatus').textContent=result.sync?.error||'목록: 약 5초 · EDO1: 약 1분 간격';showBuild();renderApkUpdate();
  }catch(e){buildCatalog=null;$('apkStatus').textContent=e.message;renderApkUpdate();}
}
$('apkVersion').addEventListener('change',showBuild);
$('installLatest').addEventListener('click',async()=>{
  if(installingLatest||!latestApkUpdate(buildCatalog,state).enabled)return;
  installingLatest=true;renderApkUpdate();
  try{
    const currentState=await api('state'),currentCatalog=await api('builds');
    const update=latestApkUpdate(currentCatalog,currentState);
    buildCatalog=currentCatalog;render(currentState);
    if(!update.enabled)throw Error(update.hint);
    await api('builds/install',{id:update.build.id,epoch:currentState.epoch});
    message('');
    await refreshBuilds();render(await api('state'));
  }catch(e){message(e.message);}
  finally{installingLatest=false;renderApkUpdate();}
});
async function decideRequest(path){
  if(!isOwner()||!requestTarget||deciding)return;
  const target=requestTarget,hadFocus=$('requestNotice').contains(document.activeElement);deciding=true;
  $('approveRequest').disabled=$('rejectRequest').disabled=true;
  try{await api(path,{target});render(await api('state'));message('');}
  catch(e){message(e.message);}
  finally{deciding=false;if(state)render(state);if(hadFocus&&$('requestNotice').hidden)($('request').hidden?$('release'):$('request')).focus();}
}
$('approveRequest').addEventListener('click',()=>decideRequest('transfer'));
$('rejectRequest').addEventListener('click',()=>decideRequest('reject'));
setInterval(()=>{if(!running||Date.now()-lastStateAt>5000){$('owner').classList.remove('ownerActive');$('installLatest').disabled=true;}},1000);

fetch('./api/state').then(response=>{if(!running)return response.ok?begin():showLogin();}).catch(()=>{if(!running)showLogin();});
