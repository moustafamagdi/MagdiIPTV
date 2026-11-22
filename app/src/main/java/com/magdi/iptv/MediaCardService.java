package com.magdi.iptv;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import java.lang.ref.WeakReference;
import java.util.*;

/** A control-only session: WebView remains the only player and audio-focus owner. */
public final class MediaCardService extends MediaBrowserServiceCompat {
 private static final String ROOT="magdi",CURRENT="current";
 private static final Map<PlayerView,State> observations=new WeakHashMap<>();
 private static WeakReference<PlayerView> selected=new WeakReference<>(null);
 private static WeakReference<MediaCardService> running=new WeakReference<>(null);
 private static final MediaCardBinding binding=new MediaCardBinding();
 private MediaSessionCompat session;
 private boolean available,metadataPublished;
 static final class State {
  final boolean playing,muted,ready;final long position;
  final long epoch=CarController.epoch(),request=CarController.request();
  final org.json.JSONObject observed;
  boolean valid(PlayerView view){return CarController.accepts(view,epoch,request)&&CarController.fresh();}
  State(boolean playing,boolean muted,boolean ready,long position){this.playing=playing;this.muted=muted;this.ready=ready;this.position=position;org.json.JSONObject state;try{state=CarController.status().getJSONObject("playback");}catch(org.json.JSONException e){state=new org.json.JSONObject();}observed=state;}
 }
 static void observe(PlayerView view,boolean playing,boolean muted,boolean ready,long position){
  if(!view.isCarView()||!CarController.accepts(view)||!CarController.fresh())return;
  State old=observations.put(view,new State(playing,muted,ready,position));PlayerView current=selected.get();
  // Muted feed previews must not take the car video's controls. Keep a paused target.
  if(ready&&!muted&&(current==null||(!current.isCarView()&&view.isCarView()))){selected=new WeakReference<>(view);Diagnostics.event("Media card target selected: "+(view.isCarView()?"car":"phone"));}
  if(selected.get()==view&&running.get()!=null&&(old==null||old.playing!=playing||old.ready!=ready))Diagnostics.event("Media card state reported: playing="+playing+"; ready="+ready+"; muted="+muted);
  if(selected.get()==view){binding.ensure(view.getContext());refresh();}
 }
 static void paused(PlayerView view){/* A command is not confirmation of playback state. */}
 static void select(PlayerView view){if(!view.isCarView()||!CarController.accepts(view))return;selected=new WeakReference<>(view);refresh();}
 static PlayerView target(){return selected.get();}
 static boolean retains(PlayerView view){return running.get()!=null&&selected.get()==view;}
 static void forget(PlayerView view){observations.remove(view);boolean chosen=selected.get()==view;if(chosen)selected=new WeakReference<>(null);refresh();if(chosen)binding.release();}
 private static void refresh(){MediaCardService service=running.get();if(service!=null)service.publish();}
 // This session only controls an already selected video; it has no searchable catalog.
 @android.annotation.SuppressLint("MissingOnPlayFromSearch")
 @Override public void onCreate(){super.onCreate();running=new WeakReference<>(this);session=new MediaSessionCompat(this,"MagdiIPTVAudio");
  session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS|MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
  session.setCallback(new MediaSessionCompat.Callback(){
   public void onPlay(){command(true);}
   public void onPause(){command(false);}
   public void onStop(){transport("stop");}
   public void onSkipToNext(){transport("next");}
   public void onSkipToPrevious(){transport("previous");}
   public void onSeekTo(long position){seek(position);}
   public void onPlayFromMediaId(String id,Bundle extras){if(CURRENT.equals(id))command(true);}
  });
  setSessionToken(session.getSessionToken());session.setActive(true);publish();Diagnostics.event("Media card service connected; no additional audio focus requested");
 }
 private boolean ar(){return getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ar");}
 @Override public BrowserRoot onGetRoot(String pkg,int uid,Bundle hints){
  boolean trusted=uid==android.os.Process.myUid();
  if(!trusted&&"com.google.android.projection.gearhead".equals(pkg))try{trusted=getPackageManager().getApplicationInfo(pkg,0).uid==uid;}catch(Exception ignored){}
  if(!trusted)return null;
  Diagnostics.event("Media card browser accepted");return new BrowserRoot(ROOT,null);
 }
 @Override public void onLoadChildren(String parent,Result<List<MediaBrowserCompat.MediaItem>> result){
  if(!ROOT.equals(parent)){result.sendResult(Collections.emptyList());return;}
  MediaDescriptionCompat description=new MediaDescriptionCompat.Builder().setMediaId(CURRENT).setTitle(ar()?"صوت الفيديو الحالي":"Current video audio").setSubtitle(available?(ar()?"تشغيل وإيقاف مؤقت":"Play and pause"):(ar()?"افتح MM IPTV واختر فيديو أولًا":"Open MM IPTV and choose a video first")).build();
  result.sendResult(Collections.singletonList(new MediaBrowserCompat.MediaItem(description,available?MediaBrowserCompat.MediaItem.FLAG_PLAYABLE:0)));
 }
 private void publish(){
  if(session==null)return;State state=observations.get(selected.get());boolean now=state!=null&&state.valid(selected.get());
  if(!metadataPublished||now!=available){metadataPublished=true;session.setMetadata(new MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,CURRENT).putString(MediaMetadataCompat.METADATA_KEY_TITLE,"MM IPTV").putString(MediaMetadataCompat.METADATA_KEY_ARTIST,now?(ar()?"صوت الفيديو الحالي":"Current video audio"):(ar()?"اختر فيديو في MM IPTV أولًا":"Choose a video in MM IPTV first")).build());}
  long actions=0;
  if(now){
   if(state.observed.optBoolean("canPlay"))actions|=PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
   if(state.observed.optBoolean("canPause"))actions|=PlaybackStateCompat.ACTION_PAUSE;
   if(actions!=0)actions|=PlaybackStateCompat.ACTION_PLAY_PAUSE;
   if(state.observed.optJSONObject("item")!=null)actions|=PlaybackStateCompat.ACTION_STOP;
   if(state.observed.optBoolean("next"))actions|=PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
   if(state.observed.optBoolean("previous"))actions|=PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
   if(state.observed.optBoolean("seekable"))actions|=PlaybackStateCompat.ACTION_SEEK_TO;
  }
  String phase=now?state.observed.optString("phase"):"";
  int playback=!now?PlaybackStateCompat.STATE_NONE:Arrays.asList("loading","buffering","seeking","recovering").contains(phase)?PlaybackStateCompat.STATE_BUFFERING:"error".equals(phase)?PlaybackStateCompat.STATE_ERROR:"ended".equals(phase)||"idle".equals(phase)?PlaybackStateCompat.STATE_STOPPED:state.playing?PlaybackStateCompat.STATE_PLAYING:PlaybackStateCompat.STATE_PAUSED;
  session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(playback,now?state.position:0,playback==PlaybackStateCompat.STATE_PLAYING?1f:0f).build());
  if(now!=available){available=now;notifyChildrenChanged(ROOT);}
 }
 void command(boolean play){
  transport(play?"resume":"pause");
 }
 void transport(String action){
  PlayerView view=selected.get();State state=observations.get(view);
  if(view==null||state==null||!state.valid(view)){Diagnostics.event("Media card command ignored: no current video");publish();return;}
  if(!Arrays.asList("resume","pause","stop","next","previous").contains(action))return;
  if(Arrays.asList("resume","next","previous").contains(action)&&getSystemService(android.media.AudioManager.class).getMode()!=android.media.AudioManager.MODE_NORMAL)return;
  try{boolean accepted=CarController.command(new org.json.JSONObject().put("action",action).put("epoch",state.epoch).put("request",state.request));Diagnostics.event(accepted?"Media card command accepted":"Media card command rejected");}catch(org.json.JSONException ignored){}
 }
 void seek(long position){
  State state=observations.get(selected.get());if(state==null||!state.valid(selected.get())||!state.observed.optBoolean("seekable"))return;
  double duration=state.observed.optDouble("duration",0);if(position<0||!Double.isFinite(duration)||duration<=0||position>duration*1000)return;
  try{CarController.command(new org.json.JSONObject().put("action","seek").put("epoch",state.epoch).put("request",state.request).put("percent",position/(duration*10)));}catch(org.json.JSONException ignored){}
 }
 @Override public void onDestroy(){if(running.get()==this)running=new WeakReference<>(null);if(session!=null){session.setActive(false);session.release();session=null;}Diagnostics.event("Media card service disconnected");super.onDestroy();}
}
