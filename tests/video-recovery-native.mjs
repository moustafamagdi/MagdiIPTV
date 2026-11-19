// Real Android WebViews + native routing, synthetic media, and explicitly injected timing faults.
import {execFileSync} from 'node:child_process';
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8787',adbPort=process.env.ADB_PORT||'5038',device=process.env.ADB_DEVICE||'emulator-5560';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('Start ControllerHarnessActivity');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};return async code=>{const id=++serial,result=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),30000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true}}));});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));

// Real WebViews/native routing with a synthetic MP4. Freeze samples are injected;
// this does not emulate Samsung's decoder or establish physical Auto compatibility.
try{const phone=await client(true),car=await client(false);
 const until=async(fn,expression)=>{for(let i=0;i<200;i++){if(await fn('return '+expression))return;await wait(100);}throw Error('Timeout: '+expression);};
 const check=async(name,fn,expression)=>{if(!await fn('return '+expression))throw Error(name);checks.push(name);console.log('PASS '+name);};
 await phone(`await api('save',{mode:'xtream',host:'http://10.0.2.2:${fixture}',username:'demo',password:'demo'});account=await api('config');await connectAccount();`);
 await until(phone,"(await api('controllerstate')).connected");await car('positions={}');await phone("await changeTab('vod');selectItem(filtered[0])");await until(car,'v.currentTime>1&&v.videoWidth>0');
 await car(`window.injectFrozenSamples=()=>{const start=v.currentTime,now=performance.now();videoHealth.reset();let sampleTime=start;const quality=v.getVideoPlaybackQuality;Object.defineProperty(v,'currentTime',{configurable:true,get:()=>sampleTime});v.getVideoPlaybackQuality=()=>({totalVideoFrames:100,droppedVideoFrames:0});try{for(let i=0;i<=10;i++){sampleTime=start+i*2;sampleVideoHealth(now+i*2000);}}finally{delete v.currentTime;v.getVideoPlaybackQuality=quality;}return start;};`);
 await car("v.style.display='none';injectFrozenSamples()");await check('Offscreen/hidden video does not trigger recovery',car,'automaticRecoveries===0&&!recoveryPending');await car("v.style.removeProperty('display')");
 await car("window.originalLease=Controller.credentials();window.beforeGeneration=playGeneration;full(true);window.resumePoint=injectFrozenSamples()");
 await until(phone,"$('controller-phase').textContent===t('recoveringVideo')");
 await check('Recovery checkpoints the last picture position before any reconnect failure',car,'Math.abs(positions[key(current)]-resumePoint)<.01');
 await check('Phone shows recovery rather than healthy Playing',phone,"$('controller-play').disabled&&!$('controller-stop').disabled");
 await until(car,"!recoveryPending&&automaticRecoveries===1&&v.currentTime>resumePoint&&v.readyState>=3");
 await check('Reload restores actual decoded video without replacing the native request',car,"v.getVideoPlaybackQuality().totalVideoFrames>0&&playGeneration===beforeGeneration+1&&Controller.credentials().request===originalLease.request");
 await check('CSS fullscreen remains active through reload',car,"$('player-box').classList.contains('full')");
 await check('Recovery resumes near the last frame position, not the advanced audio clock',car,'v.currentTime<resumePoint+10');
 await until(phone,"!$('controller-pause').disabled&&$('controller-play').disabled");
 await car('full(false);await v.requestFullscreen()');await until(car,'!!document.fullscreenElement');
 await car('window.oldMediaError=v.onerror;injectFrozenSamples()');await until(car,'!recoveryPending&&automaticRecoveries===2&&v.readyState>=3');
 await check('Native video fullscreen retains frame progress and controls after recovery',car,'!!document.fullscreenElement&&!v.paused&&v.getVideoPlaybackQuality().totalVideoFrames>0');
 await car("Object.defineProperty(v,'error',{configurable:true,get:()=>({code:2})});oldMediaError();delete v.error");
 await check('Obsolete attachment error cannot fail the recovered player',car,'!playbackFault');
 await car('injectFrozenSamples()');await until(phone,"$('controller-phase').textContent===t('retrySelection')");
 await check('Two-recovery limit stops audio and offers explicit Retry',phone,"$('controller-play').disabled&&$('controller-pause').disabled&&!$('controller-retry').disabled");
 await check('Exhausted recovery does not loop',car,'automaticRecoveries===2&&v.paused&&playbackFault&&!recoveryPending');
 await phone("$('controller-retry').click()");await until(car,'!v.paused&&v.readyState>=3&&automaticRecoveries===0');
 await car('if(document.fullscreenElement)await document.exitFullscreen();injectFrozenSamples();v.pause()');await wait(4000);
 await check('Pause during recovery cancels it and prevents later autoplay',car,'v.paused&&!recoveryPending');
 await phone("$('controller-play').click()");await until(car,'!v.paused&&v.readyState>=3');
 await car("activeMedia.url='https://app.magdi.local/missing-video';injectFrozenSamples()");await until(car,'playbackFault');
 await check('Failed reconnect stops with an error instead of retrying forever',car,'v.paused&&!recoveryPending&&automaticRecoveries<=2');
 await phone("$('controller-retry').click()");await until(car,'!v.paused&&v.readyState>=3&&automaticRecoveries===0');
 await phone("await changeTab('live');selectItem(filtered[0]);selectItem(filtered[1]);selectItem(filtered[2])");await until(car,"current?.id==='3'&&!v.paused&&v.readyState>=3");
 await car('injectFrozenSamples()');await phone('selectItem(filtered[1])');await until(car,"current?.id==='2'&&!v.paused&&v.readyState>=3");
 await check('New selection cancels recovery and resets its budget',car,'!recoveryPending&&automaticRecoveries===0');
 await car('injectFrozenSamples()');await until(car,'!recoveryPending&&automaticRecoveries===1&&v.readyState>=3');
 await check('Live recovery reconnects without a VOD resume seek',car,'activeMedia.live&&activeMedia.resumeAt===null&&!v.paused');
 await car('injectFrozenSamples();Controller.stopLocal()');await wait(3500);
 await check('Stop cancels recovery and late events cannot restart it',car,"!current&&v.paused&&!v.getAttribute('src')");
 await phone('selectItem(filtered[0])');await until(car,'!v.paused&&v.readyState>=3');await car('injectFrozenSamples()');
 await phone('Harness.toggleCar()');await until(phone,"!(await api('controllerstate')).connected");
 await phone('Harness.toggleCar()');await until(phone,"(await api('controllerstate')).connected");await wait(3000);
 await check('Disconnect cancels recovery; reconnect never autoplays',car,"!current&&v.paused&&!v.getAttribute('src')");
 await check('Phone never receives a media source or plays audio/video',phone,"v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
