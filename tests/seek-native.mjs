// Real Android WebViews + native routing, synthetic media, and explicitly injected timing faults.
import {execFileSync} from 'node:child_process';
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8787',adbPort=process.env.ADB_PORT||'5038',device=process.env.ADB_DEVICE||'emulator-5560';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('Start ControllerHarnessActivity');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};return async code=>{const id=++serial,result=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),30000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true}}));});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));


// Requires scripts/seek-fixture.mjs and work/seek-fixture.mp4 (180-second generated test pattern).
try{const phone=await client(true),car=await client(false);
 const until=async(fn,expr,limit=220)=>{for(let i=0;i<limit;i++){if(await fn('return '+expr))return;await wait(100);}throw Error('Timeout: '+expr);};
 const check=async(name,fn,expr)=>{if(!await fn('return '+expr))throw Error(name);checks.push(name);console.log('PASS '+name);};
 const mode=async value=>{await fetch('http://127.0.0.1:'+fixture+'/test/mode?value='+value);};
 const jump=async percent=>{await phone(`$('controller-seek').value='${percent}';$('controller-seek').dispatchEvent(new Event('change'))`);};
 await mode('normal');await phone(`await api('save',{mode:'xtream',host:'http://10.0.2.2:${fixture}',username:'demo',password:'demo'});account=await api('config');await connectAccount();await changeTab('vod');`);await car("account=await api('config');await connectAccount();positions={};persist();");await phone('selectItem(filtered[0])');await until(car,'v.currentTime>1&&v.currentTime<5&&v.readyState>=3&&!v.seeking&&v.seekable.length>0');await until(phone,"!$('controller-forward').disabled");
 const initialEnd=await car('return v.buffered.end(v.buffered.length-1)');if(initialEnd>=40)throw Error('Fixture already buffered past target');
 await jump(25);await until(car,'!pendingSeek&&v.currentTime>=44&&v.currentTime<50&&v.readyState>=3');
 await check('Quarter-film jump outside the initial buffer completes',car,'!v.paused&&v.getVideoPlaybackQuality().totalVideoFrames>0');
 const stats=await(await fetch('http://127.0.0.1:'+fixture+'/test/status')).json();if(!stats.ranges.some(r=>r.start>0))throw Error('No nonzero HTTP Range requested');checks.push('Out-of-buffer jump requests actual HTTP byte ranges');
 await jump(50);await until(car,'!pendingSeek&&v.currentTime>=89&&v.currentTime<95&&v.readyState>=3');checks.push('Half-film jump completes and advances video');
 await phone("$('controller-pause').click()");await until(car,'v.paused');await until(phone,"!$('controller-back').disabled");
 const before=await car('return v.currentTime');await phone("$('controller-back').click()");await until(car,`!pendingSeek&&Math.abs(v.currentTime-(${before}-10))<1`);await check('Back 10 seconds preserves Pause',car,'v.paused');
 await phone("$('controller-forward').click()");await until(car,`!pendingSeek&&Math.abs(v.currentTime-${before})<1`);await check('Forward 10 seconds preserves Pause',car,'v.paused');
 await car('pendingResume=12');await jump(30);await until(car,'!pendingSeek&&v.currentTime>=53&&v.currentTime<56');await wait(600);await check('Explicit seek supersedes pending saved-position resume',car,'pendingResume===null&&v.currentTime>=53&&v.paused');
 await phone("$('controller-play').click()");await until(car,'!v.paused');await phone("$('controller-full').click()");await jump(40);await until(car,'!pendingSeek&&v.currentTime>=71&&v.currentTime<78');await check('Fullscreen seeking keeps video, controls and playback active',car,"$('player-box').classList.contains('full')&&!v.paused");
 await phone("$('controller-pause').click()");await until(car,'v.paused');await jump(20);await jump(60);await jump(35);await until(car,'!pendingSeek&&Math.abs(v.currentTime-63)<1');await check('Rapid seeks execute the latest target',car,'v.paused');
 await car('full(false);await v.requestFullscreen();v.currentTime=40;setTimeout(()=>v.currentTime=100,150)');await until(car,'!pendingSeek&&Math.abs(v.currentTime-100)<1');await check('Native fullscreen seek follows the latest target without autoplay',car,'!!document.fullscreenElement&&v.paused');await car('await document.exitFullscreen()');
 await mode('stall-once');await jump(80);await until(phone,"$('controller-phase').textContent===t('seekingVideo')");await until(car,'pendingSeek?.reloaded',250);await until(car,'!pendingSeek&&!playbackFault&&v.currentTime>=143&&v.currentTime<147',250);await check('Stalled seek reloads once at the target and keeps Pause',car,'v.paused');
 await mode('stall');await jump(10);await until(car,'pendingSeek?.reloaded',250);await until(car,'playbackFault',250);await check('Permanent seek stall ends with Retry instead of infinite loading',phone,"$('controller-play').disabled&&$('controller-pause').disabled&&!$('controller-retry').disabled&&$('controller-phase').textContent===t('seekError')");
 await mode('normal');await phone("$('controller-retry').click()");await until(car,'v.readyState>=3&&!v.paused&&!playbackFault');
 await mode('ignore');await jump(90);await until(car,'playbackFault',450);await check('Ignored nonzero Range is reported as seek failure',car,"v.paused&&seekIssue==='seekError'");
 await mode('normal');await phone("$('controller-retry').click()");await until(car,'v.readyState>=3&&!v.paused&&!playbackFault');await mode('stall');await jump(5);await until(car,'!!pendingSeek');await phone("$('controller-stop').click()");await until(car,'!current&&v.paused');await wait(19000);await check('Stop cancels delayed seek recovery',car,"!current&&v.paused&&!v.getAttribute('src')&&!pendingSeek");
 await check('Phone never plays local media',phone,"v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
