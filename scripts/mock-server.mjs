import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const media=path.resolve('tests/media');
const names=['Atlas News HD','Nature & Discovery','Cinema One','الوثائقية HD','الرياضة HD','قناة الأخبار'];
const channel=(name,i)=>({name,stream_id:i+1,category_id:i<3?'1':'2',stream_type:'live'});
let saved={host:'http://127.0.0.1:8787',username:'demo',password:'demo',mode:'xtream'};
let slowEpg=false,publicOptions={delay:0,error:false,empty:false},publicLoads=0;
const web=path.resolve(process.argv[2]||'app/src/main/assets/www');
const server=http.createServer(async(req,res)=>{
 const u=new URL(req.url,'http://localhost');
 const json=value=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(value));};
 if(u.pathname==='/fixture-poster.png'){res.setHeader('Content-Type','image/png');fs.createReadStream(path.resolve('tests/media/fixture-poster.png')).pipe(res);return;}
 if(u.pathname==='/fixture-broken.png'){res.writeHead(404);res.end();return;}
 if(u.pathname==='/test/slow-epg'){slowEpg=u.searchParams.get('enabled')==='1';return json({slowEpg});}
 if(u.pathname==='/test/public'){publicOptions={delay:Number(u.searchParams.get('delay')||0),error:u.searchParams.has('error'),empty:u.searchParams.has('empty')};return json({publicLoads});}
 if(u.pathname==='/test/reset-account'){saved=u.searchParams.has('empty')?{}:{host:'http://127.0.0.1:8787',username:'demo',password:'demo',mode:'xtream'};return json({ok:true});}
 if(u.pathname==='/bridge'){
  let raw='';for await(const chunk of req)raw+=chunk;const {method,args}=JSON.parse(raw);let value;
  try{
   if(method==='environment')value={car:false,background:true};
   else if(method==='config')value={...saved,configured:!!saved.host,hasPassword:!!saved.password,password:undefined};
   else if(method==='publicplaylist'){
    publicLoads++;const options={...publicOptions};if(options.delay)await new Promise(r=>setTimeout(r,options.delay));if(options.error)throw Error('Fixture offline');
    value={playlist:{items:options.empty?[]:[{name:args.preset+' News',url:'http://127.0.0.1:8787/1.m3u8',tvg:{logo:'http://127.0.0.1:8787/fixture-poster.png'},group:{title:'News'}},{name:args.preset+' Nature',url:'http://127.0.0.1:8787/2.m3u8',group:{title:'Nature'}}]}};
   }
   else if(method==='publicplay')value=args.url;
   else if(method==='forget'){saved={};value=true;}
   else if(method==='save'){saved=args;value=true;}
   else if(method==='connect')value={mode:'xtream',source:'fixture',status:'Active'};
   else if(method==='artwork')value=args.url;
   else if(method==='catalog'||method==='details'||method==='episodes'||method==='epg'){
    const action=method==='details'?'get_vod_info':method==='episodes'?'get_series_info':method==='epg'?'get_short_epg':'get_'+args.type+(args.categories?'_categories':args.type==='series'?'':'_streams');
    value=await(await fetch('http://127.0.0.1:8787/player_api.php?'+new URLSearchParams({username:'demo',password:'demo',action,...args}))).json();if(method==='details')value=value.info||{};
   }else if(method==='play')value='http://127.0.0.1:8787/'+args.id+'.'+args.ext;
   else value=true;
   return json({ok:true,value});
  }catch{return json({ok:false,error:'Fixture error'});}
 }
 if(u.pathname==='/test-bridge.js'){res.setHeader('Content-Type','application/javascript');res.end(`window.Android={request(id,method,payload){fetch('/bridge',{method:'POST',body:JSON.stringify({method,args:JSON.parse(payload)})}).then(r=>r.json()).then(r=>window.nativeReply(Number(id),r));},event(){}};`);return;}
 if(u.pathname==='/'||u.pathname==='/index.html'){res.setHeader('Content-Type','text/html');res.end(fs.readFileSync(path.join(web,'index.html'),'utf8').replace('<script src="vendor/hls.min.js">','<script src="/test-bridge.js"></script><script src="vendor/hls.min.js">'));return;}
 if(/\.(js|css|txt)$/.test(u.pathname)&&!u.pathname.includes('..')){const file=path.join(web,u.pathname);if(fs.existsSync(file)){res.setHeader('Content-Type',u.pathname.endsWith('.js')?'application/javascript':u.pathname.endsWith('.css')?'text/css':'text/plain');fs.createReadStream(file).pipe(res);return;}}
 if(u.pathname==='/player_api.php'){
  if(u.searchParams.get('username')!=='demo'||u.searchParams.get('password')!=='demo')return json({user_info:{auth:0}});
  const a=u.searchParams.get('action');
  if(!a)return json({user_info:{auth:1,status:'Active',allowed_output_formats:['m3u8','ts'],exp_date:'1999999999'}});
  if(a.endsWith('_categories'))return json([{category_id:'1',category_name:'Featured / مختارات'},{category_id:'2',category_name:'Arabic / عربي'}]);
  if(a==='get_live_streams'){const cat=u.searchParams.get('category_id');return json(names.map((name,i)=>({...channel(name,i),stream_icon:i===1?'http://'+req.headers.host+'/fixture-broken.png':i===2?'':'http://'+req.headers.host+'/fixture-poster.png'})).filter(c=>!cat||c.category_id===cat));}
  if(a==='get_vod_info')return json({info:{movie_image:'http://'+req.headers.host+'/fixture-poster.png',plot:'A journey through colour. رحلة عربية لاختبار الوصف.',genre:'Documentary',director:'Fixture Director',cast:'Test Cast',duration:'00:10',rating:'8.5'},movie_data:{stream_id:101,direct_source:'must-not-be-shown'}});
  if(a==='get_vod_streams')return json([{name:'Color & Motion',stream_icon:'http://'+req.headers.host+'/fixture-poster.png',stream_id:101,category_id:'1',container_extension:'mp4',year:'2026',rating:'8.5'}]);
  if(a==='get_series')return json([{name:'The Color Study',cover:'http://'+req.headers.host+'/fixture-poster.png',plot:'A series about colour / مسلسل عن الألوان',series_id:201,category_id:'1',year:'2026',rating:'8.0'}]);
  if(a==='get_series_info')return json({info:{cover:'http://'+req.headers.host+'/fixture-poster.png',plot:'Full series description / وصف المسلسل الكامل'},episodes:{'1':[{id:'301',episode_num:1,title:'Episode 1 · Motion',info:{movie_image:'http://'+req.headers.host+'/fixture-poster.png',plot:'Episode description / وصف الحلقة'},container_extension:'mp4'},{id:'302',episode_num:2,title:'Episode 2 · Sound',container_extension:'mp4'}]}});
  if(a==='get_short_epg'){if(slowEpg)await new Promise(r=>setTimeout(r,6000));return json({epg_listings:[{title:Buffer.from('Now: Color and sound test').toString('base64')}]});}
  return json([]);
 }
 if(u.pathname==='/playlist.m3u'){res.setHeader('Content-Type','audio/x-mpegurl');res.end('#EXTM3U\n#EXTINF:-1 tvg-logo="http://10.0.2.2:8787/fixture-poster.png" group-title="Test",Test HLS\nhttp://10.0.2.2:8787/live/demo/demo/1.m3u8\n');return;}
 let filename;
 if(u.pathname.endsWith('.m3u8'))filename='index.m3u8';
 else if(/segment\d+\.ts$/.test(u.pathname))filename=path.basename(u.pathname);
 else if(u.pathname.endsWith('.ts'))filename='test.ts';
 else if(u.pathname.endsWith('.mp4'))filename='test.mp4';
 if(filename){
  const file=path.join(media,filename);if(!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  const size=fs.statSync(file).size;res.setHeader('Content-Type',filename.endsWith('m3u8')?'application/vnd.apple.mpegurl':filename.endsWith('ts')?'video/mp2t':'video/mp4');res.setHeader('Accept-Ranges','bytes');
  const range=req.headers.range?.match(/bytes=(\d+)-(\d*)/);let start=0,end=size-1;
  if(range){start=Number(range[1]);end=range[2]?Math.min(Number(range[2]),end):end;if(start>size-1){res.writeHead(416);res.end();return;}res.statusCode=206;res.setHeader('Content-Range',`bytes ${start}-${end}/${size}`);}
  res.setHeader('Content-Length',end-start+1);fs.createReadStream(file,{start,end}).pipe(res);return;
 }
 res.writeHead(404);res.end();
});
server.listen(8787,'0.0.0.0',()=>console.log('Synthetic Xtream/HLS test fixture ready on port 8787'));
