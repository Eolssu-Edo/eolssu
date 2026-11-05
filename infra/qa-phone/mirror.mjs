import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join } from 'node:path';
import { rm } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { validateBuild } from './catalog.mjs';
const execute=promisify(execFile);
// The server address and archive location are operator configuration, never browser input.
export function createMirror(catalog,host='edo1') {
  if(!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(host)) throw new Error('invalid APK SSH host');
  const abort=new AbortController();let closing=false;
  let pending=null; let error='',updatedAt=null;
  const options={signal:abort.signal,windowsHide:true,timeout:120000,maxBuffer:4*1024*1024};
  const sshOptions=['-o','BatchMode=yes','-o','ClearAllForwardings=yes','-o','ConnectTimeout=10'];
  return {
    state:()=>({error,updatedAt}),
    async close(){closing=true;abort.abort();if(pending)await pending;},
    sync() {
      if(closing)return Promise.resolve();
      if(pending) return pending;
      pending=(async()=>{
        try {
          const {stdout}=await execute('ssh',[...sshOptions,host,'cat /opt/eolssu/apks/index.json'],options);
          const manifest=JSON.parse(stdout); if(!Array.isArray(manifest.builds)||manifest.builds.length>10000) throw new Error('invalid remote catalog');
          const builds=manifest.builds.map(validateBuild);
          for(const v of builds) {
            if(catalog.list().builds.some(x=>x.id===v.id)) continue;
            const file=join(catalog.root,randomUUID()+'.download');
            try {
              await execute('scp',[...sshOptions,`${host}:/opt/eolssu/apks/${v.id}/app.apk`,file],options);
              await catalog.import(v,file);
            } finally { await rm(file,{force:true}); }
          }
          await catalog.recordMergeRequests(manifest.mergeRequests||{});
          await catalog.recordMergeRequestDetails(manifest.mergeRequestDetails||{});
          updatedAt=new Date().toISOString(); error='';
        } catch { error='EDO1 APK 목록 동기화 실패 · 마지막으로 저장된 목록을 표시합니다'; }
      })().finally(()=>pending=null);
      return pending;
    }
  };
}
