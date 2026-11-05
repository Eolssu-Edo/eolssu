'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
async function terminalShortcut(host,directory,platform=process.platform,forward) {
 if(!['edo1','edo2'].includes(host))throw new Error('지원하지 않는 서버입니다.');
 if(!['darwin','win32'].includes(platform))throw new Error('Windows와 Mac에서 사용할 수 있습니다.');
 if(forward&&(host!=='edo1'||![forward.local,forward.remote].every(port=>Number.isInteger(port)&&port>0&&port<=65535)))throw new Error('지원하지 않는 연결 포트입니다.');
 await fs.mkdir(directory,{recursive:true});
 const windows=platform==='win32';
 const file=path.join(directory,host+(forward?'-'+forward.local:'')+(windows?'.cmd':'.command'));
 const args=forward?'-N -o ClearAllForwardings=no -o ExitOnForwardFailure=no -L 127.0.0.1:'+forward.local+':127.0.0.1:'+forward.remote+' '+host:'-o ClearAllForwardings=yes '+host;
 const message=forward?(windows?'echo Keep this window open. Then retry the tool tab.\r\n':'echo "Keep this window open. Then retry the tool tab."\n'):'';
 const command=windows?'@echo off\r\ntitle Eolssu '+host+'\r\n'+message+'ssh '+args+'\r\npause\r\n':'#!/bin/sh\n'+message+'exec /usr/bin/ssh '+args+'\n';
 await fs.writeFile(file,command,{mode:0o700});
 if(!windows)await fs.chmod(file,0o700);
 return file;
}
module.exports={terminalShortcut};
