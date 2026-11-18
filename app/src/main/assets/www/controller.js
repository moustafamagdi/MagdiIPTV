'use strict';
// The phone never attaches media. Commands and snapshots stay inside the Android process.
window.Controller=(()=>{
 let phone=true,epoch=0,version=0,pendingItem=null,last={connected:false,playback:{}},polling=false;
 const isPhone=()=>phone;
 function configure(environment){phone=!!environment.controller;document.body.classList.toggle('controller',phone);$('controller-panel').hidden=!phone;$('background').closest('label').hidden=true;if(phone){v.pause();v.removeAttribute('src');v.load();document.title='Magdi IPTV Controller';poll();}}
 function alertDisconnected(){notice(t('carRequired'));$('controller-alert-text').textContent=t('carRequired');if(!$('controller-alert').open)$('controller-alert').showModal();}
 function display(state){
  last=state||{connected:false,playback:{}};const p=last.connected?last.playback||{}:{};
  $('controller-connection').textContent=t(last.connected?'carConnected':'carDisconnected');$('controller-panel').classList.toggle('connected',!!last.connected);
  $('controller-title').textContent=p.item?.name||t('carNothing');$('controller-phase').textContent=p.error?t('carPlayError'):p.item?phaseLabel(p.phase):t('controllerHint');
  $('controller-play').textContent=p.phase==='playing'||p.phase==='loading'?'Ⅱ':'▶';
  $('controller-play').setAttribute('aria-label',t(p.phase==='playing'||p.phase==='loading'?'pauseControl':'playControl'));
  for(const id of ['controller-play','controller-stop','controller-next','controller-previous','controller-retry','controller-full'])$(id).disabled=!last.connected||!p.item;
  const seekable=p.item?.type!=='live'&&Number.isFinite(p.duration)&&p.duration>0;$('controller-seek').hidden=!seekable;
  if(seekable&&document.activeElement!==$('controller-seek'))$('controller-seek').value=String((p.time||0)/p.duration*100);
  $('controller-time').textContent=!p.item?'':p.item.type==='live'?'● LIVE':clock(p.time||0)+' / '+clock(p.duration);
  current=last.connected&&p.source===source?p.item||null:null;if(current){$('selection-title').textContent=current.name;if(playbackPhase!==(p.phase||'idle'))setPlaybackPhase(p.phase||'idle');else updateCards();}else {$('selection-feedback').hidden=true;setPlaybackPhase('idle');}
 }
 async function poll(){if(!phone||polling)return;polling=true;try{display(await api('controllerstate'));}catch{display(null);}finally{polling=false;}}
 async function command(action,extra={}){
  try{const state=await api('controllerstate');display(state);if(!state.connected){alertDisconnected();return false;}
   const accepted=await api('controllercommand',{...extra,action,epoch:state.epoch});if(!accepted){display(null);alertDisconnected();return false;}
   if(action==='select'){$('controller-title').textContent=extra.item.name;$('controller-phase').textContent=t('sendingToCar');current=extra.item;setPlaybackPhase('loading');showSelection();$('selection-state').textContent=t('sendingToCar');}
   return true;
  }catch{notice(t('carCommandFailed'));return false;}
 }
 function select(item){
  const context={source,publicSource,tab,category:$('category').value,search:$('search').value,format:$('format').value};
  if(item.type==='episode')context.episodes=(seriesData?.[$('season').value]||[]).map(e=>({id:String(e.id),type:'episode',name:String(e.title||seriesName),ext:e.container_extension||'mp4'}));
  return command('select',{item,context});
 }
 function cancelPending(){++version;pendingItem=null;++connectionGeneration;++loadGeneration;connectionBusy(false);}
 function connection(next){if(phone)return;if(epoch===next)return;epoch=next;cancelPending();stop();publish();}
 function publish(){
  if(phone||!epoch)return;
  const item=pendingItem||current;
  const state={epoch,source,phase:pendingItem?'loading':playbackPhase,item:item?{id:String(item.id),name:String(item.name).slice(0,500),type:item.type}:null,time:Math.floor(v.currentTime||0),duration:Number.isFinite(v.duration)?v.duration:0,muted:v.muted,error:playbackPhase==='error'};
  try{Android.carState(JSON.stringify(state));}catch{}
 }
 async function receive(packet){
  if(phone||!epoch||packet.epoch!==epoch)return;
  if(packet.action!=='select'){
   if(pendingItem){cancelPending();stop();if(packet.action!=='stop'&&packet.action!=='pause'){publish();return;}}
   switch(packet.action){
    case 'pause':pause();break;case 'resume':resume();break;case 'stop':stop();break;
    case 'next':skip(1);break;case 'previous':skip(-1);break;case 'retry':if(current)play(current);break;
    case 'mute':v.muted=!v.muted;break;case 'fullscreen':full(!$('player-box').classList.contains('full'));break;
    case 'seek':if(current?.type!=='live'&&Number.isFinite(v.duration))v.currentTime=Math.max(0,Math.min(100,Number(packet.percent)||0))/100*v.duration;break;
   }publish();return;
  }
  cancelPending();const request=version,session=epoch,ctx=packet.context||{},item=packet.item;
  if(!item||!['live','vod','episode'].includes(item.type))return;
  stop();pendingItem=item;publish();
  const valid=()=>request===version&&session===epoch&&epoch!==0;
  try{
   for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();
   if(source!==ctx.source||$('workspace').hidden){
    if(ctx.publicSource){$('public-preset').value=ctx.publicSource;await connectPublic();}
    else {account=await api('config');if(!valid())return;if(!account.configured)throw Error('Account unavailable');await connectAccount();}
   }
   if(!valid())return;
   if(source!==ctx.source||$('workspace').hidden)throw Error('Source unavailable');
   if(item.type==='episode'){
    items=Array.isArray(ctx.episodes)&&ctx.episodes.length?ctx.episodes:[item];filtered=items;shown=60;render();
   }else{
    await changeTab(['live','vod','favorites','recent'].includes(ctx.tab)?ctx.tab:'live');if(!valid())return;
    if(ctx.category&&Array.from($('category').options).some(o=>o.value===ctx.category)){$('category').value=ctx.category;if(mode==='xtream')await loadItems();}
    if(!valid())return;$('search').value=ctx.search||'';filter();
   }
   if(!valid())return;$('format').value=['auto','m3u8','ts'].includes(ctx.format)?ctx.format:'auto';EmbeddedSelects.refresh();pendingItem=null;await play(item);
  }catch{if(valid()){pendingItem=null;current=item;setPlaybackPhase('error');message(t('carPlayError'));}}
  finally{if(valid())publish();}
 }
 function accountChanged(){if(!phone&&!publicSource){cancelPending();source='';cache.clear();stop();}}
 function localSelection(){if(!phone){cancelPending();}}
 function init(){
  $('controller-close-alert').onclick=()=>$('controller-alert').close();
  $('controller-play').onclick=()=>command(['playing','loading'].includes(last.playback?.phase)?'pause':'resume');
  for(const action of ['stop','next','previous','retry'])$('controller-'+action).onclick=()=>command(action);
  $('controller-full').onclick=()=>command('fullscreen');$('controller-seek').onchange=()=>command('seek',{percent:Number($('controller-seek').value)});
  setInterval(()=>phone?poll():publish(),1000);
  window.addEventListener('storage',e=>{if(source&&e.key?.startsWith(source+':')){favorites=readStore(source+':favorites',[]);recent=readStore(source+':recent',[]);positions=readStore(source+':positions',{});if(tab==='favorites'||tab==='recent'){items=tab==='favorites'?favorites:recent;filter();}else updateCards();}});
 }
 return {isPhone,configure,init,select,command,receive,connection,publish,localSelection,accountChanged,refresh:()=>phone&&display(last)};
})();
