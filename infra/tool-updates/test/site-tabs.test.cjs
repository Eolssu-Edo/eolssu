'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {EventEmitter}=require('node:events');
const {createSiteTabs}=require('../site-tabs.cjs');

function fixture() {
 const created=[],visible=new Set();
 const win={getContentSize:()=>[620,409],contentView:{
  addChildView:view=>{assert.equal(visible.size,0);visible.add(view);},
  removeChildView:view=>{assert.ok(visible.delete(view));}
 }};
 const createView=options=>{
  const contents=new EventEmitter();
  contents.session={setPermissionRequestHandler(fn){this.request=fn;},setPermissionCheckHandler(fn){this.check=fn;}};
  contents.setWindowOpenHandler=fn=>{contents.popup=fn;};
  contents.loadURL=url=>{contents.url=url;return new Promise((resolve,reject)=>{contents.loaded=resolve;contents.failed=reject;});};
  contents.executeJavaScript=async()=>true;
  contents.setZoomFactor=factor=>{contents.zoom=factor;};
  contents.isDestroyed=()=>false;
  contents.close=()=>{contents.closed=true;};
  const view={options,webContents:contents,setBounds(bounds){this.bounds=bounds;}};
  created.push(view);
  return view;
 };
 const tabs=createSiteTabs(win,createView,()=>{});
 return {created,visible,tabs};
}

test('a background tab finishing late cannot cover the selected blog or update screen',async()=>{
 const {tabs,created,visible}=fixture();
 tabs.select('qa');
 tabs.select('blog');
 const [qa,blog]=created;
 assert.equal(qa.webContents.url,'https://15.165.144.7/qa');
 assert.equal(blog.webContents.url,'https://team-edo.github.io/');
 assert.notEqual(qa.options.webPreferences.partition,blog.options.webPreferences.partition);
 assert.equal(tabs.snapshot().siteStatus,'loading');
 blog.webContents.loaded();
 await Promise.resolve();
 assert.deepEqual([...visible],[blog]);
 assert.equal(blog.webContents.zoom,0.8);
 qa.webContents.loaded();
 await Promise.resolve();
 assert.deepEqual([...visible],[blog]);
 tabs.select('updates');
 assert.equal(visible.size,0);
 tabs.select('qa');
 assert.deepEqual([...visible],[qa]);
 assert.equal(created.length,2);
 tabs.close();
 assert.ok(created.every(view=>view.webContents.closed));
});

test('blog allows its own pages, rejects cross-origin navigation and supports retry after failure',async()=>{
 const {tabs,created,visible}=fixture();
 assert.throws(()=>tabs.select('https://example.com/'),/화면/);
 tabs.select('blog');
 const {webContents,options}=created[0];
 assert.equal(options.webPreferences.nodeIntegration,false);
 assert.equal(options.webPreferences.sandbox,true);
 assert.equal(options.webPreferences.preload,undefined);
 for(const event of ['will-navigate','will-redirect']) {
  const navigate=url=>{let prevented=false;webContents.emit(event,{url,preventDefault:()=>{prevented=true;}});return prevented;};
  assert.equal(navigate('https://team-edo.github.io/posts/'),false);
  assert.equal(navigate('https://team-edo.github.io.example.com/'),true);
 }
 assert.equal(webContents.popup().action,'deny');
 assert.equal(webContents.session.check(),false);
 webContents.failed(new Error('ERR_INTERNET_DISCONNECTED'));
 await Promise.resolve();
 assert.equal(tabs.snapshot().siteStatus,'error');
 assert.equal(visible.size,0);
 tabs.select('blog');
 assert.equal(tabs.snapshot().siteStatus,'loading');
 webContents.loaded();
 await Promise.resolve();
 assert.equal(tabs.snapshot().siteStatus,'ready');
 assert.equal(visible.size,1);
});

test('each local tool uses its fixed loopback port in its own session',async()=>{
 const {tabs,created,visible}=fixture();
 const targets=[['grafana',13000,3000],['prometheus',13001,3001],['jenkins',18080,8080],['db',18081,8081],['n8n',15688,5688]];
 for(const [name,port,remote] of targets) {
  tabs.select(name);
  assert.equal(created.at(-1).webContents.url,'http://localhost:'+port+'/');
  assert.deepEqual(tabs.currentForward(),{local:port,remote});
 }
 assert.equal(new Set(created.map(view=>view.options.webPreferences.partition)).size,targets.length);
 created.at(-1).webContents.failed(new Error('ERR_CONNECTION_REFUSED'));
 await Promise.resolve();
 assert.equal(tabs.snapshot().siteStatus,'error');
 assert.equal(visible.size,0);
});

test('the optional PlayStore setup screen never creates an in-app browser',async()=>{
 const {tabs,created}=fixture();
 tabs.select('playstore');
 await Promise.resolve();
 assert.equal(tabs.snapshot().siteStatus,'setup');
 assert.equal(created.length,0);
 assert.equal(JSON.stringify(tabs.snapshot()).includes('kmong_key'),false);
});


test('an empty local dashboard keeps the SSH connection fallback available',async()=>{
 const {tabs,created,visible}=fixture();
 tabs.select('jenkins');
 const {webContents}=created[0];
 webContents.executeJavaScript=async()=>false;
 webContents.loaded();
 await Promise.resolve();
 await Promise.resolve();
 assert.deepEqual(tabs.currentForward(),{local:18080,remote:8080});
 assert.equal(tabs.snapshot().siteStatus,'error');
 assert.equal(visible.size,0);
});
