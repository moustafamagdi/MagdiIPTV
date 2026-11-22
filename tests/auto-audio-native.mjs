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
 const phone=await client(true),car=await client(false);
 await phone("await api('save',{mode:'xtream',host:'http://10.0.2.2:8787',username:'demo',password:'demo'});account=await api('config');await connectAccount();");
 await until(phone,"(await api('controllerstate')).connected");
 assert.equal(await phone("return Harness.audioCard('status')"),'unavailable');
 await phone('selectItem(filtered[0])');await until(car,'v.readyState>=2&&!v.paused&&v.currentTime>0.5');
 await until(phone,"Harness.audioCard('status')==='sent'");
 pass('Car playback automatically creates native MediaSession without opening Audio');
 await phone("Harness.audioCard('pause')");await until(car,'v.paused');
 await until(phone,"!$('controller-play').disabled&&$('controller-pause').disabled");
 await phone("Harness.audioCard('play')");await until(car,'!v.paused');
 await until(phone,"$('controller-play').disabled&&!$('controller-pause').disabled");
 pass('Real MediaSession Pause/Play changes car playback and phone buttons');
 await phone("await Controller.command('fullscreen')");await until(car,"$('player-box').classList.contains('full')");
 await phone('Harness.coverCar(true)');const before=await car('return v.currentTime');await sleep(1800);
 assert.equal(await car(`return v.currentTime>${before}&&!v.paused`),true);
 await phone("Harness.audioCard('pause')");await until(car,'v.paused');await phone('Harness.coverCar(false)');
 assert.equal(await car("return v.paused&&$('player-box').classList.contains('full')"),true);
 await phone("Harness.audioCard('play')");await until(car,'!v.paused');
 pass('Covered fullscreen keeps decoding and card commands; returning preserves Pause/fullscreen');
 await phone("Harness.audioCard('stop')");await until(car,'current===null&&v.paused');await sleep(1500);
 assert.equal(await phone("return Harness.audioCard('status')"),'unavailable');
 pass('MediaSession Stop cancels playback and releases automatic binding');
 await phone('Harness.toggleCar()');await phone('Harness.toggleCar()');await sleep(1200);
 assert.equal(await car("return current===null&&v.paused&&!v.getAttribute('src')"),true);
 assert.equal(await phone("return Harness.audioCard('status')"),'unavailable');
 pass('Reconnection does not recreate playback or the audio service');
 assert.equal(await phone("return v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0"),true);
 await phone('selectItem(filtered[0])');await until(car,'v.readyState>=2&&!v.paused&&v.currentTime>0.5');
 const time=await car('return v.currentTime');
 // closePhone destroys the phone WebView; its pending CDP response may be lost.
 phone('Harness.closePhone()').catch(()=>{});await sleep(1500);
 assert.equal(await car('return !v.paused&&v.currentTime>'+time),true);
 pass('Phone never plays media; destroying its WebView preserves car playback');
 console.log(JSON.stringify({passed,synthetic:true,audibleOutputTested:false,physicalAndroidAutoTested:false}));
}finally{for(const ws of sockets)ws.close();}
