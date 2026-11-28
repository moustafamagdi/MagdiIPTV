'use strict';
// A bounded, account-scoped last-good cache. Failures never advance freshness.
class CatalogCache {
 constructor(storage,now=()=>Date.now()){this.storage=storage;this.now=now;this.memory=new Map();this.pending=new Map();this.prefix='mm.catalog.v1:';}
 key(account,section){return this.prefix+encodeURIComponent(account)+':'+encodeURIComponent(section);}
 get(account,section){const key=this.key(account,section);if(this.memory.has(key))return this.memory.get(key);try{const value=JSON.parse(this.storage.getItem(key));if(value&&Number.isFinite(value.at)&&Array.isArray(value.data)){this.memory.set(key,value);return value;}}catch{}return null;}
 stale(entry,ttl=6*60*60*1000){return !entry||this.now()<entry.at||this.now()-entry.at>=ttl;}
 async fetch(account,section,request,valid=()=>true){const key=this.key(account,section);if(this.pending.has(key))return this.pending.get(key);const task=(async()=>{const data=await request();if(!Array.isArray(data))throw new Error('Invalid catalog response');if(!valid())throw new Error('Catalog request superseded');const entry={at:this.now(),data};this.memory.delete(key);this.memory.set(key,entry);while(this.memory.size>16)this.memory.delete(this.memory.keys().next().value);try{const value=JSON.stringify(entry);if(value.length<1500000){const entries=[];for(let i=0;i<this.storage.length;i++){const k=this.storage.key(i);if(k?.startsWith(this.prefix))entries.push(k);}for(const old of entries.slice(0,Math.max(0,entries.length-7)))if(old!==key)this.storage.removeItem(old);try{this.storage.setItem(key,value);}catch{for(const old of entries)if(old!==key)this.storage.removeItem(old);this.storage.setItem(key,value);}}}catch{}return entry;})();this.pending.set(key,task);try{return await task;}finally{if(this.pending.get(key)===task)this.pending.delete(key);}}
}
globalThis.CatalogCache=CatalogCache;
