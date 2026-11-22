import {validate} from './validation.mjs';
// Tokens belong to installations, not to users' content accounts. Only hashes persist.
Deno.serve(async req=>{
 if(req.method!=='POST')return new Response('',{status:405});
 const token=(req.headers.get('Authorization')||'').replace(/^Bearer /,'');
 if(!/^[a-f0-9]{64}$/.test(token))return new Response('',{status:401});
 let payload;
 try{
  const reader=req.body?.getReader();if(!reader)return new Response('',{status:400});
  let size=0,chunks=[];while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>32768){await reader.cancel();return new Response('',{status:413});}chunks.push(value);}
  const bytes=new Uint8Array(size);let at=0;for(const chunk of chunks){bytes.set(chunk,at);at+=chunk.length;}
  payload=validate(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes)));
 }catch{return new Response('',{status:400});}
 const hash=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token));
 payload.p_hash=Array.from(new Uint8Array(hash),b=>b.toString(16).padStart(2,'0')).join('');
 payload.p_device_hash=payload.deviceToken?Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(payload.deviceToken))),b=>b.toString(16).padStart(2,'0')).join(''):null;delete payload.deviceToken;
 try{
  const secret=JSON.parse(Deno.env.get('SUPABASE_SECRET_KEYS')||'{}').default;
  const legacy=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');const key=secret||legacy;
  const headers={'Content-Type':'application/json','apikey':key};if(!secret)headers.Authorization='Bearer '+legacy;
  const result=await fetch(Deno.env.get('SUPABASE_URL')+'/rest/v1/rpc/support_ingest_device',{method:'POST',headers,body:JSON.stringify(payload),signal:AbortSignal.timeout(10000)});
  if(!result.ok)return new Response('',{status:503});
  const status=await result.json();return new Response('',{status:[200,400,403,429].includes(status)?status:503});
 }catch{return new Response('',{status:503});}
});
