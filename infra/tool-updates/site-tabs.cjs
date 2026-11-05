'use strict';

const {PLAYSTORE_URL}=require('./playstore.cjs');

const loopback=(name,local,remote)=>[name,{url:'http://localhost:'+local+'/',partition:'persist:eolssu-'+name,forward:{local,remote}}];
const SITES=new Map([
 ['qa',{url:'https://15.165.144.7/qa',partition:'persist:eolssu-qa'}],
 ['blog',{url:'https://team-edo.github.io/',partition:'eolssu-blog'}],
 loopback('grafana',13000,3000),
 loopback('prometheus',13001,3001),
 loopback('jenkins',18080,8080),
 loopback('db',18081,8081),
 loopback('n8n',15688,5688),
 ['playstore',{url:PLAYSTORE_URL,partition:'eolssu-playstore',privateLink:true}]
]);
const TAB_BAR_HEIGHT=46,DEFAULT_ZOOM=0.8;

function createSiteTabs(win,createView,onChange,loadPlayStore=async()=>null) {
 const pages=new Map();
 let tab='updates',attached=null,closed=false;
 const snapshot=()=>{
  const page=pages.get(tab);
  return {tab,siteStatus:page?.status??'waiting',localPort:SITES.get(tab)?.forward?.local};
 };
 const resize=()=>{
  if(!attached)return;
  const [width,height]=win.getContentSize();
  attached.setBounds({x:0,y:TAB_BAR_HEIGHT,width,height:Math.max(1,height-TAB_BAR_HEIGHT)});
 };
 function refresh() {
  if(closed)return;
  const page=pages.get(tab);
  const next=page?.status==='ready'?page.view:null;
  if(attached!==next) {
   if(attached)win.contentView.removeChildView(attached);
   attached=next;
   if(attached)win.contentView.addChildView(attached);
   resize();
  }
  onChange();
 }
 function fail(page) {
  page.status='error';
  refresh();
 }
 function makePage(site) {
  const view=createView({webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false,partition:site.partition}});
  const page={view,status:'waiting'};
  const contents=view.webContents;
  const origin=new URL(site.url).origin;
  function guard(details,url) {
   let allowed=false;
   try{allowed=new URL(details.url??url).origin===origin;}catch{}
   if(!allowed)details.preventDefault();
  }
  contents.setWindowOpenHandler(()=>({action:'deny'}));
  contents.session.setPermissionCheckHandler(()=>false);
  contents.session.setPermissionRequestHandler((_,__,callback)=>callback(false));
  contents.on('will-navigate',guard);
  contents.on('will-redirect',guard);
  contents.on('did-fail-load',(_,code,__,___,isMainFrame)=>{
   if(isMainFrame&&code!==-3&&page.status==='ready')fail(page);
  });
  contents.on('render-process-gone',()=>fail(page));
  return page;
 }
 async function load(page,site) {
  page.status='loading';
  try {
   const url=site.privateLink?await loadPlayStore():site.url;
   if(closed||page.status!=='loading')return;
   if(!url){page.status='setup';refresh();return;}
   const contents=page.view.webContents;
   await contents.loadURL(url);
   if(closed||page.status!=='loading')return;
   if(site.forward&&!await contents.executeJavaScript('Boolean(document.body && (document.body.childElementCount || document.body.textContent.trim()))')){
    fail(page);return;
   }
   if(page.status==='loading'){
    if(!page.zoomInitialized){contents.setZoomFactor(DEFAULT_ZOOM);page.zoomInitialized=true;}
    page.status='ready';refresh();
   }
  }catch{if(page.status==='loading')fail(page);}
 }
 function select(value) {
  if(value!=='updates'&&!SITES.has(value))throw new Error('잘못된 화면입니다.');
  tab=value;
  const site=SITES.get(tab);
  if(site&&!pages.has(tab))pages.set(tab,makePage(site));
  const page=pages.get(tab);
  if(page&&['waiting','error','setup'].includes(page.status))load(page,site);
  refresh();
 }
 function close() {
  closed=true;
  attached=null;
  for(const {view} of pages.values())if(!view.webContents.isDestroyed())view.webContents.close();
  pages.clear();
 }
 return {snapshot,select,resize,close,currentForward:()=>SITES.get(tab)?.forward,currentURL:async()=>{
  const site=SITES.get(tab);
  return site?.privateLink?await loadPlayStore():site?.url;
 }};
}
module.exports={createSiteTabs};
