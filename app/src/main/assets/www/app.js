'use strict';
const $=id=>document.getElementById(id),v=$('video');
let seq=0;const pending=new Map();
window.nativeReply=(id,result)=>{const task=pending.get(id);if(!task)return;clearTimeout(task.timer);pending.delete(id);result.ok?task.resolve(result.value):task.reject(new Error(result.error));};
function api(method,args={},timeout=65000){return new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{pending.delete(id);reject(new Error(t('offline')));},timeout);pending.set(id,{resolve,reject,timer});try{Android.request(String(id),method,JSON.stringify(args));}catch(e){clearTimeout(timer);pending.delete(id);reject(new Error('Android bridge unavailable'));}});}
let env={},account={},source='',mode='xtream',tab='live',categories=[],items=[],filtered=[],shown=60,current=null,engine=null,engineName='native',attempt=0,loadGeneration=0,playGeneration=0,loading=false,seriesData=null,seriesName='',bufferTimer,pendingResume=null;
let publicSource='',connectionGeneration=0;
const videoHealth=new VideoHealth();
let activeMedia=null,recoveryPending=false,automaticRecoveries=0,playIntent=false;
let pendingSeek=null,seekTimer,seekIssue='',seekDragging=false;
const cache=new Map();let playlistItems=[],favorites=[],recent=[],positions={};
let playbackFault=false,playbackPhase='idle',feedbackTimer,seriesGeneration=0,seriesLoading=false,openingSeriesKey='';
function readStore(key,fallback){try{return JSON.parse(localStorage.getItem(key))||fallback;}catch{return fallback;}}
function persist(kind='playback'){try{if(kind==='favorites')localStorage.setItem(source+':favorites',JSON.stringify(favorites));else {localStorage.setItem(source+':recent',JSON.stringify(recent.slice(0,60)));localStorage.setItem(source+':positions',JSON.stringify(positions));}}catch{notice(language==='ar'?'مساحة حفظ السجل ممتلئة.':'History storage is full.');}}
function key(item){return item.type+':'+item.id;}
function notice(text){$('notice').textContent=text||'';$('notice').hidden=!text;}
function message(text){$('playback-message').textContent=text||'';$('playback-message').hidden=!text;}
function errorText(e){return language==='ar'?t('offline')+'\n'+e.message:e.message;}
function busy(button,value){button.disabled=value;}
async function initialize(){applyLanguage();try{env=await api('environment');$('app-version').textContent=env.version||'';Controller.configure(env);EmbeddedKeyboard.configure(!!env.car);$('keyboard').hidden=!env.car;$('search').readOnly=!!env.car;$('background').checked=env.background;$('setup-auto').hidden=!!env.car;$('log-sessions').hidden=!!env.car;account=await api('config');$('welcome-state').textContent=t(account.configured?'ready':'noAccount');await api('ready');}catch(e){notice(errorText(e));}}
let addingAccount=false,accountBusy=false;
function renderAccountForm(){
 const picker=$('saved-accounts');
 picker.replaceChildren();
 (account.profiles||[]).forEach((profile,index)=>{
  picker.add(new Option(profile.name||t('accountName')+' '+(index+1),profile.id));
 });
 picker.value=account.activeId||'';
 if(!picker.options.length)picker.add(new Option(t('noSavedAccounts'),''));
 const fields=addingAccount?{}:account;
 $('account-name').value=fields.label||'';$('host').value=fields.host||'';$('username').value=fields.username||'';
 $('password').value='';$('password').placeholder=fields.hasPassword?'••••••••':'';
 $('playlist').value=fields.playlist||'';$('source-mode').value=fields.mode||'xtream';
 $('forget').disabled=addingAccount||!account.configured;
 updateAccountSwitch();sourceMode();
}
function updateAccountSwitch(){
 $('switch-account').disabled=accountBusy||!$('saved-accounts').value||(!addingAccount&&$('saved-accounts').value===account.activeId);
 EmbeddedSelects.refresh();
}
async function openSettings(){
 if(accountBusy)return;
 account=await api('config');addingAccount=false;renderAccountForm();$('form-status').textContent='';
 if(!$('settings-dialog').open)$('settings-dialog').showModal();
}
function sourceMode(){EmbeddedSelects.refresh();const m=$('source-mode').value;$('xtream-fields').hidden=m==='m3u';$('m3u-fields').hidden=m!=='m3u';}
function clearSubscriptionView(){
 ++connectionGeneration;++loadGeneration;++seriesGeneration;
 stop();cache.clear();source='';publicSource='';items=[];filtered=[];categories=[];playlistItems=[];
 seriesData=null;favorites=[];recent=[];positions={};
 $('workspace').hidden=true;$('welcome').hidden=false;$('welcome-state').textContent=t(account.configured?'ready':'noAccount');
 connectionBusy(false);
}
async function changeAccount(method,args,connect=false){
 if(accountBusy)return;
 accountBusy=true;
 const submit=document.querySelector('#account-form button[type=submit]');
 for(const button of [submit,$('save-only'),$('add-account'),$('forget'),$('switch-account')])button.disabled=true;
 try{
  await api(method,args);account=await api('config');addingAccount=false;clearSubscriptionView();renderAccountForm();
  $('form-status').textContent=t(method==='forget'?'forgotten':'saved');
  if(connect&&account.configured){$('settings-dialog').close();await connectAccount();}
 }catch(e){$('form-status').textContent=errorText(e);}
 finally{
  accountBusy=false;submit.disabled=false;$('save-only').disabled=false;$('add-account').disabled=false;
  $('forget').disabled=addingAccount||!account.configured;updateAccountSwitch();
 }
}
async function save(connect){
 await changeAccount('save',{activeId:account.activeId||'',adding:addingAccount,label:$('account-name').value,
 mode:$('source-mode').value,host:$('host').value.trim(),username:$('username').value.trim(),
 password:$('password').value,playlist:$('playlist').value.trim()},connect);
}
async function connectAccount(){if(!account.configured){await openSettings();return;}const connection=++connectionGeneration;connectionBusy(true);stop();$('workspace').hidden=true;$('welcome').hidden=false;busy($('connect'),true);notice('');$('welcome-state').textContent=t('connecting');try{const info=await api('connect');if(connection!==connectionGeneration)return;stop();publicSource='';source=info.source;mode=info.mode;favorites=readStore(source+':favorites',[]);recent=readStore(source+':recent',[]);positions=readStore(source+':positions',{});cache.clear();if(mode==='m3u'){const data=await api('playlist');if(connection!==connectionGeneration)return;if(!Array.isArray(data.playlist?.items))throw new Error('Invalid M3U response');playlistItems=data.playlist.items.map((c,i)=>({id:String(i)+':'+hash(c.url||''),type:'live',name:c.name||'Channel '+(i+1),...Metadata.fields(c),group:c.group?.title||c.group||'',url:c.url,ext:/\.m3u8(?:\?|$)/i.test(c.url||'')?'m3u8':/\.ts(?:\?|$)/i.test(c.url||'')?'ts':'mp4'}));}
 ++loadGeneration;updateSourceLabel();$('welcome').hidden=true;$('workspace').hidden=false;document.querySelectorAll('[data-tab=vod],[data-tab=series]').forEach(e=>e.hidden=mode==='m3u');await changeTab('live');
 }catch(e){if(connection===connectionGeneration){$('welcome-state').textContent=t('offline');notice(errorText(e));}}finally{if(connection===connectionGeneration)connectionBusy(false);}}
