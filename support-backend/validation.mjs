const kinds=new Set(['media','app-start','car-open','car-disconnected','audio-browser','install-check','http-error','network-error','controller','background-unavailable','crash','download','install','release-check','repair','permission']);
const states=new Set(('play pause waiting playing error ended telemetry fullscreen video-frozen video-recovery-start video-recovery-failed video-recovery-complete seek-start seek-retry seek-failed seek-complete match mismatch unknown UnknownHostException SocketException SocketTimeoutException SSLHandshakeException IOException accepted current-loading media-mismatch request-mismatch connection-mismatch sample-unavailable helper-unavailable stale document-mismatch disconnected started ready failed cancelled pending finished expired unavailable not-installed').split(' '));
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const versions=/^(?:unknown|[0-9][a-zA-Z0-9._-]{0,63})$/;
const numbers=new Set(['at','time','ready','frames','code','dropped','sampleDelayMs','callbackMs','appJavaHeapMiB']);
export function validate(input){
 if(!input||Object.keys(input).some(k=>!['schema','action','app','support_id','batch','environment','events'].includes(k)))throw Error('shape');
 if(input.schema!==1||!['iptv','tube','auto'].includes(input.app)||!uuid.test(input.support_id)||!['upload','delete'].includes(input.action))throw Error('shape');
 if(input.action==='delete')return {p_app:input.app,p_id:input.support_id,p_action:'delete',p_batch:null,p_environment:{},p_events:[]};
 if(!uuid.test(input.batch)||!Array.isArray(input.events)||input.events.length>128)throw Error('shape');
 const environment=input.environment;
 if(!environment||Object.keys(environment).length!==8)throw Error('environment');
 for(const k of ['app_version','aa_version','webview_version'])if(!versions.test(environment[k]))throw Error('version');
 if(!Number.isInteger(environment.android_api)||environment.android_api<30||environment.android_api>100)throw Error('api');
 if(!/^(samsung|xiaomi|google|huawei|honor|oppo|vivo|oneplus|motorola|sony|realme|nothing|asus|nokia|other)$/.test(environment.manufacturer))throw Error('manufacturer');
 if(typeof environment.model!=='string'||!/^[a-zA-Z0-9 _().-]{1,48}$/.test(environment.model))throw Error('model');
 for(const k of ['installer','initiator'])if(!['play','google','android','samsung','xiaomi','other','unknown'].includes(environment[k]))throw Error('installer');
 const events=input.events.map(e=>{if(!e||!kinds.has(e.kind)||!Number.isSafeInteger(e.at)||e.at<0)throw Error('event');for(const [k,v] of Object.entries(e)){if(k==='kind')continue;if(k==='target'){if(!['auto','iptv','tube'].includes(v))throw Error('target');}else if(k==='method'){if(!['classic','shizuku','root','unknown'].includes(v))throw Error('method');}else if(k==='version'){if(!versions.test(v))throw Error('version');}else if(k==='installer'||k==='initiator'){if(!['play','google','android','samsung','xiaomi','shell','other','unknown'].includes(v))throw Error('installer');}else if(k==='state'){if(!states.has(v))throw Error('state');}else if(!numbers.has(k)||!Number.isSafeInteger(v)||v< -10000||v>10000000000000)throw Error('field');}return e;});
 return {p_app:input.app,p_id:input.support_id,p_action:'upload',p_batch:input.batch,p_environment:environment,p_events:events};
}
