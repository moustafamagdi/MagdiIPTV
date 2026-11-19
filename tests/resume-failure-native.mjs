import fs from 'node:fs';
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8787';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('Start ControllerHarnessActivity');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};const call=async(method,params={})=>{const id=++serial;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),20000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params}));});};const evaluate=async code=>{const r=await call('Runtime.evaluate',{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};return {call,evaluate};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));

try{
 const p=await client(true),c=await client(false),phone=p.evaluate,car=c.evaluate;
 const until=async(fn,expr,count=200)=>{for(let i=0;i<count;i++){if(await fn('return '+expr))return;await wait(100);}throw Error('Timeout: '+expr);};
 const check=async(name,fn,expr)=>{if(!await fn('return '+expr))throw Error(name);checks.push(name);console.log('PASS '+name);};
 const mode=async value=>{await fetch('http://127.0.0.1:'+fixture+'/test/mode?value='+value);};
 await phone("await api('save',{mode:'xtream',host:'http://10.0.2.2:"+fixture+"',username:'demo',password:'demo'});account=await api('config');await connectAccount();await changeTab('vod')");
 await car("account=await api('config');await connectAccount();positions={};positions[key({type:'vod',id:'101'})]=90;positions['vod:other']=25;persist()");await mode('stall');await phone('selectItem(filtered[0])');
 await until(car,"pendingSeek?.origin==='saved'&&pendingSeek.target===90");await car('full(true)');await check('Saved position is explicitly classified as automatic resume',car,"pendingSeek.origin==='saved'&&positions[key(current)]===90");
 await until(car,'pendingSeek?.reloaded',250);await until(car,'playbackFault',250);await until(phone,"!$('controller-restart').hidden");
 await check('Failed auto-resume preserves checkpoint and explains failure on phone',phone,"$('controller-phase').textContent===t('resumeError')&&!$('controller-retry').disabled&&!$('controller-restart').disabled");
 await check('Car offers visible Start from beginning in fullscreen',car,"positions[key(current)]===90&&!$('restart-start').hidden&&(()=>{const r=$('restart-start').getBoundingClientRect();return $('restart-start').contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));})()");
 await mode('normal');await phone("$('controller-retry').click()");await until(car,'!pendingSeek&&!playbackFault&&v.currentTime>=90&&v.currentTime<100&&v.readyState>=3',300);
 await check('Retry still resumes the saved position when data is available',car,"!v.paused&&v.currentTime>=90&&$('restart-start').hidden");
 // Explicitly injected second failure validates recovery choices without another timeout.
 await car('seekTo(140);failSeek()');await until(phone,"!$('controller-restart').hidden");await check('Manual seek failure stays distinct from saved-position failure',phone,"$('controller-phase').textContent===t('seekError')");await car('window.staleResumeReport=Controller.snapshot()');
 await phone("$('controller-restart').click()");await until(car,'!playbackFault&&!pendingSeek&&v.readyState>=3&&!v.paused&&v.currentTime>0&&v.currentTime<5');
 await check('Phone Start from beginning plays only the car from zero',car,"pendingResume===null&&v.currentTime<5&&positions['vod:other']===25&&$('player-box').classList.contains('full')");
 await car('Android.carState(JSON.stringify(staleResumeReport))');await wait(600);await check('Late failed-resume report cannot overwrite the restarted player',phone,"!$('controller-pause').disabled&&$('controller-restart').hidden");
 await car('seekTo(100);failSeek()');await until(car,"!$('restart-start').hidden");await car("$('restart-start').click()");await until(car,'!playbackFault&&!pendingSeek&&v.readyState>=3&&!v.paused&&v.currentTime>0&&v.currentTime<5');checks.push('Car Start from beginning works without phone interaction');
 await phone("$('controller-stop').click()");await until(car,'!current');await mode('stall');await car("positions[key({type:'vod',id:'101'})]=90;persist()");await phone('selectItem(filtered[0])');await until(car,"pendingSeek?.origin==='saved'");await phone('Harness.toggleCar()');await until(phone,"!(await api('controllerstate')).connected");await phone('Harness.toggleCar()');await until(phone,"(await api('controllerstate')).connected");await wait(1500);
 await check('Disconnect during auto-resume cancels it; reconnect never autoplays',car,"!current&&!pendingSeek&&v.paused&&!v.getAttribute('src')");
 await check('Disconnected recovery choice is gone and phone is silent',phone,"$('controller-restart').hidden&&v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