function connectionBusy(value){for(const id of ['connect','configure','settings','public-open','public-switch','subscription-switch','public-load','refresh'])busy($(id),value);$('public-preset').disabled=value;EmbeddedSelects.refresh();}
function updateSourceLabel(){$('active-source').textContent=publicSource?t({world:'publicWorld',egypt:'publicEgypt',saudi:'publicSaudi'}[publicSource]):t('mySubscription');$('subscription-switch').hidden=!publicSource;}
function openPublic(){if(publicSource)$('public-preset').value=publicSource;$('public-status').textContent='';EmbeddedSelects.refresh();if(!$('public-dialog').open)$('public-dialog').showModal();}
function cancelPublic(){++connectionGeneration;connectionBusy(false);$('public-dialog').close();}
async function connectPublic(){const preset=$('public-preset').value,connection=++connectionGeneration;connectionBusy(true);$('public-status').textContent=t('publicLoading');
 try{const data=await api('publicplaylist',{preset});if(connection!==connectionGeneration)return;
  if(!Array.isArray(data.playlist?.items)||!data.playlist.items.length)throw new Error('Empty public playlist');
  const next=data.playlist.items.map((c,i)=>({id:c.url,type:'live',name:String(c.name||'Channel '+(i+1)),...Metadata.fields(c),group:String(c.group?.title||c.group||''),url:c.url,ext:/\.ts(?:\?|$)/i.test(c.url)?'ts':/\.mp4(?:\?|$)/i.test(c.url)?'mp4':'m3u8'}));
  stop();++loadGeneration;publicSource=preset;source='public:'+preset;mode='m3u';playlistItems=next;cache.clear();
  favorites=readStore(source+':favorites',[]);recent=readStore(source+':recent',[]);positions={};
  $('welcome').hidden=true;$('workspace').hidden=false;document.querySelectorAll('[data-tab=vod],[data-tab=series]').forEach(e=>e.hidden=true);updateSourceLabel();await changeTab('live');$('public-dialog').close();
 }catch(e){if(connection===connectionGeneration)$('public-status').textContent=t('publicError');}
 finally{if(connection===connectionGeneration)connectionBusy(false);}
}
function hash(s){let h=0;for(let i=0;i<s.length;i++)h=((h<<5)-h+s.charCodeAt(i))|0;return (h>>>0).toString(16);}
function normalize(c,type){return {id:String(c.stream_id??c.series_id??c.id),type,name:String(c.name||c.title||'Untitled'),category:String(c.category_id||''),ext:c.container_extension||'mp4',...Metadata.fields(c)};}
async function changeTab(next){tab=next;const generation=++loadGeneration;$('section-title').textContent=t(tab);$('search').value='';document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('selected',b.dataset.tab===tab));$('category').replaceChildren();$('list').replaceChildren();empty(t('loading'));notice('');try{
 if(next==='favorites'||next==='recent'){categories=[];renderCategories();items=next==='favorites'?favorites:recent;filter();return;}
 if(mode==='m3u'){categories=[...new Set(playlistItems.map(i=>String(i.group)))].filter(Boolean).map(x=>({category_id:x,category_name:x}));renderCategories();items=playlistItems;filter();return;}
 const k=next+':categories';let data=cache.get(k);if(!data){data=await api('catalog',{type:next,categories:true});cache.set(k,data);}if(generation!==loadGeneration)return;if(!Array.isArray(data))throw new Error('Invalid catalog response');categories=data;renderCategories();await loadItems();
 }catch(e){if(generation===loadGeneration){empty(t('offline'));notice(errorText(e));}}}
