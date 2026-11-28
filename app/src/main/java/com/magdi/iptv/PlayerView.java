package com.magdi.iptv;

import android.annotation.SuppressLint;
import android.content.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One WebView owns playback and audio focus; the service only maintains foreground priority. */
public final class PlayerView extends FrameLayout {
 private final WebView web;
 private final boolean phone;
 private final CarLaunchRequest carLaunch;
 private final String surface;
 private boolean closed,active,covered,background=true,pendingResume,interrupted,documentReady;
 private long lastTelemetry,lastBack,detachedSince;
 private boolean sampleInFlight;
 private long sampleGeneration,sampleStarted;
 private String observationReason="";
 private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
 private final java.util.concurrent.ExecutorService requests=java.util.concurrent.Executors.newFixedThreadPool(3);
 private final java.util.concurrent.ExecutorService controls=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final StreamProxy proxy=new StreamProxy();
 private final ArtworkProxy artwork=new ArtworkProxy();
 private final Portal portal;
 private static final Set<PlayerView> views=Collections.newSetFromMap(new WeakHashMap<>());
 private final Runnable repaint=()->refreshPicture();
 private final Runnable expireMediaCard=()->{if(!closed&&!CarController.fresh()){MediaCardService.forget(this);PlaybackService.release(this);}};
 private FrameLayout fullscreen;
 private WebChromeClient.CustomViewCallback fullscreenCallback;
 @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"}) public PlayerView(Context c){
  super(c);phone=c instanceof android.app.Activity;surface=phone?"phone":"car";
  carLaunch=phone?new CarLaunchRequest(c,IptvCarService.class,()->{}):null;
  background=c.getSharedPreferences("iptv",0).getBoolean("backgroundAudio",true);
  setBackgroundColor(0xff091622);portal=new Portal(c,proxy);views.add(this);
  web=new WebView(c){@Override protected void onWindowVisibilityChanged(int visibility){
   if(visibility!=VISIBLE&&background&&!closed)cover();
   super.onWindowVisibilityChanged(background&&!closed?VISIBLE:visibility);
  }};
  web.setBackgroundColor(0xff091622);addView(web,new FrameLayout.LayoutParams(-1,-1));
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  // The projected host can have a different density from Chromium's phone default.
  // Initial scale is a physical percentage; keep phone browsing at its default.
  if(!phone)web.setInitialScale(Math.max(1,Math.round(c.getResources().getDisplayMetrics().density*100)));
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
  web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.getSettings().setSupportMultipleWindows(false);
  WebView.setWebContentsDebuggingEnabled((c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
  web.addJavascriptInterface(new Bridge(),"Android");
  web.setWebChromeClient(new WebChromeClient(){
   public void onShowCustomView(View v,CustomViewCallback cb){showFullscreen(v,cb);}
   public void onHideCustomView(){hideFullscreen();}
   public boolean onConsoleMessage(ConsoleMessage m){return true;}
  });
  web.setWebViewClient(new WebViewClient(){
   public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().startsWith(StreamProxy.ORIGIN+"/");}
   public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    android.net.Uri u=r.getUrl();
    if(!"https".equals(u.getScheme())||!"app.magdi.local".equals(u.getHost()))return StreamProxy.reply(403,"text/plain","External navigation disabled");
    String path=u.getPath();if(path==null)return StreamProxy.reply(404,"text/plain","");
    if(path.startsWith("/artwork/")&&"GET".equals(r.getMethod()))return artwork.open(path.substring(9));
    if(path.startsWith("/offline/"))return phone||!"GET".equals(r.getMethod())?StreamProxy.reply(403,"text/plain","Phone is a controller only"):new OfflineLibrary(c).open(path.substring(9),r.getRequestHeaders());
    if(path.startsWith("/media/"))return phone?StreamProxy.reply(403,"text/plain","Phone is a controller only"):proxy.open(path.substring(7),r.getRequestHeaders());
    if(path.contains("..")||path.contains("%")||!"GET".equals(r.getMethod()))return StreamProxy.reply(403,"text/plain","");
    if(path.equals("/"))path="/index.html";
    try{
     java.io.InputStream in=c.getAssets().open("www"+path);
     String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".html")?"text/html":"text/plain";
     Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Referrer-Policy","no-referrer");headers.put("X-Content-Type-Options","nosniff");
     headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src "+(phone?"'none'":"'self' blob:")+"; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'");
     return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,in);
    }catch(java.io.IOException e){return StreamProxy.reply(404,"text/plain","Not found");}
   }
   public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){documentReady=false;controllerChanged();if(!phone)CarController.documentLoading(PlayerView.this);}
   public void onPageFinished(WebView v,String u){Diagnostics.event(surface+" page ready");}
  });
  web.loadUrl(StreamProxy.ORIGIN+"/");Diagnostics.event(surface+" view created");
 }
 private final class Bridge {
  @JavascriptInterface public void request(String id,String method,String payload){
   if(closed||id==null||!id.matches("[0-9]{1,12}"))return;
   if(Arrays.asList("controlleropen","controllerstate","controllercommand","ready","playerbegin","carstop","play","publicplay","offlineplay").contains(method)){
    timer.post(()->{
     if(closed)return;
     try{
      JSONObject args=new JSONObject(payload);Object value;
      if("ready".equals(method)){documentReady=true;if(!phone)CarController.pageReady(PlayerView.this);value=true;}
      else if("playerbegin".equals(method)){if(phone)throw new IllegalStateException();value=CarController.begin(PlayerView.this,args);}
      else if("carstop".equals(method)){if(phone||!CarController.accepts(PlayerView.this))throw new IllegalStateException();value=CarController.command(args.put("action","stop"));}
      else if("controlleropen".equals(method)){if(!phone||!active)throw new IllegalStateException();carLaunch.request();value=carLaunch.status();}
      else if("controllerstate".equals(method))value=CarController.status().put("launch",phone?carLaunch.status():"idle");
      else if("controllercommand".equals(method)){if(phone&&"stop".equals(args.optString("action")))carLaunch.cancel();value=phone&&CarController.command(args);
       if(phone&&"select".equals(args.optString("action"))){
        if(Boolean.TRUE.equals(value)){carLaunch.cancel();carLaunch.request();}
        else if(args.toString().length()<=100000&&args.optJSONObject("item")!=null
                &&Arrays.asList("live","vod","episode").contains(args.getJSONObject("item").optString("type"))){
         carLaunch.request(()->{
          try{return CarController.command(args.put("epoch",CarController.epoch()));}
          catch(JSONException invalid){return false;}
         });
         value=carLaunch.pending()||"opened".equals(carLaunch.status());
        }
       }}
      else {
       if(phone||!CarController.accepts(PlayerView.this,args.optLong("epoch",-1),args.optLong("request",-1)))throw new IllegalStateException("Car player unavailable");
       value=portal.call(method,args);MediaCardService.select(PlayerView.this);
      }
      reply(id,new JSONObject().put("ok",true).put("value",value));
     }catch(Exception e){try{reply(id,new JSONObject().put("ok",false).put("error","Car player unavailable"));}catch(JSONException ignored){}}
    });return;
   }
   java.util.concurrent.ExecutorService worker=Arrays.asList("connect","catalog","details","episodes","epg","playlist","publicplaylist").contains(method)?requests:controls;
   worker.execute(()->{
    JSONObject answer=new JSONObject();
    try{
     JSONObject args=new JSONObject(payload);Object value;
     if("environment".equals(method))value=new JSONObject().put("car",!phone).put("controller",phone).put("version",Diagnostics.version(getContext().getPackageName())).put("background",background);
     else if("artwork".equals(method))value=artwork.register(args.getString("url"));
     else if("background".equals(method)){boolean enabled=args.getBoolean("enabled");getContext().getSharedPreferences("iptv",0).edit().putBoolean("backgroundAudio",enabled).apply();timer.post(()->{background=enabled;if(!enabled)PlaybackService.release(PlayerView.this);});value=enabled;}
     else if("logs".equals(method)||"copyLogs".equals(method)||"logSessions".equals(method)){boolean ar="ar".equals(args.optString("language","ar"));timer.post(()->{if(phone)Diagnostics.open((android.app.Activity)getContext(),ar);});value=true;}
     else if("setup".equals(method)){String language=args.optString("language","ar");timer.post(()->{if(phone)getContext().startActivity(new Intent(getContext(),InstallSetupActivity.class).putExtra("language",language));});value=true;}
     else {if(!phone&&Arrays.asList("download","downloadaction","downloadsettings").contains(method))throw new IllegalStateException();value=portal.call(method,args);if("save".equals(method)||"forget".equals(method)||"switchAccount".equals(method)||("restoreAccount".equals(method)&&Boolean.TRUE.equals(value)))timer.post(CarController::accountChanged);}
     answer.put("ok",true).put("value",value==null?JSONObject.NULL:value);
    }catch(Exception e){if("connect".equals(method))Diagnostics.event("IPTV provider connect failed="+Portal.connectionFailure(e));try{answer.put("ok",false).put("error",safeError(e));}catch(JSONException ignored){}Diagnostics.event(surface+" request="+(method!=null&&method.matches("[a-z]{1,20}")?method:"unknown")+" failed="+e.getClass().getSimpleName());}
    String encoded=answer.toString();timer.post(()->{if(!closed)web.evaluateJavascript("window.nativeReply("+id+","+encoded+")",null);});
   });
  }
  @JavascriptInterface public void event(String kind,String state){
   if(phone)return;
   if(kind==null||!kind.matches("play|pause|waiting|playing|error|ended|telemetry|fullscreen|video-frozen|video-recovery-start|video-recovered|video-recovery-failed|video-recovery-exhausted|video-recovery-cancelled|seek-start|seek-retry|seek-failed|seek-complete"))return;
   timer.post(()->{if(closed)return;try{JSONObject s=new JSONObject(state);long now=android.os.SystemClock.elapsedRealtime();if("telemetry".equals(kind)&&now-lastTelemetry<10000)return;lastTelemetry=now;Diagnostics.event(surface+" media="+kind+" time="+s.optInt("time")+" ready="+s.optInt("ready")+" frames="+s.optLong("frames")+" engine="+(s.optString("engine").matches("hls|ts|native")?s.optString("engine"):"unknown")+" code="+s.optInt("code"));}catch(Exception ignored){}});
  }
  @JavascriptInterface public void carState(String state){if(phone||state==null||state.length()>20000)return;timer.post(()->{if(!closed)try{observe(new JSONObject(state));}catch(JSONException ignored){}});}
 }
 private void reply(String id,JSONObject value){if(!closed)web.evaluateJavascript("window.nativeReply("+id+","+value+")",null);}
 boolean documentReady(){return documentReady;}
 void carConnection(long epoch,long request){controllerChanged();if(closed)return;pendingResume=false;if(epoch==0){hideFullscreen();web.evaluateJavascript("window.Controller&&Controller.connection(0,"+request+")",null);web.evaluateJavascript("if(window.__magdiPlayback)window.__magdiPlayback.stop();document.querySelectorAll('video,audio').forEach(v=>{v.pause();v.removeAttribute('src');v.load()})",null);proxy.clear();}else web.evaluateJavascript("window.Controller&&Controller.connection("+epoch+","+request+")",null);}
 void accountChanged(long request){controllerChanged();if(!closed)web.evaluateJavascript("window.Controller&&Controller.accountChanged("+request+")",null);}
 void controllerCommand(JSONObject command){if(closed)return;if(fullscreen!=null&&"fullscreen".equals(command.optString("action"))){hideFullscreen();return;}if("stop".equals(command.optString("action")))hideFullscreen();web.evaluateJavascript("window.Controller&&Controller.receive("+command+")",null);}
 private static String safeError(Exception e){
  if(e instanceof ProviderConnectionFailure)return e.getMessage();
  if(e instanceof IllegalArgumentException)return "Check the server address and required fields.";
  if(e instanceof JSONException)return "Unexpected server response. Check the address, account and network.";
  if(e instanceof java.io.IOException){String m=e.getMessage();if(Portal.HTTP_RETRY.equals(m))return m;if(m!=null&&(m.startsWith("Provider returned")||m.startsWith("Login rejected")||m.startsWith("Subscription")))return m;return "Cannot reach the IPTV server. Check your network or try again.";}
  return "Request could not be completed. Check your settings and retry.";
 }
 void controllerChanged(){sampleGeneration++;sampleInFlight=false;pendingResume=false;}
 private boolean acceptSample(long generation,long epoch,long request){if(closed||generation!=sampleGeneration)return false;sampleInFlight=false;return CarController.accepts(this,epoch,request);}
 private void observation(String reason){if(reason.equals(observationReason))return;observationReason=reason;Diagnostics.event("Controller observation="+reason);}
 private void observe(JSONObject state){
  String reason=CarController.report(this,state);observation(reason);if(!"accepted".equals(reason))return;
  timer.removeCallbacks(expireMediaCard);timer.postDelayed(expireMediaCard,6500);
  boolean playing=state.optBoolean("playing");if(!state.optBoolean("wanted"))pendingResume=false;
  MediaCardService.observe(this,playing,state.optBoolean("muted"),state.optInt("ready")>0&&!state.optBoolean("error"),state.optLong("time")*1000);
  if(playing&&background)PlaybackService.keep(this);else if(!pendingResume&&!interrupted)PlaybackService.idle(this);
 }
 private final Runnable sample=new Runnable(){public void run(){
  if(closed||phone||!active)return;
  android.media.AudioManager audio=(android.media.AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
  boolean call=audio.getMode()!=android.media.AudioManager.MODE_NORMAL;
  if(call&&!interrupted){interrupted=true;cover();}else if(!call&&interrupted){interrupted=false;tryResume();}
  long now=android.os.SystemClock.elapsedRealtime();
  if(sampleInFlight&&now-sampleStarted>=6000){controllerChanged();observation("sample-timeout");}
  if(!sampleInFlight){sampleInFlight=true;sampleStarted=now;final long ticket=++sampleGeneration,e=CarController.epoch(),r=CarController.request();
   web.evaluateJavascript("JSON.stringify(window.app?window.app.state():null)",value->{
    if(!acceptSample(ticket,e,r))return;
    try{observe(new JSONObject(new JSONArray("["+value+"]").getString(0)));}catch(JSONException ignored){observation("sample-unavailable");}
   });
  }timer.postDelayed(this,2000);
 }};
 // A covered screen keeps the local document, its request and status polling.
 void coverHost(){
  if(phone||closed)return;
  if(!CarPlaybackSession.connected()){pause();return;}
  if(!active||covered)return;
  covered=true;timer.removeCallbacks(repaint);
  background=getContext().getSharedPreferences("iptv",0).getBoolean("backgroundAudio",true);
  if(background)cover();else {pauseMedia();web.onPause();}
  Diagnostics.event("Car view covered; connected session retained; background audio="+background);
 }
 void resume(){if(closed)return;background=getContext().getSharedPreferences("iptv",0).getBoolean("backgroundAudio",true);active=true;covered=false;controllerChanged();web.onResume();if(!phone){CarController.attach(this);timer.removeCallbacks(sample);timer.post(sample);}schedulePicture();timer.postDelayed(()->tryResume(),500);Diagnostics.event(surface+" resumed");}
 void pause(){if(closed)return;if(phone)carLaunch.cancel();active=false;covered=false;controllerChanged();timer.removeCallbacks(repaint);timer.removeCallbacks(sample);if(!phone){CarController.detach(this);pendingResume=false;}web.onPause();Diagnostics.event(phone?"phone covered; controller=true":"Car player unavailable; playback stopped");}
 private void cover(){if(phone||web==null||closed||!active||!background)return;pendingResume=true;final long e=CarController.epoch(),r=CarController.request(),g=sampleGeneration;web.evaluateJavascript("window.__magdiPlayback?window.__magdiPlayback.cover():false",v->{if(!closed&&active&&g==sampleGeneration&&CarController.accepts(this,e,r))pendingResume="true".equals(v);});}
 private void tryResume(){if(phone||closed||!CarController.accepts(this)||!background||!pendingResume)return;android.media.AudioManager a=(android.media.AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);if(a.getMode()!=android.media.AudioManager.MODE_NORMAL||a.isMusicActive())return;pendingResume=false;guardedPlayerScript("window.__magdiPlayback&&window.__magdiPlayback.resume()");}
 private void guardedPlayerScript(String script){web.evaluateJavascript("if(window.Controller&&Controller.matches("+CarController.epoch()+","+CarController.request()+")){"+script+"}",null);}
 void pauseMedia(){if(phone)return;pendingResume=false;guardedPlayerScript("if(window.__magdiPlayback)window.__magdiPlayback.stop();if(window.app)window.app.pause()");MediaCardService.paused(this);PlaybackService.release(this);}
 void playFromMediaCard(){if(phone||closed||!CarController.accepts(this))return;android.media.AudioManager a=(android.media.AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);if(a.getMode()!=android.media.AudioManager.MODE_NORMAL)return;web.onResume();guardedPlayerScript("window.app&&window.app.resume()");}
 boolean isCarView(){return !phone;}
 private void schedulePicture(){if(!active||closed||covered)return;timer.removeCallbacks(repaint);timer.post(repaint);timer.postDelayed(repaint,250);timer.postDelayed(repaint,900);}
 private void refreshPicture(){if(closed||!active||covered||!isAttachedToWindow()||!isShown())return;web.onResume();web.requestLayout();web.postInvalidateOnAnimation();if(fullscreen!=null)fullscreen.postInvalidateOnAnimation();web.evaluateJavascript("window.dispatchEvent(new Event('resize'))",null);web.postVisualStateCallback(android.os.SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){public void onComplete(long id){if(!closed&&active&&!covered)web.postInvalidateOnAnimation();}});}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(!phone)CarUiDiagnostics.event(visibility==VISIBLE?"window-visible":"window-hidden");}
 @Override public void onWindowFocusChanged(boolean focus){if(!focus&&web!=null&&background)cover();super.onWindowFocusChanged(focus);if(!phone)CarUiDiagnostics.event(focus?"focus-gained":"focus-lost");if(focus&&web!=null){schedulePicture();timer.postDelayed(()->tryResume(),300);}}
 private void showFullscreen(View v,WebChromeClient.CustomViewCallback cb){
  if(fullscreen!=null){cb.onCustomViewHidden();return;}web.evaluateJavascript("window.app&&window.app.fullscreen(false)",null);fullscreenCallback=cb;
  fullscreen=new FrameLayout(getContext()){@Override public void dispatchWindowVisibilityChanged(int visibility){if(visibility!=VISIBLE&&background&&!closed)cover();super.dispatchWindowVisibilityChanged(background&&!closed?VISIBLE:visibility);}};
  fullscreen.setBackgroundColor(0xff000000);fullscreen.addView(v,new FrameLayout.LayoutParams(-1,-1));android.widget.ImageButton exit=new android.widget.ImageButton(getContext());exit.setImageResource(R.drawable.ic_fullscreen_close);exit.setBackgroundResource(R.drawable.fullscreen_close_background);exit.setContentDescription("Exit fullscreen / الخروج من ملء الشاشة");exit.setTag("native-fullscreen-exit");exit.setPadding(dp(14),dp(14),dp(14),dp(14));exit.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);exit.setElevation(0);exit.setStateListAnimator(null);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER_VERTICAL|Gravity.LEFT);p.leftMargin=dp(2);exit.setTranslationY(-dp(26));fullscreen.addView(exit,p);exit.setOnClickListener(x->hideFullscreen());Button fit=new Button(getContext());fit.setText("Fit / Fill\nملء / كامل");fit.setTextSize(12);fit.setTag("native-fullscreen-fit");fit.setContentDescription("Full picture / Fill screen (crop edges) — الصورة كاملة / ملء مع قص الأطراف");FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(112),dp(48),Gravity.CENTER_VERTICAL|Gravity.LEFT);fp.leftMargin=dp(2);fit.setTranslationY(dp(26));fullscreen.addView(fit,fp);fit.setOnClickListener(x->guardedPlayerScript("window.app&&window.app.toggleFit()"));web.setVisibility(INVISIBLE);addView(fullscreen,new FrameLayout.LayoutParams(-1,-1));
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 private void hideFullscreen(){if(fullscreen==null)return;FrameLayout old=fullscreen;fullscreen=null;removeView(old);old.removeAllViews();web.setVisibility(VISIBLE);WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;if(cb!=null)cb.onCustomViewHidden();schedulePicture();}
 boolean handleBack(){if(fullscreen!=null){hideFullscreen();return true;}long now=android.os.SystemClock.elapsedRealtime();if(now-lastBack<1500)return false;lastBack=now;web.evaluateJavascript("window.app&&window.app.back()",null);return true;}
 void prepareDetach(){pause();hideFullscreen();}
 void close(){if(closed)return;if(phone)carLaunch.cancel();if(!phone)CarController.detach(this);closed=true;active=false;timer.removeCallbacksAndMessages(null);requests.shutdownNow();controls.shutdownNow();views.remove(this);MediaCardService.forget(this);PlaybackService.release(this);hideFullscreen();proxy.clear();artwork.clear();removeView(web);web.removeJavascriptInterface("Android");web.destroy();Diagnostics.event(surface+" destroyed");}
}
