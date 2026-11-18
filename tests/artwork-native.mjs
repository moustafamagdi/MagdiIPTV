// Isolated emulator + synthetic fixture. CDP_PORT and FIXTURE_PORT avoid other projects' tests.
const port=process.env.CDP_PORT||'9225',fixture=process.env.FIXTURE_PORT||'8787';
const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json(),sockets=[],checks=[];
async function client(phone){const page=pages.find(p=>p.type==='page'&&p.title.includes('Controller')===phone);if(!page)throw Error('ControllerHarnessActivity is required');const ws=new WebSocket(page.webSocketDebuggerUrl);sockets.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let serial=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};return async code=>{const id=++serial,result=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),30000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression:'(async()=>{'+code+'})()',awaitPromise:true,returnByValue:true,userGesture:true}}));});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;};}
const wait=ms=>new Promise(r=>setTimeout(r,ms));
try{const phone=await client(true),car=await client(false);
 const until=async(fn,code)=>{for(let i=0;i<100;i++){if(await fn('return '+code))return;await wait(150);}throw Error('Timeout: '+code);};
 const check=async(name,fn,code)=>{if(!await fn('return '+code))throw Error(name);checks.push(name);console.log('PASS '+name);};
 await phone(`for(const d of document.querySelectorAll('dialog[open]'))d.close();await api('save',{mode:'xtream',host:'http://10.0.2.2:${fixture}',username:'demo',password:'demo'});account=await api('config');await connectAccount();`);
 await until(phone,"(await api('controllerstate')).connected");await phone("$('list').scrollIntoView({block:'start'})");
 await until(phone,"document.querySelector('#list .artwork img')?.naturalWidth>0");
 await check('Provider HTTP logo decodes through opaque image-only route',phone,"document.querySelector('#list .artwork img').src.startsWith('https://app.magdi.local/artwork/')");
 await wait(900);await check('Broken and absent logos keep visible fallbacks',phone,"!document.querySelectorAll('#list .artwork')[1].classList.contains('has-artwork')&&!document.querySelectorAll('#list .artwork')[2].classList.contains('has-artwork')");
 await phone("await changeTab('vod');document.querySelector('#list .pick').click()");
 await until(phone,"$('details-summary').textContent.includes('رحلة عربية')");await until(phone,"document.querySelector('#details-summary img')?.naturalWidth>0");
 await check('Movie card opens poster, Arabic description and explicit car play button',phone,"$('details-dialog').open&&$('details-summary').textContent.includes('Fixture Director')&&$('details-play').textContent===t('playOnCar')");
 await check('Browsing movie details does not start playback',car,"current===null&&v.paused");
 await check('Unsafe image protocols and HTML metadata cannot execute',phone,"Metadata.fields({stream_icon:'javascript:alert(1)',plot:'<script>window.bad=1</script>Safe'}).artwork===''&&!window.bad&&!$('details-summary').querySelector('script')");
 await phone("$('details-play').click()");await until(car,"current?.id==='101'&&!v.paused&&v.readyState>=2");
 await check('Detail play button plays on car and keeps phone silent',phone,"v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
 await phone("await Controller.command('stop');await changeTab('series');document.querySelector('#list .pick').click()");await until(phone,"$('series-summary').textContent.includes('وصف المسلسل الكامل')");
 await check('Series overview is displayed above episodes',phone,"$('episodes-dialog').open&&!!document.querySelector('#series-summary .poster-art')&&$('episodes').children.length===2");
 await phone("document.querySelector('#episodes .info-button').click()");await until(phone,"$('details-summary').textContent.includes('وصف الحلقة')");
 await check('Episode details display supplied image and description',phone,"!!document.querySelector('#details-summary .episode-art')&&$('details-dialog').open");
 await phone("$('details-play').click()");await until(car,"current?.id==='301'&&!v.paused&&v.readyState>=2");
 await check('Episode detail play uses the existing car controller',phone,"!$('episodes-dialog').open&&v.paused&&!v.getAttribute('src')");
 await phone(`await Controller.command('stop');await api('save',{mode:'m3u',playlist:'http://10.0.2.2:${fixture}/playlist.m3u'});account=await api('config');await connectAccount();$('list').scrollIntoView({block:'start'});`);await until(phone,"document.querySelector('#list img')?.naturalWidth>0");
 await check('M3U tvg-logo is preserved and rendered',phone,"!!filtered[0].artwork&&document.querySelector('#list .artwork').classList.contains('has-artwork')");
 await check('Metadata layout fits phone width',phone,"document.documentElement.scrollWidth<=innerWidth");
 console.log(JSON.stringify({passed:checks.length,checks}));
}finally{for(const ws of sockets)ws.close();}
