'use strict';
const $=id=>document.getElementById(id);
let state,dirty=false,editing=false;
async function invoke(work,success) {
 try{const result=await work();if(!result.ok)throw new Error(result.error);if(success)success(result.value);return result.value;}
 catch(e){$('message').textContent=e.message;return null;}
}
function form(config) {
 $('project').value=config.projectPath;$('host').value=config.sshHost;$('auto').checked=config.autoDownload;$('startup').checked=config.startAtLogin;$('runtime').value=config.cocoRuntime;$('scope').value=config.cocoScope;
}
function render(value) {
 state=value;
 const qa=value.tab==='qa';$('updates-panel').hidden=qa;$('qa-panel').hidden=!qa;$('tab-updates').setAttribute('aria-selected',String(!qa));$('tab-qa').setAttribute('aria-selected',String(qa));$('qa-state').textContent=value.qaError||'QA 페이지를 연결합니다.';
 const files=value.apps||{};for(const [id,key] of [['phone-apk','phone'],['tablet-apk','tablet'],['iphone-ipa','iphone']]){$(id).disabled=!!files.busy||!files[key];$(id).title=files[key]?files[key].builtAt+' · '+(files[key].commit||'').slice(0,8)+(key!=='iphone'?' · 폰·태블릿 공용 APK':''):'게시된 파일 없음';}$('apps-message').textContent=files.message||files.error||'';
 if(!dirty&&!editing)form(value.config);
 const g=value.graph,c=value.coco;
 const names={setup:'프로젝트 연결 필요',available:'새 버전',current:'최신 버전',error:'확인 필요','code-behind':'코드 갱신 필요'};
 $('graph-badge').textContent=names[g.phase]||'확인 중';$('graph-badge').className='badge '+(g.phase==='available'?'ready':g.phase==='error'||g.phase==='code-behind'?'warning':'');
 $('graph-status').textContent=g.error||(g.phase==='code-behind'?'프로젝트 코드를 먼저 갱신해주세요.':'');
 $('graph-detail').textContent=g.source_commit?'develop '+g.source_commit.slice(0,8)+(g.archive_size?' · 다운로드 '+(g.archive_size/1024/1024).toFixed(2)+' MB':''):'';
 $('download').disabled=value.busyGraph||!g.compatible||g.installed;
 $('download').textContent=value.busyGraph?'받는 중…':'다운로드';
 $('graph-time').textContent=g.lastChecked?new Date(g.lastChecked).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'})+' 확인':'';
 $('coco-badge').textContent=value.busyCoco?'설치 중':c.phase==='error'?'조회 확인 필요':c.latest?'배포 '+c.latest:'확인 중';
 $('coco-status').textContent=c.error&&c.phase==='error'?c.error:c.latest?'새 버전을 선택해 설치하거나 업데이트할 수 있습니다.':'npm 배포 목록을 확인합니다.';
 const select=$('version'),selected=value.focusCocoVersion||select.value;
 if(JSON.stringify([...select.options].map(o=>o.value))!==JSON.stringify(c.versions||[])) {
  select.replaceChildren(...(c.versions||[]).map(v=>{const o=document.createElement('option');o.value=v;o.textContent=v+(v.includes('-')?' · 프리릴리스':'');return o;}));
 }
 if((c.versions||[]).includes(selected))select.value=selected;
 $('install').disabled=value.busyCoco||!select.value;$('install').textContent=value.busyCoco?'설치 중…':'설치·업데이트';
 $('coco-result').textContent=c.installed?'최근 완료: '+c.installed.version+' · '+c.installed.runtime+' · '+(c.installed.scope==='global'?'개인 전역':'프로젝트')+' — 새 AI 세션에서 확인하세요.':'';
 $('coco-error').textContent=c.installError||c.error||'';
 for(const id of ['save','choose','host','auto','startup','runtime','scope'])$(id).disabled=value.busyCoco||value.busyGraph;
 $('startup-detail').textContent=value.native?.packaged?'OS 로그인 실행: '+(value.native.startupEnabled?'켜짐':'꺼짐')+' · 설정 저장 시 적용됩니다.':'배포 앱에서 설정을 저장하면 로그인 실행이 적용됩니다.';
 $('notification-error').textContent=value.notificationError||'';
 if(value.focusCocoVersion)$('coco-title').scrollIntoView({behavior:'smooth',block:'center'});
}
for(const id of ['host','auto','startup','runtime','scope'])$(id).addEventListener('change',()=>{dirty=true;});
$('choose').onclick=()=>invoke(()=>window.updates.chooseProject(),p=>{if(p){$('project').value=p;dirty=true;}});
$('save').onclick=()=>{editing=true;invoke(()=>window.updates.save({projectPath:$('project').value,sshHost:$('host').value,autoDownload:$('auto').checked,startAtLogin:$('startup').checked,cocoRuntime:$('runtime').value,cocoScope:$('scope').value}),v=>{dirty=false;editing=false;render(v);$('message').textContent='설정을 저장했습니다.';}).finally(()=>editing=false);};
$('check').onclick=async()=>{$('check').disabled=true;$('message').textContent='새 배포를 확인하고 있습니다…';await invoke(()=>window.updates.check(),render);$('check').disabled=false;$('message').textContent='확인을 마쳤습니다. 약 1분마다 다시 확인합니다.';};
$('download').onclick=()=>invoke(()=>window.updates.download(),()=>{$('message').textContent='다운로드를 마쳤습니다.';});
$('install').onclick=async()=>{
 if(dirty){$('message').textContent='설치 대상·위치를 바꿨다면 먼저 설정을 저장해주세요.';return;}
 await invoke(()=>window.updates.install($('version').value),()=>{$('message').textContent='선택한 COCO 버전을 적용했습니다. 새 AI 세션에서 확인하세요.';});
};
$('test').onclick=()=>invoke(()=>window.updates.testNotification(),()=>{$('message').textContent='OS 알림이 표시되는지 확인해주세요.';});
window.updates.onState(render);
invoke(()=>window.updates.state(),render);

$('notification-settings').onclick=()=>invoke(()=>window.updates.notificationSettings());

$('tab-updates').onclick=()=>invoke(()=>window.updates.tab('updates'),render);
$('tab-qa').onclick=()=>invoke(()=>window.updates.tab('qa'),render);
$('qa-retry').onclick=()=>invoke(()=>window.updates.tab('qa'),render);
$('qa-browser').onclick=()=>invoke(()=>window.updates.qaBrowser());
for(const [id,kind] of [['phone-apk','phone'],['tablet-apk','tablet'],['iphone-ipa','iphone']])$(id).onclick=()=>invoke(()=>window.updates.downloadApp(kind));

for(const host of ['edo1','edo2'])$('ssh-'+host).onclick=()=>invoke(()=>window.updates.ssh(host));
