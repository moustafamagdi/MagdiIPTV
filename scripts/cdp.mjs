import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const port=process.argv.includes('--android')?'9225':process.env.CDP_PORT||'9224';
process.argv=process.argv.filter(a=>a!=='--android');
const pages=await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const page=pages.find(p=>p.type==='page');if(!page)throw new Error('No debuggable WebView');
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=0;const pending=new Map();
ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}}};
const call=(method,params={})=>new Promise((resolve,reject)=>{const n=++id;const timer=setTimeout(()=>reject(new Error('CDP timeout')),90000);pending.set(n,{resolve,reject,timer});ws.send(JSON.stringify({id:n,method,params}));});
try{
 if(process.argv[2]==='flow'){const flow=await import(pathToFileURL(path.resolve(process.argv[3])).href);console.log(JSON.stringify(await flow.run(call)));}
 else if(process.argv[2]==='close'){await call('Browser.close');console.log('Test browser closed');}
 else if(process.argv[2]==='screenshot'){if(process.argv[4]){await call('Emulation.setDeviceMetricsOverride',{width:Number(process.argv[4]),height:Number(process.argv[5]),deviceScaleFactor:1,mobile:Number(process.argv[4])<600});await new Promise(r=>setTimeout(r,300));}const {data}=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(process.argv[3],Buffer.from(data,'base64'));console.log('Screenshot saved');}
 else if(process.argv[2]==='viewport'){await call('Emulation.setDeviceMetricsOverride',{width:Number(process.argv[3]),height:Number(process.argv[4]),deviceScaleFactor:1,mobile:process.argv[5]==='mobile'});console.log('Viewport changed');}
 else if(process.argv[2]==='reload'){await call('Page.reload',{ignoreCache:true});await new Promise(r=>setTimeout(r,700));console.log('Reloaded');}
 else {if(process.argv[3])await call('Emulation.setDeviceMetricsOverride',{width:Number(process.argv[3]),height:Number(process.argv[4]),deviceScaleFactor:1,mobile:Number(process.argv[3])<600});const expression=fs.readFileSync(process.argv[2],'utf8');const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails){console.error(r.exceptionDetails.text,r.exceptionDetails.exception?.description);process.exitCode=1;}else console.log(JSON.stringify(r.result.value));}
}finally{ws.close();}
