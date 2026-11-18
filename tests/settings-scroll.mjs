// Use a fresh ControllerHarnessActivity; run once per phone/car surface.
export async function run(call){
 const checks=[],wait=ms=>new Promise(r=>setTimeout(r,ms));
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const check=async(name,expression)=>{if(!await evaluate(expression))throw Error(name);checks.push(name);console.log('PASS '+name);};
 const point=async selector=>evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.left+12,y:r.top+r.height/2};})()`);
 const wheel=async(selector,down)=>{const p=await point(selector);await call('Input.dispatchMouseEvent',{type:'mouseWheel',...p,deltaX:0,deltaY:down?400:-400});await wait(250);};
 const touch=async(selector,down)=>{const p=await point(selector),delta=down?-150:150;await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...p,id:1}]});for(let i=1;i<=10;i++){await call('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:p.x,y:p.y+delta*i/10,id:1}]});await wait(20);}await wait(160);await call('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:p.x,y:p.y+delta,id:1}]});await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(350);};
 await evaluate("(async()=>{await api('save',{mode:'xtream',host:'http://10.0.2.2:8793',username:'demo',password:'demo'});account=await api('config');await connectAccount();const base=filtered[0];items=Array.from({length:60},(_,i)=>({...base,id:String(i+1),name:'Synthetic channel '+i}));filtered=items;shown=60;render();})()".replace('8793',process.env.FIXTURE_PORT||'8787'));
 try{
  for(const lang of ['en','ar']){
   await evaluate(`language='${lang}';applyLanguage();document.querySelectorAll('dialog[open]').forEach(d=>d.close());window.scrollTo(0,400);`);await wait(200);await evaluate('openSettings()');await wait(250);
   await evaluate("window.scrollBaseline=scrollY;window.dialogStart=$('settings-dialog').scrollTop=80");
   await check(lang+': settings content is actually scrollable',"$('settings-dialog').scrollHeight>$('settings-dialog').clientHeight+100");
   await wheel('#settings-dialog',true);await check(lang+': wheel scroll moves settings content',"$('settings-dialog').scrollTop>dialogStart");await evaluate("$('settings-dialog').scrollTop=80");await touch('#settings-dialog',true);await check(lang+': touch scroll moves settings content',"$('settings-dialog').scrollTop>80");
   for(const bottom of [true,false]){
    await evaluate(`$('settings-dialog').scrollTop=${bottom?"$('settings-dialog').scrollHeight":'0'}`);await wait(200);
    await wheel('#settings-dialog',bottom);await wheel('#settings-dialog',bottom);await check(lang+': wheel at '+(bottom?'bottom':'top')+' does not scroll background','Math.abs(scrollY-scrollBaseline)<2');
    await touch('#settings-dialog',bottom);await touch('#settings-dialog',bottom);await check(lang+': touch at '+(bottom?'bottom':'top')+' does not scroll background','Math.abs(scrollY-scrollBaseline)<2');
   }
   await evaluate("$('settings-dialog').scrollTop=0;EmbeddedKeyboard.open($('host'))");await wait(150);await evaluate("window.parentDialogScroll=$('settings-dialog').scrollTop;$('keyboard-dialog').scrollTop=$('keyboard-dialog').scrollHeight");await wheel('#keyboard-dialog',true);await check(lang+': nested keyboard cannot scroll settings or background',"Math.abs(scrollY-scrollBaseline)<2&&Math.abs($('settings-dialog').scrollTop-parentDialogScroll)<2");await evaluate('EmbeddedKeyboard.back()');await wait(100);
   await check(lang+': closing nested keyboard keeps background locked',"$('settings-dialog').open&&getComputedStyle(document.documentElement).overflowY==='hidden'");
   await evaluate("$('source-mode-picker').click()");await wait(150);await evaluate("window.parentDialogScroll=$('settings-dialog').scrollTop");await wheel('#choice-dialog',true);await check(lang+': source chooser cannot scroll settings or background',"Math.abs(scrollY-scrollBaseline)<2&&Math.abs($('settings-dialog').scrollTop-parentDialogScroll)<2");await evaluate('EmbeddedSelects.back()');await wait(100);
   await check(lang+': closing nested source chooser keeps background locked',"$('settings-dialog').open&&getComputedStyle(document.documentElement).overflowY==='hidden'");
   await evaluate(lang==='en'?"$('close-settings').click()":"app.back()");await wait(200);
   await check(lang+': closing settings unlocks background at original position',"!$('settings-dialog').open&&getComputedStyle(document.documentElement).overflowY!=='hidden'&&Math.abs(scrollY-scrollBaseline)<2");
   const p=await evaluate("(()=>{const r=document.querySelector('.browse').getBoundingClientRect();return {x:Math.round(r.left+r.width/2),y:Math.round(innerHeight/2)};})()");await call('Input.dispatchMouseEvent',{type:'mouseWheel',...p,deltaX:0,deltaY:300});await wait(300);await check(lang+': background scroll works again after close','scrollY>scrollBaseline+20');
   await evaluate("$('controller-alert').showModal();window.shortBaseline=scrollY");await wait(100);await wheel('#controller-alert',true);await check(lang+': short non-scrolling dialog also locks background','Math.abs(scrollY-shortBaseline)<2');await evaluate("$('controller-alert').close()");
  }
  return {passed:checks.length,checks};
 }finally{await evaluate("EmbeddedKeyboard.back();EmbeddedSelects.back();document.querySelectorAll('dialog[open]').forEach(d=>d.close())");}
}