function renderCategories(){const select=$('category');select.replaceChildren();select.add(new Option(t('all'),''));for(const c of categories)select.add(new Option(String(c.category_name||c.name||''),String(c.category_id)));select.disabled=tab==='favorites'||tab==='recent';}
async function loadItems(){const type=tab,category=$('category').value,generation=++loadGeneration;loading=true;empty(t('loading'));$('more').hidden=true;try{
 if(mode==='m3u'){items=playlistItems;filter();return;}const k=type+':'+category;let data=cache.get(k);if(!data){data=await api('catalog',{type,category_id:category});if(!Array.isArray(data))throw new Error('Invalid catalog response');if(cache.size>12)cache.clear();cache.set(k,data);}if(generation!==loadGeneration)return;items=data.map(c=>normalize(c,type));filter();
 }catch(e){if(generation===loadGeneration){empty(t('offline'));notice(errorText(e));}}finally{if(generation===loadGeneration)loading=false;}}
function filter(){const q=$('search').value.trim().toLocaleLowerCase(),cat=$('category').value;filtered=items.filter(i=>(!q||i.name.toLocaleLowerCase().includes(q))&&(mode!=='m3u'||!cat||String(i.group)===cat));shown=60;render();}
function empty(text){const div=document.createElement('div');div.className='empty';div.textContent=text;$('list').replaceChildren(div);$('count').textContent='';}
function render(){const list=$('list');list.replaceChildren();$('count').textContent=filtered.length.toLocaleString();if(!filtered.length)empty(t(tab==='favorites'?'emptyFav':tab==='recent'?'emptyRecent':'empty'));for(const item of filtered.slice(0,shown))list.append(card(item));$('more').hidden=filtered.length<=shown;Metadata.prune();}
function phaseLabel(phase){return t({seeking:'seekingVideo',recovering:'recoveringVideo',loading:'starting',buffering:'buffering',playing:'playingNow',paused:'pausedState',error:'retrySelection',ended:'endedState'}[phase]||'starting');}
function updateCard(row){const active=!!current&&row.dataset.key===key(current),opening=seriesLoading&&row.dataset.key===openingSeriesKey,phase=opening?'loading':active?playbackPhase:'idle';row.classList.toggle('active',active||opening);row.classList.toggle('is-loading',['loading','recovering','seeking'].includes(phase));row.classList.toggle('is-error',phase==='error');const status=row.querySelector('.selection-status'),pick=row.querySelector('.pick');status.hidden=phase==='idle';status.textContent=opening?t('loadingEpisodes'):phaseLabel(phase);pick.setAttribute('aria-busy',String(['loading','recovering'].includes(phase)));if(active)pick.setAttribute('aria-current','true');else pick.removeAttribute('aria-current');}
function updateCards(){document.querySelectorAll('.card[data-key]').forEach(updateCard);}
function setPlaybackPhase(phase){if(phase==='error')playbackFault=true;$('restart-start').hidden=Controller.isPhone()||phase!=='error'||!current||current.type==='live';playbackPhase=phase;Controller.publish();updateCards();const feedback=$('selection-feedback');if(!current||feedback.hidden)return;clearTimeout(feedbackTimer);feedback.classList.toggle('is-loading',['loading','recovering'].includes(phase));feedback.classList.toggle('is-error',phase==='error');$('selection-state').textContent=phaseLabel(phase);if(!['loading','recovering','seeking','error'].includes(phase))feedbackTimer=setTimeout(()=>{feedback.hidden=true;},3000);}
function showSelection(){if(!current)return;$('selection-title').textContent=current.name;$('selection-feedback').hidden=false;setPlaybackPhase(playbackPhase);}
function selectItem(item){if(item.type==='series'){openSeries(item);return;}if($('episodes-dialog').open)$('episodes-dialog').close();if(current&&key(current)===key(item)&&['loading','recovering','playing','paused'].includes(playbackPhase)){showSelection();if(playbackPhase==='paused')resume();return;}Controller.localSelection();play(item);}
function pressFeedback(button){let start;const clear=()=>{button.classList.remove('pressed');start=null;};button.addEventListener('pointerdown',e=>{if(e.isPrimary&&e.button===0){start={x:e.clientX,y:e.clientY};button.classList.add('pressed');}},{passive:true});button.addEventListener('pointermove',e=>{if(start&&Math.hypot(e.clientX-start.x,e.clientY-start.y)>10)clear();},{passive:true});for(const name of ['pointerup','pointercancel','pointerleave','lostpointercapture','blur'])button.addEventListener(name,clear);}
function card(item){const row=document.createElement('article');row.className='card';row.dataset.key=key(item);const tile=Metadata.tile(item);const pick=document.createElement('button');pick.type='button';pick.className='pick';pressFeedback(pick);const copy=document.createElement('span');copy.className='card-copy';const title=document.createElement('strong');title.textContent=item.name;const sub=document.createElement('small');sub.className='card-meta';sub.textContent=[t(item.type==='episode'?'episode':item.type),item.year,item.rating?'★ '+item.rating:''].filter(Boolean).join(' · ');const status=document.createElement('small');status.className='selection-status';status.hidden=true;copy.append(title,sub,status);pick.append(tile,copy);pick.onclick=()=>item.type==='vod'?Metadata.open(item):selectItem(item);const star=document.createElement('button');star.type='button';star.className='star';star.textContent=isFavorite(item)?'★':'☆';star.setAttribute('aria-label',t('favorites'));star.onclick=()=>toggleFavorite(item);row.append(pick);if(item.type==='episode'||(item.type==='live'&&item.description)){const info=document.createElement('button');info.className='info-button';info.textContent='ⓘ';info.setAttribute('aria-label',t('contentDetails'));info.onclick=()=>Metadata.open(item);row.append(info);}row.append(star);updateCard(row);return row;}
function isFavorite(item){return favorites.some(x=>key(x)===key(item));}
function toggleFavorite(item){if(isFavorite(item))favorites=favorites.filter(x=>key(x)!==key(item));else favorites.unshift(item);persist('favorites');if(tab==='favorites'){items=favorites;filter();}else render();updateStar();}
function updateStar(){$('favorite-current').hidden=!current;if(current)$('favorite-current').textContent=isFavorite(current)?'★':'☆';}
async function openSeries(item){if(seriesLoading&&openingSeriesKey===key(item)&&$('episodes-dialog').open)return;const generation=++seriesGeneration;seriesLoading=true;openingSeriesKey=key(item);seriesData=null;seriesName=item.name;updateCards();$('series-title').textContent=item.name;Metadata.summary($('series-summary'),item);$('episodes').replaceChildren();$('season').replaceChildren();$('season').disabled=true;const loading=document.createElement('div');loading.className='empty episode-loading';loading.setAttribute('role','status');const spinner=document.createElement('span');spinner.className='feedback-indicator';spinner.setAttribute('aria-hidden','true');loading.append(spinner,document.createTextNode(t('loadingEpisodes')));$('episodes').append(loading);if(!$('episodes-dialog').open)$('episodes-dialog').showModal();try{const data=await api('episodes',{series_id:item.id});if(generation!==seriesGeneration||!$('episodes-dialog').open)return;seriesData=data.episodes||{};Metadata.summary($('series-summary'),Metadata.merge(item,data.info||{}));const seasons=Object.keys(seriesData).filter(s=>Array.isArray(seriesData[s])).sort((a,b)=>Number(a)-Number(b));for(const season of seasons)$('season').add(new Option(t('season')+' '+season,season));$('season').disabled=!seasons.length;renderEpisodes();}catch(e){if(generation!==seriesGeneration||!$('episodes-dialog').open)return;const p=document.createElement('p');p.textContent=errorText(e);const retry=document.createElement('button');retry.textContent=t('retry');retry.onclick=()=>openSeries(item);$('episodes').replaceChildren(p,retry);}finally{if(generation===seriesGeneration){seriesLoading=false;updateCards();}}}
function renderEpisodes(){$('episodes').replaceChildren();for(const e of (seriesData?.[$('season').value]||[])){const item={id:String(e.id),type:'episode',name:e.title||seriesName+' · '+t('episode')+' '+e.episode_num,ext:e.container_extension||'mp4',...Metadata.fields(e.info||e)};$('episodes').append(card(item));}if(!$('episodes').children.length){const p=document.createElement('p');p.className='empty';p.textContent=t('noEpisodes');$('episodes').append(p);}Metadata.prune();}
function destroyEngine(){cancelSeek();seekIssue='';activeMedia=null;recoveryPending=false;playIntent=false;videoHealth.reset();v.onerror=null;pendingResume=null;clearTimeout(bufferTimer);if(window.__magdiPlayback)window.__magdiPlayback.stop();if(engine){try{engine.destroy();}catch{}engine=null;}v.pause();v.removeAttribute('src');v.load();}
async function play(item,forced,startAtBeginning=false){if(Controller.isPhone())return Controller.select(item);const began=Controller.begin(item);automaticRecoveries=0;playbackFault=false;savePosition();if(startAtBeginning){delete positions[key(item)];persist();}const generation=++playGeneration;destroyEngine();playIntent=true;current=item;attempt=forced?1:0;$('player-placeholder').hidden=true;$('video-controls').hidden=false;$('now-title').textContent=item.name;$('now-meta').textContent=t(item.type==='episode'?'episode':item.type);$('format').disabled=item.type!=='live'||mode==='m3u';$('seek').hidden=item.type==='live';$('epg').hidden=true;updateStar();setPlaybackPhase('loading');showSelection();message(t('buffering'));notice('');
 try{if(!await began||generation!==playGeneration)return;let ext=forced||(item.type==='live'&&mode!=='m3u'?($('format').value==='auto'?'m3u8':$('format').value):item.ext||'mp4');let url;
 if(item.demo){url=item.url;ext=item.ext;}else url=await api(publicSource?'publicplay':'play',{...Controller.credentials(),preset:publicSource,type:item.type==='episode'?'series':item.type==='vod'?'movie':'live',id:item.id,ext,url:item.url||''});
 if(generation!==playGeneration)return;attach(url,ext,item.type==='live',generation);recent=[item,...recent.filter(x=>key(x)!==key(item))].slice(0,60);persist();
 if(item.type==='live'&&mode==='xtream'&&!item.demo)loadEpg(item,generation);
 }catch(e){if(generation===playGeneration){setPlaybackPhase('error');message(errorText(e));emit('error',1);}}}
