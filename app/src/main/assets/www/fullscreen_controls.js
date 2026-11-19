'use strict';
// Presentation only: never change playback, media requests, or the video element.
window.FullscreenControls=class {
 constructor(box,video,controls,exit){
  this.box=box;this.video=video;this.controls=controls;this.exit=exit;this.timer=0;this.pointers=new Set();this.keyboard=false;
  box.addEventListener('pointerdown',e=>{if(!this.active())return;this.revealGesture=box.classList.contains('controls-hidden');this.keyboard=false;this.pointers.add(e.pointerId);this.show();},true);
  const release=e=>{if(this.pointers.delete(e.pointerId))this.show();};
  window.addEventListener('pointerup',release,true);window.addEventListener('pointercancel',release,true);
  box.addEventListener('click',e=>{if(!this.active())return;if(this.revealGesture){this.revealGesture=false;e.preventDefault();e.stopImmediatePropagation();}this.show();},true);
  box.addEventListener('keydown',()=>{if(this.active()){this.keyboard=true;this.show();}},true);
  box.addEventListener('focusout',()=>queueMicrotask(()=>this.arm()));
  for(const name of ['playing','pause','waiting','seeking','seeked','ended','error','emptied'])video.addEventListener(name,()=>this.show());
  document.addEventListener('visibilitychange',()=>{this.pointers.clear();this.show();});
  window.addEventListener('blur',()=>{this.pointers.clear();this.show();});
 }
 active(){return this.box.classList.contains('full');}
 eligible(){const v=this.video;return this.active()&&!document.hidden&&!v.paused&&!v.ended&&!v.seeking&&!v.error&&v.readyState>=3&&!this.pointers.size&&!(this.keyboard&&(this.controls.contains(document.activeElement)||this.exit===document.activeElement));}
 show(){clearTimeout(this.timer);this.box.classList.remove('controls-hidden');this.controls.inert=false;this.exit.inert=false;this.arm();}
 arm(){clearTimeout(this.timer);this.timer=0;if(!this.eligible())return;this.timer=setTimeout(()=>{this.timer=0;if(!this.eligible())return;this.box.classList.add('controls-hidden');this.controls.inert=true;this.exit.inert=true;},10000);}
 sync(){this.pointers.clear();this.keyboard=false;this.revealGesture=false;this.show();}
};
