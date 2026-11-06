'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {createConnection}=require('node:net');
const {setTimeout:delay}=require('node:timers/promises');
async function terminalShortcut(host,directory,platform=process.platform,forwards=[]) {
 if(!['edo1','edo2'].includes(host))throw new Error('지원하지 않는 서버입니다.');
 if(!['darwin','win32'].includes(platform))throw new Error('Windows와 Mac에서 사용할 수 있습니다.');
 if(!Array.isArray(forwards)||forwards.length>16||(forwards.length&&host!=='edo1')||
    !forwards.every(forward=>[forward?.local,forward?.remote].every(port=>Number.isInteger(port)&&port>0&&port<=65535)))
  throw new Error('지원하지 않는 연결 포트입니다.');
 const tunneled=forwards.length>0;
 await fs.mkdir(directory,{recursive:true});
 const windows=platform==='win32';
 const file=path.join(directory,host+(windows?'.cmd':'.command'));
 const localArgs=forwards.map(({local,remote})=>'-L 127.0.0.1:'+local+':127.0.0.1:'+remote).join(' ');
 const args=(tunneled?'-o ClearAllForwardings=no -o ExitOnForwardFailure=no '+localArgs:'-o ClearAllForwardings=yes')+' '+host;
 const message=tunneled?(windows?'echo Keep this SSH session open while using the tool tab.\r\n':'echo "Keep this SSH session open while using the tool tab."\n'):'';
 const command=windows?'@echo off\r\ntitle Eolssu '+host+'\r\n'+message+'ssh '+args+'\r\npause\r\n':'#!/bin/sh\n'+message+'exec /usr/bin/ssh '+args+'\n';
 await fs.writeFile(file,command,{mode:0o700});
 if(!windows)await fs.chmod(file,0o700);
 return file;
}
function probeLoopback(port,timeout) {
 return new Promise(resolve=>{
  const socket=createConnection({host:'127.0.0.1',port});
  const finish=connected=>{socket.destroy();resolve(connected);};
  socket.setTimeout(timeout);
  socket.once('connect',()=>finish(true));
  socket.once('timeout',()=>finish(false));
  socket.once('error',()=>finish(false));
 });
}

async function waitForLoopback(port,timeout=12000) {
 const end=Date.now()+timeout;
 while(Date.now()<end){
  if(await probeLoopback(port,Math.min(350,Math.max(1,end-Date.now()))))return true;
  const remaining=end-Date.now();
  if(remaining>0)await delay(Math.min(250,remaining));
 }
 return false;
}

module.exports={terminalShortcut,waitForLoopback};
