import test from 'node:test';
import assert from 'node:assert/strict';
import {latestApkUpdate} from '../public/apk-update.js';
const build={id:'latest',package:'com.ssafy.eolssu.qa',versionCode:10,versionName:'qa-10'};
const catalog={builds:[build,{...build,id:'older',versionCode:9}],latest:'latest',installedStatus:'absent',installed:null,job:{status:'idle'},sync:{error:''}};
const state={online:true,inputFault:false,maintenance:false,owner:'alice',you:'alice'};
test('latest update targets the catalog latest regardless of a historical selection',()=>{
 const result=latestApkUpdate(catalog,state);assert.equal(result.enabled,true);assert.equal(result.build.id,'latest');
 assert.equal(latestApkUpdate({...catalog,latest:'missing'},state).enabled,false);
});
test('only an online healthy current controller can start an update',()=>{
 for(const next of [null,{...state,owner:'bob'},{...state,online:false},{...state,inputFault:true},{...state,maintenance:true}])assert.equal(latestApkUpdate(catalog,next).enabled,false);
 assert.equal(latestApkUpdate(catalog,state,true).enabled,false);
});
test('stale catalog, unknown installed state and installation in progress prevent duplicates',()=>{
 for(const next of [{...catalog,sync:{error:'failed'}},{...catalog,installedStatus:'checking'},{...catalog,installedStatus:'unavailable'},{...catalog,job:{status:'installing'}}])assert.equal(latestApkUpdate(next,state).enabled,false);
});
test('same package and both version fields identify already installed latest',()=>{
 const installed={...build};let result=latestApkUpdate({...catalog,installed,installedStatus:'installed'},state);
 assert.equal(result.enabled,false);assert.equal(result.label,'최신 버전 사용 중');
 for(const changed of [{...installed,versionCode:9},{...installed,versionName:'qa-9'},{...installed,package:'different'}])assert.equal(latestApkUpdate({...catalog,installed:changed,installedStatus:'installed'},state).enabled,true);
});