function attach(url,ext,live,generation,resumeAt=null){if(Controller.isPhone())return;activeMedia={url,ext,live,generation,resumeAt,...Controller.credentials()};const onFailure=()=>{if(generation!==playGeneration)return;clearTimeout(bufferTimer);if(pendingSeek){failSeek();return;}if(recoveryPending){recoveryPending=false;playIntent=false;v.pause();emit('video-recovery-failed',automaticRecoveries);setPlaybackPhase('error');message(t('videoFrozenError'));return;}if(playIntent&&live&&mode==='xtream'&&ext==='m3u8'&&$('format').value==='auto'&&attempt===0){message(t('tryTs'));play(current,'ts');return;}setPlaybackPhase('error');message(t('playError'));emit('error',2);};
 const onPlayRejected=e=>{if(generation!==playGeneration||e?.name==='AbortError')return;if(e?.name==='NotAllowedError'){cancelVideoRecovery();playIntent=false;setPlaybackPhase('paused');message(t('retry'));}else onFailure();};
 bufferTimer=setTimeout(()=>{if(v.readyState<2||recoveryPending)onFailure();},30000);
 if(ext==='m3u8'&&window.Hls?.isSupported()){
  engineName='hls';const hls=new Hls({enableWorker:false,maxBufferLength:25,backBufferLength:15,manifestLoadingMaxRetry:2,levelLoadingMaxRetry:2,fragLoadingMaxRetry:2});engine=hls;hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)onFailure();});hls.on(Hls.Events.MANIFEST_PARSED,()=>{if(generation===playGeneration&&playIntent)v.play().catch(onPlayRejected);});hls.attachMedia(v);hls.loadSource(url);
 }else if(ext==='ts'&&window.mpegts?.isSupported()){
  engineName='ts';const ts=mpegts.createPlayer({type:'mpegts',isLive:live,url},{enableWorker:false,enableStashBuffer:false,liveBufferLatencyChasing:live,autoCleanupSourceBuffer:true});engine=ts;ts.on(mpegts.Events.ERROR,onFailure);ts.attachMediaElement(v);ts.load();if(playIntent)ts.play().catch(onPlayRejected);
 }else {engineName='native';v.src=url;if(playIntent)v.play().catch(onPlayRejected);else v.load();}
 v.onerror=()=>{if(v.error)onFailure();};
}


