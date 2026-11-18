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
 @Test public void missingCarRejectsCommandWithoutQueue()throws Exception{assertFalse(CarController.command(command()));CarController.attach(car);CarController.pageReady(car);assertFalse(shadowOf(web(car)).getLastEvaluatedJavascript().contains("Controller.receive"));}
 @Test public void connectedHostMustFinishInitializingBeforeCommands()throws Exception{CarController.attach(car);assertFalse(CarController.status().getBoolean("connected"));assertFalse(CarController.command(command()));CarController.pageReady(car);assertTrue(CarController.status().getBoolean("connected"));assertTrue(CarController.command(command()));assertTrue(shadowOf(web(car)).getLastEvaluatedJavascript().contains("Controller.receive"));}
 @Test public void disconnectedCommandsNeverReplayAfterReconnect()throws Exception{CarController.attach(car);CarController.pageReady(car);JSONObject old=command();CarController.detach(car);assertFalse(CarController.command(old));CarController.attach(car);CarController.pageReady(car);assertFalse(CarController.command(old));assertTrue(CarController.command(command()));}
 @Test public void lateReportFromOldSessionCannotRestorePlayback()throws Exception{CarController.attach(car);CarController.pageReady(car);JSONObject report=new JSONObject().put("epoch",CarController.status().getLong("epoch")).put("phase","playing");CarController.report(car,report);assertEquals("playing",CarController.status().getJSONObject("playback").getString("phase"));CarController.detach(car);CarController.report(car,report);assertEquals(0,CarController.status().getJSONObject("playback").length());CarController.attach(car);CarController.pageReady(car);CarController.report(car,report);assertEquals(0,CarController.status().getJSONObject("playback").length());}
 @Test public void phoneCannotBecomePlaybackTarget()throws Exception{CarController.attach(phone);CarController.pageReady(phone);assertFalse(CarController.status().getBoolean("connected"));MediaCardService.select(phone);assertNotSame(phone,MediaCardService.target());}
 @Test public void closingPhoneDoesNotDisconnectCar()throws Exception{CarController.attach(car);CarController.pageReady(car);phone.close();assertTrue(CarController.status().getBoolean("connected"));}
 @Test public void carPauseImmediatelyInvalidatesController()throws Exception{car.resume();CarController.pageReady(car);assertTrue(CarController.status().getBoolean("connected"));car.pause();assertFalse(CarController.status().getBoolean("connected"));assertTrue(shadowOf(web(car)).getLastEvaluatedJavascript().contains("removeAttribute"));}
 @Test public void serviceDisconnectInvalidatesRetainedCarHost()throws Exception{Object host=new Object();PlayerView page=CarPlaybackSession.attach(activity,host);try{page.resume();CarController.pageReady(page);assertTrue(CarController.status().getBoolean("connected"));CarPlaybackSession.connectionLost();assertFalse(CarController.status().getBoolean("connected"));}finally{CarPlaybackSession.detach(host);CarPlaybackSession.stopDetached();}}
 @Test public void phoneCspAndProxyBothRejectMedia()throws Exception{
  WebView w=web(phone);WebViewClient client=shadowOf(w).getWebViewClient();
  WebResourceResponse page=client.shouldInterceptRequest(w,request("/"));assertTrue(page.getResponseHeaders().get("Content-Security-Policy").contains("media-src 'none'"));page.getData().close();
  WebResourceResponse media=client.shouldInterceptRequest(w,request("/media/test"));assertEquals(403,media.getStatusCode());media.getData().close();
 }
 private WebResourceRequest request(String path){return new WebResourceRequest(){public android.net.Uri getUrl(){return android.net.Uri.parse(StreamProxy.ORIGIN+path);}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return true;}public String getMethod(){return "GET";}public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}};}
}
