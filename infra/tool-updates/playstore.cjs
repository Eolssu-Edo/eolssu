'use strict';

const fs=require('node:fs/promises');
const {randomUUID}=require('node:crypto');
const PLAYSTORE_URL='https://www.doply.io/ko/dashboard/closed-testing/6679';

function validatePlayStoreLink(input) {
 const invalid=()=>new Error('Doply에서 받은 비공개 출시 링크를 붙여넣어 주세요.');
 if(typeof input!=='string'||input.length>2048)throw invalid();
 let url;
 try{url=new URL(input.trim());}catch{throw invalid();}
 const base=new URL(PLAYSTORE_URL);
 if(url.origin!==base.origin||url.pathname!==base.pathname||url.username||url.password||url.hash||
    [...url.searchParams].length!==1||!/^dply_k_[A-Za-z0-9_-]{1,512}$/.test(url.searchParams.get('kmong_key')||''))throw invalid();
 return url.href;
}

function playStoreLinkStore(file,safeStorage,bundledFile) {
 async function write(link) {
  const temporary=file+'.'+randomUUID()+'.pending';
  try {
   if(!await safeStorage.isAsyncEncryptionAvailable())throw new Error('암호화 저장소 없음');
   const encrypted=await safeStorage.encryptStringAsync(link);
   await fs.writeFile(temporary,encrypted,{flag:'wx',mode:0o600});
   await fs.rename(temporary,file);
  }catch{throw new Error('기기의 암호화 저장소에 출시 링크를 저장하지 못했습니다.');}
  finally{await fs.rm(temporary,{force:true});}
 }
 return {
  async load() {
   try {
    const encrypted=await fs.readFile(file);
    const {result,shouldReEncrypt}=await safeStorage.decryptStringAsync(encrypted);
    const link=validatePlayStoreLink(result);
    if(shouldReEncrypt)await write(link);
    return link;
   }catch(error) {
    if(error.code==='ENOENT'){
     if(!bundledFile)return null;
     try{return validatePlayStoreLink(await fs.readFile(bundledFile,'utf8'));}
     catch(bundledError){
      if(bundledError.code==='ENOENT')return null;
      throw new Error('팀 출시 링크를 읽지 못했습니다.');
     }
    }
    throw new Error('저장된 출시 링크를 읽지 못했습니다.');
   }
  },
  save(input){return write(validatePlayStoreLink(input));}
 };
}

module.exports={PLAYSTORE_URL,validatePlayStoreLink,playStoreLinkStore};
