import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AdbPhone } from '../adb.mjs';
test('phone selection is explicit; emulator and shell injection serial rejected', () => {
  for (const serial of [undefined, 'emulator-5554', 'phone;id', 'phone$(id)', 'x y']) assert.throws(() => new AdbPhone({ serial }));
});
test('normalized input maps to screenshot pixels and only allowlisted ADB commands', async () => {
  const phone = new AdbPhone({ serial: 'physical-test' }), calls = [];
  phone.size = { width: 1080, height: 2400 }; phone.run = async args => calls.push(args);
  const packet = { width: 1080, height: 2400 };
  await phone.input({ kind: 'tap', x: 1, y: 1 }, packet);
  await phone.input({ kind: 'text', text: 'test1@test.com hi' }, packet);
  await phone.input({ kind: 'key', key: 'ENTER' }, packet);
  assert.deepEqual(calls, [['shell','input','tap','1079','2399'], ['shell','input','text','test1@test.com%shi'], ['shell','input','keyevent','KEYCODE_ENTER']]);
  await assert.rejects(phone.input({ kind: 'text', text: 'a;reboot' }, packet));
  await assert.rejects(phone.input({ kind: 'tap', x: 0, y: 0 }, { width: 2400, height: 1080 }), /geometry/);
});
test('PNG geometry is read from actual binary screenshot; invalid data rejected', async () => {
  const phone = new AdbPhone({ serial: 'physical-test' });
  const data = Buffer.alloc(24); Buffer.from([137,80,78,71,13,10,26,10]).copy(data); data.writeUInt32BE(1080,16); data.writeUInt32BE(2400,20);
  phone.run = async () => data;
  const frame = await phone.capture(); assert.equal(frame.width,1080); assert.equal(frame.height,2400);
  phone.run = async () => Buffer.from('not png'); await assert.rejects(phone.capture());
});


test('APK signature rejection never uninstalls or clears app data',async()=>{
 const phone=new AdbPhone({serial:'physical-test'}),calls=[];
 phone.run=async args=>{calls.push(args);throw Object.assign(new Error('install failed'),{stdout:'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]'});};
 await assert.rejects(phone.install('/fixture.apk',{package:'com.ssafy.eolssu.qa',versionCode:1,versionName:'qa-1'}),/서명이 달라/);
 assert.deepEqual(calls,[['install','-r','-d','/fixture.apk']]);
});
test('APK apply verifies the installed package and version before opening it',async()=>{
 const phone=new AdbPhone({serial:'physical-test'}),calls=[];
 phone.run=async args=>{calls.push(args);return args[0]==='install'?'Success':'versionCode=12 minSdk=24\nversionName=qa-12\n';};
 const result=await phone.install('/fixture.apk',{package:'com.ssafy.eolssu.qa',versionCode:12,versionName:'qa-12'});
 assert.equal(result.versionCode,12);assert.equal(calls[1][2],'package'); assert.equal(calls[2][1],'monkey');
});

test('video touch geometry uses physical or override display size with current video orientation',async()=>{
 const phone=new AdbPhone({serial:'physical-test'});
 phone.run=async()=> 'Physical size: 1440x3040\nOverride size: 1080x2280\n';
 assert.deepEqual(await phone.geometry({width:720,height:1520}),{width:1080,height:2280});
 assert.deepEqual(await phone.geometry({width:1520,height:720}),{width:2280,height:1080});
 phone.run=async()=> 'not a display';await assert.rejects(phone.geometry({width:720,height:1520}));
});


test('input recovery accepts only a readable process table without lingering Android input helpers',async()=>{
 const phone=new AdbPhone({serial:'physical-test'});
 phone.run=async()=> 'ARGS\ncom.android.systemui\ncom.ssafy.qa.BrightScreen\n';assert.equal(await phone.inputReady(),true);
 for(const args of ['com.android.commands.input.Input text example','app_process / com.android.commands.input.Input','input keyevent KEYCODE_HOME','app_process / com.ssafy.qa.UnicodeText']){
  phone.run=async()=>`ARGS\n${args}\n`;assert.equal(await phone.inputReady(),false);
 }
 phone.run=async()=> 'permission denied';assert.equal(await phone.inputReady(),false);
 phone.run=async()=>{throw Error('ADB offline');};await assert.rejects(phone.inputReady());
});
