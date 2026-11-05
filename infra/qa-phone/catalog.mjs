import { readFile, writeFile, mkdir, rename, rm } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { join, resolve } from 'node:path';
import { Rejected } from './control.mjs';
export const validId = id => typeof id === 'string' && /^\d{8}T\d{6}Z-\d{1,10}-[a-f0-9]{8}$/.test(id);
export function validateBuild(v) {
  if (!v || !validId(v.id) || !/^[a-f0-9]{40}$/.test(v.commit) || !Number.isSafeInteger(v.build) || v.build < 1 ||
      v.package !== 'com.ssafy.eolssu.qa' || !Number.isSafeInteger(v.versionCode) || v.versionCode < 1 || v.versionCode > 2100000000 ||
      typeof v.versionName !== 'string' || !/^[A-Za-z0-9._+-]{1,100}$/.test(v.versionName) || !/^[a-f0-9]{64}$/.test(v.sha256) ||
      !Number.isSafeInteger(v.size) || v.size < 1 || v.size > 300*1024*1024 ||
      typeof v.builtAt !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(v.builtAt) || !Number.isFinite(Date.parse(v.builtAt))) throw new Rejected('잘못된 APK 빌드 정보',400);
  return Object.fromEntries(['id','commit','build','package','versionCode','versionName','sha256','size','builtAt'].map(k=>[k,v[k]]));
}
export function validateMergeRequests(value={}){
  if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).length>10000)throw Error('invalid MR metadata');
  for(const [commit,mr] of Object.entries(value))if(!/^[a-f0-9]{40}$/.test(commit)||!Number.isSafeInteger(mr)||mr<1||mr>2147483647)throw Error('invalid MR metadata');
  return {...value};
}
export function validateMergeRequestDetails(value={}) {
  if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).length>10000)throw Error('invalid MR details');
  const clean={};
  for(const [iid,detail] of Object.entries(value)){
    if(!/^[1-9][0-9]{0,9}$/.test(iid)||Number(iid)>2147483647||!detail||typeof detail!=='object')throw Error('invalid MR details');
    for(const [key,limit] of [['authorName',128],['title',512]])if(typeof detail[key]!=='string'||!detail[key].trim()||detail[key].length>limit||/[\p{C}]/u.test(detail[key]))throw Error('invalid MR details');
    clean[iid]={authorName:detail.authorName.trim(),title:detail.title.trim()};
  }
  return clean;
}
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
async function atomic(file,value) { const temp=file+'.'+randomUUID()+'.tmp'; try { await writeFile(temp,JSON.stringify(value,null,2)); await rename(temp,file); } finally { await rm(temp,{force:true}); } }
export class Catalog {
  static async open(root) {
    const c=new Catalog(); c.root=resolve(root); c.tail=Promise.resolve(); await mkdir(c.root,{recursive:true});
    try { c.index=JSON.parse(await readFile(join(c.root,'index.json'),'utf8')); }
    catch(e) { if(e.code!=='ENOENT') throw e; c.index={builds:[],aliases:{}}; }
    if(!Array.isArray(c.index.builds) || c.index.builds.length>10000 || !c.index.aliases || typeof c.index.aliases!=='object') throw new Error('invalid APK catalog');
    c.index.builds=c.index.builds.map(validateBuild);c.index.mergeRequests=validateMergeRequests(c.index.mergeRequests);c.index.mergeRequestDetails=validateMergeRequestDetails(c.index.mergeRequestDetails); return c;
  }
  serialize(fn) { const r=this.tail.then(fn); this.tail=r.catch(()=>{}); return r; }
  list() { const builds=[...this.index.builds].sort((a,b)=>b.builtAt.localeCompare(a.builtAt) || b.build-a.build).map(v=>({...v,mergeRequest:this.index.mergeRequests[v.commit]||null,authorName:this.index.mergeRequestDetails[this.index.mergeRequests[v.commit]]?.authorName||'',mergeRequestTitle:this.index.mergeRequestDetails[this.index.mergeRequests[v.commit]]?.title||'',alias:this.index.aliases[v.id]||''})); return {builds,latest:builds[0]?.id||null}; }
  async save(next) { await atomic(join(this.root,'index.json'),next); this.index=next; }
  import(raw,source) { return this.serialize(async()=>{
    const v=validateBuild(raw), old=this.index.builds.find(x=>x.id===v.id);
    if(old) { if(JSON.stringify(old)!==JSON.stringify(v)) throw new Error('immutable build conflict'); await this.file(v.id); return; }
    const bytes=await readFile(source); if(bytes.length!==v.size || digest(bytes)!==v.sha256) throw new Error('APK checksum mismatch');
    const dir=join(this.root,v.id); await mkdir(dir,{recursive:true});
    const temp=join(dir,'app.'+randomUUID()+'.tmp');
    try { await writeFile(temp,bytes,{flag:'wx'}); await rename(temp,join(dir,'app.apk')); await this.save({...this.index,builds:[...this.index.builds,v]}); }
    finally { await rm(temp,{force:true}); }
  }); }
  recordMergeRequests(values){return this.serialize(async()=>{
    const incoming=validateMergeRequests(values),current=this.index.mergeRequests;
    for(const [commit,mr] of Object.entries(incoming))if(current[commit]&&current[commit]!==mr)throw Error('MR metadata conflict');
    if(Object.keys(incoming).some(commit=>!current[commit]))await this.save({...this.index,mergeRequests:{...current,...incoming}});
  });}
  recordMergeRequestDetails(values){return this.serialize(async()=>{
    const incoming=validateMergeRequestDetails(values);
    if(Object.entries(incoming).some(([id,v])=>JSON.stringify(this.index.mergeRequestDetails[id])!==JSON.stringify(v)))await this.save({...this.index,mergeRequestDetails:{...this.index.mergeRequestDetails,...incoming}});
  });}
  alias(id,value) { return this.serialize(async()=>{
    if(!validId(id) || !this.index.builds.some(v=>v.id===id)) throw new Rejected('APK를 찾을 수 없습니다',404);
    if(typeof value!=='string' || value.length>80 || /[\p{C}]/u.test(value)) throw new Rejected('별칭은 80자 이내로 입력하세요',400);
    await this.save({...this.index,aliases:{...this.index.aliases,[id]:value.trim()}});
  }); }
  async file(id) {
    const v=this.index.builds.find(x=>x.id===id); if(!validId(id)||!v) throw new Rejected('APK를 찾을 수 없습니다',404);
    const file=join(this.root,id,'app.apk'),bytes=await readFile(file);
    if(bytes.length!==v.size || digest(bytes)!==v.sha256) throw new Error('보관 APK 무결성 검사 실패'); return file;
  }
}
