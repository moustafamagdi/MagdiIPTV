package com.magdi.iptv;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;

/** Screen visibility is separate from the lifetime of the bound projection service. */
final class CarPlaybackSession {
 private static PlayerView page;
 private static MutableContextWrapper context;
 private static Object host,connection;
 private static boolean visible;
 private static boolean disconnectedPage;
 static int observationFlags(){return (connected()?1:0)|(visible()?2:0)|(page!=null&&page.hasWindowFocus()?4:0)|(page!=null&&page.getWindowVisibility()==android.view.View.VISIBLE?8:0)|(page!=null&&page.isAttachedToWindow()?16:0);}
 static boolean visible(){return connected()&&host!=null&&visible;}
 static void visibility(Object token,boolean value){if(host==token)visible=value;}
 static boolean show(android.content.Intent intent){if(!connected()||!(host instanceof IptvCarActivity))return false;((IptvCarActivity)host).startCarActivity(intent);return true;}
 static PlayerView attach(Context car,Object token){
  Diagnostics.carConnected();
  // Retain temporary covers, but do not reuse a renderer from a disconnected display.
  if(disconnectedPage&&page!=null){
   if(page.getParent() instanceof ViewGroup)((ViewGroup)page.getParent()).removeView(page);
   page.close();page=null;context=null;host=null;
   Diagnostics.event("Car display recreated after disconnection");
  }
  disconnectedPage=false;
  if(page==null){context=new MutableContextWrapper(car);page=new PlayerView(context);Diagnostics.event("Car playback session created");}
  else {if(host!=token){if(connected())page.coverHost();else page.pause();}context.setBaseContext(car);Diagnostics.event("Car playback session reused");}
  if(page.getParent() instanceof ViewGroup)((ViewGroup)page.getParent()).removeView(page);
  if(host!=token)visible=false;host=token;return page;
 }
 static boolean isHost(Object token){return host==token;}
 static boolean connected(){return connection!=null;}
 static void connectionAvailable(Object service){
  if(connection==service)return;
  if(connection!=null&&page!=null){page.pause();disconnectedPage=true;}
  if(connection!=service)visible=false;connection=service;Diagnostics.carConnected();
 }
 static void connectionLost(Object service){
  if(connection!=service)return;
  connection=null;visible=false;CarUiDiagnostics.reset();disconnectedPage=true;Diagnostics.carDisconnected();if(page!=null)page.pause();
  Diagnostics.event("Car service disconnected; playback cancelled");
 }
 static void detach(Object token){
  if(host!=token||page==null)return;
  if(connected())page.coverHost();else page.prepareDetach();
  if(page.getParent() instanceof ViewGroup)((ViewGroup)page.getParent()).removeView(page);
  Context app=context.getApplicationContext();context.setBaseContext(new ContextThemeWrapper(app,android.R.style.Theme_Material_NoActionBar));host=null;visible=false;
  Diagnostics.event(connected()?"Car host covered/detached; connected session retained":"Car host detached; playback cancelled");
 }
 static void stopDetached(){if(host==null&&page!=null){PlayerView old=page;page=null;context=null;old.close();Diagnostics.event("Detached car session released");}}
}
