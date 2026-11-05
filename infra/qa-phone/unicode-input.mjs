import {spawn} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {randomUUID,createHash} from 'node:crypto';
import {validateAction} from './control.mjs';
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
export async function pasteUnicode(phone,file,text,{launch=spawn}={}){
  validateAction({kind:'text',text});
  const metadata=JSON.parse(await readFile(join(dirname(file),'bright-screen.json'),'utf8'));
  const binary=await readFile(file),source=await readFile(new URL('./android/UnicodeText.java',import.meta.url));
  if(metadata.sha256!==digest(binary)||metadata.textSourceSha256!==digest(source))throw Error('Unicode helper integrity mismatch');
  const remote=`/data/local/tmp/qa290-text-${randomUUID().replaceAll('-','')}.jar`;let child,exited=false;
  try{
    await phone.run(['push',file,remote],false,10000);
    child=launch(phone.adb,['-P',String(phone.port),'-s',phone.serial,'shell',`CLASSPATH=${remote}`,'app_process','/','com.ssafy.qa.UnicodeText'],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    await new Promise((resolve,reject)=>{
      let output='';const timeout=setTimeout(()=>reject(Error('Unicode helper timeout')),5000);
      child.stdout.on('data',chunk=>{output=(output+chunk.toString()).slice(-4096);});child.stderr.resume();
      child.once('error',()=>{clearTimeout(timeout);reject(Error('Unicode helper launch failed'));});
      child.once('exit',code=>{exited=true;clearTimeout(timeout);if(code===0&&/(?:^|\r?\n)READY\r?\n/.test(output))resolve();else reject(Error('Unicode clipboard unavailable'));});
      child.stdin.on('error',()=>{clearTimeout(timeout);reject(Error('Unicode helper input failed'));});
      // User text is never placed in shell arguments or command strings.
      child.stdin.end(Buffer.from(text,'utf8').toString('base64')+'\n');
    });
    await phone.run(['shell','input','keyevent','KEYCODE_PASTE']);
  }finally{
    if(child&&!exited)child.kill();await phone.run(['shell','rm','-f',remote]).catch(()=>{});
  }
}
