export async function run(call){
 const wait=ms=>new Promise(r=>setTimeout(r,ms)),checks=[];
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const check=async(name,expression)=>{if(!await evaluate(expression))throw Error(name);checks.push(name);};
 const tap=async selector=>{await evaluate('document.querySelector('+JSON.stringify(selector)+").scrollIntoView({block:'center',behavior:'instant'})");await wait(50);const p=await evaluate('(()=>{const r=document.querySelector('+JSON.stringify(selector)+').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...p,id:1}]});await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(80);};
 await call('Page.reload',{ignoreCache:true});await wait(700);await call('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});await call('Emulation.setTouchEmulationEnabled',{enabled:true});
 await evaluate("(async()=>{await connectAccount();window.originalPhoneTestApi=api;window.phoneTestState={connected:false,epoch:1,playback:{}};window.phoneTestCommands=[];api=async(method,args)=>{if(method==='controllerstate')return structuredClone(phoneTestState);if(method==='controllercommand'){phoneTestCommands.push(args);return phoneTestState.connected;}return originalPhoneTestApi(method,args);};Controller.configure({controller:true});})()");
 try{
  await tap('#list .pick');
  await check('Disconnected touch shows warning and sends no playback command',"$('controller-alert').open&&phoneTestCommands.length===0&&v.paused&&!v.getAttribute('src')");
  await tap('#controller-close-alert');await evaluate('phoneTestState.connected=true');await wait(1100);await tap('#list .pick');
  await check('Single touch sends selection and shows immediate feedback',"phoneTestCommands.length===1&&phoneTestCommands[0].action==='select'&&!$('selection-feedback').hidden&&$('selection-state').textContent===t('sendingToCar')");
  await evaluate("phoneTestState.playback={source,phase:'playing',item:filtered[0],time:12,duration:0,canPlay:false,canPause:true,next:true,previous:true,canFullscreen:true}");await wait(1200);
  await check('Car title and playback state appear on phone',"$('controller-title').textContent===filtered[0].name&&$('controller-play').disabled&&!$('controller-pause').disabled");
  await tap('#controller-pause');await check('Pause control sends a car command',"phoneTestCommands.at(-1).action==='pause'");
  await tap('#controller-next');await check('Next control sends a car command',"phoneTestCommands.at(-1).action==='next'");
  await tap('#controller-full');await check('Fullscreen command does not fullscreen the phone',"phoneTestCommands.at(-1).action==='fullscreen'&&!$('player-box').classList.contains('full')");
  await wait(3200);await check('Playback toast dismisses despite status polling',"$('selection-feedback').hidden");
  await check('Phone player is hidden and never attaches media',"getComputedStyle(document.querySelector('.player-column')).display==='none'&&v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
  await evaluate('phoneTestState.connected=false');await wait(1200);
  await check('Disconnection clears stale title and disables transport',"$('controller-title').textContent===t('carNothing')&&$('controller-play').disabled&&$('controller-stop').disabled");
  await check('Controller fits phone width',"document.documentElement.scrollWidth<=innerWidth&&$('controller-panel').getBoundingClientRect().right<=innerWidth");
  return {checks};
 }finally{await evaluate('api=originalPhoneTestApi');}
}
