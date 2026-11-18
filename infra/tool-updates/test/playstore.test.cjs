'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const {PLAYSTORE_URL,validatePlayStoreLink,playStoreLinkStore}=require('../playstore.cjs');
const bundled=PLAYSTORE_URL+'?kmong_key=dply_k_test_bundle';
const replacement=PLAYSTORE_URL+'?kmong_key=dply_k_test_replacement';

test('a team bundle opens directly and a device can replace its link with an encrypted override',async()=>{
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'playstore-links-'));
 try {
  const packaged=path.join(directory,'bundled'),saved=path.join(directory,'encrypted');
  await fs.writeFile(packaged,bundled);
  let encryptedValue;
  const provider={
   isAsyncEncryptionAvailable:async()=>true,
   encryptStringAsync:async value=>{encryptedValue=value;return Buffer.from('encrypted data');},
   decryptStringAsync:async bytes=>{assert.equal(bytes.toString(),'encrypted data');return {result:encryptedValue};}
  };
  const store=playStoreLinkStore(saved,provider,packaged);
  assert.equal(await store.load(),bundled);
  await store.save(replacement);
  assert.equal((await fs.readFile(saved)).toString(),'encrypted data');
  assert.equal(await store.load(),replacement);
 }finally{await fs.rm(directory,{recursive:true,force:true});}
});

test('only the intended Doply HTTPS page accepts a release key',()=>{
 assert.equal(validatePlayStoreLink(bundled),bundled);
 for(const link of [
  bundled.replace('https:','http:'),
  bundled.replace('www.doply.io','www.doply.io.example.com'),
  bundled.replace('/6679','/1234'),
  bundled+'&kmong_key=dply_k_other',
  PLAYSTORE_URL
 ])assert.throws(()=>validatePlayStoreLink(link),error=>!error.message.includes('dply_k_'));
});
