const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const root = require('node:path').join(__dirname, '../web/');
class Element {
  constructor(id = '') { this.id=id; this.value=''; this.children=[]; this.hidden=false; this.checked=false; this.disabled=false; this.textContent=''; this.dataset={}; this.attributes={}; this.classList={toggle(){}}; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children=nodes; }
  setAttribute(k,v) { this.attributes[k]=v; }
  querySelectorAll() { return []; }
  pause() { this.paused=true; }
  async play() { this.paused=false; }
}
const elements={};
const get=id=>elements[id] ||= new Element(id);
for (const id of fs.readFileSync(root+'index.html','utf8').matchAll(/id="([^"]+)"/g)) get(id[1]);
get('vibrato').value='quarter'; get('notation').value='staff'; get('instrument').value='danso';
const score={schema:'gugak-reviewed-score/v1',title:'A source score',quarter_bpm:60,context:{pitch_mode:'sounding'},notes:[{id:'n1',pitch:75,duration:'1'}],source:{edition:'test'}};
const oldResult={instrument:'danso',instrument_label:'단소',vibrato:'quarter',review_mode:'automatic-preview',seconds:1,timeline:[],audio_url:'/old/danso.wav',midi_url:'/old/danso.mid',score_url:'/old/score.json',warnings:['첫 결과 경고'],credit:'source'};
let serverJob={id:'a'.repeat(32),notation:'staff',instrument:'danso',revision:1,score,regions:[],warnings:[],image_url:'/photo.png',result:oldResult};
const catalog=[{id:'danso',label:'단소',available:true,message:'단소 음원',range:[66,92]},{id:'jeongak-daegeum',label:'정악대금',available:true,message:'정악대금·산조대금은 같은 CAT 대금 음원을 사용합니다.',range:[58,92]},{id:'sanjo-daegeum',label:'산조대금',available:true,message:'정악대금·산조대금은 같은 CAT 대금 음원을 사용합니다.',range:null},{id:'sogeum',label:'소금',available:false,message:'소금 파일 없음',range:[70,96]}];
const requests=[]; let failPreview=false,failRender=false;
const context={console,Image:class{constructor(){this.complete=false;}},document:{getElementById:get,querySelectorAll:()=>Object.values(elements),createElement:()=>new Element(),createTextNode:text=>({textContent:text})},location:{hash:'#'+serverJob.id},crypto:{randomUUID:()=> 'added-note'},setTimeout,clearTimeout,setInterval,clearInterval,URL,Blob,
  fetch:async(url,options={})=>{requests.push({url,options,body:options.body && typeof options.body==='string'?JSON.parse(options.body):null}); let value;
    if(url==='/api/config') value={recognition_configured:true,default_instrument:'danso',instruments:catalog};
    else if(url.endsWith('/preview')) { if(failPreview) return {ok:false,json:async()=>({detail:'선택 악기 음역 초과'})}; const b=JSON.parse(options.body); serverJob={...serverJob,instrument:b.instrument,result:{...oldResult,instrument:b.instrument,instrument_label:catalog.find(x=>x.id===b.instrument).label,vibrato:b.vibrato,audio_url:'/new/'+b.instrument+'.wav',midi_url:'/new/'+b.instrument+'.mid',warnings:['새 결과 경고']}}; value=serverJob; }
    else if(url.endsWith('/draft')) { const b=JSON.parse(options.body); serverJob={...serverJob,...b,revision:serverJob.revision+1}; value=serverJob; }
    else if(url.endsWith('/render')) {if(failRender)return {ok:false,json:async()=>({detail:'선택 악기 파일 오류'})};const b=JSON.parse(options.body); value={...oldResult,instrument:b.instrument,instrument_label:catalog.find(x=>x.id===b.instrument).label,vibrato:b.vibrato,review_mode:'user-confirmed'};}
    else if(url.startsWith('/api/scores?')) {const parsed=new URL(url,'http://local'); serverJob={...serverJob,notation:parsed.searchParams.get('notation'),instrument:parsed.searchParams.get('instrument'),result:null};value=serverJob;}
    else if(url.endsWith('/automatic')) {const b=JSON.parse(options.body); serverJob={...serverJob,instrument:b.instrument,result:{...oldResult,instrument:b.instrument,instrument_label:catalog.find(x=>x.id===b.instrument).label,vibrato:b.vibrato}};value=serverJob;}
    else value=serverJob;
    return {ok:true,json:async()=>structuredClone(value)};
  }};
