package com.magdi.iptv;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.webkit.WebView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWebView;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,shadows=CarViewportTest.ScaleWebView.class)
public class CarViewportTest {
 @Implements(WebView.class)
 public static class ScaleWebView extends ShadowWebView {
  int initialScale;
  @Implementation protected void setInitialScale(int percent){initialScale=percent;}
 }
 private WebView web(PlayerView view)throws Exception{
  java.lang.reflect.Field field=PlayerView.class.getDeclaredField("web");field.setAccessible(true);return (WebView)field.get(view);
 }
 @Test @Config(qualifiers="xxhdpi") public void carUsesHostDensityEvenWhenPhoneDensityDiffers()throws Exception{
  Activity phone=Robolectric.buildActivity(Activity.class).setup().get();
  try {
   for(int dpi:new int[]{160,240,320}){
    Configuration config=new Configuration(phone.getResources().getConfiguration());config.densityDpi=dpi;
    PlayerView car=new PlayerView(new ContextThemeWrapper(phone.createConfigurationContext(config),android.R.style.Theme_Material_NoActionBar));
    try {assertEquals(dpi*100/160,((ScaleWebView)shadowOf(web(car))).initialScale);assertEquals(StreamProxy.ORIGIN+"/",shadowOf(web(car)).getLastLoadedUrl());}
    finally {car.close();}
   }
  }finally{phone.finish();}
 }
 @Test @Config(qualifiers="xxhdpi") public void phoneKeepsDefaultBrowserScaling()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();PlayerView phone=new PlayerView(activity);
  try{assertEquals(0,((ScaleWebView)shadowOf(web(phone))).initialScale);}
  finally{phone.close();activity.finish();}
 }
 @Test public void reconnectRebuildsButTemporaryCoverageRetainsPage()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();Object service=new Object(),first=new Object(),second=new Object(),third=new Object();
  try{
   CarPlaybackSession.connectionAvailable(service);PlayerView page=CarPlaybackSession.attach(activity,first);
   CarPlaybackSession.detach(first);assertSame(page,CarPlaybackSession.attach(activity,second));
   CarPlaybackSession.connectionLost(service);CarPlaybackSession.detach(second);
   Object newService=new Object();CarPlaybackSession.connectionAvailable(newService);
   PlayerView fresh=CarPlaybackSession.attach(activity,third);assertNotSame(page,fresh);
   assertEquals(StreamProxy.ORIGIN+"/",shadowOf(web(fresh)).getLastLoadedUrl());
   // Late disconnect from the old service cannot destroy the new session.
   CarPlaybackSession.connectionLost(service);assertSame(fresh,CarPlaybackSession.attach(activity,third));
   CarPlaybackSession.connectionLost(newService);
  }finally{CarPlaybackSession.detach(third);CarPlaybackSession.stopDetached();activity.finish();}
 }
}
