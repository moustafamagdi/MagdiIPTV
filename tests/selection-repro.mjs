export async function run(call){
 const evalJs=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 await call('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});await call('Emulation.setTouchEmulationEnabled',{enabled:true});
 await evalJs(`(async()=>{await connectAccount();await changeTab('live');await play(filtered[0]);await new Promise(r=>setTimeout(r,1500));pause();await changeTab('vod');items=[{...filtered[0],id:'102',name:'AEW Dynamite 09.07.2026'},{...filtered[0],id:'101',name:'Avengers Endgame Encore ( 2026 )'}];filter();})()`);
 const results=[];
 for(const selector of ['#list .card:nth-child(2) .tile','#list .card:nth-child(2) .pick']){
  await evalJs(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center',behavior:'instant'})`);
  const pt=await evalJs(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2};})()`);
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...pt,id:1}]});await wait(60);await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(250);
  results.push(await evalJs(`({target:${JSON.stringify(selector)},selectedType:current?.type,nowTitle:$('now-title').textContent,cardHighlighted:getComputedStyle(document.querySelector('#list .card:nth-child(2)')).backgroundColor,hasNewSelectionStatus:!!document.querySelector('.selection-status')})`));
 }
 await evalJs('stop()');await call('Emulation.setTouchEmulationEnabled',{enabled:false});return results;
}
