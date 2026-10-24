'use strict';
const $=id=>document.getElementById(id);
let job=null, result=null, busy=false, selected=null, image=new Image(), drag=null, config={};
const yul=[...'黃大太夾姑仲蕤林夷南無應'];
const names=['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'];
function say(message,error=false){$('status').textContent=message;$('status').classList.toggle('error',error);}
async function api(url,options={}){
  options.headers={'X-Playback-Client':'local',...options.headers};
  const response=await fetch(url,options);let value=await response.json();
  if(!response.ok)throw Error(typeof value.detail==='string'?value.detail:JSON.stringify(value.detail));
  return value;
}
function jsonRequest(method,value){return {method,headers:{'Content-Type':'application/json'},body:JSON.stringify(value)};}
async function action(callback){if(busy)return;busy=true;document.querySelectorAll('button,input,select,textarea').forEach(b=>b.disabled=true);
  try{await callback();}catch(error){say(error.message,true);}finally{busy=false;document.querySelectorAll('button,input,select,textarea').forEach(b=>b.disabled=false);$('recognize').disabled=!config.recognition_configured;}}
function dirty(){result=null;$('result').hidden=true;$('audio').pause();$('approved').checked=false;syncJSON();}
function syncJSON(){if(job)$('json').value=JSON.stringify(job.score,null,2);}
function settings(){job.score.title=$('title').value;job.score.quarter_bpm=Number($('bpm').value);
  if(job.notation==='jeonggan')job.score.context.huangjong_midi=$('huangjong').value===''?null:Number($('huangjong').value);}
function draw(active=[]){const c=$('score-canvas'),ctx=c.getContext('2d');if(!image.complete||!image.naturalWidth)return;
  ctx.clearRect(0,0,c.width,c.height);ctx.drawImage(image,0,0,c.width,c.height);
  for(const region of job.regions){const [x,y,w,h]=region.bbox;const on=active.includes(region.note_id);ctx.strokeStyle=on?'#b04408':'#176b50';ctx.lineWidth=on?5:2;
    ctx.fillStyle=on?'rgba(255,190,70,.35)':'rgba(24,140,90,.08)';ctx.fillRect(x*c.width,y*c.height,w*c.width,h*c.height);ctx.strokeRect(x*c.width,y*c.height,w*c.width,h*c.height);}}
function selectNote(id){selected=id;document.querySelectorAll('#notes tr').forEach(tr=>tr.classList.toggle('selected',tr.dataset.id===id));$('selection').textContent=`음표 ${id}: 사진에서 이 음표 영역을 드래그하세요.`;}
function cell(row,node){const td=document.createElement('td');td.append(node);row.append(td);return td;}
function button(label,callback){const b=document.createElement('button');b.type='button';b.textContent=label;b.onclick=callback;return b;}
function field(value,label,change,type='text'){const input=document.createElement('input');input.type=type;input.value=value;input.setAttribute('aria-label',label);input.onchange=()=>{change(input.value);dirty();};return input;}
function rows(){const target=$('notes');target.replaceChildren();$('note-count').textContent=`입력한 음표 ${job.score.notes.length}개`;
  for(const [index,n] of job.score.notes.entries()){const tr=document.createElement('tr');tr.dataset.id=n.id;
    cell(tr,button(`${index+1} 위치`,()=>selectNote(n.id)));
    if(n.rest){cell(tr,document.createTextNode('쉼'));}
    else if(job.notation==='jeonggan'){
      const wrap=document.createElement('div'),sel=document.createElement('select');sel.setAttribute('aria-label',`${index+1} 율명`);
      yul.forEach(v=>{const o=document.createElement('option');o.value=v;o.textContent=v;sel.append(o);});sel.value=n.pitch?.yul||'黃';
      sel.onchange=()=>{n.pitch.yul=sel.value;dirty();};wrap.append(sel);
      const register=field(n.pitch?.register??0,`${index+1} 옥타브 register`,v=>n.pitch.register=Number(v),'number');register.className='register';wrap.append(register);cell(tr,wrap);
    }else{const sel=document.createElement('select');sel.setAttribute('aria-label',`${index+1} 실음`);
      for(let p=0;p<=127;p++){const o=document.createElement('option');o.value=p;o.textContent=`${names[p%12]}${Math.floor(p/12)-1} · ${p}`;sel.append(o);}sel.value=n.pitch;sel.onchange=()=>{n.pitch=Number(sel.value);dirty();};cell(tr,sel);}
    cell(tr,field(n.duration,`${index+1} 길이`,v=>n.duration=v));const rest=document.createElement('input');rest.type='checkbox';rest.checked=!!n.rest;rest.setAttribute('aria-label',`${index+1} 쉼표`);
    rest.onchange=()=>{job.score.notes[index]=rest.checked?{id:n.id,duration:n.duration,rest:true}:{id:n.id,duration:n.duration,pitch:job.notation==='staff'?75:{yul:'黃',register:1}};dirty();rows();};cell(tr,rest);
    cell(tr,button('삭제',()=>{job.score.notes.splice(index,1);job.regions=job.regions.filter(r=>r.note_id!==n.id);dirty();rows();draw();}));target.append(tr);}
  syncJSON();}
