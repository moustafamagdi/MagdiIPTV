export async function run(call){
 const checks=[],wait=ms=>new Promise(r=>setTimeout(r,ms));
 const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const check=async(name,expression)=>{if(!await evaluate(expression))throw Error(name);checks.push({name,passed:true});};
 const until=async expression=>{for(let i=0;i<60;i++){if(await evaluate(expression))return;await wait(100);}throw Error('Timeout: '+expression);};
 const native=await evaluate("location.hostname==='app.magdi.local'");
 if(!native){await call('Emulation.setDeviceMetricsOverride',{width:800,height:400,deviceScaleFactor:1,mobile:false});await call('Emulation.setTouchEmulationEnabled',{enabled:true});}
 const tap=async selector=>{
  if(native){await evaluate('document.querySelector('+JSON.stringify(selector)+').click()');return;}
  await evaluate('document.querySelector('+JSON.stringify(selector)+").scrollIntoView({block:'center',behavior:'instant'})");await wait(40);
  const p=await evaluate('(()=>{const r=document.querySelector('+JSON.stringify(selector)+').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...p,id:1}]});await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(40);
 };
 const key=async value=>tap('#keys button[data-key='+JSON.stringify(value)+']');
 const type=async value=>{for(const letter of value)await key(letter);};
 await until("account.configured");
 await evaluate("(async()=>{language='en';applyLanguage();env.car=true;EmbeddedKeyboard.configure(true);await openSettings();window.keyboardSaved=0;window.keyboardOriginalApi=api;api=async function(method,args){if(method==='save'){keyboardSaved++;window.keyboardLastSave=args;return true;}return keyboardOriginalApi(method,args);};})()");
 try{
  await check('All account fields suppress the system keyboard in car mode',"['host','username','password','playlist'].every(id=>$(id).readOnly&&$(id).inputMode==='none')");
  await tap('#host');
  await check('Server tap opens embedded keyboard over settings',"$('keyboard-dialog').open&&$('settings-dialog').open&&$('keyboard-input').readOnly&&$('keyboard-input').type==='text'");
  await tap('#keys-clear');await type('http');await tap('[data-keyboard-char=":"]');await tap('[data-keyboard-char="/"]');await tap('[data-keyboard-char="/"]');await type('demo');await tap('[data-keyboard-char="."]');await type('test');await tap('[data-keyboard-char=":"]');await type('8080');
  await tap('#keyboard-done');
  await check('Server URL with port is applied without submitting credentials',"$('host').value==='http://demo.test:8080'&&keyboardSaved===0&&!$('keyboard-dialog').open&&$('settings-dialog').open");
  await tap('#username');await tap('#keys-clear');await type('123');await tap('#keys-left');await key('9');await tap('#keys-delete');await tap('#keyboard-done');
  await check('Cursor insertion and backspace edit the intended position',"$('username').value==='123'");
  await tap('#password');await tap('#keys-clear');await tap('#keys-caps');await key('A');await tap('#keys-caps');await key('b');await key('7');await tap('#keys-symbols');await key('@');await key(':');await key('/');await key('\\');await key('"');await key("'");
  await check('Password accepts case, numbers and punctuation while masked',"$('keyboard-input').type==='password'&&$('keyboard-input').value===('Ab7@:/'+String.fromCharCode(92,34,39))");
  await tap('#keyboard-reveal');await check('Password reveal requires explicit action',"$('keyboard-input').type==='text'");
  await tap('#keyboard-done');
  await check('Password draft is cleared from the keyboard after Done',"$('password').value===('Ab7@:/'+String.fromCharCode(92,34,39))&&$('keyboard-input').value===''&&$('password').type==='password'");
  await tap('#password');await check('Reopening a password is masked again',"$('keyboard-input').type==='password'");
  await tap('#keys-clear');await key('x');await evaluate('app.back()');
  await check('Back discards edits and closes only the keyboard',"$('settings-dialog').open&&!$('keyboard-dialog').open&&$('password').value===('Ab7@:/'+String.fromCharCode(92,34,39))&&$('keyboard-input').value===''");
  await tap('#username');await tap('#keys-clear');await tap('#keys-language');await key('م');await key('ج');await key('د');await key('ي');await key('1');await tap('#keyboard-done');
  await check('Arabic input and ASCII digits are supported',"$('username').value==='مجدي1'");
  await tap('#save-only');await until('keyboardSaved===1');
  await check('Only the existing Save button submits the edited fields',"keyboardLastSave.host==='http://demo.test:8080'&&keyboardLastSave.username==='مجدي1'&&keyboardLastSave.password===('Ab7@:/'+String.fromCharCode(92,34,39))");
  await evaluate("$('source-mode').value='m3u';sourceMode()");
  await tap('#playlist');await tap('#keys-clear');await type('https');await tap('[data-keyboard-char=":"]');await tap('[data-keyboard-char="/"]');await tap('[data-keyboard-char="/"]');await type('demo');await tap('[data-keyboard-char="."]');await type('test');await tap('[data-keyboard-char="/"]');await type('list');await tap('#keys-symbols');await key('?');await key('=');await key('&');await key('%');await key('+');
  await check('Keyboard controls fit the viewport',"document.documentElement.scrollWidth<=innerWidth&&$('keyboard-dialog').getBoundingClientRect().top>=0&&$('keyboard-dialog').getBoundingClientRect().bottom<=innerHeight&&$('keys-clear').getBoundingClientRect().bottom<=$('keyboard-dialog').getBoundingClientRect().bottom");
  await tap('#keyboard-done');
  await check('Playlist URL accepts query-string symbols',"$('playlist').value==='https://demo.test/list?=&%+'");
  await evaluate("env.car=false;EmbeddedKeyboard.configure(false);$('source-mode').value='xtream';sourceMode()");
  if(!native)await call('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});
  await check('Phone fields retain normal keyboard input', "!$('host').readOnly&&!$('password').readOnly");
  await tap('[data-keyboard-for="host"]');await key('x');await tap('#keyboard-close');
  await check('Explicit keyboard button works on phone and Cancel preserves field', "!$('keyboard-dialog').open&&$('host').value==='http://demo.test:8080'");
  await evaluate("(async()=>{api=keyboardOriginalApi;delete window.keyboardLastSave;await openSettings();$('settings-dialog').close();await connectAccount();env.car=true;EmbeddedKeyboard.configure(true);})()");
  await tap('#search');await tap('#keys-clear');await tap('#keys-language');await key('م');await tap('#keyboard-done');
  await check('Search still applies keyboard text and filters', "$('search').value.length===1&&!$('keyboard-dialog').open");
  return {surface:native?'Android WebView DOM activation':'Chromium touch',checks};
 }finally{
  await evaluate("api=keyboardOriginalApi;delete window.keyboardLastSave;EmbeddedKeyboard.back();document.querySelectorAll('dialog[open]').forEach(d=>d.close());env.car=false;EmbeddedKeyboard.configure(false);stop()");
  if(!native)await call('Emulation.setTouchEmulationEnabled',{enabled:false});
 }
}
