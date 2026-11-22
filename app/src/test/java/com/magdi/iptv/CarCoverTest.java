package com.magdi.iptv;

import android.app.Activity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,shadows={CarCoverTest.SdkLifecycle.class,CarCoverTest.SdkService.class})
public class CarCoverTest {
 @org.robolectric.annotation.Implements(com.google.android.apps.auto.sdk.CarActivity.class)
 public static class SdkLifecycle {
  @org.robolectric.annotation.Implementation protected void onStop(){}
  @org.robolectric.annotation.Implementation protected void onStart(){}
 }
 // The bundled SDK declares bind/unbind in this superclass, not CarActivityService.
 @org.robolectric.annotation.Implements(className="com.google.android.gms.car.d")
 public static class SdkService {
  static boolean reject;
  @org.robolectric.annotation.Implementation protected android.os.IBinder onBind(android.content.Intent intent){return reject?null:new android.os.Binder();}
  @org.robolectric.annotation.Implementation protected boolean onUnbind(android.content.Intent intent){return true;}
  @org.robolectric.annotation.Implementation protected void onRebind(android.content.Intent intent){}
  @org.robolectric.annotation.Implementation protected void onDestroy(){}
 }
 private Activity activity;private PlayerView page;private final IptvCarActivity host=new IptvCarActivity();private final Object service=new Object();
 @Before public void setup()throws Exception{
  SdkService.reject=false;
  CarPlaybackSession.connectionAvailable(service);
  activity=Robolectric.buildActivity(Activity.class).setup().get();page=CarPlaybackSession.attach(activity,host);page.resume();field("documentReady").setBoolean(page,true);CarController.pageReady(page);
  java.lang.reflect.Field f=IptvCarActivity.class.getDeclaredField("page");f.setAccessible(true);f.set(host,page);
 }
 @After public void cleanup(){CarPlaybackSession.connectionLost(service);CarPlaybackSession.detach(host);CarPlaybackSession.stopDetached();activity.finish();}
 private java.lang.reflect.Field field(String name)throws Exception{java.lang.reflect.Field f=PlayerView.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private WebView web()throws Exception{return (WebView)field("web").get(page);}
 private JSONObject action(String name)throws Exception{return new JSONObject().put("action",name).put("epoch",CarController.epoch()).put("request",CarController.request());}
 private void select()throws Exception{assertTrue(CarController.command(action("select").put("item",new JSONObject().put("id","1").put("type","live"))));}
 private JSONObject report(int sequence)throws Exception{return new JSONObject().put("epoch",CarController.epoch()).put("request",CarController.request()).put("sequence",sequence).put("item",new JSONObject().put("id","1").put("type","live")).put("phase","playing").put("playing",true).put("wanted",true).put("ready",4).put("time",12).put("canPause",true);}
 @Test public void hostStopPreservesLoadingSelection()throws Exception{
  assertTrue(CarController.command(new JSONObject().put("action","select").put("epoch",CarController.epoch()).put("item",new JSONObject().put("id","1").put("type","live"))));
  long e=CarController.epoch(),r=CarController.request();host.onStop();
  assertTrue("Temporarily covered car remains available",CarController.status().getBoolean("connected"));
  assertEquals(e,CarController.epoch());assertEquals(r,CarController.request());
  assertEquals("loading",CarController.status().getJSONObject("playback").getString("phase"));
 }
 @Test public void playingCoverKeepsPollingAndAcceptsProgressAndPause()throws Exception{
  select();assertEquals("accepted",CarController.report(page,report(1)));host.onStop();
  assertFalse(shadowOf(web()).wasOnPauseCalled());assertTrue(field("active").getBoolean(page));
  ((Runnable)field("sample").get(page)).run();
  assertTrue(shadowOf(web()).getLastEvaluatedJavascript().contains("window.app.state"));
  shadowOf(web()).getLastEvaluatedJavascriptCallback().onReceiveValue(JSONObject.quote(report(2).put("time",18).toString()));
  assertEquals(18,CarController.status().getJSONObject("playback").getInt("time"));
  assertSame(page,MediaCardService.target());assertTrue(CarController.command(action("pause")));
 }
 @Test public void fullscreenReturnPreservesDocumentRequestAndOverlay()throws Exception{
  select();int[] exits={0};java.lang.reflect.Method show=PlayerView.class.getDeclaredMethod("showFullscreen",View.class,WebChromeClient.CustomViewCallback.class);show.setAccessible(true);
  show.invoke(page,new View(activity),(WebChromeClient.CustomViewCallback)()->exits[0]++);
  long e=CarController.epoch(),r=CarController.request();String url=shadowOf(web()).getLastLoadedUrl();
  host.onStop();host.onStart();assertEquals(e,CarController.epoch());assertEquals(r,CarController.request());
  assertTrue(CarController.status().getBoolean("connected"));assertEquals(url,shadowOf(web()).getLastLoadedUrl());assertEquals(0,exits[0]);assertNotNull(page.findViewWithTag("native-fullscreen-exit"));
 }
 @Test public void hostReplacementPreservesRequestAndOldHostCannotStopIt()throws Exception{
  select();long r=CarController.request();CarPlaybackSession.detach(host);Object replacement=new Object();
  try{assertSame(page,CarPlaybackSession.attach(activity,replacement));page.resume();host.onStop();CarPlaybackSession.detach(host);
   assertTrue(CarController.status().getBoolean("connected"));assertEquals(r,CarController.request());
  }finally{CarPlaybackSession.connectionLost(service);CarPlaybackSession.detach(replacement);}
 }
 @Test public void disconnectDuringCoveredLoadCancelsAndReconnectDoesNotAutoplay()throws Exception{
  select();JSONObject late=report(1),pending=action("select").put("item",new JSONObject().put("id","2").put("type","live"));host.onStop();
  CarPlaybackSession.connectionLost(service);assertFalse(CarController.status().getBoolean("connected"));
  assertTrue(shadowOf(web()).getLastEvaluatedJavascript().contains("removeAttribute"));
  assertEquals("stale-request",CarController.report(page,late));assertFalse(CarController.command(pending));
  host.onStart();assertFalse(CarController.status().getBoolean("connected"));
  CarPlaybackSession.connectionAvailable(service);host.onStart();
  assertTrue(CarController.status().getBoolean("connected"));assertEquals("idle",CarController.status().getJSONObject("playback").getString("phase"));
  assertFalse(CarController.command(pending));assertEquals("stale-request",CarController.report(page,late));
 }
 @Test public void stopWhileCoveredInvalidatesLateReportAndResumeIntent()throws Exception{
  select();JSONObject late=report(1);host.onStop();android.webkit.ValueCallback<String> callback=shadowOf(web()).getLastEvaluatedJavascriptCallback();
  assertTrue(CarController.command(action("stop")));callback.onReceiveValue("true");
  assertFalse(field("pendingResume").getBoolean(page));assertEquals("stale-request",CarController.report(page,late));
  host.onStart();assertEquals("idle",CarController.status().getJSONObject("playback").getString("phase"));
 }
 @Test public void oldServiceDisconnectCannotCancelNewConnection()throws Exception{
  select();Object replacement=new Object();CarPlaybackSession.connectionAvailable(replacement);
  try{host.onStart();select();long r=CarController.request();CarPlaybackSession.connectionLost(service);
   assertTrue(CarController.status().getBoolean("connected"));assertEquals(r,CarController.request());
  }finally{CarPlaybackSession.connectionLost(replacement);}
 }
 @Test public void backgroundDisabledPausesWithoutLosingSelectionOrAutoResuming()throws Exception{
  select();activity.getSharedPreferences("iptv",0).edit().putBoolean("backgroundAudio",false).commit();long r=CarController.request();host.onStop();
  assertTrue(shadowOf(web()).wasOnPauseCalled());assertTrue(shadowOf(web()).getLastEvaluatedJavascript().contains("window.app.pause()"));
  assertFalse(field("pendingResume").getBoolean(page));assertEquals(r,CarController.request());
  host.onStart();assertEquals(r,CarController.request());assertFalse(field("pendingResume").getBoolean(page));
 }
 @Test public void closingPhoneDoesNotStopCoveredCar()throws Exception{
  select();host.onStop();long r=CarController.request();PlayerView phone=new PlayerView(activity);phone.resume();phone.pause();phone.close();
  assertTrue(CarController.status().getBoolean("connected"));assertEquals(r,CarController.request());
 }
 @Test public void projectionServiceBindUnbindAndRebindControlAvailability()throws Exception{
  IptvCarService first=new IptvCarService(),second=new IptvCarService();android.content.Intent intent=new android.content.Intent();
  try{
   assertNotNull(first.onBind(intent));host.onStart();select();host.onStop();first.onUnbind(intent);
   assertFalse(CarPlaybackSession.connected());assertFalse(CarController.status().getBoolean("connected"));
   first.onRebind(intent);host.onStart();assertTrue(CarController.status().getBoolean("connected"));
   assertEquals("idle",CarController.status().getJSONObject("playback").getString("phase"));
   assertNotNull(second.onBind(intent));host.onStart();select();long request=CarController.request();first.onDestroy();
   assertTrue(CarController.status().getBoolean("connected"));assertEquals(request,CarController.request());
  }finally{first.onDestroy();second.onDestroy();}
 }
 @Test public void rejectedProjectionBindDoesNotCreateConnection(){
  CarPlaybackSession.connectionLost(service);SdkService.reject=true;
  assertNull(new IptvCarService().onBind(new android.content.Intent()));assertFalse(CarPlaybackSession.connected());
 }
}
