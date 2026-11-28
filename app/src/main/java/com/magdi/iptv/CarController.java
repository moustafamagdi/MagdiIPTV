package com.magdi.iptv;

import org.json.*;

/** Main-thread ownership of the local HTML player. No URL/document-fragment dependency. */
final class CarController {
 private static PlayerView target;
 private static long epoch,request,revision,lastSequence,lastReport;
 private static boolean ready;
 private static JSONObject selection,playback=new JSONObject();
 static long epoch(){return epoch;}
 static long request(){return request;}
 static void attach(PlayerView view){
  if(!view.isCarView())return;
  if(target!=view){if(target!=null)detach(target);target=view;epoch++;reset(null);}
  ready=view.documentReady();view.carConnection(epoch,request);
 }
 static void pageReady(PlayerView view){if(target==view){ready=true;revision++;view.carConnection(epoch,request);}}
 static void documentLoading(PlayerView view){if(target==view){detach(view);attach(view);}}
 static boolean accepts(PlayerView view){return target==view&&ready;}
 static boolean fresh(){return lastReport!=0&&android.os.SystemClock.elapsedRealtime()-lastReport<6500;}
 static boolean accepts(PlayerView view,long e,long r){return accepts(view)&&e==epoch&&r==request;}
 private static JSONObject item(JSONObject value)throws JSONException {return value==null?null:new JSONObject().put("id",value.optString("id")).put("type",value.optString("type")).put("name",value.optString("name").substring(0,Math.min(500,value.optString("name").length())));}
 private static void reset(JSONObject next){request++;revision++;lastSequence=0;lastReport=0;selection=next;playback=new JSONObject();try{playback.put("phase",next==null?"idle":"loading").put("item",next);}catch(JSONException ignored){}if(target!=null){target.controllerChanged();MediaCardService.forget(target);}}
 static void detach(PlayerView view){
  if(target!=view)return;target=null;ready=false;epoch++;reset(null);view.carConnection(0,request);
  MediaCardService.forget(view);PlaybackService.release(view);Diagnostics.event("Controller disconnected; pending requests cancelled");
 }
 static JSONObject status()throws JSONException {
  return new JSONObject().put("connected",target!=null&&ready).put("epoch",epoch).put("request",request).put("revision",revision).put("fresh",lastReport!=0&&android.os.SystemClock.elapsedRealtime()-lastReport<6500).put("playback",new JSONObject(playback.toString()));
 }
 static JSONObject begin(PlayerView view,JSONObject args)throws JSONException {
  if(!accepts(view,args.optLong("epoch",-1),args.optLong("request",-1)))throw new IllegalStateException("Obsolete media request");
  JSONObject next=args.getJSONObject("item");if(!java.util.Arrays.asList("live","vod","episode").contains(next.optString("type")))throw new IllegalArgumentException("Invalid media type");
  reset(item(next));return new JSONObject().put("epoch",epoch).put("request",request);
 }
 static boolean command(JSONObject packet)throws JSONException {
  if(target==null||!ready||packet.optLong("epoch",-1)!=epoch)return false;
  String action=packet.optString("action");
  if(!java.util.Arrays.asList("select","pause","resume","stop","next","previous","seek","seekBy","mute","fullscreen","fill","retry","restart").contains(action)||packet.toString().length()>100000)return false;
  if(!action.equals("select")&&!action.equals("stop")&&packet.optLong("request",-1)!=request)return false;
  boolean fresh=lastReport!=0&&android.os.SystemClock.elapsedRealtime()-lastReport<6500;
  String capability=action.equals("resume")?"canPlay":action.equals("pause")?"canPause":(action.equals("seek")||action.equals("seekBy"))?"seekable":action.equals("fullscreen")?"canFullscreen":action.equals("fill")?"canFill":action;
  if(java.util.Arrays.asList("resume","pause","seek","seekBy","next","previous","fullscreen","fill","mute").contains(action)&&(!fresh||!playback.optBoolean(capability)))return false;
  if(action.equals("fill")&&!(packet.opt("fill") instanceof Boolean))return false;
  if(action.equals("seek")&&!Double.isFinite(packet.optDouble("percent",Double.NaN)))return false;
  if(action.equals("seekBy")&&argsSecondsInvalid(packet))return false;
  if(action.equals("retry")&&selection==null)return false;
  if(action.equals("restart")&&(selection==null||selection.optString("type").equals("live")))return false;
  if(action.equals("select")){JSONObject next=packet.optJSONObject("item");if(next==null||!java.util.Arrays.asList("live","vod","episode").contains(next.optString("type")))return false;reset(item(next));}
  else if(action.equals("stop"))reset(null);
  else if(java.util.Arrays.asList("retry","restart","next","previous").contains(action))reset(selection);
  packet.put("request",request);target.controllerCommand(packet);return true;
 }
 private static boolean argsSecondsInvalid(JSONObject packet){double seconds=packet.optDouble("seconds",Double.NaN);return seconds!=10&&seconds!=-10;}
 static void accountChanged(){if(target!=null){reset(null);target.accountChanged(request);}}
 static String report(PlayerView view,JSONObject state){
  if(!accepts(view,state.optLong("epoch",-1),state.optLong("request",-1)))return "stale-request";
  long sequence=state.optLong("sequence",-1);if(sequence<=lastSequence)return "stale-sequence";
  JSONObject reported=state.optJSONObject("item");
  if(selection==null?reported!=null:reported==null||!selection.optString("id").equals(reported.optString("id"))||!selection.optString("type").equals(reported.optString("type")))return "media-mismatch";
  lastSequence=sequence;lastReport=android.os.SystemClock.elapsedRealtime();revision++;CarUiDiagnostics.player(state);playback=state;return "accepted";
 }
}
