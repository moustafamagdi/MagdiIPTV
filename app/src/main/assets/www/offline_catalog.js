"use strict";
// Pure grouping: legacy rows retain their original account-scoped groupKey.
globalThis.OfflineCatalog={
 info(row){
  const group=String(row.group||''),cut=group.lastIndexOf(' · ');
  const title=row.series|| (cut>=0?group.slice(0,cut):group)||'';
  const season=row.season|| (cut>=0?group.slice(cut+3):'');
  const scope=group&&String(row.groupKey||'').endsWith(group)?String(row.groupKey).slice(0,-group.length):String(row.groupKey||row.token);
  return {title,season,seriesKey:scope+title,seasonKey:row.groupKey||row.token};
 },
 series(rows){const groups=new Map();for(const row of rows.filter(r=>r.type==='episode')){const info=this.info(row);const key=info.title?info.seriesKey:'ungrouped:'+row.token;if(!groups.has(key))groups.set(key,{key,title:info.title,items:[]});groups.get(key).items.push(row);}return [...groups.values()];},
 seasons(rows){const groups=new Map();for(const row of rows){const info=this.info(row);if(!groups.has(info.seasonKey))groups.set(info.seasonKey,{key:info.seasonKey,title:info.season,items:[]});groups.get(info.seasonKey).items.push(row);}return [...groups.values()];},
 size(rows){return rows.reduce((n,r)=>n+Math.max(0,Number(r.bytes)||0),0);}
};
