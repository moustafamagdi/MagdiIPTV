package com.magdi.iptv;
import android.app.Activity;
import android.view.*;
import android.webkit.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public class PlayerLifecycleTest {
 private Activity activity;private PlayerView view;private WebView web;
 @Before public void setup()throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();view=new PlayerView(new ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar));activity.setContentView(view);java.lang.reflect.Field f=PlayerView.class.getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(view);}
 @After public void cleanup(){view.close();activity.finish();}
 @Test public void launchesLocalAssetsAndDisablesFileAccess(){assertEquals(StreamProxy.ORIGIN+"/",shadowOf(web).getLastLoadedUrl());assertFalse(web.getSettings().getAllowFileAccess());assertFalse(web.getSettings().getAllowContentAccess());assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,web.getSettings().getMixedContentMode());}
 @Test public void carHostPauseStopsPlayback(){view.resume();view.pause();assertTrue(shadowOf(web).wasOnPauseCalled());assertTrue(shadowOf(web).getLastEvaluatedJavascript().contains("removeAttribute"));}
 @Test public void manualPauseCancelsResumeIntent(){view.pauseMedia();String js=shadowOf(web).getLastEvaluatedJavascript();assertTrue(js.contains(".stop()"));assertTrue(js.contains("window.app.pause()"));}
 @Test public void foregroundServiceDoesNotTakeAudioFocusOrAutoPause(){String before=shadowOf(web).getLastEvaluatedJavascript();PlaybackService.keep(view);PlaybackService service=Robolectric.buildService(PlaybackService.class).create().get();try{service.onStartCommand(new android.content.Intent(),0,1);assertNull(shadowOf(service.getSystemService(android.media.AudioManager.class)).getLastAudioFocusRequest());assertEquals(before,shadowOf(web).getLastEvaluatedJavascript());service.onStartCommand(new android.content.Intent().setAction("stop"),0,2);assertTrue(shadowOf(web).getLastEvaluatedJavascript().contains("window.app.pause()"));}finally{service.onDestroy();}}
 @Test public void fullScreenBackRestoresViewAndCallbackExactlyOnce()throws Exception{int[] callbacks={0};java.lang.reflect.Method method=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);method.setAccessible(true);method.invoke(view,new View(activity),(WebChromeClient.CustomViewCallback)()->callbacks[0]++);assertEquals(View.INVISIBLE,web.getVisibility());assertTrue(view.handleBack());assertEquals(View.VISIBLE,web.getVisibility());assertEquals(1,callbacks[0]);}
 @Test public void carSessionReusesDocumentAndIgnoresOldHost(){Object first=new Object(),second=new Object();PlayerView car=CarPlaybackSession.attach(activity,first);CarPlaybackSession.detach(first);assertSame(car,CarPlaybackSession.attach(activity,second));CarPlaybackSession.detach(first);assertTrue(CarPlaybackSession.isHost(second));CarPlaybackSession.detach(second);CarPlaybackSession.stopDetached();}
 @Test public void phoneCannotTakeCarMediaControls(){MediaCardService.select(view);PlayerView phone=new PlayerView(activity);try{MediaCardService.select(phone);assertSame(view,MediaCardService.target());}finally{phone.close();}}
 @Test public void rejectedSampleReleasesOnlyItsOwnSlot()throws Exception{
  CarController.attach(view);long e=CarController.epoch(),r=CarController.request();java.lang.reflect.Field g=PlayerView.class.getDeclaredField("sampleGeneration"),slot=PlayerView.class.getDeclaredField("sampleInFlight");g.setAccessible(true);slot.setAccessible(true);java.lang.reflect.Method accept=PlayerView.class.getDeclaredMethod("acceptSample",long.class,long.class,long.class);accept.setAccessible(true);
  g.setLong(view,7);slot.setBoolean(view,true);assertEquals(false,accept.invoke(view,7L,e,r));assertFalse(slot.getBoolean(view));CarController.pageReady(view);g.setLong(view,8);slot.setBoolean(view,true);assertEquals(false,accept.invoke(view,7L,e,r));assertTrue(slot.getBoolean(view));assertEquals(true,accept.invoke(view,8L,e,r));assertFalse(slot.getBoolean(view));
 }
 @Test public void missingSampleRecoversAfterDeadlineWithoutAcceptingOldCallback()throws Exception{
  view.resume();CarController.pageReady(view);
  java.lang.reflect.Field slot=PlayerView.class.getDeclaredField("sampleInFlight"),started=PlayerView.class.getDeclaredField("sampleStarted"),generation=PlayerView.class.getDeclaredField("sampleGeneration"),sample=PlayerView.class.getDeclaredField("sample");
  for(java.lang.reflect.Field field:new java.lang.reflect.Field[]{slot,started,generation,sample})field.setAccessible(true);
  long old=generation.getLong(view);slot.setBoolean(view,true);started.setLong(view,android.os.SystemClock.elapsedRealtime()-6001);
  ((Runnable)sample.get(view)).run();assertTrue(generation.getLong(view)>old);assertEquals(android.os.SystemClock.elapsedRealtime(),started.getLong(view));
  java.lang.reflect.Method accept=PlayerView.class.getDeclaredMethod("acceptSample",long.class,long.class,long.class);accept.setAccessible(true);assertEquals(false,accept.invoke(view,old,CarController.epoch(),CarController.request()));
 }
 @Test public void mediaReplacementInvalidatesOutstandingSample()throws Exception{CarController.attach(view);CarController.pageReady(view);java.lang.reflect.Field g=PlayerView.class.getDeclaredField("sampleGeneration");g.setAccessible(true);long before=g.getLong(view);CarController.begin(view,new org.json.JSONObject().put("epoch",CarController.epoch()).put("request",CarController.request()).put("item",new org.json.JSONObject().put("id","1").put("type","live")));assertTrue(g.getLong(view)>before);}
 @Test public void fullscreenRetainsMeasuredWebViewport()throws Exception{activity.setContentView(view);view.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));view.layout(0,0,800,480);java.lang.reflect.Method show=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);show.setAccessible(true);show.invoke(view,new View(activity),(WebChromeClient.CustomViewCallback)()->{});view.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));view.layout(0,0,1024,600);assertEquals(1024,web.getMeasuredWidth());assertEquals(600,web.getMeasuredHeight());assertEquals(View.INVISIBLE,web.getVisibility());}
 @Test public void remoteFullscreenExitsNativeOverlayAndRestoresViewport()throws Exception{
  int[] callbacks={0};java.lang.reflect.Method show=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);show.setAccessible(true);show.invoke(view,new View(activity),(WebChromeClient.CustomViewCallback)()->callbacks[0]++);
  View exit=view.findViewWithTag("native-fullscreen-exit");assertNotNull(exit);assertTrue(exit instanceof android.widget.ImageButton);android.widget.FrameLayout.LayoutParams p=(android.widget.FrameLayout.LayoutParams)exit.getLayoutParams();assertEquals(Gravity.CENTER_VERTICAL|Gravity.LEFT,p.gravity);assertEquals(p.width,p.height);
  view.controllerCommand(new org.json.JSONObject().put("action","fullscreen"));assertEquals(1,callbacks[0]);assertEquals(View.VISIBLE,web.getVisibility());assertNull(view.findViewWithTag("native-fullscreen-exit"));assertFalse(shadowOf(web).getLastEvaluatedJavascript().contains("Controller.receive"));
 }
 @Test public void stopClosesNativeFullscreenExactlyOnce()throws Exception{
  int[] callbacks={0};java.lang.reflect.Method show=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);show.setAccessible(true);show.invoke(view,new View(activity),(WebChromeClient.CustomViewCallback)()->callbacks[0]++);view.controllerCommand(new org.json.JSONObject().put("action","stop"));assertEquals(1,callbacks[0]);assertEquals(View.VISIBLE,web.getVisibility());assertTrue(shadowOf(web).getLastEvaluatedJavascript().contains("Controller.receive"));
 }
 @Test public void bundledPageAndLibrariesArePresent()throws Exception{for(String p:new String[]{"www/index.html","www/app.js","www/vendor/hls.min.js","www/vendor/mpegts.js","www/licenses.txt"})try(java.io.InputStream in=activity.getAssets().open(p)){assertTrue(in.read()!=-1);}}
}
