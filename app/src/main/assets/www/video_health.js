'use strict';
// Measures frame progress, not image differences: a static shot still has frames.
// Ignore hidden/paused/seeking/audio-only players and scheduler gaps. No URLs or timers.
class VideoHealth {
 constructor(){this.reset();}
 reset(){this.last=null;this.anchor=null;}
 sample(s,now){
  if(!s.active||s.paused||s.ended||s.seeking||s.error||s.ready<2||s.width<=0||s.height<=0||!Number.isFinite(s.frames)||s.frames<=0||!Number.isFinite(s.time)||!Number.isFinite(now)){this.reset();return null;}
  const last=this.last;this.last={...s,now};
  if(!last||now<=last.now||now-last.now>6000||s.frames<last.frames||s.time<last.time||s.time-last.time>Math.max(5,(now-last.now)/1000*4)){
   this.anchor={frames:s.frames,time:s.time,now};return null;
  }
  if(s.frames>last.frames){this.anchor={frames:s.frames,time:s.time,now};return s.time>last.time+.05?{kind:'progress'}:null;}
  if(s.time<=last.time+.05){this.anchor={frames:s.frames,time:s.time,now};return null;}
  if(now-this.anchor.now>=20000&&s.time-this.anchor.time>=5){const position=this.anchor.time;this.reset();return {kind:'frozen',position};}
  return null;
 }
}
if(typeof module!=='undefined')module.exports=VideoHealth;
