'use strict';
const $=id=>document.getElementById(id);
const tabs=[...document.querySelectorAll('.tabs [role=tab]')].map(button=>button.id.slice(4));
let activeTab='updates',state,pendingSaves=0,saveQueue=Promise.resolve();
async function invoke(work,success,errorTarget='message') {
 try{const result=await work();if(!result.ok)throw new Error(result.error);if(success)success(result.value);return result.value;}
 catch(e){$(errorTarget).textContent=e.message;return null;}
}
function form({config,native}) {
 const project=$('project');
 project.value=config.projectPath;
 project.title=config.projectPath;
 $('auto').checked=config.autoApply;$('startup').checked=native?.packaged?native.startupEnabled:config.startAtLogin;$('runtime').value=config.cocoRuntime;$('scope').value=config.cocoScope;
}
function render(value) {
 state=value;
 const remote=value.tab!=='updates';
 if(activeTab!==value.tab){activeTab=value.tab;$('site-error').textContent='';window.scrollTo(0,0);}
 $('updates-panel').hidden=remote;
 for(const tab of tabs) {
  const selected=value.tab===tab,button=$('tab-'+tab);
  button.setAttribute('aria-selected',String(selected));button.tabIndex=selected?0:-1;
 }
 const panel=$('site-panel'),failed=value.siteStatus==='error',setup=value.siteStatus==='setup';
 const playstore=value.tab==='playstore',ready=value.siteStatus==='ready';
 panel.hidden=!remote||ready;
 panel.classList.toggle('local-fallback',!!value.localPort);
 $('site-connect-row').hidden=!value.localPort;
 $('playstore-form').hidden=!playstore||!setup;
 if(remote) {
  const label=$('tab-'+value.tab).textContent,port=value.localPort;
  panel.setAttribute('aria-labelledby','tab-'+value.tab);
  $('site-state').textContent=setup?'이 기기에 출시 링크를 저장해 주세요.':failed?
   label+' 페이지를 열지 못했습니다.'+(port?' localhost:'+port+' 연결을 확인해 주세요.':''):
   label+' 페이지를 불러오는 중…';
  $('site-retry').hidden=!failed;
  $('site-browser').hidden=setup;
 }
 const files=value.apps||{};for(const [id,key] of [['phone-apk','phone'],['tablet-apk','tablet'],['iphone-ipa','iphone']]){$(id).disabled=!!files.busy||!files[key];$(id).title=files[key]?files[key].builtAt+' · '+(files[key].commit||'').slice(0,8)+(key!=='iphone'?' · 폰·태블릿 공용 APK':''):'게시된 파일 없음';}$('apps-message').textContent=files.message||files.error||'';
 if(pendingSaves===0)form(value);
 const g=value.graph,c=value.coco;
 const names={available:'새 버전',current:'최신 버전',error:'확인 필요'};
 $('graph-badge').hidden=g.phase==='setup';
 $('graph-badge').textContent=names[g.phase]||'확인 중';$('graph-badge').className='badge '+(g.phase==='available'?'ready':g.phase==='error'?'warning':'');
 $('graph-status').textContent=g.error||(g.release&&g.compatible===false?'코드 버전이 달라 파일 위치가 다를 수 있습니다.':'');
 $('graph-detail').textContent=g.source_commit?'develop '+g.source_commit.slice(0,8)+(g.archive_size?' · 다운로드 '+(g.archive_size/1024/1024).toFixed(2)+' MB':''):'';
 $('download').disabled=value.busyGraph||!g.release||g.installed;
 $('download').textContent=value.busyGraph?'받는 중…':'다운로드';
 $('graph-time').textContent=g.lastChecked?new Date(g.lastChecked).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'})+' 확인':'';
 $('coco-status').textContent=c.error&&c.phase==='error'?c.error:c.latest?'새 버전을 선택해 설치하거나 업데이트할 수 있습니다.':'npm 배포 목록을 확인합니다.';
 const select=$('version'),selected=value.focusCocoVersion||select.value;
 const versions=c.versions||[];
 const options=versions.length?versions:[''];
 if(options.length!==select.options.length||options.some((version,index)=>version!==select.options[index].value)) {
  select.replaceChildren(...options.map(version=>new Option(version+(version.includes('-')?' · 프리릴리스':''),version)));
 }
 if(!versions.length)select.options[0].textContent=c.phase==='error'?'조회 실패':'확인 중';
 if(versions.includes(selected))select.value=selected;
 select.disabled=!versions.length||value.busyCoco;
 select.title=select.selectedOptions[0]?.textContent||'';
 $('install').disabled=value.busyCoco||pendingSaves>0||!select.value;$('install').textContent=value.busyCoco?'설치 중…':'설치·업데이트';
 const runtimeOption=[...$('runtime').options].find(option=>option.value===c.installed?.runtime);
 $('coco-result').textContent=c.installed?'최근 완료: '+c.installed.version+' · '+(runtimeOption?.textContent||c.installed.runtime)+' · '+(c.installed.scope==='global'?'전역':'프로젝트')+' — 새 AI 세션에서 확인하세요.':'';
 $('coco-error').textContent=c.installError||c.error||'';
 for(const id of ['choose','auto','startup','runtime','scope'])$(id).disabled=value.busyCoco||value.busyGraph;
 $('startup-detail').textContent=value.native?.packaged?'OS 로그인 실행: '+(value.native.startupEnabled?'켜짐':'꺼짐'):'배포 앱에서 로그인 실행을 설정할 수 있습니다.';
 $('notification-error').textContent=value.notificationError||'';
 if(value.focusCocoVersion)$('coco-title').scrollIntoView({behavior:'smooth',block:'center'});
}
function saveSettings(input) {
 pendingSaves++;
 if(state)render(state);
 saveQueue=saveQueue.then(()=>invoke(()=>window.updates.save(input),value=>{
  render(value);$('message').textContent='설정을 적용했습니다.';
 })).finally(()=>{pendingSaves--;if(state)render(state);});
 return saveQueue;
}
$('auto').onchange=()=>saveSettings({autoApply:$('auto').checked});
$('startup').onchange=()=>saveSettings({startAtLogin:$('startup').checked});
$('runtime').onchange=()=>saveSettings({cocoRuntime:$('runtime').value});
$('scope').onchange=()=>saveSettings({cocoScope:$('scope').value});
$('version').onchange=event=>{
 const select=event.currentTarget;
 select.title=select.selectedOptions[0]?.textContent||'';
};
$('choose').onclick=()=>invoke(()=>window.updates.chooseProject(),projectPath=>{
 if(projectPath){$('project').value=projectPath;saveSettings({projectPath});}
});
$('check').onclick=async()=>{$('check').disabled=true;$('message').textContent='새 배포를 확인하고 있습니다…';await invoke(()=>window.updates.check(),render);$('check').disabled=false;$('message').textContent='확인을 마쳤습니다. 약 1분마다 다시 확인합니다.';};
$('download').onclick=()=>invoke(()=>window.updates.download(),()=>{$('message').textContent='다운로드를 마쳤습니다.';});
$('install').onclick=async()=>{
 if(pendingSaves)return;
 await invoke(()=>window.updates.install($('version').value),()=>{$('message').textContent='선택한 COCO 버전을 적용했습니다. 새 AI 세션에서 확인하세요.';});
};
$('test').onclick=()=>invoke(()=>window.updates.testNotification(),()=>{$('message').textContent='OS 알림이 표시되는지 확인해주세요.';});
window.updates.onState(render);
invoke(()=>window.updates.state(),render);