function cancelSeek(){clearTimeout(seekTimer);pendingSeek=null;}
function canSeek(){return !Controller.isPhone()&&!!current&&current.type!=='live'&&!!activeMedia&&!playbackFault&&v.readyState>0&&Number.isFinite(v.duration)&&v.duration>0&&v.seekable.length>0;}
function updateSeekControls(){const enabled=canSeek();for(const id of ['seek','seek-back','seek-forward']){$(id).disabled=!enabled;$(id).hidden=!current||current.type==='live'||Controller.isPhone();}}
function seekValid(op){return pendingSeek===op&&activeMedia&&op.generation===playGeneration&&Controller.matches(op.epoch,op.request);}
function failSeek(){if(!pendingSeek)return;const origin=pendingSeek.origin;++playGeneration;destroyEngine();seekIssue=origin==='saved'?'resumeError':'seekError';setPlaybackPhase('error');message(t(seekIssue));emit('seek-failed',origin==='saved'?1:0);}
function armSeek(op){clearTimeout(seekTimer);seekTimer=setTimeout(()=>{
 if(!seekValid(op))return;if(op.reloaded){failSeek();return;}
 const media=activeMedia,generation=++playGeneration;destroyEngine();op.generation=generation;op.reloaded=true;pendingSeek=op;playIntent=op.wasPlaying;playbackFault=false;
 setPlaybackPhase('seeking');message(t('seekingVideo'));emit('seek-retry');attach(media.url,media.ext,false,generation,op.target);armSeek(op);
},18000);}
function trackSeek(target,wasPlaying,origin='native'){cancelVideoRecovery();cancelSeek();pendingResume=null;videoHealth.reset();seekIssue='';const op={target,wasPlaying,origin,generation:playGeneration,...Controller.credentials(),reloaded:false};pendingSeek=op;positions[key(current)]=target;persist();setPlaybackPhase('seeking');message(t('seekingVideo'));emit('seek-start',origin==='saved'?1:origin==='manual'?2:0);armSeek(op);return op;}
function seekTo(target){
 if(!canSeek()||!Number.isFinite(target))return false;
 target=Math.max(0,Math.min(v.duration-.05,target));
 // Seekable is not the same as buffered: do not silently turn a half-film jump into a short jump.
 let allowed=false;for(let i=0;i<v.seekable.length;i++)if(target>=v.seekable.start(i)&&target<=v.seekable.end(i)){allowed=true;break;}
 if(!allowed){seekIssue='seekUnavailable';message(t('seekUnavailable'));Controller.publish();return false;}
 const wanted=pendingSeek?.wasPlaying??(!v.paused&&!v.ended);trackSeek(target,wanted,'manual');try{v.currentTime=target;}catch{failSeek();return false;}return true;
}
function seekBy(seconds){if(Controller.isPhone())return Controller.command('seekBy',{seconds});return seekTo((pendingSeek?.target??v.currentTime)+seconds);}
function finishSeek(){const op=pendingSeek;if(!op||!seekValid(op)||v.seeking||v.readyState<2||Math.abs(v.currentTime-op.target)>2)return;cancelSeek();clearTimeout(bufferTimer);seekIssue='';message('');setPlaybackPhase(v.paused?'paused':v.ended?'ended':'playing');savePosition();emit('seek-complete');}
v.addEventListener('seeking',()=>{if(!recoveryPending&&current?.type!=='live'&&activeMedia&&Number.isFinite(v.currentTime)&&(!pendingSeek||Math.abs(v.currentTime-pendingSeek.target)>2))trackSeek(v.currentTime,pendingSeek?.wasPlaying??(!v.paused&&!v.ended));});
for(const event of ['seeked','canplay','playing','timeupdate'])v.addEventListener(event,finishSeek);
v.addEventListener('pause',()=>{if(pendingSeek&&v.paused)pendingSeek.wasPlaying=false;});

