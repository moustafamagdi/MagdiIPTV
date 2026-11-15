export async function run(call){
 const checks=[];
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const check=(name,ok,detail={})=>{if(!ok)throw Error(name+': '+JSON.stringify(detail));checks.push({name,passed:true,...detail});};
 const until=async expression=>{for(let i=0;i<60;i++){if(await evaluate(expression))return;await wait(100);}throw Error('Timed out: '+expression);};
 const position=async selector=>{await evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center',behavior:'instant'})`);await wait(80);return evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);};
 const touch=async point=>{await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...point,id:1,radiusX:5,radiusY:5}]});await wait(60);await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});};
 const tap=async selector=>touch(await position(selector));
 await call('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});
 await call('Emulation.setTouchEmulationEnabled',{enabled:true});
 await evaluate(`(async()=>{await connectAccount();stop();language='ar';applyLanguage();await changeTab('live');window.touchChecks={play:0,episodes:0,delay:750,fail:false};window.touchOriginalRequest=Android.request;Android.request=function(id,method,payload){if(method==='play'){touchChecks.play++;setTimeout(()=>{if(touchChecks.fail)nativeReply(Number(id),{ok:false,error:'Synthetic playback failure'});else touchOriginalRequest.call(Android,id,method,payload);},touchChecks.delay);}else if(method==='episodes'){touchChecks.episodes++;setTimeout(()=>touchOriginalRequest.call(Android,id,method,payload),650);}else touchOriginalRequest.call(Android,id,method,payload);};window.touchFirstRow=$('list').firstElementChild;})()`);
 try{
  const point=await position('#list .tile');
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...point,id:1,radiusX:5,radiusY:5}]});await wait(60);
  const pressed=await evaluate(`(()=>{const p=document.querySelector('#list .pick');return {pressed:p.classList.contains('pressed'),background:getComputedStyle(p).backgroundColor,hit:document.elementFromPoint(${point.x},${point.y})?.className};})()`);check('Touch down visibly presses the whole selection button',pressed.pressed&&pressed.background!=='rgba(0, 0, 0, 0)',pressed);
  await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  let state=await evaluate(`({requests:touchChecks.play,phase:playbackPhase,selected:$('list').firstElementChild.classList.contains('active'),busy:document.querySelector('#list .pick').getAttribute('aria-busy'),banner:!$('selection-feedback').hidden,rowRetained:touchFirstRow===$('list').firstElementChild})`);
  check('One artwork tap immediately selects and acknowledges loading',state.requests===1&&state.phase==='loading'&&state.selected&&state.busy==='true'&&state.banner,state);
  check('Selecting does not replace the touched row',state.rowRetained);
  await touch(point);check('Second tap while starting does not restart',await evaluate('touchChecks.play===1'));
  await until(`playbackPhase==='playing'&&v.currentTime>.3`);
  await tap('#list .pick');check('Tapping the playing channel does not restart',await evaluate('touchChecks.play===1'));
  await tap('#list .star');check('Favorite tap does not start playback',await evaluate('touchChecks.play===1'));
  const pad=await position('#list .card:nth-child(2) .pick');
  const edge=await evaluate(`(()=>{const r=document.querySelector('#list .card:nth-child(2) .pick').getBoundingClientRect();return {x:r.x+4,y:r.y+4};})()`);
  await touch(edge);check('Card padding selects another channel in one tap',await evaluate(`touchChecks.play===2&&current.id==='2'&&playbackPhase==='loading'`));
  await until(`playbackPhase==='playing'`);
  await evaluate(`pause()`);const before=await evaluate('v.currentTime');await tap('#list .card:nth-child(2) .pick');await wait(500);
  check('Selecting a paused channel resumes without a new stream request',await evaluate(`touchChecks.play===2&&!v.paused&&v.currentTime>${before}`));
  await evaluate(`(async()=>{stop();await changeTab('series');})()`);
  await tap('#list .tile');check('Series tap immediately shows loading in the episode dialog',await evaluate(`$('episodes-dialog').open&&!!document.querySelector('.episode-loading')&&touchChecks.episodes===1`));
  await evaluate(`openSeries(filtered[0])`);check('Repeated series selection shares the pending request',await evaluate('touchChecks.episodes===1'));
  await until(`!seriesLoading&&$('episodes').children.length===2`);
  await tap('#episodes .tile');state=await evaluate(`({requests:touchChecks.play,dialogOpen:$('episodes-dialog').open,type:current?.type,phase:playbackPhase,acknowledged:$('selection-title').textContent===current?.name&&!$('selection-feedback').hidden})`);
  check('One episode artwork tap starts playback and confirms its title',state.requests===3&&!state.dialogOpen&&state.type==='episode'&&state.phase==='loading'&&state.acknowledged,state);
  await until(`playbackPhase==='playing'&&v.getVideoPlaybackQuality().totalVideoFrames>2`);
  await evaluate(`(async()=>{stop();await changeTab('live');touchChecks.fail=true;touchChecks.delay=30;})()`);await tap('#list .tile');await until(`playbackPhase==='error'`);
  check('Failure visibly offers retry instead of staying busy',await evaluate(`document.querySelector('#list .selection-status').textContent===t('retrySelection')&&document.querySelector('#list .pick').getAttribute('aria-busy')==='false'`));
  await evaluate('touchChecks.fail=false');await tap('#list .tile');await until(`playbackPhase==='playing'`);check('One tap retries a failed selection',await evaluate('touchChecks.play===5'));
  await evaluate('stop()');const drag=await position('#list .tile');const count=await evaluate('touchChecks.play');
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...drag,id:1}]});await call('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:drag.x,y:drag.y-100,id:1}]});await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(200);
  check('Scrolling the list does not activate a channel',await evaluate(`touchChecks.play===${count}`));
  check('Feedback fits the mobile viewport',await evaluate(`document.documentElement.scrollWidth<=innerWidth`));
  return checks;
 }finally{try{await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});}catch{}await evaluate(`Android.request=touchOriginalRequest;stop();if($('episodes-dialog').open)$('episodes-dialog').close()`);await call('Emulation.setTouchEmulationEnabled',{enabled:false});}
}
