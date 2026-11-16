// The prototype stores one local, non-sensitive numeric layout preference only.
export function setupDashboardSplitter(){
  const $=id=>document.getElementById(id);
  const layout=document.querySelector('.layout'),grip=$('dashboardSplitter'),storageKey='eolssu-qa-dashboard-split-ratio-v1',defaultRatio=2/3,gripWidth=8,minLeft=420,minRight=340;
  let preferredRatio=defaultRatio,dragging=false;
  try{const raw=localStorage.getItem(storageKey),value=raw===null?NaN:Number(raw);if(Number.isFinite(value)&&value>=.1&&value<=.9)preferredRatio=value;}catch{}
  const desktop=()=>matchMedia('(min-width:981px)').matches;
  function bounds(){const available=Math.max(1,layout.clientWidth-gripWidth);return{available,min:Math.min(.9,minLeft/available),max:Math.max(.1,1-minRight/available)};}
  function paint(){
    if(!desktop()){layout.dataset.leftNarrow='false';grip.tabIndex=-1;return;}
    const {available,min,max}=bounds(),ratio=Math.min(max,Math.max(min,preferredRatio)),left=available*ratio;
    layout.style.setProperty('--left-pane-px',left+'px');layout.dataset.leftNarrow=String(left<680);grip.tabIndex=0;
    grip.setAttribute('aria-valuemin',String(Math.round(min*100)));grip.setAttribute('aria-valuemax',String(Math.round(max*100)));grip.setAttribute('aria-valuenow',String(Math.round(ratio*100)));grip.setAttribute('aria-valuetext',`앱 영역 ${Math.round(ratio*100)}퍼센트`);
  }
  function change(value){const {min,max}=bounds();preferredRatio=Math.min(max,Math.max(min,value));paint();try{localStorage.setItem(storageKey,String(preferredRatio));}catch{}}
  function drag(event){const rect=layout.getBoundingClientRect(),{available}=bounds();change((event.clientX-rect.left-gripWidth/2)/available);}
  function finish(){dragging=false;document.body.classList.remove('resizingQaPanes');}
  grip.addEventListener('pointerdown',event=>{if(event.button!==0||!desktop())return;event.preventDefault();grip.focus();dragging=true;grip.setPointerCapture(event.pointerId);document.body.classList.add('resizingQaPanes');drag(event);});
  grip.addEventListener('pointermove',event=>{if(dragging)drag(event);});
  grip.addEventListener('pointerup',event=>{finish();if(grip.hasPointerCapture(event.pointerId))grip.releasePointerCapture(event.pointerId);});
  grip.addEventListener('pointercancel',finish);grip.addEventListener('lostpointercapture',finish);
  grip.addEventListener('dblclick',()=>change(defaultRatio));
  grip.addEventListener('keydown',event=>{if(!desktop())return;const {min,max,available}=bounds(),current=parseFloat(layout.style.getPropertyValue('--left-pane-px'))/available,step=event.shiftKey ? .05 : .02;let next;if(event.key==='ArrowLeft')next=current-step;else if(event.key==='ArrowRight')next=current+step;else if(event.key==='Home')next=min;else if(event.key==='End')next=max;else return;event.preventDefault();change(next);});
  new ResizeObserver(paint).observe(layout);paint();
}
