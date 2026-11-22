// Requires fixture emulator WITHOUT Android Auto. Verifies actual UI fallback, not car launch compatibility.
// API 36 debug harness + CONTROLLER_MEDIA=1 node scripts/mock-server.mjs.
// Forward the fresh harness WebView CDP to 9225. Synthetic streams only.
import assert from 'node:assert/strict';
const pages=await(await fetch('http://127.0.0.1:9225/json/list')).json(),sockets=[];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let passed=0;
async function client(phone){
 const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);assert(page,'Missing harness page');
 const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);
 await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let seq=0;const pending=new Map();
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
 return async expression=>{const id=++seq;const result=await new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression:`(async()=>{${expression}})()`,awaitPromise:true,returnByValue:true,userGesture:true}}));});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};
}
async function until(run,expression){for(let i=0;i<100;i++){if(await run('return '+expression))return;await sleep(150);}throw Error('Timeout: '+expression);}
function pass(name){console.log('PASS '+name);passed++;}
try{
 const phone=await client(true),car=await client(false);await until(phone,"(await api('controllerstate')).connected");
 const before=await phone("return (await api('controllerstate')).request");await phone("$('controller-open').click()");
 await until(phone,"['unavailable','timeout'].includes((await api('controllerstate')).launch)");await until(phone,"!$('controller-open').disabled&&$('controller-open-status').textContent===t('carOpenUnavailable')");pass('Actual HTML Open on car button reports unsupported host and is retryable');
 assert.equal(await car('return current===null&&v.paused'),true);assert.equal(await phone("return (await api('controllerstate')).request"),before);pass('Launch failure neither selects nor starts media');
 await phone("await api('save',{mode:'xtream',host:'http://10.0.2.2:8787',username:'demo',password:'demo'});account=await api('config');await connectAccount();Harness.toggleCar();");await until(phone,"!(await api('controllerstate')).connected");await phone('selectItem(filtered[0])');await sleep(8500);await phone('Harness.toggleCar()');await sleep(1500);
 assert.equal(await car('return current===null&&v.paused'),true);assert.equal(await phone("return v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0"),true);pass('Unavailable selection is discarded across failed launch and reconnect; phone stays silent');
 await phone("$('language').click()");await sleep(500);assert.equal(await phone("return $('controller-open').textContent===t('carOpen')&&$('controller-open-status').textContent===t('carOpenUnavailable')"),true);pass('Launch button and result follow the changed UI language');
 console.log(JSON.stringify({passed,actualAndroidAutoLaunchTested:false}));
}finally{for(const ws of sockets)ws.close();}
