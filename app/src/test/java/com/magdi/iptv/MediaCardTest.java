package com.magdi.iptv;
import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.webkit.WebView;
import android.support.v4.media.session.PlaybackStateCompat;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,shadows=MediaCardTest.CapturedSession.class)
public class MediaCardTest {
 @org.robolectric.annotation.Implements(android.media.session.MediaSession.class)
 public static class CapturedSession extends org.robolectric.shadows.ShadowMediaSession {
  static android.media.session.PlaybackState state;
  @org.robolectric.annotation.Implementation protected void setPlaybackState(android.media.session.PlaybackState value){state=value;}
 }
 private Activity activity;private PlayerView car,phone;private MediaCardService service;private int sequence;
 @Before public void setup()throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();car=new PlayerView(new ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar));phone=new PlayerView(activity);car.resume();CarController.pageReady(car);select();service=Robolectric.buildService(MediaCardService.class).create().get();}
 @After public void cleanup(){car.close();phone.close();service.onDestroy();activity.finish();}
 private void select()throws Exception{sequence=0;assertTrue(CarController.command(new JSONObject().put("action","select").put("epoch",CarController.epoch()).put("item",new JSONObject().put("id","1").put("type","vod"))));MediaCardService.select(car);}
 private JSONObject report(String phase)throws Exception{return new JSONObject().put("epoch",CarController.epoch()).put("request",CarController.request()).put("sequence",++sequence).put("item",new JSONObject().put("id","1").put("type","vod")).put("phase",phase).put("playing",phase.equals("playing")).put("wanted",true).put("ready",phase.equals("loading")?0:4).put("time",12).put("duration",120).put("canPause",phase.equals("playing")||phase.equals("buffering")).put("canPlay",phase.equals("paused")||phase.equals("ended")).put("seekable",phase.equals("playing")||phase.equals("paused"));}
 private void observe(JSONObject state)throws Exception{java.lang.reflect.Method method=PlayerView.class.getDeclaredMethod("observe",JSONObject.class);method.setAccessible(true);method.invoke(car,state);}
 private org.robolectric.shadows.ShadowWebView web(PlayerView p)throws Exception{java.lang.reflect.Field f=PlayerView.class.getDeclaredField("web");f.setAccessible(true);return shadowOf((WebView)f.get(p));}
 private PlaybackStateCompat state(){return PlaybackStateCompat.fromPlaybackState(CapturedSession.state);}
 @Test public void observationBindsAudioCardAutomaticallyWithoutOpeningAudioEntry()throws Exception{String before=web(car).getLastEvaluatedJavascript();observe(report("playing"));assertEquals(1,shadowOf(activity.getApplication()).getBoundServiceConnections().size());assertEquals(before,web(car).getLastEvaluatedJavascript());observe(report("playing"));assertEquals(1,shadowOf(activity.getApplication()).getBoundServiceConnections().size());assertNull(shadowOf(service.getSystemService(android.media.AudioManager.class)).getLastAudioFocusRequest());}
 @Test public void pauseAndPlayFollowObservedStateAndKeepBinding()throws Exception{observe(report("playing"));assertEquals(0,state().getActions()&PlaybackStateCompat.ACTION_PLAY);assertTrue((state().getActions()&PlaybackStateCompat.ACTION_PAUSE)!=0);service.command(false);assertTrue(web(car).getLastEvaluatedJavascript().contains("\"action\":\"pause\""));assertEquals(PlaybackStateCompat.STATE_PLAYING,state().getState());observe(report("paused"));assertEquals(PlaybackStateCompat.STATE_PAUSED,state().getState());assertEquals(0,state().getActions()&PlaybackStateCompat.ACTION_PAUSE);service.command(true);assertEquals(PlaybackStateCompat.STATE_PAUSED,state().getState());observe(report("playing"));assertEquals(PlaybackStateCompat.STATE_PLAYING,state().getState());assertEquals(1,shadowOf(activity.getApplication()).getBoundServiceConnections().size());}
 @Test public void bufferingEndedErrorAndUnsupportedSkipAreExplicit()throws Exception{observe(report("loading"));assertEquals(PlaybackStateCompat.STATE_BUFFERING,state().getState());observe(report("buffering"));assertEquals(PlaybackStateCompat.STATE_BUFFERING,state().getState());assertEquals(0f,state().getPlaybackSpeed(),0);observe(report("ended"));assertEquals(PlaybackStateCompat.STATE_STOPPED,state().getState());observe(report("error"));assertEquals(PlaybackStateCompat.STATE_ERROR,state().getState());assertEquals(0,state().getActions()&PlaybackStateCompat.ACTION_PLAY);String before=web(car).getLastEvaluatedJavascript();service.transport("next");assertEquals(before,web(car).getLastEvaluatedJavascript());}
 @Test public void stopAndReplacementDropOldStateAndCannotReplayIt()throws Exception{JSONObject late=report("playing");observe(late);service.transport("stop");assertNull(MediaCardService.target());assertTrue(shadowOf(activity.getApplication()).getUnboundServiceConnections().size()>0);assertEquals(0,state().getActions());observe(late);assertNull(MediaCardService.target());select();observe(report("playing"));select();String before=web(car).getLastEvaluatedJavascript();service.command(true);assertEquals(before,web(car).getLastEvaluatedJavascript());assertEquals(0,state().getActions());}
 @Test public void disconnectAndPhoneCannotStartOrRetainMediaCard()throws Exception{observe(report("playing"));car.pause();assertNull(MediaCardService.target());int binds=shadowOf(activity.getApplication()).getBoundServiceConnections().size();MediaCardService.observe(phone,true,false,true,1000);MediaCardService.select(phone);assertEquals(binds,shadowOf(activity.getApplication()).getBoundServiceConnections().size());assertEquals(0,state().getActions());}
 @Test public void progressExpiryClearsStaleControlsWithoutReloadingDocument()throws Exception{observe(report("playing"));String url=web(car).getLastLoadedUrl();shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(6501));assertEquals(0,state().getActions());assertNull(MediaCardService.target());assertTrue(shadowOf(activity.getApplication()).getUnboundServiceConnections().size()>0);assertEquals(url,web(car).getLastLoadedUrl());}
 @Test public void seekAndSkipOnlyUseObservedCapabilities()throws Exception{observe(report("playing").put("next",true));service.seek(60000);assertTrue(web(car).getLastEvaluatedJavascript().contains("\"percent\":50"));assertTrue((state().getActions()&PlaybackStateCompat.ACTION_SKIP_TO_NEXT)!=0);assertEquals(0,state().getActions()&PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);service.transport("next");assertTrue(web(car).getLastEvaluatedJavascript().contains("\"action\":\"next\""));}
 @Test public void untrustedBrowserRejected(){assertNull(service.onGetRoot("attacker",123456,null));assertNull(service.onGetRoot("com.google.android.projection.gearhead",123456,null));}
}
