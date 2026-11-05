'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
async function terminalShortcut(host,directory,platform=process.platform) {
 if(!['edo1','edo2'].includes(host))throw new Error('지원하지 않는 서버입니다.');
 if(!['darwin','win32'].includes(platform))throw new Error('Windows와 Mac에서 사용할 수 있습니다.');
 await fs.mkdir(directory,{recursive:true});
 const windows=platform==='win32';
 const file=path.join(directory,host+(windows?'.cmd':'.command'));
 const command=windows?'@echo off\r\ntitle Eolssu '+host+'\r\nssh -o ClearAllForwardings=yes '+host+'\r\npause\r\n':'#!/bin/sh\nexec /usr/bin/ssh -o ClearAllForwardings=yes '+host+'\n';
 await fs.writeFile(file,command,{mode:0o700});
 if(!windows)await fs.chmod(file,0o700);
 return file;
}
module.exports={terminalShortcut};
