import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const code=fs.readFileSync(new URL('../app/src/main/assets/www/offline.js',import.meta.url),'utf8');
function harness(){
 const calls=[],nodes={},dialogs=[];
 function node(){return {children:[],open:false,append(...n){this.children.push(...n)},setAttribute(){},addEventListener(e,fn){this[e]=fn},remove(){},showModal(){this.open=true;dialogs.push(this)},close(){this.open=false;this.closeEvent?.()},replaceChildren(){}};}
 const document={createElement(){const n=node();n.addEventListener=(e,fn)=>{if(e==='close')n.closeEvent=fn};return n},body:node(),querySelectorAll(){return []}};
 const ctx={strings:{en:{},ar:{}},window:{},document,Controller:{isPhone:()=>true},mode:'xtream',publicSource:'',account:{activeId:'a'},notice(){},t:k=>k,$:id=>nodes[id]??=(node()),api:async(method,args)=>{calls.push({method,args});return method==='download'?{added:1,skipped:0,failed:0}:{items:[],used:0,free:999,metered:false}}};
 vm.createContext(ctx);vm.runInContext(code,ctx);return {ctx,calls,dialogs,offline:ctx.window.Offline};
}
const item={id:'1',name:'Episode',type:'episode',ext:'mkv'};
for(const [index,expected] of [[3,true],[4,false],[5,null]])test('network choice '+expected,async()=>{const h=harness();const p=h.offline.enqueue([item]);h.dialogs[0].children[index].onclick();await p;const downloads=h.calls.filter(c=>c.method==='download');assert.equal(downloads.length,expected===null?0:1);if(expected!==null)assert.equal(downloads[0].args.metered,expected);assert.equal(h.calls.some(c=>c.method==='downloadsettings'),false)});
test('account change and dismiss cannot enqueue',async()=>{const h=harness();let p=h.offline.enqueue([item]);h.ctx.account.activeId='b';h.dialogs[0].children[3].onclick();await p;assert.equal(h.calls.length,0);p=h.offline.enqueue([item]);h.dialogs[1].close();await p;assert.equal(h.calls.length,0)});
test('duplicate taps cannot enqueue a second request',async()=>{const h=harness();const p=h.offline.enqueue([item]);await h.offline.enqueue([item]);assert.equal(h.dialogs.length,1);h.dialogs[0].children[3].onclick();await p;assert.equal(h.calls.filter(c=>c.method==='download').length,1)});
