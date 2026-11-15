(async()=>{
 const result=[];const wait=ms=>new Promise(r=>setTimeout(r,ms));const check=(name,ok,detail={})=>{if(!ok)throw new Error(name+': '+JSON.stringify(detail));result.push({name,passed:true,...detail});};
 account=await api('config');await connectAccount();await play(filtered[0]);await wait(250);
 const start=performance.now();await play(filtered[1]);const elapsed=performance.now()-start;check('Channel change is not blocked by delayed EPG',elapsed<2000,{milliseconds:Math.round(elapsed)});await wait(3000);check('Replacement channel plays',v.currentTime>.2,{time:v.currentTime});pause();
 await api('save',{mode:'m3u',playlist:'http://10.0.2.2:8787/playlist.m3u'});account=await api('config');await connectAccount();check('M3U import',filtered.length===1,{channels:filtered.length});await play(filtered[0]);await wait(3500);check('M3U HLS decoded frames',v.currentTime>0&&v.getVideoPlaybackQuality().totalVideoFrames>5,{time:v.currentTime,frames:v.getVideoPlaybackQuality().totalVideoFrames});stop();
 await api('save',{mode:'xtream',host:'http://10.0.2.2:8787',username:'demo',password:'wrong'});let rejected=false;try{await api('connect');}catch{rejected=true;}check('Invalid credentials are rejected',rejected);
 await api('save',{mode:'xtream',host:'http://10.0.2.2:8787',username:'demo',password:'demo'});account=await api('config');await connectAccount();
 const settings=await api('config');check('Password is not exposed to web page',settings.hasPassword&&!Object.hasOwn(settings,'password'));
 return result;
})()
