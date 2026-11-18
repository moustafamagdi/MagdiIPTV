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
 @Test public void fullScreenBackRestoresViewAndCallbackExactlyOnce()throws Exception{int[] callbacks={0};java.lang.reflect.Method method=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);method.setAccessible(true);method.invoke(view,new View(activity),(WebChromeClient.CustomViewCallback)()->callbacks[0]++);assertEquals(View.GONE,web.getVisibility());assertTrue(view.handleBack());assertEquals(View.VISIBLE,web.getVisibility());assertEquals(1,callbacks[0]);}
 @Test public void carSessionReusesDocumentAndIgnoresOldHost(){Object first=new Object(),second=new Object();PlayerView car=CarPlaybackSession.attach(activity,first);CarPlaybackSession.detach(first);assertSame(car,CarPlaybackSession.attach(activity,second));CarPlaybackSession.detach(first);assertTrue(CarPlaybackSession.isHost(second));CarPlaybackSession.detach(second);CarPlaybackSession.stopDetached();}
 @Test public void phoneCannotTakeCarMediaControls(){MediaCardService.select(view);PlayerView phone=new PlayerView(activity);try{MediaCardService.select(phone);assertSame(view,MediaCardService.target());}finally{phone.close();}}
 @Test public void bundledPageAndLibrariesArePresent()throws Exception{for(String p:new String[]{"www/index.html","www/app.js","www/vendor/hls.min.js","www/vendor/mpegts.js","www/licenses.txt"})try(java.io.InputStream in=activity.getAssets().open(p)){assertTrue(in.read()!=-1);}}
}