function showJob(value){job=value;result=value.result;$('workspace').hidden=false;$('notation').value=job.notation;$('title').value=job.score.title;$('bpm').value=job.score.quarter_bpm;
  $('huangjong-label').hidden=$('direction-label').hidden=job.notation!=='jeonggan';$('huangjong').value=job.score.context.huangjong_midi??'';
  $('pitch-help').textContent=job.notation==='staff'?'오선보는 옥타브 표기를 반영한 실제 소리(MIDI)로 입력하세요. 조표·임시표도 확인하세요.':'황종의 실음을 먼저 지정하세요. register 0=중성, 1=청성, -1=탁성입니다. 한 정간의 길이와 칸 안 분할을 직접 확인하세요.';
  $('warnings').textContent=job.warnings.join(' ');selected=null;rows();
  image.onload=()=>{$('score-canvas').width=image.naturalWidth;$('score-canvas').height=image.naturalHeight;draw();};image.src=job.image_url;
  $('approved').checked=false;$('result').hidden=true;location.hash=job.id;if(result)showResult(result);}
function showResult(value){result=value;$('vibrato').value=value.vibrato;$('result').hidden=false;$('audio').src=value.audio_url;$('midi').href=value.midi_url;$('score-download').href=value.score_url;
  $('result-info').textContent=`${value.seconds.toFixed(2)}초 · ${value.vibrato==='quarter'?'1/4 반음':value.vibrato==='third'?'1/3 반음':'추가 흔들림 없음'}`;$('credit').textContent=value.credit;}
$('upload').onclick=()=>action(async()=>{const file=$('photo').files[0];if(!file)throw Error('사진을 선택하세요.');if(file.size>8*1024*1024)throw Error('8 MiB 이하 사진을 선택하세요.');say('사진을 여는 중입니다.');
  const value=await api(`/api/scores?notation=${$('notation').value}`,{method:'POST',headers:{'Content-Type':file.type},body:file});$('audio').pause();showJob(value);say('사진을 열었습니다. 음표를 입력하거나 검수한 JSON을 불러오세요.');});
$('add').onclick=()=>{if(!job)return;const id=`n-${crypto.randomUUID()}`;job.score.notes.push({id,duration:'1',pitch:job.notation==='staff'?75:{yul:'黃',register:1}});dirty();rows();};
for(const id of ['title','bpm','huangjong'])$(id).oninput=()=>{if(job){settings();dirty();}};
$('vibrato').onchange=()=>{if(result){$('audio').pause();$('result').hidden=true;result=null;}};
function importScore(value){if(!value||value.schema!=='gugak-reviewed-score/v1'||!Array.isArray(value.notes)||value.notes.length>1000||!value.source||Array.isArray(value.source)||typeof value.source!=='object'||!value.context||Array.isArray(value.context)||typeof value.context!=='object')throw Error('출처·조율 문맥이 포함된 악보 JSON과 1000개 이하 음표를 사용하세요.');
  if(value.context?.pitch_mode!==(job.notation==='staff'?'sounding':'yul'))throw Error('사진 표기법과 JSON pitch_mode가 다릅니다.');
  if(value.notes.some(n=>!n||typeof n.id!=='string'||(!n.rest&&(job.notation==='staff'? !Number.isInteger(n.pitch):!n.pitch||!yul.includes(n.pitch.yul)||!Number.isInteger(n.pitch.register)))))throw Error('음표 ID·음높이 형식을 확인하세요.');
  job.score=value;job.regions=[];showJob(job);dirty();say('음표 데이터를 불러왔습니다. 사진과 대조해 확인하세요.');}
