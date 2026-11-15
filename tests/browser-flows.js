(async()=>{
 const checks=[];const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const check=(name,ok,details)=>{if(!ok)throw new Error(name+': '+JSON.stringify(details));checks.push({name,passed:true,...details});};
 await connectAccount();check('Xtream categories and live catalog',filtered.length===6,{channels:filtered.length});
 $('search').value='الرياضة';filter();check('Arabic search',filtered.length===1,{matches:filtered.length});
 $('search').value='';filter();const selected=filtered[0];if(!isFavorite(selected))toggleFavorite(selected);await changeTab('favorites');check('Favorites persisted',filtered.some(i=>i.id===selected.id),{});await changeTab('live');
 await play(filtered[0]);await wait(4500);const hlsState={time:v.currentTime,frames:v.getVideoPlaybackQuality().totalVideoFrames,ready:v.readyState,engine:engineName};check('HLS decoded frames',hlsState.time>1&&hlsState.frames>10,hlsState);
 pause();const paused=v.currentTime;await wait(700);check('Manual pause stays paused',v.paused&&Math.abs(v.currentTime-paused)<.1,{});
 full(true);check('Fullscreen occupies viewport',$('player-box').getBoundingClientRect().width===innerWidth,{});full(false);
 resume();await wait(700);check('Resume advances video',!v.paused&&v.currentTime>paused,{});
 const live=current;await play(live,'ts');await wait(4000);check('MPEG-TS decoded frames',engineName==='ts'&&v.currentTime>1&&v.getVideoPlaybackQuality().totalVideoFrames>10,{engine:engineName,time:v.currentTime,frames:v.getVideoPlaybackQuality().totalVideoFrames});
 await changeTab('vod');check('Movie catalog',filtered.length===1,{});await play(filtered[0]);await wait(4500);check('MP4 decoded frames',v.currentTime>1&&v.readyState>=2&&v.getVideoPlaybackQuality().totalVideoFrames>10,{time:v.currentTime,frames:v.getVideoPlaybackQuality().totalVideoFrames});v.currentTime=12;await wait(500);savePosition();const movie=current;stop();await play(movie);await wait(4000);check('VOD resume position',v.currentTime>13&&!v.paused&&v.getVideoPlaybackQuality().totalVideoFrames>10,{time:v.currentTime,frames:v.getVideoPlaybackQuality().totalVideoFrames});
 await changeTab('series');await openSeries(filtered[0]);check('Series seasons and episodes',$('season').options.length===1&&$('episodes').children.length===2,{});$('episodes-dialog').close();stop();await changeTab('live');await play(filtered[0]);await wait(1500);pause();
 check('Page has no horizontal overflow',document.documentElement.scrollWidth<=innerWidth,{width:innerWidth,scroll:document.documentElement.scrollWidth});
 return checks;
})()
