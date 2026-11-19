import fs from 'node:fs';
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8794';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('Start ControllerHarnessActivity');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};const call=async(method,params={})=>{const id=++serial;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),20000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params}));});};const evaluate=async code=>{const r=await call('Runtime.evaluate',{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};return {call,evaluate};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));

try{
 const p=await client(true),c=await client(false),phone=p.evaluate,car=c.evaluate;
 const until=async(fn,expr)=>{for(let i=0;i<150;i++){if(await fn('return '+expr))return;await wait(100);}throw Error('Timeout: '+expr);};
 const check=async(name,fn,expr)=>{if(!await fn('return '+expr))throw Error(name);checks.push(name);console.log('PASS '+name);};
 const tap=async(x,y)=>{await c.call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y,id:1}]});await c.call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(700);};
 const shot=async name=>{const r=await c.call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync('work/'+name+'.png',Buffer.from(r.data,'base64'));};
 const button=async id=>{await until(phone,"!$('controller-"+id+"').disabled");await phone("$('controller-"+id+"').click()");};
 await phone("await api('save',{mode:'xtream',host:'http://10.0.2.2:"+fixture+"',username:'demo',password:'demo'});account=await api('config');await connectAccount();await changeTab('vod');");await car("account=await api('config');await connectAccount();positions={};persist();");await phone('selectItem(filtered[0])');
 await until(car,'v.readyState>=3&&!v.paused');await until(phone,"!$('controller-full').disabled");await phone("$('controller-full').click()");
 await check('Phone fullscreen starts with visible controls',car,"$('player-box').classList.contains('full')&&!$('player-box').classList.contains('controls-hidden')");
 await car('window.testVideo=v;window.testLease=Controller.credentials();');await shot('fullscreen-controls-shown');
 await wait(7000);await check('Controls stay visible before ten seconds',car,"!$('player-box').classList.contains('controls-hidden')");await until(car,"$('player-box').classList.contains('controls-hidden')");
 await check('Idle fullscreen hides transport and exit with no hit targets',car,"getComputedStyle($('video-controls')).visibility==='hidden'&&getComputedStyle($('exit-full')).visibility==='hidden'&&$('video-controls').inert&&$('exit-full').inert");await shot('fullscreen-controls-hidden');
 const pos=await car("const r=$('play-pause').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}");const before=await car('return v.currentTime');await tap(pos.x,pos.y);
 await check('One touch over a hidden button reveals without pausing or seeking',car,"!$('player-box').classList.contains('controls-hidden')&&!v.paused&&v.currentTime>="+(before-1));
 await button('pause');await until(car,'v.paused');await wait(10500);await check('Paused player keeps controls visible',car,"!$('player-box').classList.contains('controls-hidden')");
 await button('play');await until(car,'!v.paused');await until(car,"$('player-box').classList.contains('controls-hidden')");
 await check('Phone progress and Pause remain usable while controls are hidden',phone,"!$('controller-pause').disabled&&$('controller-play').disabled&&(await api('controllerstate')).playback.time>0");
 await button('pause');await until(car,"v.paused&&!$('player-box').classList.contains('controls-hidden')");checks.push('Remote Pause restores the controls');
 await button('play');await until(car,'!v.paused');
 await car("$('seek').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:77}))");await wait(10500);await check('Holding the seek control prevents hiding',car,"!$('player-box').classList.contains('controls-hidden')");
 await car("window.dispatchEvent(new PointerEvent('pointercancel',{pointerId:77}))");await until(car,"$('player-box').classList.contains('controls-hidden')");checks.push('Cancelled pointer releases the hide timer');
 await car("Object.defineProperty(v,'readyState',{configurable:true,get:()=>2});v.dispatchEvent(new Event('waiting'))");await check('Buffering restores visible controls',car,"!$('player-box').classList.contains('controls-hidden')");await car("delete v.readyState;v.dispatchEvent(new Event('playing'))");
 await car("$('player-box').dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true}));$('play-pause').focus()");await wait(10500);await check('Keyboard focused controls remain accessible',car,"!$('player-box').classList.contains('controls-hidden')&&!$('video-controls').inert");
 await check('Auto-hide preserves media element and native playback request',car,'v===testVideo&&Controller.credentials().request===testLease.request&&!v.paused');
 await phone("$('controller-full').click()");await check('Exit restores normal controls and clears hidden state',car,"!$('player-box').classList.contains('full')&&!$('player-box').classList.contains('controls-hidden')&&!$('video-controls').inert");
 await wait(10500);await check('Normal player does not auto-hide',car,"getComputedStyle($('video-controls')).visibility==='visible'");
 await phone("$('controller-full').click()");await until(car,"$('player-box').classList.contains('full')");await phone("$('controller-stop').click()");await until(car,'!current');await check('Stop cancels fullscreen hiding without restarting playback',car,"!$('player-box').classList.contains('full')&&!fullscreenControls.timer&&!current&&v.paused&&!v.getAttribute('src')");
 await check('Phone remains a silent controller',phone,"v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
