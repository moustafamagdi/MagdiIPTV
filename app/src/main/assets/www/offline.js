'use strict';
Object.assign(strings.en,{offlineHelp:'Download information',downloads:'Downloads',download:'Download',downloadSeason:'Download this season',startFullscreen:'Start playback fullscreen on the car',allowMetered:'Allow new downloads on mobile data / metered hotspots',offlineHint:'Direct MP4 / WebM files only in this preview. Completed files play without the provider. Downloads are removed if the app is uninstalled. Android resumes interrupted transfers when supported by the server.',offlineEmpty:'No downloads yet. Choose Download beside a movie or episode.',offlineReady:'Ready offline',offlineQueued:'Queued',offlineDownloading:'Downloading',offlineWaiting:'Waiting for network / Android retry',offlineFailed:'Download failed — retry',offlineInvalid:'The server did not return a supported video file',offlineMissing:'File missing',offlineCancelled:'Cancelled',offlineDelete:'Delete',offlineCancel:'Cancel download',offlineRetry:'Download again',offlineConfirmDelete:'Delete this download and its file?',offlineConfirmSeason:'Add these episodes to downloads? Files may use significant storage. Episodes already in Downloads will be skipped.',offlineAdded:'Added',offlineSkipped:'Already listed',offlineRejected:'Not added / unsupported',offlineError:'Could not complete this download action. Check storage and try again.',offlineStorage:'Used / Free',offlinePhone:'Manage downloads from the phone.',offlineLimit:'Choose a season with no more than 200 episodes.',offlineConfirmDownload:'Download this title for offline playback? Downloading uses storage and your allowed network connection.'});
Object.assign(strings.ar,{offlineHelp:'معلومات التنزيل',downloads:'التنزيلات',download:'تنزيل',downloadSeason:'تنزيل الموسم الحالي',startFullscreen:'بدء التشغيل بملء الشاشة على العربية',allowMetered:'السماح للتنزيلات الجديدة ببيانات الهاتف والهوت سبوت المحدود',offlineHint:'النسخة التجريبية تدعم ملفات MP4 وWebM المباشرة. الملفات المكتملة تعمل بدون السيرفر. حذف التطبيق يزيل تنزيلاته. أندرويد يستكمل التنزيل المتقطع حسب دعم السيرفر.',offlineEmpty:'لا توجد تنزيلات. اختر تنزيل بجانب فيلم أو حلقة.',offlineReady:'جاهز بدون إنترنت',offlineQueued:'في الانتظار',offlineDownloading:'جارٍ التنزيل',offlineWaiting:'بانتظار الشبكة أو إعادة محاولة أندرويد',offlineFailed:'فشل التنزيل — حاول مجددًا',offlineInvalid:'السيرفر لم يُرجع ملف فيديو مدعومًا',offlineMissing:'الملف غير موجود',offlineCancelled:'تم الإلغاء',offlineDelete:'حذف',offlineCancel:'إلغاء التنزيل',offlineRetry:'تنزيل مجددًا',offlineConfirmDelete:'حذف هذا التنزيل وملفه من الهاتف؟',offlineConfirmSeason:'إضافة هذه الحلقات للتنزيل؟ قد تحتاج مساحة كبيرة. الحلقات الموجودة بقائمة التنزيلات لن تتكرر.',offlineAdded:'تمت الإضافة',offlineSkipped:'موجود بالفعل',offlineRejected:'لم يُضف أو غير مدعوم',offlineError:'تعذر إكمال العملية. راجع المساحة وحاول مجددًا.',offlineStorage:'المستخدم / المتاح',offlinePhone:'إدارة التنزيلات من الموبايل.',offlineLimit:'اختر موسمًا لا يزيد عن ٢٠٠ حلقة.',offlineConfirmDownload:'تنزيل هذا العنوان للمشاهدة بدون إنترنت؟ التنزيل يستخدم مساحة واتصال الشبكة المسموح به.'});
window.Offline=(()=>{
 let selected=false,refreshing=false,operation=false,snapshot=null;
 const active=()=>selected&&source==='offline';
 const bytes=n=>{n=Math.max(0,Number(n)||0);return n>=1073741824?(n/1073741824).toFixed(1)+' GB':(n/1048576).toFixed(1)+' MB';};
 const playable=row=>({id:row.token,name:row.name,type:row.type,ext:row.ext,offline:true});
 const supported=item=>!item.offline&&['vod','episode'].includes(item.type)&&['mp4','m4v','webm'].includes(String(item.ext||'mp4').toLowerCase())&&mode==='xtream'&&!publicSource;
 function button(text,fn){const b=document.createElement('button');b.type='button';b.textContent=text;b.onclick=fn;return b;}
 async function refresh(){if(refreshing)return;refreshing=true;try{snapshot=await api('downloads');renderLibrary(snapshot);}catch{$('offline-status').textContent=t('offlineError');}finally{refreshing=false;}}
 function renderLibrary(data){
  $('offline-space').textContent=t('offlineStorage')+': '+bytes(data.used)+' / '+bytes(data.free);
  $('offline-metered').checked=!!data.metered;$('offline-metered-label').hidden=!Controller.isPhone();
  const list=$('offline-list');list.replaceChildren();
  if(!data.items.length){const p=document.createElement('p');p.textContent=t('offlineEmpty');list.append(p);}
  for(const row of data.items){const article=document.createElement('article');article.className='offline-row';const title=document.createElement('strong');title.textContent=row.name;
   const state=document.createElement('p');state.textContent=[row.group,t(({ready:'offlineReady',queued:'offlineQueued',downloading:'offlineDownloading',waiting:'offlineWaiting',failed:'offlineFailed',invalid:'offlineInvalid',missing:'offlineMissing',cancelled:'offlineCancelled'})[row.state]||'offlineFailed'),bytes(row.bytes)+(row.total>0?' / '+bytes(row.total):'')].filter(Boolean).join(' · ');article.append(title,state);
   if(row.state==='ready')article.append(button(t('playOnCar'),async()=>{try{if(await activate())selectItem(playable(row));}catch{notice(t('offlineError'));}}));
   if(Controller.isPhone()){
    if(['queued','downloading','waiting'].includes(row.state))article.append(button(t('offlineCancel'),()=>action(row,'cancel')));
    else if(row.state!=='ready')article.append(button(t('offlineRetry'),()=>action(row,'retry')));
    article.append(button(t('offlineDelete'),()=>{if(confirm(t('offlineConfirmDelete')))action(row,'delete');}));
    if(row.group)article.append(button(language==='ar'?'حذف الموسم':'Delete season',async()=>{
     const same=data.items.filter(i=>i.groupKey===row.groupKey);
     if(operation||!confirm(t('offlineConfirmDelete')+' · '+same.length))return;
     operation=true;try{for(const item of same)await api('downloadaction',{token:item.token,action:'delete'});await refresh();}catch{$('offline-status').textContent=t('offlineError');}finally{operation=false;}
    }));
   }list.append(article);
  }
 }
 async function open(){if(!$('offline-dialog').open)$('offline-dialog').showModal();$('offline-status').textContent='';await refresh();}
 async function action(row,action){if(operation)return;operation=true;try{await api('downloadaction',{token:row.token,action});await refresh();}catch{$('offline-status').textContent=t('offlineError');}finally{operation=false;}}
 async function enqueue(list){if(operation||!Controller.isPhone())return;if(!list.length||list.length>200){notice(t('offlineLimit'));return;}
  if(!confirm(t(list.length>1?'offlineConfirmSeason':'offlineConfirmDownload')+'\n'+list.length+' · '+list[0].name))return;
  operation=true;try{const result=await api('download',{account:account.activeId,items:list.map(i=>({id:i.id,name:i.name,type:i.type,ext:i.ext||'mp4',group:i.group||''}))});
   for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();await open();$('offline-status').textContent=t('offlineAdded')+': '+result.added+' · '+t('offlineSkipped')+': '+result.skipped+' · '+t('offlineRejected')+': '+result.failed;
  }catch{notice(t('offlineError'));}finally{operation=false;}
 }
 function decorate(row,item){if(Controller.isPhone()&&supported(item)){const b=button(t('download'),()=>enqueue([item]));b.className='offline-download';row.append(b);}}
 async function activate(valid=()=>true){if(!valid())return false;selected=true;source="offline";const connection=++connectionGeneration;++loadGeneration;connectionBusy(false);
  const data=await api('downloads');if(connection!==connectionGeneration||!valid())return false;source='offline';publicSource='';mode='offline';tab='downloads';items=data.items.filter(r=>r.state==='ready').map(playable);filtered=items;shown=60;
  favorites=[];recent=readStore('offline:recent',[]);positions=readStore('offline:positions',{});categories=[];renderCategories();
  $('welcome').hidden=true;$('workspace').hidden=false;$('tabs').hidden=true;$('active-source').textContent=t('downloads');$('subscription-switch').hidden=false;$('section-title').textContent=t('downloads');$('search').value='';
  for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();render();return true;
 }
 function init(){
  $('offline-open').onclick=open;$('offline-close').onclick=()=>$('offline-dialog').close();
  $('offline-metered').onchange=async()=>{try{await api('downloadsettings',{metered:$('offline-metered').checked});}catch{$('offline-status').textContent=t('offlineError');}};
  $('start-fullscreen').onchange=()=>api('playprefs',{fullscreen:$('start-fullscreen').checked}).catch(()=>notice(t('offlineError')));
  api('playprefs').then(p=>$('start-fullscreen').checked=p.fullscreen).catch(()=>{});
  $('offline-season').onclick=()=>{if(!Controller.isPhone())return;const season=$('season').value;enqueue((seriesData?.[season]||[]).map(e=>({id:String(e.id),type:'episode',name:e.title||seriesName+' '+e.episode_num,ext:e.container_extension||'mp4',group:seriesName+' · '+t('season')+' '+season})));};
  setInterval(()=>{if($('offline-dialog').open&&!operation)refresh();$('offline-season').hidden=!Controller.isPhone();},2000);
 }
 return {init,open,activate,active,decorate,supported,enqueue};
})();