$('import').onchange=()=>action(async()=>{const file=$('import').files[0];if(file){if(file.size>1024*1024)throw Error('악보 JSON은 1 MiB 이하여야 합니다.');importScore(JSON.parse(await file.text()));}});
$('apply-json').onclick=()=>action(async()=>{importScore(JSON.parse($('json').value));});
$('export').onclick=()=>{settings();const blob=new Blob([JSON.stringify(job.score,null,2)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='score.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);};
$('recognize').onclick=()=>action(async()=>{settings();say('로컬 모델이 사진을 읽는 중입니다. 완료 후 원보와 대조해 주세요.');
  const context={...job.score.context,quarter_bpm:job.score.quarter_bpm};if(job.notation==='jeonggan')context.reading_direction=$('direction').value;
  showJob(await api(`/api/scores/${job.id}/recognize`,jsonRequest('POST',{revision:job.revision,context})));say('인식 초안입니다. 빠진 기호·음높이·길이를 사진과 대조하세요.');});
$('render').onclick=()=>action(async()=>{if(!$('approved').checked)throw Error('먼저 사진과 음표를 확인하고 확인란을 선택하세요.');settings();say('악보를 검사하고 단소 음원을 만드는 중입니다.');
  job=await api(`/api/scores/${job.id}/draft`,jsonRequest('PUT',{revision:job.revision,score:job.score,regions:job.regions}));
  const value=await api(`/api/scores/${job.id}/render`,jsonRequest('POST',{revision:job.revision,approved:true,vibrato:$('vibrato').value}));showResult(value);say('음원을 만들었습니다. 재생 버튼으로 들어보세요.');});
const canvas=$('score-canvas');function point(e){const r=canvas.getBoundingClientRect();return [Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),Math.max(0,Math.min(1,(e.clientY-r.top)/r.height))];}
canvas.onpointerdown=e=>{if(!selected||busy)return;drag=point(e);canvas.setPointerCapture(e.pointerId);};
canvas.onpointerup=e=>{if(!drag||!selected)return;const end=point(e),x=Math.min(drag[0],end[0]),y=Math.min(drag[1],end[1]),w=Math.abs(end[0]-drag[0]),h=Math.abs(end[1]-drag[1]);drag=null;
  if(w<.001||h<.001)return;job.regions=job.regions.filter(r=>r.note_id!==selected);job.regions.push({note_id:selected,bbox:[x,y,w,h]});dirty();draw();say(`음표 ${selected}의 위치를 연결했습니다.`);};
canvas.onpointercancel=()=>{drag=null;};
$('audio').ontimeupdate=()=>{if(!result)return;const time=$('audio').currentTime;const active=result.timeline.filter(n=>n.start<=time&&time<n.end).flatMap(n=>n.note_ids);
  document.querySelectorAll('#notes tr').forEach(tr=>tr.classList.toggle('current',active.includes(tr.dataset.id)));draw(active);};
action(async()=>{config=await api('/api/config');$('recognition-status').textContent=config.recognition_configured?'로컬 인식 모델이 설정되어 있습니다. 결과는 확인이 필요한 초안입니다.':'자동 인식 모델이 연결되지 않았습니다. 사진을 보며 음표를 입력하거나 검수한 JSON을 불러올 수 있습니다.';
  if(/^#[a-f0-9]{32}$/.test(location.hash)){showJob(await api(`/api/scores/${location.hash.slice(1)}`));say('저장한 악보를 열었습니다.');}});
