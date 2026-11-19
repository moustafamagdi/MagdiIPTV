import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const VideoHealth=createRequire(import.meta.url)('../app/src/main/assets/www/video_health.js');
const base={active:true,paused:false,ended:false,seeking:false,error:false,ready:4,width:1920,height:1080,frames:100,time:10};
function run(overrides={}){const h=new VideoHealth(),events=[];for(let i=0;i<=12;i++){const e=h.sample({...base,time:10+i*2,...overrides},i*2000);if(e)events.push(e);}return events;}
test('audio clock advances with frozen picture for 20 seconds: detect and retain last frame position',()=>assert.deepEqual(run(),[{kind:'frozen',position:10}]));
test('frames continue for a static shot: never treat unchanged pixels as a freeze',()=>{const h=new VideoHealth();for(let i=0;i<100;i++)assert.notEqual(h.sample({...base,time:i*2,frames:100+i*50},i*2000)?.kind,'frozen');});
for(const [name,value] of Object.entries({active:false,paused:true,ended:true,seeking:true,error:true,ready:1,width:0,height:0,frames:NaN,time:10}))test('ignore '+name+'='+value,()=>assert.deepEqual(run({[name]:value}),[]));
test('no false freeze after background timer gap',()=>{const h=new VideoHealth();h.sample(base,0);assert.equal(h.sample({...base,time:60},60000),null);assert.equal(h.sample({...base,time:62},62000),null);});
test('seek, counter reset and new media reset detection history',()=>{for(const next of [{time:1000},{time:1},{frames:1}]){const h=new VideoHealth();for(let i=0;i<10;i++)h.sample({...base,time:10+i*2},i*2000);assert.equal(h.sample({...base,time:30,...next},20000),null);}});
test('manual reset discards stale freeze evidence',()=>{const h=new VideoHealth();for(let i=0;i<10;i++)h.sample({...base,time:10+i*2},i*2000);h.reset();assert.equal(h.sample({...base,time:30},20000),null);});
test('playback recovery is confirmed by advancing frames AND time',()=>{const h=new VideoHealth();assert.equal(h.sample(base,0),null);assert.equal(h.sample({...base,frames:101},2000),null);assert.deepEqual(h.sample({...base,frames:151,time:12},4000),{kind:'progress'});});
