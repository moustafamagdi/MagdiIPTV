'use strict';
// Render choices inside the document: projected car contexts cannot rely on native popup windows.
window.EmbeddedSelects=(()=>{
 const entries=new Map();let active=null;
 const dialog=document.createElement('dialog');dialog.id='choice-dialog';dialog.className='choice-dialog';dialog.setAttribute('aria-labelledby','choice-title');
 const header=document.createElement('div');header.className='dialog-header';
 const title=document.createElement('h2');title.id='choice-title';
 const close=document.createElement('button');close.type='button';close.className='quiet';close.textContent='✕';close.setAttribute('aria-label','Close');
 const list=document.createElement('div');list.className='choice-list';
 header.append(title,close);dialog.append(header,list);
 function label(select){const ar=document.documentElement.lang==='ar';if(select.id==='category')return ar?'الأقسام':'Categories';if(select.id==='season')return ar?'الموسم':'Season';return select.closest('label')?.querySelector('span')?.textContent||select.getAttribute('aria-label')||'Choose';}
 function sync(select){
  const button=entries.get(select);if(!button)return;
  button.textContent=(select.selectedOptions[0]?.textContent||'—')+' ▾';
  button.disabled=select.disabled||!select.options.length;
  button.setAttribute('aria-label',label(select)+': '+(select.selectedOptions[0]?.textContent||''));
 }
 function finish(){const previous=active;active=null;if(previous){const button=entries.get(previous);button.setAttribute('aria-expanded','false');if(button.isConnected&&!button.disabled)button.focus({preventScroll:true});}}
 function open(select){
  sync(select);if(entries.get(select).disabled)return;
  active=select;title.textContent=label(select);list.replaceChildren();
  Array.from(select.options).forEach((option,index)=>{
   const button=document.createElement('button');button.type='button';button.className='choice-option';button.textContent=option.textContent;
   button.disabled=option.disabled||!!option.parentElement?.disabled;
   button.setAttribute('aria-pressed',String(index===select.selectedIndex));
   button.onclick=()=>{
    if(active!==select||select.disabled||option.disabled)return;
    const changed=select.selectedIndex!==index;select.selectedIndex=index;sync(select);dialog.close();finish();
    if(changed){select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));}
   };
   list.append(button);
  });
  dialog.showModal();entries.get(select).setAttribute('aria-expanded','true');
  (list.querySelector('[aria-pressed="true"]:not(:disabled)')||list.querySelector('button:not(:disabled)'))?.focus({preventScroll:true});
  list.querySelector('[aria-pressed="true"]')?.scrollIntoView({block:'nearest'});
 }
 close.onclick=()=>{dialog.close();finish();};
 dialog.addEventListener('close',()=>{if(!dialog.open)finish();});
 return {
  install(root=document){
   if(!dialog.isConnected)document.body.append(dialog);
   root.querySelectorAll('select').forEach(select=>{
    if(entries.has(select))return;
    const button=document.createElement('button');button.type='button';button.id=select.id+'-picker';button.className='select-picker';
    button.setAttribute('aria-haspopup','dialog');button.setAttribute('aria-controls',dialog.id);button.setAttribute('aria-expanded','false');
    select.hidden=true;select.after(button);entries.set(select,button);sync(select);
    button.onclick=()=>open(select);select.addEventListener('change',()=>sync(select));
    new MutationObserver(()=>{sync(select);if(active===select&&dialog.open){dialog.close();finish();}}).observe(select,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['disabled','selected','label']});
   });
  },
  refresh(){entries.forEach((button,select)=>sync(select));},
  back(){if(!dialog.open)return false;dialog.close();finish();return true;}
 };
})();
