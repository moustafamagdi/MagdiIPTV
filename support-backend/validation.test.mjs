import {test} from 'node:test';import assert from 'node:assert/strict';import {validate} from './validation.mjs';
const input=()=>({schema:1,app:'tube',support_id:'00000000-0000-0000-0000-000000000001',action:'upload',batch:'00000000-0000-0000-0000-000000000002',environment:{app_version:'0.1.36-support',android_api:36,aa_version:'unknown',webview_version:'unknown',manufacturer:'other',model:'Synthetic',installer:'android',initiator:'android'},events:[{at:Date.now(),kind:'media',state:'playing',frames:15}]});
test('accept closed report and deletion',()=>{assert.equal(validate(input()).p_app,'tube');assert.equal(validate({...input(),action:'delete'}).p_action,'delete');});
test('reject private fields at every level',()=>{for(const data of [{...input(),password:'SECRET'},{...input(),environment:{...input().environment,cookie:'SECRET'}},{...input(),events:[{at:1,kind:'media',state:'playing',url:'https://SECRET'}]}])assert.throws(()=>validate(data));});
test('reject unbounded values and arbitrary event text',()=>{for(const event of [{at:1,kind:'raw-log',state:'SECRET'},{at:1,kind:'media',state:'https://SECRET'},{at:1,kind:'media',frames:1e20},{at:1,kind:'media',frames:'SECRET'}])assert.throws(()=>validate({...input(),events:[event]}));});
test('bound count and reject invalid IDs/software fields',()=>{assert.throws(()=>validate({...input(),events:Array(129).fill(input().events[0])}));assert.throws(()=>validate({...input(),support_id:'SECRET'}));assert.throws(()=>validate({...input(),environment:{...input().environment,app_version:'https://SECRET'}}));});
test('Auto diagnostics accepted without changing existing clients',()=>{for(const app of ['auto','iptv','tube'])assert.equal(validate({...input(),app}).p_app,app);assert.equal(validate({...input(),app:'auto',events:[{at:1,kind:'install-check',target:'tube',state:'mismatch',method:'shizuku',installer:'play',initiator:'shell',version:'0.1.36-support'}]}).p_events.length,1);});
test('Auto rejects free form installation data and unrelated inventory',()=>{for(const event of [{at:1,kind:'install',target:'other-app'},{at:1,kind:'install-check',installer:'com.secret.personal'},{at:1,kind:'install',method:'pm install SECRET'},{at:1,kind:'install-check',path:'/private/account.apk'}])assert.throws(()=>validate({...input(),app:'auto',events:[event]}));});

test('shared device proof is optional but strictly paired',()=>{const x=input();assert.equal(validate(x).p_device,null);const device='11111111-1111-4111-8111-111111111111',proof='a'.repeat(64);assert.equal(validate({...x,device_id:device,device_token:proof}).p_device,device);for(const fields of [{device_id:device},{device_id:device,device_token:'short'},{device_token:proof},{device_id:'bad',device_token:proof}])assert.throws(()=>validate({...x,...fields}));assert.equal(validate({...x,action:'delete',device_id:device}).p_device,null);});

test('IPTV provider failures allow only a fixed reason code',()=>{
 const event={at:1,kind:'provider-connect',state:'failed',code:1};
 assert.deepEqual(validate({...input(),app:'iptv',events:[event]}).p_events,[event]);
 for(const e of [{...event,code:8},{...event,url:'https://private.example'},{...event,state:'ready'}]){
  assert.throws(()=>validate({...input(),app:'iptv',events:[e]}));
 }
 assert.throws(()=>validate({...input(),events:[event]}));
});