$('notification-settings').onclick=()=>invoke(()=>window.updates.notificationSettings());

const selectTab=tab=>invoke(()=>window.updates.tab(tab),render);
for(const [index,tab] of tabs.entries()) {
 const button=$('tab-'+tab);
 button.onclick=()=>selectTab(tab);
 button.onkeydown=event=>{
  const target={ArrowLeft:(index+tabs.length-1)%tabs.length,ArrowRight:(index+1)%tabs.length,Home:0,End:tabs.length-1}[event.key];
  if(target===undefined)return;
  event.preventDefault();
  $('tab-'+tabs[target]).focus();
  selectTab(tabs[target]);
 };
}
$('site-retry').onclick=()=>selectTab(activeTab);
async function connectSSH(host) {
 const tab=activeTab,tool=tab!=='updates',button=tool?$('site-connect'):$('ssh-'+host);
 button.disabled=true;
 if(tool)$('site-error').textContent='SSH 터널 연결을 확인하고 있습니다…';
 await invoke(()=>window.updates.ssh(host),result=>{
  if(tool&&activeTab===tab)$('site-error').textContent=result.connected?'':'터미널에서 연결한 뒤 다시 연결을 눌러 주세요.';
 },tool?'site-error':'message');
 button.disabled=false;
}
$('site-connect').onclick=()=>connectSSH('edo1');
$('site-browser').onclick=()=>invoke(()=>window.updates.siteBrowser(),null,'site-error');
$('playstore-form').onsubmit=async event=>{
 event.preventDefault();
 const button=$('playstore-save'),field=$('playstore-link');
 button.disabled=true;$('site-error').textContent='';
 const link=field.value;field.value='';
 await invoke(()=>window.updates.savePlayStore(link),render,'site-error');
 button.disabled=false;
};
for(const [id,kind] of [['phone-apk','phone'],['tablet-apk','tablet'],['iphone-ipa','iphone']])$(id).onclick=()=>invoke(()=>window.updates.downloadApp(kind));

for(const host of ['edo1','edo2'])$('ssh-'+host).onclick=()=>connectSSH(host);
