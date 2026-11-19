import http from 'node:http';
import fs from 'node:fs';
// Synthetic-only Xtream fixture. Generate work/seek-fixture.mp4 per tests/seek-native.mjs.
const port=Number(process.env.FIXTURE_PORT||8794),file='work/seek-fixture.mp4';
let mode='normal',ranges=[];
http.createServer((req,res)=>{
 const u=new URL(req.url,'http://localhost'),json=x=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(x));};
 if(u.pathname==='/test/mode'){mode=u.searchParams.get('value')||'normal';ranges=[];return json({mode});}
 if(u.pathname==='/test/status')return json({mode,ranges});
 if(u.pathname==='/player_api.php'){
  const a=u.searchParams.get('action');if(!a)return json({user_info:{auth:1,status:'Active',allowed_output_formats:['m3u8','ts'],exp_date:'1999999999'}});
  if(a.endsWith('_categories'))return json([{category_id:'1',category_name:'Seek fixture'}]);
  if(a==='get_vod_streams')return json([{name:'Seek fixture',stream_id:101,category_id:'1',container_extension:'mp4'}]);
  if(a==='get_live_streams')return json([{name:'Live fixture',stream_id:1,category_id:'1',container_extension:'mp4'}]);
  if(a==='get_series')return json([{name:'Seek episodes',series_id:201,category_id:'1'}]);
  if(a==='get_series_info')return json({episodes:{'1':[{id:301,title:'Seek episode',container_extension:'mp4'}]}});
  return json([]);
 }
 if(!u.pathname.endsWith('.mp4')||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
 const size=fs.statSync(file).size,m=/^bytes=(\d+)-(\d*)$/.exec(req.headers.range||'');let start=m?Number(m[1]):0,end=m?.[2]?Math.min(Number(m[2]),size-1):size-1;
 if(start>=size){res.writeHead(416,{'Content-Range':`bytes */${size}`});res.end();return;}
 const fault=start>0?mode:'normal';const record={start,end,fault,sent:0,closed:false};ranges.push(record);
 if(fault==='ignore')start=0;
 res.statusCode=m&&fault!=='ignore'?206:200;res.setHeader('Accept-Ranges','bytes');res.setHeader('Content-Type','video/mp4');res.setHeader('Content-Length',end-start+1);
 if(res.statusCode===206)res.setHeader('Content-Range',`bytes ${start}-${end}/${size}`);
 res.flushHeaders();
 if(fault==='stall'||fault==='stall-once'){if(fault==='stall-once')mode='normal';const timer=setTimeout(()=>res.destroy(),45000);res.on('close',()=>clearTimeout(timer));return;}
 const input=fs.createReadStream(file,{start,end,highWaterMark:16384});input.pause();
 const timer=setInterval(()=>{const chunk=input.read(16384);if(chunk){record.sent+=chunk.length;res.write(chunk);}},250);
 input.on('end',()=>{clearInterval(timer);res.end();});input.on('error',()=>res.destroy());res.on('close',()=>{record.closed=true;clearInterval(timer);input.destroy();});
}).listen(port,'0.0.0.0',()=>console.log('Synthetic seek fixture on '+port));
