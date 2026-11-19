import fs from 'node:fs';
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8787';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('Start ControllerHarnessActivity');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};const call=async(method,params={})=>{const id=++serial;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),20000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params}));});};const evaluate=async code=>{const r=await call('Runtime.evaluate',{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};return {call,evaluate};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));

try{const p=await client(true),c=await client(false),phone=p.evaluate,car=c.evaluate;
 const check=async(name,expr)=>{if(!await car('return '+expr))throw Error(name);checks.push(name);console.log('PASS '+name);};
 const alphabet='ابتثجحخدذرزسشصضطظعغفقكلمنهويءأإآؤئةى';
 for(const [width,height] of [[360,780],[800,400]])for(const lang of ['ar','en']){
  await phone(`Harness.setCarViewport(${width},${height})`);await wait(200);
  await car(`language='${lang}';applyLanguage();await openSettings();EmbeddedKeyboard.open($('username'));if($('keys-language').textContent!=='EN')$('keys-language').click();$('keys-clear').click();`);
  await check(width+' '+lang+': all Arabic letters and variants including dal/dhal',`[...${JSON.stringify(alphabet)}].every(letter=>Array.from(document.querySelectorAll('#keys button')).filter(b=>b.dataset.key===letter).length===1)`);
  const reachable=await car(`let ok=true;for(const b of document.querySelectorAll('#keys button')){b.scrollIntoView({block:'nearest'});const r=b.getBoundingClientRect(),d=$('keyboard-dialog').getBoundingClientRect();ok&&=r.width>0&&r.left>=d.left&&r.right<=d.right&&document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===b;}return ok;`);
  if(!reachable)throw Error('Clipped or obscured Arabic key at '+width+' '+lang);checks.push(width+' '+lang+': keys reachable without clipping');
  await car(`for(const letter of ${JSON.stringify(alphabet)})Array.from(document.querySelectorAll('#keys button')).find(b=>b.dataset.key===letter).click();$('keyboard-done').click();`);
  await check(width+' '+lang+': each letter actually inserts into settings',`$('username').value===${JSON.stringify(alphabet)}`);
  await car("$('settings-dialog').close();EmbeddedKeyboard.open($('search'));if($('keys-language').textContent!=='EN')$('keys-language').click();$('keys-clear').click()");
  for(const letter of ['د','ذ']){await car(`document.querySelector('#keys button[data-key="${letter}"]').scrollIntoView({block:'nearest'})`);const pos=await car(`const r=document.querySelector('#keys button[data-key="${letter}"]').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};`);await c.call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...pos,id:1}]});await c.call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(80);}
  await car("$('keyboard-done').click()");await check(width+' '+lang+': dal and dhal respond to touch in search',"$('search').value==='دذ'");
 }
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
