'use strict';
window.EmbeddedKeyboard=(()=>{
 const get=id=>document.getElementById(id),dialog=get('keyboard-dialog'),editor=get('keyboard-input');
 const fields=['account-name','host','username','password','playlist','search'];
 let target=null,arabic=false,caps=false,symbols=false,car=false;
 const text=(ar,en)=>document.documentElement.lang==='ar'?ar:en;
 const symbolRows=["1234567890","!@#$%^&*()","-_=+[]{}\\|",";:'\",.<>/?`~"];
 function revealLabel(){get('keyboard-reveal').textContent=editor.type==='password'?text('إظهار','Show'):text('إخفاء','Hide');}
 function render(){
  const rows=symbols?symbolRows:['1234567890',...(arabic?['ضصثقفغعهخحجد','شسيبلاتنمكط','ئءؤرأإآىةوزظذ']:caps?['QWERTYUIOP','ASDFGHJKL','ZXCVBNM']:['qwertyuiop','asdfghjkl','zxcvbnm'])];
  get('keys').replaceChildren();
  for(const letters of rows){
   const row=document.createElement('div');row.className='keyrow';
   for(const letter of letters){const button=document.createElement('button');button.type='button';button.textContent=letter;button.dataset.key=letter;button.onclick=()=>insert(letter);row.append(button);}
   get('keys').append(row);
  }
  get('keys-language').textContent=arabic?'EN':'عربي';
  get('keys-symbols').textContent=symbols?(arabic?'أ ب ج':'ABC'):'#+=';
  get('keys-symbols').setAttribute('aria-pressed',String(symbols));
  get('keys-caps').disabled=arabic||symbols;get('keys-caps').setAttribute('aria-pressed',String(caps));get('keys-caps').textContent=caps?'ABC':'abc';
  get('keys-space').textContent=text('مسافة','Space');get('keys-clear').textContent=text('مسح الكل','Clear');
  get('keyboard-close').setAttribute('aria-label',text('إلغاء','Cancel'));revealLabel();
 }
 function selection(){return {start:editor.selectionStart??editor.value.length,end:editor.selectionEnd??editor.value.length};}
 function caret(position){editor.focus({preventScroll:true});editor.setSelectionRange(position,position);}
 function insert(value){const {start,end}=selection();editor.setRangeText(value,start,end,'end');caret(start+value.length);}
 function erase(){let {start,end}=selection();if(start===end&&start>0)start-=Array.from(editor.value.slice(0,start)).pop().length;editor.setRangeText('',start,end,'end');caret(start);}
 function move(delta){const {start,end}=selection();if(start!==end){caret(delta<0?start:end);return;}const before=editor.value.slice(0,start),after=editor.value.slice(end);caret(delta<0?start-(Array.from(before).pop()?.length||0):end+(Array.from(after)[0]?.length||0));}
 function clearDraft(){const previous=target;target=null;editor.value='';editor.type='text';get('keyboard-reveal').hidden=true;if(previous?.isConnected)previous.focus({preventScroll:true});}
 function cancel(){if(!dialog.open)return false;dialog.close();clearDraft();return true;}
 function apply(){
  if(!target)return;const destination=target;destination.value=editor.value;
  dialog.close();clearDraft();
  destination.dispatchEvent(new Event('input',{bubbles:true}));destination.dispatchEvent(new Event('change',{bubbles:true}));
 }
 function open(input=get('search')){
  if(!fields.includes(input.id)||input.disabled||dialog.open)return;
  target=input;const search=input.id==='search';
  arabic=search&&document.documentElement.lang==='ar';caps=false;symbols=false;
  editor.type=input.type==='password'?'password':'text';editor.value=input.value;
  editor.dir=search?'auto':'ltr';editor.readOnly=true;editor.inputMode='none';
  get('keyboard-label').textContent=search?text('بحث','Search'):(input.closest('label')?.querySelector('span')?.textContent||input.id);
  get('keyboard-done').textContent=search?text('بحث','Search'):text('تم','Done');
  get('keyboard-reveal').hidden=input.type!=='password';
  render();dialog.showModal();caret(editor.value.length);
 }
 function configure(inCar){
  car=!!inCar;
  for(const id of fields){const input=get(id);input.readOnly=car;if(car)input.inputMode='none';else input.removeAttribute('inputmode');}
 }
 function install(){
  for(const id of fields){const input=get(id);input.addEventListener('click',()=>{if(car)open(input);});
   if(id==='search')continue;
   const row=document.createElement('span');row.className='settings-input-row';input.before(row);row.append(input);
   const button=document.createElement('button');button.type='button';button.className='field-keyboard quiet';button.textContent='⌨';button.dataset.keyboardFor=id;
   button.setAttribute('aria-label',text('لوحة المفاتيح','Keyboard')+' — '+(input.closest('label')?.querySelector('span')?.textContent||id));
   button.onclick=()=>open(input);row.append(button);
  }
  get('keyboard').onclick=()=>open();
  get('keyboard-close').onclick=cancel;get('keyboard-done').onclick=apply;
  get('keys-language').onclick=()=>{arabic=!arabic;symbols=false;render();};
  get('keys-symbols').onclick=()=>{symbols=!symbols;render();};
  get('keys-caps').onclick=()=>{caps=!caps;render();};
  get('keys-space').onclick=()=>insert(' ');get('keys-delete').onclick=erase;
  get('keys-clear').onclick=()=>{editor.value='';caret(0);};
  get('keys-left').onclick=()=>move(-1);get('keys-right').onclick=()=>move(1);
  get('keyboard-reveal').onclick=()=>{const {start,end}=selection();editor.type=editor.type==='password'?'text':'password';editor.setSelectionRange(start,end);revealLabel();};
  document.querySelectorAll('[data-keyboard-char]').forEach(button=>button.onclick=()=>insert(button.dataset.keyboardChar));
  dialog.addEventListener('cancel',event=>{event.preventDefault();cancel();});
  dialog.addEventListener('close',()=>{if(!dialog.open)clearDraft();});
  editor.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();apply();}});
 }
 return {install,configure,open,back:cancel};
})();
