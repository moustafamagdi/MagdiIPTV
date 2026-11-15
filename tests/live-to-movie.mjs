export async function run(call){
 const results=[];
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const check=(name,ok,details)=>{if(!ok)throw Error(name+': '+JSON.stringify(details));results.push({name,passed:true,...details});};
 const until=async expression=>{for(let i=0;i<80;i++){if(await evaluate(expression))return;await wait(100);}throw Error('Timed out: '+expression);};
 const tap=async selector=>{await evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center',behavior:'instant'})`);const point=await evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2};})()`);await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...point,id:1}]});await wait(60);await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});};
 await call('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});await call('Emulation.setTouchEmulationEnabled',{enabled:true});
 try{
  await evaluate(`(async()=>{await connectAccount();await changeTab('live');await play(filtered[0]);})()`);await until(`playbackPhase==='playing'&&v.currentTime>.5`);
  for(const paused of [false,true]){
   if(paused){await evaluate(`(async()=>{await changeTab('live');await play(filtered[0]);})()`);await until(`playbackPhase==='playing'&&v.currentTime>.5`);await evaluate('pause()');}
   await tap('[data-tab="vod"]');await until(`tab==='vod'&&filtered.length===1`);
   await evaluate(`items=[{...filtered[0],id:'102',name:'AEW Dynamite 09.07.2026'},{...filtered[0],id:'1',name:'Avengers Endgame Encore ( 2026 )'}];filter();`);
   await tap('#list .card:nth-child(2) .tile');
   let state=await evaluate(`({type:current?.type,title:$('now-title').textContent,streamFormatDisabled:$('format').disabled,phase:playbackPhase,oldLiveLabel:$('time').textContent})`);
   check(paused?'Paused live channel switches to movie after one artwork tap':'Playing live channel switches to movie after one artwork tap',state.type==='vod'&&state.title==='Avengers Endgame Encore ( 2026 )'&&state.streamFormatDisabled,state);
   await until(`playbackPhase==='playing'&&v.getVideoPlaybackQuality().totalVideoFrames>10&&v.currentTime>1`);
   state=await evaluate(`({engine:engineName,frames:v.getVideoPlaybackQuality().totalVideoFrames,time:v.currentTime})`);check('Selected movie decodes frames',state.engine==='native'&&state.frames>10,state);
  }
  return results;
 }finally{await evaluate('stop()');await call('Emulation.setTouchEmulationEnabled',{enabled:false});}
}
