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
 static PlayerView attach(Context car,Object token){
  Diagnostics.carConnected();
  if(page==null){context=new MutableContextWrapper(car);page=new PlayerView(context);Diagnostics.event("Car playback session created");}
  else {if(host!=token){if(connected())page.coverHost();else page.pause();}context.setBaseContext(car);Diagnostics.event("Car playback session reused");}
  if(page.getParent() instanceof ViewGroup)((ViewGroup)page.getParent()).removeView(page);
  host=token;return page;
 }
 static boolean isHost(Object token){return host==token;}
 static boolean connected(){return connection!=null;}
 static void connectionAvailable(Object service){
  if(connection==service)return;
  if(connection!=null&&page!=null)page.pause();
  connection=service;Diagnostics.carConnected();
 }
 static void connectionLost(Object service){
  if(connection!=service)return;
  connection=null;Diagnostics.carDisconnected();if(page!=null)page.pause();
  Diagnostics.event("Car service disconnected; playback cancelled");
 }
 static void detach(Object token){
  if(host!=token||page==null)return;
  if(connected())page.coverHost();else page.prepareDetach();
  if(page.getParent() instanceof ViewGroup)((ViewGroup)page.getParent()).removeView(page);
  Context app=context.getApplicationContext();context.setBaseContext(new ContextThemeWrapper(app,android.R.style.Theme_Material_NoActionBar));host=null;
  Diagnostics.event(connected()?"Car host covered/detached; connected session retained":"Car host detached; playback cancelled");
 }
 static void stopDetached(){if(host==null&&page!=null){PlayerView old=page;page=null;context=null;old.close();Diagnostics.event("Detached car session released");}}
}
