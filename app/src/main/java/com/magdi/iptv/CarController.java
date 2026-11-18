package com.magdi.iptv;

import org.json.*;

/** In-process, main-thread routing. No network listener, pending queue, or phone player. */
final class CarController {
 private static PlayerView target;
 private static long epoch;
 private static boolean ready;
 private static JSONObject playback=new JSONObject();
 static void attach(PlayerView view){
  if(!view.isCarView())return;
  if(target!=view){if(target!=null)detach(target);target=view;epoch++;playback=new JSONObject();}
  ready=view.documentReady();view.carConnection(epoch);
 }
 static void pageReady(PlayerView view){if(target==view){ready=true;view.carConnection(epoch);}}
 static boolean accepts(PlayerView view){return target==view&&ready;}
 static void detach(PlayerView view){
  if(target!=view)return;target=null;ready=false;epoch++;playback=new JSONObject();view.carConnection(0);
  MediaCardService.forget(view);PlaybackService.release(view);
 }
 static JSONObject status() throws JSONException {
  return new JSONObject().put("connected",target!=null&&ready).put("epoch",epoch).put("playback",playback);
 }
 static boolean command(JSONObject packet) throws JSONException {
  if(target==null||!ready||packet.optLong("epoch",-1)!=epoch)return false;
  if(!java.util.Arrays.asList("select","pause","resume","stop","next","previous","seek","mute","fullscreen","retry").contains(packet.optString("action")))throw new IllegalArgumentException("Unknown controller action");
  if(packet.toString().length()>100000)throw new IllegalArgumentException("Controller command too large");
  target.controllerCommand(packet);return true;
 }
 static void accountChanged(){if(target!=null)target.accountChanged();}
 static void report(PlayerView view,JSONObject state){
  if(accepts(view)&&state.optLong("epoch",-1)==epoch&&state.toString().length()<=20000)playback=state;
 }
}