function cancelVideoRecovery(){if(recoveryPending){recoveryPending=false;clearTimeout(bufferTimer);message('');emit('video-recovery-cancelled',automaticRecoveries);}videoHealth.reset();}
function recoverFrozenVideo(position){
 const media=activeMedia;if(!media||!current||!playIntent||v.paused||v.ended||v.seeking||media.generation!==playGeneration||!Controller.matches(media.epoch,media.request))return;
 emit('video-frozen',automaticRecoveries);
 if(automaticRecoveries>=2){playIntent=false;recoveryPending=false;videoHealth.reset();v.pause();setPlaybackPhase('error');message(t('videoFrozenError'));emit('video-recovery-exhausted',automaticRecoveries);return;}
 automaticRecoveries++;const generation=++playGeneration,muted=v.muted;
 // Keep the native connection/request lease and the same video element/fullscreen surface.
 // Resume VOD at the last frame-progress position, not the advancing audio clock.
 const resumeAt=!media.live&&Number.isFinite(position)?Math.max(0,position):null;
 if(resumeAt!==null){positions[key(current)]=resumeAt;persist();}
 destroyEngine();playIntent=true;recoveryPending=true;playbackFault=false;v.muted=muted;
 setPlaybackPhase('recovering');message(t('recoveringVideo'));emit('video-recovery-start',automaticRecoveries);
 attach(media.url,media.ext,media.live,generation,resumeAt);
}
function sampleVideoHealth(now=performance.now()){
 const media=activeMedia,rect=v.getBoundingClientRect(),visible=!!document.fullscreenElement||(rect.width>0&&rect.height>0&&rect.bottom>0&&rect.right>0&&rect.top<innerHeight&&rect.left<innerWidth&&getComputedStyle(v).visibility==='visible');let frames;try{const quality=v.getVideoPlaybackQuality?.();frames=quality?quality.totalVideoFrames-quality.droppedVideoFrames:NaN;}catch{frames=NaN;}
 const result=videoHealth.sample({active:!pendingSeek&&visible&&!Controller.isPhone()&&!!current&&!!media&&media.generation===playGeneration&&Controller.matches(media.epoch,media.request)&&(!document.hidden||!!document.fullscreenElement)&&playIntent,paused:v.paused,ended:v.ended,seeking:v.seeking,error:playbackFault||!!v.error,ready:v.readyState,width:v.videoWidth,height:v.videoHeight,frames,time:v.currentTime},now);
 if(result?.kind==='frozen')recoverFrozenVideo(result.position);
 else if(result?.kind==='progress'&&recoveryPending&&pendingResume===null){recoveryPending=false;clearTimeout(bufferTimer);setPlaybackPhase('playing');message('');emit('video-recovered',automaticRecoveries);}
}
for(const event of ['seeking','pause','ended'])v.addEventListener(event,()=>{videoHealth.reset();if(recoveryPending&&((event==='pause'&&v.paused)||(event==='ended'&&v.ended))){cancelVideoRecovery();playIntent=false;}});
setInterval(sampleVideoHealth,2000);

