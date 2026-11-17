export async function run(call){
 const checks=[];
 const evaluate=async expression=>{if(expression.includes('await ')&&!expression.startsWith('(async'))expression='(async()=>{'+expression+'})()';const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const check=(name,ok)=>{if(!ok)throw Error(name);checks.push({name,passed:true});};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const until=async expression=>{for(let i=0;i<80;i++){if(await evaluate(expression))return;await wait(100);}throw Error('Timeout: '+expression);};
 const native=await evaluate("location.hostname==='app.magdi.local'");
 if(!native){await call('Emulation.setDeviceMetricsOverride',{width:1000,height:500,deviceScaleFactor:1,mobile:false});await call('Emulation.setTouchEmulationEnabled',{enabled:true});}
 const tap=async selector=>{
  if(native){await evaluate('document.querySelector('+JSON.stringify(selector)+').click()');return;}
  await evaluate('document.querySelector('+JSON.stringify(selector)+").scrollIntoView({block:'center',behavior:'instant'})");await wait(80);
  const p=await evaluate('(()=>{const r=document.querySelector('+JSON.stringify(selector)+').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');
  await call('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...p,id:1,radiusX:5,radiusY:5}]});
  await call('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await wait(80);
 };
 try{
  await until("account.configured");
  await evaluate("(async()=>{document.querySelectorAll('dialog[open]').forEach(d=>d.close());await connectAccount();stop();await changeTab('series');})()");
  check('Every native select is hidden and replaced',await evaluate("[...document.querySelectorAll('select')].every(s=>s.hidden&&getComputedStyle(s).display==='none')&&document.querySelectorAll('.select-picker').length===document.querySelectorAll('select').length"));
  await evaluate("window.pickerChanges=0;$('category').addEventListener('change',()=>pickerChanges++)");
  await tap('#category-picker');
  check('Series categories open inside the document',await evaluate("$('choice-dialog').open&&document.querySelectorAll('.choice-option').length===3"));
  await tap('.choice-option:nth-child(2)');await until("!loading&&$('category').value==='1'");
  check('One tap selects category and dispatches one change',await evaluate("pickerChanges===1&&!$('choice-dialog').open&&$('category-picker').textContent.includes('Featured')&&filtered.length===1"));
  await tap('#category-picker');await tap('.choice-option:nth-child(2)');
  check('Selecting current category does not reload',await evaluate("pickerChanges===1"));
  await tap('#category-picker');await evaluate("app.back()");
  check('Back cancels category choice and retains selection',await evaluate("!$('choice-dialog').open&&$('category').value==='1'&&pickerChanges===1"));
  await evaluate("openSeries(filtered[0])");await until("!seriesLoading&&$('season').options.length>0");
  await evaluate("seriesData['2']=[{id:399,title:'Synthetic season two',container_extension:'mp4'}];$('season').add(new Option('Season 2','2'))");
  await tap('#season-picker');
  check('Season picker opens above episode dialog',await evaluate("$('choice-dialog').open&&$('episodes-dialog').open"));
  await evaluate("app.back()");
  check('Back closes only the top picker',await evaluate("!$('choice-dialog').open&&$('episodes-dialog').open"));
  await tap('#season-picker');await tap('.choice-option:nth-child(2)');
  check('Season selection updates episodes and label',await evaluate("$('season').value==='2'&&$('episodes').textContent.includes('Synthetic season two')&&$('season-picker').textContent.includes('Season 2')&&$('episodes-dialog').open"));
  await evaluate("$('episodes-dialog').close();await changeTab('favorites')");
  check('Unavailable category picker is disabled',await evaluate("$('category-picker').disabled"));
  await evaluate("await changeTab('live')");await tap('#format-picker');await tap('.choice-option:nth-child(3)');
  check('Stream format selection is retained',await evaluate("$('format').value==='ts'&&$('format-picker').textContent.includes('MPEG-TS')"));
  await evaluate("$('format').value='auto';EmbeddedSelects.refresh();await openSettings()");
  await tap('#source-mode-picker');await tap('.choice-option:nth-child(2)');
  check('Source selector changes form without submitting',await evaluate("$('source-mode').value==='m3u'&&!$('m3u-fields').hidden&&$('xtream-fields').hidden&&$('settings-dialog').open"));
  await evaluate("$('settings-dialog').close();await changeTab('series');categories=Array.from({length:400},(_,i)=>({category_id:String(i),category_name:'قسم طويل / Long category '+i}));renderCategories()");
  await tap('#category-picker');
  check('Large Arabic/English lists scroll within viewport',await evaluate("document.querySelectorAll('.choice-option').length===401&&document.querySelector('.choice-list').scrollHeight>document.querySelector('.choice-list').clientHeight&&$('choice-dialog').getBoundingClientRect().bottom<=innerHeight&&document.documentElement.scrollWidth<=innerWidth"));
  await evaluate("app.back();language=language==='ar'?'en':'ar';applyLanguage();renderCategories()");
  check('Category button follows translated options',await evaluate("$('category-picker').textContent.includes(t('all'))"));
  return {surface:native?'Android WebView DOM activation':'Chromium landscape touch',checks};
 }finally{
  await evaluate("EmbeddedSelects.back();document.querySelectorAll('dialog[open]').forEach(d=>d.close());stop()");
  if(!native)await call('Emulation.setTouchEmulationEnabled',{enabled:false});
 }
}
