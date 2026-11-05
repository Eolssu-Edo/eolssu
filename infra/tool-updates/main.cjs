'use strict';
const {app,BrowserWindow,Tray,Menu,Notification,ipcMain,dialog,nativeImage,powerMonitor,shell,WebContentsView}=require('electron');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {pathToFileURL}=require('node:url');
const {UpdateEngine}=require('./engine.cjs');
const {adapters,JsonStore,project}=require('./adapters.cjs');
const {listBuilds,downloadBuild}=require('./apps.cjs');
const {terminalShortcut}=require('./ssh.cjs');
const QA_URL='https://15.165.144.7/qa';
let qaView,qaAttached=false,tab='updates',qaStatus='waiting',qaError='',appBuilds={},appsMessage='',appsBusy=false;
const APP_ID='com.ssafy.eolssu.updates';
app.setName('Eolssu Updates');
app.setAppUserModelId(APP_ID);
let win,tray,engine,timer,quitting=false,checking;
const activeNotices=new Set();
const html=path.join(__dirname,'index.html');
const fileURL=pathToFileURL(html).href;
if(!app.requestSingleInstanceLock()) app.quit();
else {
 app.on('second-instance',()=>showWindow());
 app.on('activate',()=>showWindow());
 app.on('before-quit',()=>{quitting=true;clearTimeout(timer);});
 app.on('window-all-closed',()=>{});
 app.whenReady().then(start).catch(error=>{dialog.showErrorBox('Eolssu Updates',error.message);app.quit();});
}
function showWindow() {if(!win)return;win.show();win.focus();app.dock?.show();}
function loginSettings(enabled) {
 const settings={openAtLogin:enabled};
 if(process.platform==='win32') {settings.path=process.execPath;settings.args=['--background'];}
 return settings;
}
function nativeInfo() {
 let startupEnabled=false,startupStatus='',wasOpenedAtLogin=false;
 try{const info=app.getLoginItemSettings(loginSettings(false));startupEnabled=info.openAtLogin;startupStatus=info.status||'';wasOpenedAtLogin=!!info.wasOpenedAtLogin;}catch{}
 return {platform:process.platform,startupEnabled,startupStatus,wasOpenedAtLogin,packaged:app.isPackaged};
}
function snapshot(){return {...engine.snapshot(),native:nativeInfo(),tab,qaStatus,qaError,apps:{...appBuilds,busy:appsBusy,message:appsMessage}};}
function push(){if(win&&!win.isDestroyed())win.webContents.send('updates:state',snapshot());updateTray();}
function updateTray() {
 if(!tray||!engine)return;
 tray.setContextMenu(Menu.buildFromTemplate([
  {label:'Eolssu Updates 열기',click:showWindow},
  {label:'지금 확인',click:()=>checkNow()},
  {type:'separator'},
  {label:'그래프 자동 다운로드',type:'checkbox',checked:engine.data.config.autoDownload,click:item=>{try{engine.configure({autoDownload:item.checked});checkNow();}catch(e){dialog.showErrorBox('설정',e.message);}}},
  {label:'종료',click:()=>app.quit()}
 ]));
}
function checkNow() {
 if(checking)return checking;
 clearTimeout(timer);
 checking=(async()=>{try{await Promise.allSettled([engine.check(),refreshApps()]);}catch(e){engine.view.notificationError=e.message;push();}finally{clearTimeout(timer);timer=setTimeout(checkNow,60000);timer.unref();}})().finally(()=>checking=null);
 return checking;
}
async function refreshApps() {try{appBuilds=await listBuilds(engine.data.config.sshHost);}catch(e){appsMessage=e.message;}push();}
function attachQA(){if(!qaAttached){win.contentView.addChildView(qaView);qaAttached=true;}qaView.setVisible(true);qaBounds();}
function qaBounds(){if(qaView){const [width,height]=win.getContentSize();qaView.setBounds({x:0,y:46,width,height:Math.max(1,height-46)});}}
async function switchTab(value) {
 if(!['updates','qa'].includes(value))throw new Error('잘못된 화면입니다.');
 tab=value;
 if(value==='updates'){if(qaAttached){win.contentView.removeChildView(qaView);qaAttached=false;}push();return snapshot();}
 if(!qaView){
  qaView=new WebContentsView({webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false,partition:'persist:eolssu-qa'}});
  qaView.setVisible(false);qaBounds();
  const contents=qaView.webContents;
  contents.setWindowOpenHandler(()=>({action:'deny'}));
  contents.session.setPermissionRequestHandler((_,__,callback)=>callback(false));
  const safe=url=>{try{return new URL(url).origin===new URL(QA_URL).origin;}catch{return false;}};
  contents.on('will-navigate',(event,url)=>{if(!safe(url))event.preventDefault();});
  contents.on('will-redirect',(event,url)=>{if(!safe(url))event.preventDefault();});
  contents.on('did-finish-load',()=>{qaStatus='ready';qaError='';if(tab==='qa')attachQA();push();});
  contents.on('did-fail-load',(_,code,description,__,isMainFrame)=>{if(!isMainFrame||code===-3)return;qaStatus='error';qaError=description;qaView.setVisible(false);push();});
 }
 if(qaStatus==='ready'){attachQA();push();return snapshot();}
 qaStatus='loading';qaError='';push();
 try{await qaView.webContents.loadURL(QA_URL);}catch(error){qaStatus='error';qaError=error.message;qaView.setVisible(false);push();}
 return snapshot();
}
function selectCoco(version) {switchTab('updates');showWindow();win.webContents.send('updates:state',{...snapshot(),focusCocoVersion:version});}
async function noticeAction(value) {
 if(value.kind==='coco'||value.kind==='coco-installed') return selectCoco(value.version);
 if(value.kind==='graph'&&value.projectPath===engine.data.config.projectPath&&value.compatible) {
  try{await engine.downloadGraph();}catch{showWindow();}
 }else showWindow();
}
async function notify(value) {
 if(!Notification.isSupported()) throw new Error('이 OS에서 알림을 지원하지 않습니다.');
 const action=value.kind==='coco'?'설치·업데이트':value.kind==='graph'&&value.compatible?'다운로드':'열기';
 let title,body;
 if(value.kind==='coco') {title='COCO 새 배포';body=value.versions.join(', ')+(value.versions.length>1?' 버전이 배포됐습니다.':' 버전이 배포됐습니다.');}
 else if(value.kind==='coco-installed') {title='COCO 설치 완료';body=value.version+' 적용이 끝났습니다. 새 AI 세션에서 확인하세요.';}
 else if(value.kind==='graph-downloaded') {title='Graphify 다운로드 완료';body='프로젝트의 코드 지도를 새 버전으로 바꿨습니다.';}
 else if(value.kind==='graph-failed') {title='Graphify 자동 다운로드 실패';body='기존 그래프를 유지했습니다. 앱에서 다시 시도할 수 있습니다.';}
 else if(value.kind==='test') {title='Eolssu Updates 알림 테스트';body='새 그래프와 COCO 배포를 이곳에서 알려드립니다.';}
 else {title='Graphify 새 버전';body=value.compatible?'새 코드 지도가 준비됐습니다. 지금 내려받을 수 있습니다.':'새 코드 지도가 준비됐습니다. 프로젝트 코드를 먼저 갱신해주세요.';}
 const id=crypto.createHash('sha256').update(value.kind+String(value.release||value.version||Date.now())).digest('hex').slice(0,16);
 const n=new Notification({id,groupId:value.kind.startsWith('coco')?'coco':'graphify',title,body,actions:[{type:'button',text:action}],silent:false});
 const wrapped={...value,projectPath:engine.data.config.projectPath};
 n.on('click',()=>value.kind==='coco'?selectCoco(value.version):showWindow());
 n.on('action',event=>{if((event.actionIndex??0)===0) noticeAction(wrapped);});
 n.on('show',()=>{engine.view.notificationError='';push();});
 n.on('failed',(_,error)=>{engine.view.notificationError=error||'OS 알림 권한을 확인해주세요.';push();});
 activeNotices.add(n);if(activeNotices.size>30)activeNotices.delete(activeNotices.values().next().value);
 n.show();
}
async function start() {
 const store=new JsonStore(path.join(app.getPath('userData'),'settings.json'));
 const backend=adapters(app.isPackaged?path.join(process.resourcesPath,'context.py'):path.join(__dirname,'resources/context.py'));
 engine=new UpdateEngine({load:()=>store.load(),save:v=>store.save(v),...backend,notify,now:Date.now});
 win=new BrowserWindow({width:620,height:440,minWidth:580,minHeight:380,show:false,backgroundColor:'#f4f5f1',title:'Eolssu Updates',webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
 win.on('resize',qaBounds);
 win.on('closed',()=>{if(qaView&&!qaView.webContents.isDestroyed())qaView.webContents.close();});
 win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
 win.webContents.on('will-navigate',(event,url)=>{if(url!==fileURL)event.preventDefault();});
 win.webContents.session.setPermissionRequestHandler((_,permission,callback)=>callback(permission==='notifications'));
 win.on('close',event=>{if(!quitting){event.preventDefault();win.hide();app.dock?.hide();}});
 const icon=nativeImage.createFromPath(path.join(__dirname,'assets/tray.png'));icon.setTemplateImage(true);
 tray=new Tray(icon);tray.setToolTip('Graphify · COCO 업데이트');tray.on('double-click',showWindow);tray.on('click',()=>{if(process.platform==='darwin')showWindow();});
 engine.on('change',push);updateTray();
 const allowed=event=>{if(event.sender!==win.webContents||event.senderFrame!==win.webContents.mainFrame)throw new Error('허용되지 않은 요청입니다.');};
 const handle=(name,fn)=>ipcMain.handle('updates:'+name,async(event,...args)=>{allowed(event);try{return {ok:true,value:await fn(...args)};}catch(e){return {ok:false,error:e.message};}});
 handle('state',()=>snapshot());
 handle('tab',switchTab);
 handle('ssh',async host=>{const file=await terminalShortcut(host,path.join(app.getPath('userData'),'ssh'));const error=await shell.openPath(file);if(error)throw new Error(error);return {host};});
 handle('qa-browser',()=>shell.openExternal(QA_URL));
 handle('download-app',async kind=>{
  if(!['phone','tablet','iphone'].includes(kind)||appsBusy)throw new Error('다운로드 상태를 확인해주세요.');
  appBuilds=await listBuilds(engine.data.config.sshHost);const meta=appBuilds[kind];
  if(!meta)throw new Error('게시된 파일이 없습니다.');
  const extension=kind==='iphone'?'ipa':'apk';
  const selected=await dialog.showSaveDialog(win,{title:'최신 QA 앱 다운로드',defaultPath:path.join(app.getPath('downloads'),'eolssu-'+kind+'-'+meta.id+'.'+extension),filters:[{name:extension.toUpperCase(),extensions:[extension]}]});
  if(selected.canceled||!selected.filePath)return {status:'cancelled'};
  appsBusy=true;appsMessage='다운로드 중…';push();
  try{const result=await downloadBuild(kind,engine.data.config.sshHost,selected.filePath,meta,p=>{appsMessage=p+'% 다운로드';push();});appsMessage='받음: '+path.basename(result.file);return result;}
  catch(e){appsMessage=e.message;throw e;}
  finally{appsBusy=false;push();}
 });
 handle('choose-project',async()=>{const selected=await dialog.showOpenDialog(win,{properties:['openDirectory'],title:'Git 프로젝트 루트 선택'});return selected.canceled?'':await project(selected.filePaths[0]);});
 handle('save',async input=>{
  if(!input||typeof input!=='object')throw new Error('설정을 확인해주세요.');
  if(input.projectPath)input.projectPath=await project(input.projectPath);
  const old=engine.data.config;
  engine.configure(input);
  try{if(app.isPackaged)app.setLoginItemSettings(loginSettings(engine.data.config.startAtLogin));}
  catch(e){engine.configure(old);throw e;}
  checkNow();return snapshot();
 });
 handle('check',async()=>{await checkNow();return snapshot();});
 handle('download',()=>engine.downloadGraph());
 handle('install',version=>engine.installCoco(String(version)));
 handle('notification-settings',()=>shell.openExternal(process.platform==='win32'?'ms-settings:notifications':'x-apple.systempreferences:com.apple.Notifications-Settings.extension'));
 handle('test-notification',()=>notify({kind:'test'}));
 powerMonitor.on('resume',()=>checkNow());
 if(process.platform==='win32'&&Notification.handleActivation) Notification.handleActivation(()=>{showWindow();checkNow();});
 await win.loadFile(html);
 if((!process.argv.includes('--background')&&!nativeInfo().wasOpenedAtLogin)||!engine.data.config.setupComplete)showWindow();
 else app.dock?.hide();
 checkNow();
}
