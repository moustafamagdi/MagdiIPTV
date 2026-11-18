export async function run(call){
 const checks=[],wait=ms=>new Promise(r=>setTimeout(r,ms));
 const run=async expression=>{const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
 const check=async(name,expression)=>{if(!await run(expression))throw Error(name);checks.push(name);};
 await run("for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();window.originalMetadataApi=api;");await wait(100);
 try{
  await run("api=(method,args)=>method==='details'?Promise.reject(Error('fixture offline')):originalMetadataApi(method,args);void Metadata.open({id:'900001',type:'vod',name:'Missing metadata'});");await wait(100);
  await check('Failed details keep a fallback description and usable play button',"$('details-summary').textContent.includes(t('noDescription'))&&$('details-status').textContent===t('detailsUnavailable')&&!$('details-play').disabled");
  await run("$('details-play').click()");await wait(200);
  await check('Disconnected detail play warns and never starts phone media',"$('controller-alert').open&&v.paused&&!v.getAttribute('src')&&v.getVideoPlaybackQuality().totalVideoFrames===0");
  await run("$('controller-alert').close();api=(method,args)=>method==='details'?new Promise(resolve=>window.resolveOldMetadata=resolve):originalMetadataApi(method,args);void Metadata.open({id:'900002',type:'vod',name:'Old request'});");await wait(100);await run("$('details-dialog').close()");await wait(100);
  await run("api=(method,args)=>method==='details'?Promise.resolve({plot:'New description'}):originalMetadataApi(method,args);void Metadata.open({id:'900003',type:'vod',name:'New request'});");await wait(100);await run("resolveOldMetadata({plot:'STALE DESCRIPTION'})");await wait(100);
  await check('Late response cannot replace a newer details selection',"$('details-title').textContent==='New request'&&$('details-summary').textContent.includes('New description')&&!$('details-summary').textContent.includes('STALE')");
  await run("$('details-dialog').close()");await wait(100);
  await run("Metadata.open({id:'900004',type:'episode',name:'Safe text',description:'<img src=x onerror=window.metadataInjected=1>Provider text'});");
  await check('Provider markup is not executed or inserted as HTML',"!window.metadataInjected&&!$('details-summary').querySelector('[onerror]')");
  await check('Unsafe image URLs are rejected before native requests',"['data:image/png;base64,AAA','file:///etc/passwd','javascript:alert(1)','https://u:p@example.com/a'].every(url=>Metadata.fields({cover:url}).artwork==='')");
  return {passed:checks.length,checks};
 }finally{await run("api=originalMetadataApi;for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();");}
}
