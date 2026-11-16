export async function run(call){
 const checks=[];
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const check=async(name,expression)=>{if(!await evaluate(expression))throw Error(name);checks.push({name,passed:true});};
 const tap=async selector=>{
  await evaluate('document.querySelector('+JSON.stringify(selector)+").scrollIntoView({block:'center',behavior:'instant'})");
  await wait(100);
  const p=await evaluate('(()=>{const r=document.querySelector('+JSON.stringify(selector)+').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...p,id:1}]});
  await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(150);
 };
 await call('Emulation.setTouchEmulationEnabled',{enabled:true});
 try{
  await evaluate("(async()=>{await connectAccount();await changeTab('live');await play(filtered[0]);})()");
  for(let i=0;i<60;i++){if(await evaluate("v.readyState>=2&&!v.paused"))break;await wait(100);}
  await check('Fixture is playing before fullscreen', 'v.readyState>=2&&!v.paused');
  for(const [width,height] of [[1000,500],[412,915]]){
   await call('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<600});
   await tap('#full');
   await check(width+': fullscreen button enters fullscreen',"$('player-box').classList.contains('full')&&$('full').getAttribute('aria-pressed')==='true'&&Math.abs($('player-box').getBoundingClientRect().width-innerWidth)<1");
   await tap('#full');
   await check(width+': same button exits and playback continues',"!$('player-box').classList.contains('full')&&!document.body.classList.contains('fullscreen')&&$('exit-full').hidden&&$('full').getAttribute('aria-pressed')==='false'&&!v.paused");
   await tap('#full');await tap('#exit-full');
   await check(width+': dedicated exit still works',"!$('player-box').classList.contains('full')&&!v.paused");
   await tap('#full');await evaluate("app.back()");
   await check(width+': Back exits fullscreen without stopping playback',"!$('player-box').classList.contains('full')&&!v.paused");
  }
  return checks;
 }finally{await evaluate('full(false);stop()');await call('Emulation.setTouchEmulationEnabled',{enabled:false});}
}
