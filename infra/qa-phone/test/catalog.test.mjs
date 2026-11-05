import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { Catalog } from '../catalog.mjs';
const bytes = Buffer.from('small isolated APK fixture');
const item = { id: '20261030T031000Z-12-abcdef01', builtAt: '2026-10-30T03:10:00Z', commit: 'a'.repeat(40), build: 12, package: 'com.ssafy.eolssu.qa', versionCode: 12, versionName: 'qa-12', sha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length };
async function setup(t) { const root = await mkdtemp(join(tmpdir(), 'qa312-')); t.after(() => rm(root, {recursive:true,force:true})); return { root, catalog: await Catalog.open(root) }; }
test('archive survives restart, aliases persist, bad import preserves previous latest', async t => {
 const {root,catalog} = await setup(t); const file=join(root,'input.apk'); await writeFile(file,bytes);
 await catalog.import(item,file); await catalog.alias(item.id,'녹음 수정 확인');
 const again=await Catalog.open(root); assert.equal(again.list().builds[0].alias,'녹음 수정 확인');
 await assert.rejects(again.import({...item,id:'20261030T031100Z-13-abcdef02',sha256:'0'.repeat(64)},file));
 assert.equal(again.list().latest,item.id); assert.equal(again.list().builds.length,1);
 await assert.rejects(again.alias('../escape','x')); await assert.rejects(again.alias(item.id,'x'.repeat(81)));
 assert.equal(await again.file(item.id),join(root,item.id,'app.apk'));
});
test('same id is immutable and imports are idempotent', async t => {
 const {root,catalog}=await setup(t); const file=join(root,'input.apk'); await writeFile(file,bytes);
 await catalog.import(item,file); await catalog.import(item,file); assert.equal(catalog.list().builds.length,1);
 await assert.rejects(catalog.import({...item,versionCode:99},file));
 await writeFile(join(root,item.id,'app.apk'),'tampered'); await assert.rejects(catalog.file(item.id));
});

test('MR names are commit metadata, preserve immutable APKs and survive restart',async t=>{
 const {root,catalog}=await setup(t),file=join(root,'input.apk');await writeFile(file,bytes);await catalog.import(item,file);
 await catalog.recordMergeRequests({[item.commit]:191});const again=await Catalog.open(root);
 assert.equal(again.list().builds[0].mergeRequest,191);assert.equal(await again.file(item.id),join(root,item.id,'app.apk'));
 await assert.rejects(again.recordMergeRequests({[item.commit]:192}),/conflict/);
 await assert.rejects(again.recordMergeRequests({'../bad':3}));assert.equal(again.list().builds[0].mergeRequest,191);
});


test('MR author and title survive restart without changing archived APK identity',async t=>{
 const {root,catalog}=await setup(t),file=join(root,'input.apk');await writeFile(file,bytes);await catalog.import(item,file);
 await catalog.recordMergeRequests({[item.commit]:328});
 await catalog.recordMergeRequestDetails({'328':{authorName:'박찬형',title:'검수 장단 음원 API 제공'}});
 const again=await Catalog.open(root),entry=again.list().builds[0];
 assert.equal(entry.authorName,'박찬형');assert.equal(entry.mergeRequestTitle,'검수 장단 음원 API 제공');
 assert.equal(entry.sha256,item.sha256);assert.equal(await again.file(item.id),join(root,item.id,'app.apk'));
 await assert.rejects(again.recordMergeRequestDetails({'../bad':{authorName:'a',title:'b'}}));
 await assert.rejects(again.recordMergeRequestDetails({'328':{authorName:'a\nb',title:'title'}}));
});
