'use strict';
// One owned local document; Android issues connection/request generations, not URL fragments.
window.Controller=(()=>{
 let phone=true,epoch=0,request=0,version=0,beginVersion=0,beginQueue=Promise.resolve(),bound=false,sequence=0,pendingItem=null,last={connected:false,revision:-1,playback:{}},pollSlot=null,pollTicket=0,intent=0;
 const isPhone=()=>phone;
 function configure(environment){phone=!!environment.controller;document.body.classList.toggle('controller',phone);$('controller-panel').hidden=!phone;$('background').closest('label').hidden=true;if(phone){v.pause();v.removeAttribute('src');v.load();document.title='MM IPTV Controller';display(last);poll();}}
 function alertDisconnected(){notice(t('carRequired'));$('controller-alert-text').textContent=t('carRequired');if(!$('controller-alert').open)$('controller-alert').showModal();}
 function display(state){
  if(state&&Number(state.revision??0)<Number(last.revision??-1))return;
  last=state||{connected:false,revision:last.revision,playback:{}};const p=last.connected?last.playback||{}:{},usable=last.connected&&last.fresh!==false;
  $('controller-connection').textContent=t(last.connected?'carConnected':'carDisconnected');$('controller-panel').classList.toggle('connected',!!last.connected);
  $('controller-title').textContent=p.item?.name||t('carNothing');$('controller-phase').textContent=p.item?(last.fresh===false&&!['loading','idle'].includes(p.phase)?t('statusRecovering'):phaseLabel(p.phase)):t('controllerHint');
  const controls={play:usable&&p.canPlay,pause:usable&&p.canPause,stop:last.connected&&!!p.item,next:usable&&p.next,previous:usable&&p.previous,retry:last.connected&&!!p.item,full:usable&&p.canFullscreen};
  for(const [id,enabled] of Object.entries(controls))$('controller-'+id).disabled=!enabled;
  const seekable=!!(usable&&p.seekable);$('controller-seek').hidden=!seekable;$('controller-seek').disabled=!seekable;
  if(seekable&&document.activeElement!==$('controller-seek'))$('controller-seek').value=String((p.time||0)/p.duration*100);
  $('controller-time').textContent=!p.item?'':p.item.type==='live'?'● LIVE':clock(p.time||0)+' / '+clock(p.duration);
  current=last.connected&&p.source===source?p.item||null:null;if(current){$('selection-title').textContent=current.name;if(playbackPhase!==(p.phase||'idle'))setPlaybackPhase(p.phase||'idle');else updateCards();}else {$('selection-feedback').hidden=true;setPlaybackPhase('idle');}
 }
 async function poll(){
  if(!phone)return;const now=performance.now();if(pollSlot&&now-pollSlot.started<3000)return;
  const slot={ticket:++pollTicket,started:now};pollSlot=slot;
  try{const state=await api('controllerstate',{},2500);if(pollSlot===slot)display(state);}catch{if(pollSlot===slot)display(null);}finally{if(pollSlot===slot)pollSlot=null;}
 }
 async function command(action,extra={}){
  const own=++intent,connection=last.epoch;
  // No wait-for-connection path: never carry a click across reconnection.
  if(!last.connected){alertDisconnected();return false;}
  try{const accepted=await api('controllercommand',{...extra,action,epoch:connection,request:last.request},2500);if(own!==intent)return accepted;
   if(!accepted){notice(t('carCommandFailed'));poll();return false;}
   // Sending is not evidence of playing or paused. Buttons follow the next observed state.
   poll();return true;
  }catch{if(own===intent)notice(t('carCommandFailed'));return false;}
 }
 function select(item){const context={source,publicSource,tab,category:$('category').value,search:$('search').value,format:$('format').value};if(item.type==='episode')context.episodes=(seriesData?.[$('season').value]||[]).map(e=>({id:String(e.id),type:'episode',name:String(e.title||seriesName),ext:e.container_extension||'mp4'}));return command('select',{item,context});}
 function cancelPending(){++version;++beginVersion;pendingItem=null;++connectionGeneration;++loadGeneration;connectionBusy(false);}
 function connection(next,nextRequest){if(phone||epoch===next)return;epoch=next;request=nextRequest;sequence=0;bound=!!next;cancelPending();stop();publish();}
 const credentials=()=>({epoch,request});
 const matches=(e,r)=>!phone&&bound&&epoch===e&&request===r;
 function begin(item){const op=++beginVersion;bound=false;
  // Serialize native lease assignment, but skip obsolete selections before they start.
  // An already-issued lease must advance the request even if its media was superseded.
  const next=beginQueue.then(async()=>{if(op!==beginVersion||!epoch)return false;const e=epoch,r=request;
   try{const lease=await api('playerbegin',{epoch:e,request:r,item:{id:String(item.id),type:item.type,name:item.name}},2500);if(e!==epoch||r!==request)return false;request=lease.request;sequence=0;bound=op===beginVersion;return bound;}catch{return false;}
  });beginQueue=next.catch(()=>false);return next;
 }
 function snapshot(){
  if(phone||!epoch||!bound)return null;
  const item=pendingItem||current,hasMedia=!!current&&!pendingItem&&!!v.getAttribute('src'),ready=hasMedia?v.readyState:0,error=!!current&&(playbackFault||hasMedia&&!!v.error),ended=hasMedia&&v.ended;
  const phase=pendingItem?'loading':!item?'idle':error?'error':!ready?'loading':ended?'ended':v.paused?'paused':ready<3?'buffering':'playing';
  const usable=hasMedia&&ready>0&&!error,queue=filtered.filter(i=>i.type!=='series'),inQueue=!!item&&queue.some(i=>key(i)===key(item)),skip=usable&&inQueue&&queue.length>1;
  return {epoch,request,sequence:++sequence,source,phase,item:item?{id:String(item.id),name:String(item.name).slice(0,500),type:item.type}:null,ready,playing:usable&&!v.paused&&!ended,paused:v.paused,ended,error,canPlay:usable&&(v.paused||ended),canPause:usable&&!v.paused&&!ended,seekable:usable&&item.type!=='live'&&Number.isFinite(v.duration)&&v.duration>0&&v.seekable.length>0,next:skip,previous:skip,canFullscreen:usable,mute:usable,time:hasMedia?Math.max(0,v.currentTime||0):0,duration:hasMedia&&Number.isFinite(v.duration)?v.duration:0,muted:v.muted,wanted:window.__magdiPlayback?.state().wanted||false};
 }
 function publish(){const state=snapshot();if(state)try{Android.carState(JSON.stringify(state));}catch{}}
 async function receive(packet){
  if(phone||!epoch||packet.epoch!==epoch||packet.request<request)return;
  request=packet.request;bound=true;
  if(packet.action!=='select'){
   if(['stop','retry','next','previous'].includes(packet.action))cancelPending();
   switch(packet.action){
    case 'pause':pause();break;case 'resume':resume();break;case 'stop':stop();break;
    case 'next':skip(1);break;case 'previous':skip(-1);break;case 'retry':if(current)play(current);break;
    case 'mute':v.muted=!v.muted;break;case 'fullscreen':toggleFullscreen();break;
    case 'seek':if(current?.type!=='live'&&Number.isFinite(v.duration)&&v.seekable.length){const target=Math.max(0,Math.min(100,Number(packet.percent)||0))/100*v.duration;v.currentTime=Math.max(v.seekable.start(0),Math.min(v.seekable.end(v.seekable.length-1),target));}break;
   }publish();return;
  }
  cancelPending();const generation=version,session=epoch,ctx=packet.context||{},item=packet.item;
  if(!item||!['live','vod','episode'].includes(item.type))return;
  stop();pendingItem=item;publish();
  const valid=()=>generation===version&&session===epoch&&epoch!==0;
  try{
   for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();
   if(source!==ctx.source||$('workspace').hidden){if(ctx.publicSource){$('public-preset').value=ctx.publicSource;await connectPublic();}else {account=await api('config');if(!valid())return;if(!account.configured)throw Error('Account unavailable');await connectAccount();}}
   if(!valid())return;if(source!==ctx.source||$('workspace').hidden)throw Error('Source unavailable');
   if(item.type==='episode'){items=Array.isArray(ctx.episodes)&&ctx.episodes.length?ctx.episodes:[item];filtered=items;shown=60;render();}
   else {await changeTab(['live','vod','favorites','recent'].includes(ctx.tab)?ctx.tab:'live');if(!valid())return;if(ctx.category&&Array.from($('category').options).some(o=>o.value===ctx.category)){$('category').value=ctx.category;if(mode==='xtream')await loadItems();}if(!valid())return;$('search').value=ctx.search||'';filter();}
   if(!valid())return;$('format').value=['auto','m3u8','ts'].includes(ctx.format)?ctx.format:'auto';EmbeddedSelects.refresh();pendingItem=null;await play(item);
  }catch{if(valid()){pendingItem=null;current=item;playbackFault=true;setPlaybackPhase('error');message(t('carPlayError'));}}
  finally{if(valid())publish();}
 }
 function stopLocal(){if(phone)return command('stop');cancelPending();stop();if(epoch)api('carstop',{epoch,request},2500).catch(()=>{});}
 function accountChanged(nextRequest){if(!phone&&!publicSource){request=nextRequest;bound=true;cancelPending();source='';cache.clear();stop();}}
 function localSelection(){if(!phone)cancelPending();}
 function init(){
  $('controller-close-alert').onclick=()=>$('controller-alert').close();$('controller-play').onclick=()=>command('resume');$('controller-pause').onclick=()=>command('pause');
  for(const action of ['stop','next','previous','retry'])$('controller-'+action).onclick=()=>command(action);
  $('controller-full').onclick=()=>command('fullscreen');$('controller-seek').onchange=()=>command('seek',{percent:Number($('controller-seek').value)});
  setInterval(()=>phone?poll():publish(),500);
  window.addEventListener('storage',e=>{if(source&&e.key?.startsWith(source+':')){favorites=readStore(source+':favorites',[]);recent=readStore(source+':recent',[]);positions=readStore(source+':positions',{});if(tab==='favorites'||tab==='recent'){items=tab==='favorites'?favorites:recent;filter();}else updateCards();}});
 }
 return {isPhone,configure,init,select,command,receive,connection,publish,snapshot,credentials,matches,begin,stopLocal,localSelection,accountChanged,refresh:()=>phone&&display(last)};
})();