vm.createContext(context);vm.runInContext(fs.readFileSync(root+'app.js','utf8'),context);
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
  await tick(); await tick();
  assert.equal(get('instrument').children.length,4,'all four known choices must be shown');
  assert.equal(get('instrument').children[3].disabled,true);
  assert.match(get('instrument').children[3].textContent,/소금 파일 없음/);
  const before=vm.runInContext('JSON.stringify(job.score)',context);
  get('instrument').value='jeongak-daegeum'; get('instrument').onchange();
  get('vibrato').value='third'; get('vibrato').onchange();
  assert.equal(vm.runInContext('JSON.stringify(job.score)',context),before);
  assert.equal(get('audio').src,'/old/danso.wav');assert.equal(get('result').hidden,false);
  assert.match(get('preview-title').textContent,/단소/);assert.match(get('result-state').textContent,/이전/);
  assert.equal(get('approved').checked,false);assert.match(get('instrument-info').textContent,/같은 CAT/);
  failPreview=true; await get('preview').onclick();
  assert.equal(get('instrument').value,'jeongak-daegeum');assert.equal(get('audio').src,'/old/danso.wav');assert.match(get('status').textContent,/음역 초과/);
  failPreview=false; await get('preview').onclick();
  const preview=requests.filter(x=>x.url.endsWith('/preview')).at(-1);
  assert.deepEqual(preview.body,{revision:1,instrument:'jeongak-daegeum',vibrato:'third'});
  assert.equal(requests.some(x=>x.url.endsWith('/recognize')||x.url.endsWith('/automatic')),false,'preview must not OCR');
  assert.equal(get('midi').download,'jeongak-daegeum.mid');assert.equal(get('audio').src,'/new/jeongak-daegeum.wav');
  assert.match(get('preview-title').textContent,/정악대금/);assert.equal(get('result-warnings').children[0].textContent,'새 결과 경고');
  get('title').value='Edited source'; get('title').oninput();
  await get('preview').onclick();
  assert.equal(requests.at(-2).url.endsWith('/draft'),true);assert.equal(requests.at(-2).body.instrument,'jeongak-daegeum');
  assert.equal(requests.at(-2).body.score.title,'Edited source');assert.equal(requests.at(-1).body.revision,2);
  get('instrument').value='sanjo-daegeum'; get('instrument').onchange();assert.match(get('instrument-info').textContent,/미확정/);
  const draftWrites=requests.filter(x=>x.url.endsWith('/draft')).length;
  const priorAudio=get('audio').src,priorRevision=serverJob.revision;
  failRender=true;get('approved').checked=true;await get('render').onclick();
  assert.equal(requests.filter(x=>x.url.endsWith('/draft')).length,draftWrites,'instrument-only render must not discard the previous result by rewriting the draft');
  assert.equal(serverJob.revision,priorRevision);assert.equal(get('audio').src,priorAudio);
  assert.match(get('status').textContent,/파일 오류/);assert.equal(get('instrument').value,'sanjo-daegeum');
  failRender=false;get('approved').checked=true;await get('render').onclick();
  assert.equal(requests.at(-1).body.instrument,'sanjo-daegeum');assert.equal(requests.at(-1).body.revision,priorRevision);
  assert.equal(requests.filter(x=>x.url.endsWith('/draft')).length,draftWrites);
  get('photo').files=[{size:10,type:'image/png'}];await get('upload').onclick();
  const upload=requests.find(x=>x.url.startsWith('/api/scores?'));assert.match(upload.url,/instrument=sanjo-daegeum/);assert.equal(requests.find(x=>x.url.endsWith('/automatic')).body.instrument,'sanjo-daegeum');
  const legacy=structuredClone(serverJob);delete legacy.instrument;delete legacy.result.instrument;delete legacy.result.instrument_label;
  context.legacy=legacy;vm.runInContext('showJob(legacy)',context);assert.equal(get('instrument').value,'danso');assert.match(get('preview-title').textContent,/단소/);
  console.log('PASS: four choices, unavailable reason, preserved score/audio, failed preview, preview payload/no OCR, draft-before-preview, render/upload instrument, legacy Danso');
})().catch(error=>{console.error(error);process.exitCode=1;});