function emit(kind,code=0){try{Android.event(kind,JSON.stringify({time:Math.floor(v.currentTime||0),ready:v.readyState,frames:v.getVideoPlaybackQuality?.().totalVideoFrames||0,engine:engineName,code}));}catch{}}
function savePosition(){if(Controller.isPhone()||recoveryPending||pendingSeek)return;if(!current||current.type==='live'||!Number.isFinite(v.duration)||v.currentTime<5)return;positions[key(current)]=v.duration-v.currentTime<15?0:v.currentTime;const keys=Object.keys(positions);if(keys.length>300)delete positions[keys[0]];persist();}
function stop(){playbackFault=false;savePosition();++playGeneration;destroyEngine();message('');$('player-placeholder').hidden=false;$('video-controls').hidden=true;$('epg').hidden=true;$('now-title').textContent=t('noSelection');$('now-meta').textContent='';current=null;clearTimeout(feedbackTimer);$('selection-feedback').hidden=true;setPlaybackPhase('idle');updateStar();full(false);}
function restartFromBeginning(){if(Controller.isPhone())return Controller.command('restart');if(current&&current.type!=='live')play(current,undefined,true);}
function pause(){if(Controller.isPhone())return Controller.command('pause');cancelVideoRecovery();playIntent=false;if(pendingSeek)pendingSeek.wasPlaying=false;Controller.localSelection();window.__magdiPlayback?.stop();v.pause();if(current)setPlaybackPhase('paused');savePosition();}
function resume(){if(Controller.isPhone())return Controller.command('resume');if(current){playIntent=true;if(pendingSeek)pendingSeek.wasPlaying=true;v.muted=false;v.play().catch(()=>{message(t('retry'));Controller.publish();});}}
const fullscreenControls=new FullscreenControls($('player-box'),v,$('video-controls'),$('exit-full'));
let fullscreenBackground=[];
function full(value){value=!!value;if(value&&Controller.isPhone())return false;const changed=$('player-box').classList.contains('full')!==value;
 if(value&&changed){for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();fullscreenBackground=Array.from(document.querySelectorAll('.top,.workspace-top,.source-bar,.tabs,.browse,.now,.play-options,.epg,footer,#notice')).map(el=>[el,el.inert]);for(const [el] of fullscreenBackground)el.inert=true;}
 if(!value){for(const [el,wasInert] of fullscreenBackground)el.inert=wasInert;fullscreenBackground=[];}
 $('full').setAttribute('aria-pressed',String(value));$('player-box').classList.toggle('full',value);document.body.classList.toggle('fullscreen',value);$('exit-full').hidden=!value;MMVideoFit.inline(value);updateVideoFit();fullscreenControls.sync();if(changed){window.dispatchEvent(new Event('resize'));emit('fullscreen');}return true;}
