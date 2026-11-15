(async()=>{
 const check=(name,condition)=>{if(!condition)throw new Error(name);return {name,passed:true};};
 const results=[];
 if($('workspace').hidden)await connectAccount();
 results.push(check('No horizontal page overflow',document.documentElement.scrollWidth<=innerWidth));
 await openSettings();results.push(check('Settings fit viewport',$('settings-dialog').getBoundingClientRect().width<=innerWidth));
 $('settings-dialog').close();env.car=true;openKeyboard();$('keys-language').click();$('keys').querySelector('button').click();$('keyboard-done').click();results.push(check('Car keyboard can submit search',$('search').value.length>0));
 $('search').value='';filter();env.car=false;full(true);results.push(check('Fullscreen fits small screen',$('player-box').getBoundingClientRect().width===innerWidth&&$('player-box').getBoundingClientRect().height===innerHeight));full(false);
 return {width:innerWidth,height:innerHeight,checks:results};
})()
