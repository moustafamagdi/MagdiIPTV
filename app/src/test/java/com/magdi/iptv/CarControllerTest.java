package com.magdi.iptv;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.webkit.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public class CarControllerTest {
 private Activity activity;private PlayerView car,phone;
 @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup().get();car=new PlayerView(new ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar));phone=new PlayerView(activity);}
 @After public void cleanup(){car.close();phone.close();activity.finish();}
 private JSONObject command()throws Exception{return new JSONObject().put("action","select").put("epoch",CarController.status().getLong("epoch")).put("item",new JSONObject().put("id","1").put("type","live").put("name","Synthetic channel"));}
 private WebView web(PlayerView view)throws Exception{java.lang.reflect.Field f=PlayerView.class.getDeclaredField("web");f.setAccessible(true);return (WebView)f.get(view);}
 @Test public void restartRejectsLiveAndStaleRequestsAndCreatesNewVodLease()throws Exception{
  selected();assertFalse(CarController.command(action("restart")));
  JSONObject movie=command();movie.getJSONObject("item").put("type","vod");assertTrue(CarController.command(movie));
  JSONObject restart=action("restart");long before=CarController.request();assertTrue(CarController.command(restart));assertTrue(CarController.request()>before);
  assertFalse(CarController.command(new JSONObject(restart.toString()).put("request",before)));CarController.detach(car);assertFalse(CarController.command(action("restart")));
 }
 @Test public void missingCarRejectsCommandWithoutQueue()throws Exception{assertFalse(CarController.command(command()));CarController.attach(car);CarController.pageReady(car);assertFalse(shadowOf(web(car)).getLastEvaluatedJavascript().contains("Controller.receive"));}
 @Test public void connectedHostMustFinishInitializingBeforeCommands()throws Exception{CarController.attach(car);assertFalse(CarController.status().getBoolean("connected"));assertFalse(CarController.command(command()));CarController.pageReady(car);assertTrue(CarController.status().getBoolean("connected"));assertTrue(CarController.command(command()));assertTrue(shadowOf(web(car)).getLastEvaluatedJavascript().contains("Controller.receive"));}
 @Test public void disconnectedCommandsNeverReplayAfterReconnect()throws Exception{CarController.attach(car);CarController.pageReady(car);JSONObject old=command();CarController.detach(car);assertFalse(CarController.command(old));CarController.attach(car);CarController.pageReady(car);assertFalse(CarController.command(old));assertTrue(CarController.command(command()));}
 private JSONObject report()throws Exception{return new JSONObject().put("epoch",CarController.epoch()).put("request",CarController.request()).put("sequence",1).put("phase","playing").put("ready",4).put("canPause",true).put("item",command().getJSONObject("item"));}
 private JSONObject action(String action)throws Exception{return new JSONObject().put("action",action).put("epoch",CarController.epoch()).put("request",CarController.request());}
 private void selected()throws Exception{CarController.attach(car);CarController.pageReady(car);assertTrue(CarController.command(command()));}
 @Test public void lateReportFromOldSessionCannotRestorePlayback()throws Exception{selected();JSONObject old=report();assertEquals("accepted",CarController.report(car,old));CarController.detach(car);assertEquals("stale-request",CarController.report(car,old));CarController.attach(car);CarController.pageReady(car);assertEquals("stale-request",CarController.report(car,old));assertEquals("idle",CarController.status().getJSONObject("playback").getString("phase"));}
 @Test public void rapidABASelectionsRejectOldReportEvenWithSameItem()throws Exception{selected();JSONObject old=report();CarController.command(command().put("item",new JSONObject().put("id","2").put("type","live")));CarController.command(command());assertEquals("stale-request",CarController.report(car,old));assertEquals("accepted",CarController.report(car,report()));}
 @Test public void stopRejectsLateReportAndOldMediaRegistration()throws Exception{selected();JSONObject old=report();assertTrue(CarController.command(action("stop")));assertEquals("stale-request",CarController.report(car,old));try{CarController.begin(car,old);fail();}catch(IllegalStateException expected){}assertEquals("idle",CarController.status().getJSONObject("playback").getString("phase"));}
 @Test public void reportsRequireCorrectPlayerAndMonotonicSequence()throws Exception{selected();assertEquals("stale-request",CarController.report(phone,report()));JSONObject newer=report().put("sequence",5);assertEquals("accepted",CarController.report(car,newer));assertEquals("stale-sequence",CarController.report(car,report()));assertEquals("media-mismatch",CarController.report(car,report().put("sequence",6).put("item",new JSONObject().put("id","9").put("type","live"))));}
 @Test public void commandsRequireActualReadinessAndTransportCapabilities()throws Exception{selected();assertFalse(CarController.command(action("pause")));assertFalse(CarController.command(action("resume")));CarController.report(car,report());assertTrue(CarController.command(action("pause")));assertFalse(CarController.command(action("resume")));assertFalse(CarController.command(action("next")));assertFalse(CarController.command(action("seek").put("percent",20)));CarController.report(car,report().put("sequence",2).put("canPause",false).put("canPlay",true).put("seekable",true));assertTrue(CarController.command(action("resume")));assertFalse(CarController.command(action("pause")));assertTrue(CarController.command(action("seek").put("percent",20)));assertFalse(CarController.command(action("seek")));}
 @Test public void tenSecondCommandsRequireCurrentSeekablePlayerAndFixedDelta()throws Exception{
  selected();assertFalse(CarController.command(action("seekBy").put("seconds",10)));
  CarController.report(car,report().put("seekable",true));
  assertTrue(CarController.command(action("seekBy").put("seconds",10)));assertTrue(CarController.command(action("seekBy").put("seconds",-10)));
  assertFalse(CarController.command(action("seekBy").put("seconds",11)));assertFalse(CarController.command(action("seekBy")));
  JSONObject stale=action("seekBy").put("seconds",10);CarController.command(action("stop"));assertFalse(CarController.command(stale));
 }
 @Test public void phoneCannotBecomePlaybackTarget()throws Exception{CarController.attach(phone);CarController.pageReady(phone);assertFalse(CarController.status().getBoolean("connected"));MediaCardService.select(phone);assertNotSame(phone,MediaCardService.target());}
 @Test public void closingPhoneDoesNotDisconnectCar()throws Exception{CarController.attach(car);CarController.pageReady(car);phone.close();assertTrue(CarController.status().getBoolean("connected"));}
 @Test public void carPauseImmediatelyInvalidatesController()throws Exception{car.resume();CarController.pageReady(car);assertTrue(CarController.status().getBoolean("connected"));car.pause();assertFalse(CarController.status().getBoolean("connected"));assertTrue(shadowOf(web(car)).getLastEvaluatedJavascript().contains("removeAttribute"));}
 @Test public void serviceDisconnectInvalidatesRetainedCarHost()throws Exception{Object host=new Object(),service=new Object();CarPlaybackSession.connectionAvailable(service);PlayerView page=CarPlaybackSession.attach(activity,host);try{page.resume();CarController.pageReady(page);assertTrue(CarController.status().getBoolean("connected"));CarPlaybackSession.connectionLost(service);assertFalse(CarController.status().getBoolean("connected"));}finally{CarPlaybackSession.connectionLost(service);CarPlaybackSession.detach(host);CarPlaybackSession.stopDetached();}}
 @Test public void phoneCspAndProxyBothRejectMedia()throws Exception{
  WebView w=web(phone);WebViewClient client=shadowOf(w).getWebViewClient();
  WebResourceResponse page=client.shouldInterceptRequest(w,request("/"));assertTrue(page.getResponseHeaders().get("Content-Security-Policy").contains("media-src 'none'"));page.getData().close();
  WebResourceResponse media=client.shouldInterceptRequest(w,request("/media/test"));assertEquals(403,media.getStatusCode());media.getData().close();
 }
 private WebResourceRequest request(String path){return new WebResourceRequest(){public android.net.Uri getUrl(){return android.net.Uri.parse(StreamProxy.ORIGIN+path);}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return true;}public String getMethod(){return "GET";}public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}};}
}
