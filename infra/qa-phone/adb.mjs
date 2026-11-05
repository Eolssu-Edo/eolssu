import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { validateAction } from './control.mjs';
import {pasteUnicode} from './unicode-input.mjs';
const execute = promisify(execFile);
export class AdbPhone {
  constructor({ adb = 'adb', serial, port = 5037, textHelper = null, identity = null, executor = execute }) {
    if (!serial || !/^[A-Za-z0-9._:-]+$/.test(serial) || serial.startsWith('emulator-')) throw new Error('Select an explicit physical phone serial with QA_SERIAL');
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('invalid ADB port');
    if(identity!==null&&!/^[A-Za-z0-9_-]{4,80}$/.test(identity))throw Error('invalid expected device identity');
    this.adb = adb; this.serial = serial; this.port = port; this.size = null;this.textHelper=textHelper;
    this.identity=identity;this.executor=executor;this.transportId=null;this.bindingTask=null;
  }
  async execute(args, binary = false, timeout = 3000) {
    const { stdout } = await this.executor(this.adb, args, {
      encoding: binary ? 'buffer' : 'utf8', timeout, maxBuffer: 20 * 1024 * 1024, windowsHide: true,
    });
    return stdout;
  }
  async deviceArgs(){
    if(this.bindingTask)return this.bindingTask;
    this.bindingTask=(async()=>{
      const prefix=['-P',String(this.port)];
      let transport=this.transportId;
      if(!transport){
        const rows=(await this.execute([...prefix,'devices','-l'])).split(/\r?\n/).map(line=>line.trim().split(/\s+/));
        const matches=rows.filter(row=>row[0]===this.serial&&row[1]==='device');
        if(matches.length!==1)throw Error('selected device unavailable');
        transport=matches[0].find(field=>/^transport_id:[1-9][0-9]{0,19}$/.test(field))?.split(':')[1];
        if(!transport)throw Error('ADB transport identity unavailable');
      }
      const selection=[...prefix,'-t',transport];
      const actual=(await this.execute([...selection,'shell','getprop','ro.serialno'])).trim();
      if(!/^[A-Za-z0-9_-]{4,80}$/.test(actual)||(this.identity&&actual!==this.identity))throw Error('selected device identity mismatch');
      this.identity=actual;this.transportId=transport;return selection;
    })().catch(error=>{this.transportId=null;this.size=null;throw error;}).finally(()=>{this.bindingTask=null;});
    return this.bindingTask;
  }
  async run(args, binary = false, timeout = 3000) {
    const selection=await this.deviceArgs();
    try{return await this.execute([...selection,...args],binary,timeout);}
    catch(error){this.transportId=null;this.size=null;throw error;}
  }
  async inputReady() {
    // A lost ADB client must not leave a phone-side input command running.
    const table=await this.run(['shell','ps','-A','-o','ARGS']);
    if(!/^ARGS\s*(?:\r?\n)/.test(table))return false;
    return !/\bcom\.android\.commands\.input\.Input\b|\bcom\.ssafy\.qa\.UnicodeText\b|(?:^|[\s/])input(?:\s|$)/m.test(table);
  }
  async geometry(videoSize) {
    const text=await this.run(['shell','wm','size']);
    const sizes=[...text.matchAll(/(?:Physical|Override) size:\s*(\d+)x(\d+)/g)];
    if(!sizes.length)throw Error('display geometry unavailable');
    let width=Number(sizes.at(-1)[1]),height=Number(sizes.at(-1)[2]);
    if(!width||!height||width>8192||height>8192)throw Error('invalid display geometry');
    if((videoSize.width>videoSize.height)!==(width>height))[width,height]=[height,width];
    this.size={width,height};return this.size;
  }
  async installed(packageName='com.ssafy.eolssu.qa') {
    if(packageName !== 'com.ssafy.eolssu.qa') throw new Error('unsupported package');
    const text=await this.run(['shell','dumpsys','package',packageName]);
    const code=/versionCode=(\d+)/.exec(text),name=/versionName=([^\r\n]+)/.exec(text);
    return code?{package:packageName,versionCode:Number(code[1]),versionName:name?.[1]?.trim()||''}:null;
  }
  async install(file,build) {
    if(build.package !== 'com.ssafy.eolssu.qa') throw new Error('unsupported package');
    try {
      const result=await this.run(['install','-r','-d',file],false,120000);
      if(!/\bSuccess\b/.test(result)) throw new Error(result);
      const actual=await this.installed(build.package);
      if(actual?.versionCode!==build.versionCode || actual.versionName!==build.versionName) throw new Error('installed version mismatch');
      await this.run(['shell','monkey','-p',build.package,'-c','android.intent.category.LAUNCHER','1'],false,10000);
      return actual;
    } catch(e) {
      const text=String(e.stdout||'')+' '+String(e.stderr||'')+' '+e.message;
      if(/UPDATE_INCOMPATIBLE|signatures do not match/.test(text)) throw new Error('서명이 달라 설치할 수 없습니다. 기존 앱과 데이터는 보존했습니다.');
      if(/VERSION_DOWNGRADE/.test(text)) throw new Error('이 기기에서 이전 버전 설치가 거부됐습니다. 기존 앱과 데이터는 보존했습니다.');
      throw new Error('APK 적용 실패 · 폰 연결 또는 Android 설치 상태를 확인하세요');
    }
  }
  async capture() {
    const data = await this.run(['exec-out', 'screencap', '-p'], true);
    if (data.length < 24 || !data.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw new Error('invalid screenshot');
    const width = data.readUInt32BE(16), height = data.readUInt32BE(20);
    if (!width || !height || width > 8192 || height > 8192) throw new Error('invalid screen size');
    this.size = { width, height };
    return { data, width, height };
  }
  async input(raw, packet) {
    const a = validateAction(raw);
    if (!this.size || packet.width !== this.size.width || packet.height !== this.size.height) throw new Error('screen geometry changed');
    const x = n => String(Math.round(n * (this.size.width - 1)));
    const y = n => String(Math.round(n * (this.size.height - 1)));
    let args;
    if (a.kind === 'tap') args = ['tap', x(a.x), y(a.y)];
    if (a.kind === 'swipe') args = ['swipe', x(a.x), y(a.y), x(a.x2), y(a.y2), String(a.duration)];
    if (a.kind === 'key') args = ['keyevent', `KEYCODE_${a.key}`];
    // Android's remote shell also parses arguments. This whitelist excludes all shell operators/quotes.
    if(a.kind==='text'){
      if(!/^[A-Za-z0-9 @._+,:/=-]+$/.test(a.text)){if(!this.textHelper)throw Error('Unicode input helper required');return pasteUnicode(this,this.textHelper,a.text);}
      args=['text',a.text.replaceAll(' ','%s')];
    }
    await this.run(['shell', 'input', ...args]);
  }
}
