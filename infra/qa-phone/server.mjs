import http from 'node:http';
import { isIP } from 'node:net';
import { randomBytes, randomUUID, createHash, timingSafeEqual } from 'node:crypto';
import { readFile, access, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { Control, Rejected } from './control.mjs';
import { AdbPhone } from './adb.mjs';
import { ApplogHost, ApplogPath, ApplogTokenPattern, parseApplogRequest, parseApplogResponse } from './applog.mjs';
import { Catalog } from './catalog.mjs';
import { createMirror } from './mirror.mjs';
import { PhoneVideo } from './video.mjs';
import { ScreenKeeper } from './screen-keeper.mjs';
import {pairWireless,queueWirelessPort,wirelessPortState} from './wireless-recovery.mjs';
import {promisify} from 'node:util';
import {execFile} from 'node:child_process';
import {loadLiveClient} from './live-client.mjs';
import {InstalledAppLogs} from './installed-app-logs.mjs';
import {appDatabaseReader} from './read-only-app-db.mjs';
const hash = value => createHash('sha256').update(value).digest();
const IDLE_PROMPT_MS = 10 * 60_000;
const IDLE_GRACE_MS = 60_000;
const staticFiles = new Map([['/video-view.js',['video-view.js','text/javascript; charset=utf-8']],['/video-wire.js',['video-wire.js','text/javascript; charset=utf-8']],['/', ['index.html', 'text/html; charset=utf-8']], ['/app.js', ['app.js', 'text/javascript; charset=utf-8']], ['/style.css', ['style.css', 'text/css; charset=utf-8']]]);
staticFiles.set('/apk-update.js', ['apk-update.js','text/javascript; charset=utf-8']);
staticFiles.set('/live-panel.js', ['live-panel.js','text/javascript; charset=utf-8']);
staticFiles.set('/guide.html', ['guide.html','text/html; charset=utf-8']);
for(const file of ['device-session.js','dashboard-splitter.js'])staticFiles.set('/'+file,[file,'text/javascript; charset=utf-8']);
staticFiles.set('/dashboard.css',['dashboard.css','text/css; charset=utf-8']);
staticFiles.set('/devices.json',['devices.json','application/json; charset=utf-8']);
async function body(req) {
  if (req.headers['content-type'] !== 'application/json') throw new Rejected('JSON required', 415);
  let size = 0; const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 4096) throw new Rejected('body too large', 413);
    chunks.push(chunk);
  }
  try { const value = JSON.parse(Buffer.concat(chunks).toString('utf8')); if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(); return value; }
  catch { throw new Rejected('invalid JSON', 400); }
}
export async function startRelay({ phone, port = 17680, origin, basePath = '/', deviceId = 'phone', deviceName = '휴대폰', trustProxy = false, catalog = null, mirror = null, video = null, screenKeeper = null, wireless = null, applog = null, live = null, sessionNow = Date.now } = {}) {
  if (typeof basePath !== 'string' || !/^\/(?:[a-zA-Z0-9_-]+\/)*$/.test(basePath)) throw new Error('invalid QA_BASE_PATH');
  if (!/^[a-z][a-z0-9-]{0,31}$/.test(deviceId) || typeof deviceName !== 'string' || !deviceName.trim() || deviceName.length > 60 || /[\p{C}]/u.test(deviceName)) throw new Error('invalid QA device identity');
  if (applog && (deviceId !== 'phone' || basePath !== '/qa/' || typeof phone.applog !== 'function' ||
      typeof applog.token !== 'string' || !ApplogTokenPattern.test(applog.token) ||
      typeof applog.identity !== 'string' || !/^[A-Za-z0-9_-]{4,80}$/.test(applog.identity))) {
    throw new Error('invalid phone app log configuration');
  }
  const applogCredential = applog ? hash(applog.token) : null;
  let applogTask = null;
  const device = { id: deviceId, name: deviceName.trim() };
  const pairingAvailable = typeof wireless?.pair === 'function';
  let pairingPending = false, nextPairingAt = 0;
  // A parent-path cookie is also sent to nested paths. Distinct names avoid order-dependent login.
  const cookieName = ['/', '/qa/'].includes(basePath) ? 'qa_session' : 'qa_session_' + hash(basePath).toString('hex').slice(0,16);
  const cookiePattern = new RegExp('(?:^|;\\s*)' + cookieName + '=([a-f0-9]{64})(?:;|$)');
  let url; let inputFault = false; let frame = null; let frameAt = 0; let capturing = null; let closed = false; let timer;
  let installTask=null, installed=null, installedStatus='checking', installedChecked=0, job={status:'idle'}, syncAt=0;
  const sessions = new Map(), attempts = new Map(), streams=new Map();
  let fallbackAt=0, geometryPending=false, videoGeometry=null, recoverAt=0;
  const freshVideo=()=>video?.healthy&&!geometryPending;
  const closeStreams=id=>{for(const response of streams.get(id)||[])response.destroy();streams.delete(id);};
  const idleLeft = (session, now) => session.activeAt + IDLE_PROMPT_MS + IDLE_GRACE_MS - now;
  const expired = (session, now) => session.until <= now || idleLeft(session, now) <= 0;
  const control = new Control({ input: async (action, packet) => {
    if (closed || !frame || geometryPending || (!freshVideo() && Date.now() - frameAt > 3000)) throw new Error('stale screen');
    try { await phone.input(action, packet); } catch (error) {
      inputFault=true;recoverAt=Date.now()+2000;
      // Never log user text, raw ADB argv or an error containing either.
      console.warn(`QA input failed: kind=${action.kind}; reason=${error.killed?'timeout':'transport-or-helper'}; control revoked`);
      throw error;
    }
  }});
  const readAppDatabase=typeof phone.readPerformanceLog==='function'?appDatabaseReader(phone):null;
  const installedLogs=readAppDatabase&&typeof phone.installed==='function'
    ?new InstalledAppLogs({installed:()=>phone.installed(),snapshot:async(after,stream)=>{
      if(closed||control.maintenance||applogTask)throw new Error('App log reader busy');
      try{applogTask=readAppDatabase(after,stream);return await applogTask;}
      finally{applogTask=null;}
    }}):null;
  function syncScreen(){
    if(!screenKeeper)return;
    const now = sessionNow();
    const activeIds=new Set([...sessions.values()].filter(s=>!expired(s,now)).map(s=>s.id));
    const present=!closed&&(control.state(null).peers.some(p=>activeIds.has(p.id))||[...streams].some(([id,responses])=>responses.size&&activeIds.has(id)));
    void screenKeeper.setPresent(present).catch(()=>{});
  }
  async function endSession(secret, session) {
    if (sessions.get(secret) !== session) return;
    sessions.delete(secret);
    closeStreams(session.id);
    await control.leave(session.id);
    syncScreen();
  }
  async function capture() {
    if (control.maintenance) return;
    if (capturing) return capturing;
    capturing = (async () => {
      try {
        const next = await phone.capture();
        if (closed || control.maintenance) return;
        if (frame && (frame.width !== next.width || frame.height !== next.height)) await control.setOnline(false);
        frame = next; frameAt = Date.now(); await control.setOnline(!inputFault);
      } catch {
        if(control.maintenance || (freshVideo()&&frame)) return;
        frame = videoGeometry?{...videoGeometry,data:null}:null; frameAt = 0; await control.setOnline(!!frame&&freshVideo()&&!inputFault);
      }
    })().finally(() => { capturing = null; });
    return capturing;
  }
  async function recoverInput() {
    if(!inputFault||!phone.inputReady||Date.now()<recoverAt||control.maintenance)return;
    recoverAt=Date.now()+2000;
    try {
      await control.tail; // The failed command and its cleanup must settle first.
      if(closed||!inputFault||control.maintenance||!await phone.inputReady())return;
      const next=await phone.capture();
      if(closed||control.maintenance)return;
      frame=next;frameAt=Date.now();inputFault=false;
      // The revoked owner/epoch stays revoked. Users explicitly request control again.
      await control.setOnline(true);
      console.info('QA input transport recovered; a new control request is required');
    } catch { /* Keep input disabled and retry only the read-only checks. */ }
  }
  const instance = randomUUID();
  const server = http.createServer(async (req, res) => {
    res.setHeader('X-QA-Instance', instance);
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Permissions-Policy', 'microphone=(), camera=(), display-capture=()');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    const json = (code, value) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(value)); };
    try {
      if (req.url === ApplogPath && req.method === 'POST') {
        if (!applogCredential) throw new Rejected('not found', 404);
        const supplied = /^Bearer ([A-Za-z0-9_-]{32,128})$/.exec(req.headers.authorization || '')?.[1];
        if (req.headers.host !== ApplogHost || req.headers.origin || req.headers['sec-fetch-site'] ||
            !supplied || !timingSafeEqual(hash(supplied), applogCredential)) {
          throw new Rejected('app_log_unauthorized', 401);
        }
        let request;
        try {
          request = parseApplogRequest(await body(req));
        } catch (error) {
          throw new Rejected('app_log_invalid_request', error instanceof Rejected ? error.status : 400);
        }
        if (closed) throw new Rejected('app_log_unavailable', 503);
        if (control.maintenance) throw new Rejected('app_log_installing', 409);
        if (applogTask) throw new Rejected('app_log_busy', 409);
        try {
          let raw;
          try {
            applogTask = phone.applog(request, applog.identity);
            raw = await applogTask;
          } catch {
            throw new Rejected('app_log_unavailable', 503);
          }
          json(200, parseApplogResponse(raw, request));
        } finally {
          applogTask = null;
        }
        return;
      }
      if (req.headers.host !== new URL(url).host) throw new Rejected('invalid host', 403);
      const pathname = new URL(req.url, url).pathname;
      if (!pathname.startsWith(basePath)) throw new Rejected('not found', 404);
      const path = '/' + pathname.slice(basePath.length);
      if (req.method === 'GET' && staticFiles.has(path)) {
        const [file, type] = staticFiles.get(path);
        const data = await readFile(new URL(`./public/${file}`, import.meta.url));
        res.writeHead(200, { 'Content-Type': type }); res.end(data); return;
      }
      if (!path.startsWith('/api/')) throw new Rejected('not found', 404);
      if (!['GET', 'POST'].includes(req.method)) throw new Rejected('method not allowed', 405);
      if ((req.headers.origin && req.headers.origin !== url) || (req.method === 'POST' && req.headers.origin !== url)) throw new Rejected('invalid origin', 403);
      if (req.headers['sec-fetch-site'] === 'cross-site') throw new Rejected('cross-site request', 403);
      if (path === '/api/device' && req.method === 'GET') { json(200, device); return; }
      const now = Date.now();
      if (path === '/api/login' && req.method === 'POST') {
        const forwarded = req.headers['x-real-ip'];
        const ip = trustProxy && typeof forwarded === 'string' && isIP(forwarded) ? forwarded : req.socket.remoteAddress;
        let attempt = attempts.get(ip);
        if (!attempt || now - attempt.at > 60000) {
          if (attempts.size >= 64) attempts.delete(attempts.keys().next().value);
          attempt = { at: now, count: 0 }; attempts.set(ip, attempt);
        }
        if (++attempt.count > 10) throw new Rejected('too many login attempts; wait one minute', 429);
        const data = await body(req);
        if (typeof data.name !== 'string' || !data.name.trim() || data.name.length > 32 || /[\p{C}]/u.test(data.name)) throw new Rejected('name required (1-32 characters)', 400);
        if (sessions.size >= 64) throw new Rejected('session limit reached; log out unused clients', 429);
        const joinedAt = sessionNow();
        const secret = randomBytes(32).toString('hex'), id = randomUUID();
        sessions.set(secret, { id, until: joinedAt + 8 * 3600000, activeAt: joinedAt });
        control.join(id, data.name.trim());syncScreen();
        res.setHeader('Set-Cookie', `${cookieName}=${secret}; HttpOnly; SameSite=Strict; Path=${basePath}; Max-Age=28800${url.startsWith('https:') ? '; Secure' : ''}`);
        json(200, { ok: true }); return;
      }
      const secret = cookiePattern.exec(req.headers.cookie || '')?.[1];
      const session = sessions.get(secret);
      const id = session?.id;
      async function requireSession() {
        if (!session || sessions.get(secret) !== session) throw new Rejected('연결이 종료됐습니다. 이름을 입력해 다시 연결해 주세요.', 401);
        if (expired(session, sessionNow())) {
          await endSession(secret, session);
          throw new Rejected('연결이 종료됐습니다. 이름을 입력해 다시 연결해 주세요.', 401);
        }
      }
      await requireSession();
      await control.sweep(); control.touch(id);syncScreen();
      if (path === "/api/state" && (!frame || (!freshVideo() && now - frameAt > 3000))) await capture();
      if (req.method === 'GET' && path === '/api/state') {
        if(freshVideo()&&frame&&!inputFault&&!control.online)await control.setOnline(true);
        const remaining = idleLeft(session, sessionNow());
        json(200, { ...control.state(id), idlePromptMs: remaining <= IDLE_GRACE_MS ? Math.max(0,remaining) : null, wireless:wireless?{...await wireless.state(),pairing:pairingAvailable}:null, width: frame?.width, height: frame?.height, frameAt: freshVideo()?video.lastFrameAt:frameAt, video:!!video, screenAwake:screenKeeper?.state()||null, heartbeatMs: 900, graceMs: 10000, inputFault }); return;
      }
      if(req.method==='GET'&&path==='/api/video'){
        if(!video)throw new Rejected('video unavailable',503);
        const owned=streams.get(id)||new Set();if(owned.size>=2)throw new Rejected('video stream limit',429);
        owned.add(res);streams.set(id,owned);res.on('close',()=>{owned.delete(res);if(!owned.size)streams.delete(id);syncScreen();});
        syncScreen();video.subscribe(res,{onSession:size=>{
          geometryPending=true;videoGeometry=null;
          void control.setOnline(false).then(async()=>{
            if(capturing)await capturing;
            if(phone.geometry){const physical=await phone.geometry(size);videoGeometry=physical;frame={...physical,data:null};frameAt=0;}
            else await capture();
          }).catch(()=>{frame=null;}).finally(()=>{geometryPending=false;});
        }});return;
      }
      if (req.method === 'GET' && path === '/api/builds') {
        json(200, { ...(catalog?.list() || {builds:[],latest:null}), installed, installedStatus, job, sync: mirror?.state() || null }); return;
      }
      if(req.method==='GET'&&path==='/api/live/status'){
        const logsFor=new URL(req.url,url).searchParams.get('logs');
        if(installedLogs&&!control.maintenance&&!installed?.versionName?.endsWith('-qa283-live'))
          installedLogs.refresh(installed);
        let snapshot={available:false,message:'격리 관측 서비스 연결 대기',operations:[],history:[]};
        if(live)try{snapshot=await live.status({logsFor});}catch{}
        json(200,installed?.versionName?.endsWith('-qa283-live')?snapshot:installedLogs?.project(snapshot,logsFor)||snapshot);return;
      }
      if (req.method === 'GET' && path === '/api/frame') {
        fallbackAt=Date.now();if(!frame?.data||Date.now()-frameAt>700)await capture();
        if (!frame?.data || !control.online || (!control.maintenance && now - frameAt > 3000)) throw new Rejected('device offline', 503);
        res.writeHead(200, { 'Content-Type': 'image/png' }); res.end(frame.data); return;
      }
      if (req.method !== 'POST') throw new Rejected('not found', 404);
      const data = await body(req);
      await requireSession();
      switch (path) {
        case '/api/app/launch': {
          const result=await control.serial(async()=>{
            control.checkMaintenance();control.owns(id);
            if(!control.online||data.epoch!==control.epoch||!frame||geometryPending||(!freshVideo()&&Date.now()-frameAt>3000))throw new Rejected('최신 기기 화면과 제어권을 확인하세요.',409);
            if(typeof phone.launchQa!=='function')throw new Rejected('기기 앱 실행을 지원하지 않습니다.',503);
            if(live)return live.command({action:'launch'},phone.identity,id);
            try{return await phone.launchQa();}catch{throw new Rejected('QA 앱 실행을 확인하지 못했습니다. 설치 상태를 확인하세요.',503);}
          });
          json(200,result);return;
        }
        case '/api/live/control': {
          if(!live)throw new Rejected('격리 관측 서비스 설정이 필요합니다.',503);
          const result=await control.serial(async()=>{
            control.checkMaintenance();
            const cancelling=['stop','cancel'].includes(data.action);
            if(!cancelling){
              control.owns(id);
              if(!control.online||data.epoch!==control.epoch)throw new Rejected('제어권 또는 기기 연결이 바뀌었습니다.',409);
              if(!frame||geometryPending||(!freshVideo()&&Date.now()-frameAt>3000))throw new Rejected('최신 기기 화면을 확인한 뒤 다시 시도하세요.',409);
            }
            return live.command(data,phone.identity,id);
          });
          json(202,result);return;
        }
        case '/api/activity': {
          const activeAt = sessionNow();
          if (data.confirm === true || idleLeft(session,activeAt) > IDLE_GRACE_MS) session.activeAt = activeAt;
          break;
        }
        case '/api/wireless/pair': {
          if(!pairingAvailable)throw new Rejected('이 기기는 웹 페어링을 지원하지 않습니다.',503);
          if(!Number.isInteger(data.port)||data.port<1||data.port>65535||typeof data.code!=='string'||!/^\d{6}$/.test(data.code))
            throw new Rejected('페어링 팝업의 포트와 6자리 코드를 입력하세요.',400);
          if(pairingPending||Date.now()<nextPairingAt)throw new Rejected('잠시 후 다시 시도하세요.',429);
          pairingPending=true;
          try{
            const result=await control.serial(()=>{
              control.checkMaintenance();
              const current=control.state(id);
              if(current.owner&&current.owner!==id)throw new Rejected('현재 조작자가 연결을 사용 중입니다.',409);
              return wireless.pair(data.port,data.code);
            });
            json(200,result);return;
          }finally{
            pairingPending=false;
            nextPairingAt=Date.now()+2000;
          }
        }
        case '/api/wireless': {
          if(!wireless)throw new Rejected('이 기기는 웹 포트 변경을 지원하지 않습니다',503);
          control.checkMaintenance();
          if(!Number.isInteger(data.port)||data.port<1||data.port>65535)throw new Rejected('포트는 1~65535 사이 숫자로 입력하세요',400);
          const result=await control.serial(()=>{
            control.checkMaintenance();const current=control.state(id);
            if(current.owner&&current.owner!==id)throw new Rejected('현재 조작자만 포트를 변경할 수 있습니다',403);
            return wireless.change(data.port);
          });
          json(202,result);return;
        }
        case '/api/builds/alias':
          if(!catalog) throw new Rejected('APK 보관소가 설정되지 않았습니다',503);
          control.owns(id); control.checkMaintenance();
          if(data.epoch!==control.epoch) throw new Rejected('제어권이 변경됐습니다',409);
          await catalog.alias(data.id,data.alias); break;
        case '/api/builds/install': {
          if(!catalog) throw new Rejected('APK 보관소가 설정되지 않았습니다',503);
          control.owns(id); control.checkMaintenance();
          if (applogTask) throw new Rejected('앱 로그를 읽고 있습니다. 잠시 후 다시 설치하세요.',409);
          if(live&&(await live.status()).busy)throw new Rejected('격리 QA 실행을 종료한 뒤 APK를 설치하세요.',409);
          if(applogTask)throw new Rejected('앱 로그를 읽고 있습니다. 잠시 후 다시 설치하세요.',409);
          const build=catalog.list().builds.find(v=>v.id===data.id);
          if(!build) throw new Rejected('APK를 찾을 수 없습니다',404);
          const drained=control.beginMaintenance(id,data.epoch);
          installedStatus='installing';
          job={status:'installing',id:build.id,startedAt:new Date().toISOString(),error:''};
          installTask=(async()=>{
            try {
              await drained; if(capturing) await capturing;
              if(inputFault || !control.online) throw new Error('device disconnected before install');
              const file=await catalog.file(build.id);
              installed=await phone.install(file,build); installedStatus='installed'; installedChecked=Date.now();
              job={...job,status:'success',finishedAt:new Date().toISOString()};
            } catch(error) {
              job={...job,status:'failed',error:error.message==='APK 적용 실패 · 폰 연결 또는 Android 설치 상태를 확인하세요'||/서명이 달라|이전 버전 설치/.test(error.message)?error.message:'APK 적용 실패 · 파일 무결성 또는 폰 연결을 확인하세요',finishedAt:new Date().toISOString()};
              installed=null; installedStatus='unavailable'; installedChecked=0;
            } finally { frame=null; frameAt=0; control.endMaintenance(); installTask=null; }
          })();
          json(202,{ok:true}); return;
        }
        case '/api/request': await control.request(id); break;
        case '/api/reject': await control.reject(id,data.target); break;
        case '/api/transfer': await control.transfer(id, data.target); break;
        case '/api/release': await control.release(id); break;
        case '/api/input': await control.input(id, data); break;
        case '/api/logout':
          await endSession(secret, session);
          res.setHeader('Set-Cookie', `${cookieName}=; HttpOnly; SameSite=Strict; Path=${basePath}; Max-Age=0${url.startsWith('https:') ? '; Secure' : ''}`); break;
        default: throw new Rejected('not found', 404);
      }
      json(200, { ok: true });
    } catch (error) {
      if (!res.headersSent && !res.destroyed) json(error instanceof Rejected ? error.status : 500, { error: error instanceof Rejected ? error.message : 'relay error' });
    }
  });
  server.requestTimeout = 5000; server.headersTimeout = 5000; server.maxHeadersCount = 32;
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  try {
    url = origin || `http://127.0.0.1:${server.address().port}`;
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol) || parsed.origin !== url) throw new Error('QA_ORIGIN must be an exact HTTP(S) origin without a trailing slash');
  } catch (e) { server.close(); throw e; }
  await capture();
  if(catalog && phone.installed && control.online) { try { installed=await phone.installed(); installedStatus=installed?'installed':'absent'; installedChecked=Date.now(); } catch { installedStatus='unavailable'; } }
  if(mirror) { syncAt=Date.now(); void mirror.sync(); }
  async function tick() {
    if (closed) return;
    const now = sessionNow();
    for (const [secret, s] of sessions) if (expired(s,now)) await endSession(secret,s);
    await control.sweep();syncScreen();
    await recoverInput();
    if(mirror && Date.now()-syncAt>60000) { syncAt=Date.now(); void mirror.sync(); }
    if(catalog && phone.installed && !control.maintenance && control.online && Date.now()-installedChecked>15000) {
      const checkedAt=Date.now(); installedChecked=checkedAt;
      try { const actual=await phone.installed(); if(!control.maintenance && installedChecked===checkedAt) {installed=actual;installedStatus=actual?'installed':'absent';} } catch { if(!control.maintenance && installedChecked===checkedAt) {installed=null;installedStatus='unavailable';} }
    }
    // One shared capture, independent of viewer count. No viewers means no repeated capture.
    if(freshVideo()&&frame&&!inputFault&&!control.online)await control.setOnline(true);
    if (control.state(null).peers.length && (!freshVideo() || Date.now()-fallbackAt<3000)) await capture();
  }
  function schedule() {
    timer = setTimeout(() => tick().catch(() => {}).finally(() => { if (!closed) schedule(); }), 700);
  }
  schedule();
  return { url, publicUrl: url + basePath, control, async close() {
    if(installedLogs)await installedLogs.close();
    closed = true; clearTimeout(timer); if(screenKeeper)await screenKeeper.close(); if(mirror?.close)await mirror.close();if(catalog)await catalog.tail; if(video)await video.stop(); if(installTask) await installTask; await control.tail; await control.setOnline(false);
    if (applogTask) await applogTask.catch(() => {});
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
    if (capturing) await capturing;
  }};
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const phone = new AdbPhone({ adb: process.env.QA_ADB || 'adb', serial: process.env.QA_SERIAL, identity:process.env.QA_EXPECTED_IDENTITY||null, textHelper:process.env.QA_SCREEN_SERVER, port: Number(process.env.QA_ADB_PORT || 5037) });
    const catalog=process.env.QA_APK_DIR ? await Catalog.open(process.env.QA_APK_DIR) : null;
    const mirror=catalog && process.env.QA_APK_SSH_HOST ? createMirror(catalog,process.env.QA_APK_SSH_HOST,process.env.QA_SSH_CONFIG) : null;
    const video=process.env.QA_VIDEO_SERVER ? new PhoneVideo(phone,process.env.QA_VIDEO_SERVER) : null;
    const screenKeeper=process.env.QA_SCREEN_SERVER ? new ScreenKeeper(phone,process.env.QA_SCREEN_SERVER) : null;
    const wirelessFile=process.env.QA_WIRELESS_STATE,execute=promisify(execFile);
    const wireless=wirelessFile?{
      state:()=>wirelessPortState(wirelessFile),
      change:port=>queueWirelessPort({file:wirelessFile,port,run:async args=>(await execute(phone.adb,['-P',String(phone.port),...args],{windowsHide:true,timeout:4000,maxBuffer:256*1024})).stdout}),
      pair:(!process.env.QA_ORIGIN||process.env.QA_ORIGIN.startsWith('https://'))?(port,code)=>pairWireless({file:wirelessFile,port,code,adb:phone.adb,adbPort:phone.port,resumeAfterPairing:true}):null,
    }:null;
    const applog = process.env.QA_APPLOG_TOKEN || process.env.QA_APPLOG_DEVICE_IDENTITY ?
      { token: process.env.QA_APPLOG_TOKEN, identity: process.env.QA_APPLOG_DEVICE_IDENTITY } : null;
    delete process.env.QA_APPLOG_TOKEN;
    delete process.env.QA_APPLOG_DEVICE_IDENTITY;
    const live=await loadLiveClient(process.env.QA_LIVE_CONFIG);
    const relay = await startRelay({ phone, catalog, mirror, video, screenKeeper, wireless, applog, live, port: Number(process.env.QA_PORT || 17680), origin: process.env.QA_ORIGIN, basePath: process.env.QA_BASE_PATH || '/', deviceId:process.env.QA_DEVICE_ID || 'phone', deviceName:process.env.QA_DEVICE_NAME || '휴대폰', trustProxy: process.env.QA_TRUST_PROXY === '1' });
    console.log(`QA phone relay: ${relay.publicUrl} (loopback only; audio stays on phone)`);
    let stopping = false, stopTimer;
    const stop = async () => { if (stopping) return; stopping = true; clearInterval(stopTimer); await relay.close();if(process.env.QA_STOP_FILE)await unlink(process.env.QA_STOP_FILE).catch(()=>{}); process.exit(0); };
    if(process.env.QA_STOP_FILE)stopTimer=setInterval(()=>{void access(process.env.QA_STOP_FILE).then(stop).catch(()=>{});},500);
    process.on('SIGINT', stop); process.on('SIGTERM', stop);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