function updateVideoFit(){const fit=MMVideoFit.state();$('picture-fit').hidden=!fit.fullscreen;$('picture-fit').textContent=t(fit.fill?'fullPicture':'fillScreen');$('picture-fit').title=t('fillHint');$('picture-fit').setAttribute('aria-label',t('fillHint'));}
function setVideoFill(value){if(Controller.isPhone())return false;const accepted=MMVideoFit.set(value);updateVideoFit();Controller.publish();return accepted;}
function toggleVideoFill(){return setVideoFill(!MMVideoFit.state().fill);}
$('picture-fit').onclick=toggleVideoFill;window.addEventListener('mmvideofit',updateVideoFit);
function toggleFullscreen(){if(document.fullscreenElement){document.exitFullscreen().catch(()=>{});return;}full(!$('player-box').classList.contains('full'));}
function skip(delta){if(Controller.isPhone())return Controller.command(delta>0?'next':'previous');const playable=filtered.filter(i=>i.type!=='series');const i=playable.findIndex(x=>current&&key(x)===key(current));if(playable.length)play(playable[(i+delta+playable.length)%playable.length]);}
async function loadEpg(item,generation){try{const data=await api('epg',{stream_id:item.id,limit:'2'});if(generation!==playGeneration)return;const entries=data.epg_listings;if(!Array.isArray(entries)||!entries.length)return;$('epg').replaceChildren();for(const e of entries){const p=document.createElement('p');let title=e.title||'';try{title=new TextDecoder().decode(Uint8Array.from(atob(title),c=>c.charCodeAt(0)));}catch{}p.textContent=title;$('epg').append(p);}$('epg').hidden=false;}catch{}}
v.addEventListener('playing',()=>{if(Controller.isPhone()){v.pause();v.removeAttribute('src');return;}if(current&&!v.paused&&!v.error)setPlaybackPhase(pendingSeek?'seeking':recoveryPending?'recovering':'playing');if(!recoveryPending&&!pendingSeek){clearTimeout(bufferTimer);message('');}$('play-pause').textContent='Ⅱ';emit('playing');});
v.addEventListener('pause',()=>{if(current&&v.paused&&!v.ended&&!v.error&&['playing','recovering'].includes(playbackPhase))setPlaybackPhase('paused');$('play-pause').textContent='▶';emit('pause');});v.addEventListener('play',()=>{if(activeMedia&&!v.paused){playIntent=true;if(pendingSeek)pendingSeek.wasPlaying=true;}emit('play');});
v.addEventListener('waiting',()=>{if(current&&playbackPhase!=='error'){setPlaybackPhase(pendingSeek?'seeking':recoveryPending?'recovering':'loading');message(t(pendingSeek?'seekingVideo':recoveryPending?'recoveringVideo':'buffering'));}emit('waiting');});v.addEventListener('ended',()=>{if(current)setPlaybackPhase('ended');savePosition();emit('ended');});
v.addEventListener('loadedmetadata',()=>{if(current&&current.type!=='live'){const override=activeMedia?.resumeAt,position=override??positions[key(current)];if(pendingSeek){pendingResume=null;try{v.currentTime=pendingSeek.target;}catch{failSeek();}return;}if(Number.isFinite(position)&&Number.isFinite(v.duration)&&(override!==null&&override!==undefined?position>=0&&position<v.duration:position>5&&position<v.duration-15))pendingResume=position;}});
function clock(s){if(!Number.isFinite(s))return '--:--';return Math.floor(s/60)+':'+String(Math.floor(s%60)).padStart(2,'0');}
v.addEventListener('timeupdate',()=>{if(!current)return;if(pendingResume!==null&&v.currentTime>.5&&v.readyState>=2&&!v.paused){const position=pendingResume;pendingResume=null;if(!recoveryPending)trackSeek(position,true,'saved');v.currentTime=position;}$('time').textContent=current.type==='live'?'● LIVE':clock(v.currentTime)+' / '+clock(v.duration);if(Number.isFinite(v.duration))if(!seekDragging)$('seek').value=String(v.currentTime/v.duration*100);});
setInterval(()=>{if(current){emit('telemetry');savePosition();}},10000);
setInterval(updateSeekControls,500);
$('restart-start').onclick=restartFromBeginning;$('play-pause').onclick=()=>v.paused?resume():pause();$('stop').onclick=()=>Controller.stopLocal();$('retry').onclick=()=>{if(current)play(current);};$('previous').onclick=()=>skip(-1);$('next').onclick=()=>skip(1);$('mute').onclick=()=>{v.muted=!v.muted;$('mute').textContent=v.muted?'×♪':'♪';};$('full').onclick=toggleFullscreen;$('exit-full').onclick=()=>full(false);$('seek').onpointerdown=()=>seekDragging=true;$('seek').onpointercancel=$('seek').onpointerup=()=>seekDragging=false;$('seek').onchange=()=>{seekDragging=false;seekTo(Number($('seek').value)/100*v.duration);};$('seek-back').onclick=()=>seekBy(-10);$('seek-forward').onclick=()=>seekBy(10);$('format').onchange=()=>{if(current)play(current);};$('favorite-current').onclick=()=>{if(current)toggleFavorite(current);};
$('public-open').onclick=openPublic;$('public-switch').onclick=openPublic;$('public-close').onclick=cancelPublic;$('public-load').onclick=connectPublic;$('subscription-switch').onclick=connectAccount;
$('settings').onclick=()=>openSettings().catch(e=>notice(errorText(e)));$('configure').onclick=$('settings').onclick;$('connect').onclick=connectAccount;$('close-settings').onclick=()=>$('settings-dialog').close();$('account-form').onsubmit=e=>{e.preventDefault();save(true);};$('save-only').onclick=()=>save(false);$('source-mode').onchange=sourceMode;
$('background').onchange=()=>api('background',{enabled:$('background').checked}).catch(e=>notice(errorText(e)));$('setup-auto').onclick=()=>api('setup',{language});$('log-sessions').onclick=()=>api('logSessions',{language});
$('saved-accounts').onchange=updateAccountSwitch;
$('add-account').onclick=()=>{if(accountBusy)return;addingAccount=true;renderAccountForm();$('form-status').textContent=t('addingAccount');};
$('switch-account').onclick=()=>{
 const id=$('saved-accounts').value;
 if(!id)return;
 if(id===account.activeId){addingAccount=false;renderAccountForm();$('form-status').textContent='';return;}
 void changeAccount('switchAccount',{id},true);
};
$('forget').onclick=()=>{if(!accountBusy&&!addingAccount&&account.configured&&confirm(t('confirmForget')))void changeAccount('forget',{activeId:account.activeId});};
document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>changeTab(b.dataset.tab));$('category').onchange=()=>mode==='m3u'?filter():loadItems();$('search').oninput=filter;$('more').onclick=()=>{shown+=60;render();};$('refresh').onclick=()=>{if(publicSource){openPublic();connectPublic();return;}cache.clear();changeTab(tab);};$('close-episodes').onclick=()=>$('episodes-dialog').close();$('season').onchange=renderEpisodes;
$('public-dialog').addEventListener('cancel',e=>{e.preventDefault();cancelPublic();});
$('episodes-dialog').addEventListener('close',()=>{if(!$('episodes-dialog').open){++seriesGeneration;seriesLoading=false;openingSeriesKey='';updateCards();}});
$('show-player').onclick=()=>{$(Controller.isPhone()?'controller-panel':'player-box').scrollIntoView({block:'center',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});};
$('language').onclick=()=>{language=language==='ar'?'en':'ar';localStorage.setItem('language',language);applyLanguage();Controller.refresh();updateSourceLabel();EmbeddedSelects.refresh();$('section-title').textContent=t(tab);if(source){renderCategories();filter();}else $('welcome-state').textContent=t(account.configured?'ready':'noAccount');};
function openKeyboard(){EmbeddedKeyboard.open();}
window.app={pause,resume,toggleFit:toggleVideoFill,fullscreen:full,state:()=>Controller.snapshot(),search:q=>{$('search').value=q;filter();},back:()=>{if(EmbeddedKeyboard.back())return;if(EmbeddedSelects.back())return;if($('public-dialog').open){cancelPublic();return;}const modal=document.querySelector('dialog[open]');if(modal){modal.close();return;}if($('player-box').classList.contains('full')){full(false);return;}if(current){Controller.stopLocal();return;}notice(t('backHint'));},demo:(url,ext='m3u8')=>{if(Controller.isPhone())return false;$('welcome').hidden=true;$('workspace').hidden=false;source='demo';mode='m3u';play({id:'demo',name:'Playback verification',type:'live',ext,url,demo:true});}};
$('show-licenses').onclick=async()=>{const out=$('license-text');out.textContent=await(await fetch('licenses.txt')).text();out.hidden=!out.hidden;};
EmbeddedSelects.install();EmbeddedKeyboard.install();Controller.init();Metadata.init();
initialize();
