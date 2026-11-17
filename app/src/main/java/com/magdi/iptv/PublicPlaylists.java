package com.magdi.iptv;

import org.json.*;
import java.net.URI;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional remote directories; never reads or writes the user's saved subscription. */
final class PublicPlaylists {
 interface Loader { JSONObject load(String url) throws Exception; }
 private final Loader loader;
 private final Map<String,Set<String>> allowed = new ConcurrentHashMap<>();
 private final Map<String,Long> generations = new HashMap<>();
 PublicPlaylists(Loader loader) { this.loader=loader; }
 static String address(String id) {
  switch(id) {
   case "world": return "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8";
   case "egypt": return "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/eg.m3u";
   case "saudi": return "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/sa.m3u";
   default: throw new IllegalArgumentException("Unknown public playlist");
  }
 }
 JSONObject load(String id) throws Exception {
  String address=address(id);long generation;
  synchronized(this){generation=generations.getOrDefault(id,0L)+1;generations.put(id,generation);}
  JSONObject result=loader.load(address);
  JSONObject playlist=result.optJSONObject("playlist");
  JSONArray raw=playlist==null?null:playlist.optJSONArray("items");
  if(raw==null)throw new IOException("Public playlist unavailable");
  JSONArray items=new JSONArray();Set<String> urls=new HashSet<>();
  for(int i=0;i<raw.length();i++) {
   JSONObject item=raw.optJSONObject(i);if(item==null)continue;
   String url=item.optString("url").trim();
   if(!supported(url)||!urls.add(url))continue;
   item.put("url",url);items.put(item);
  }
  if(items.length()==0)throw new IOException("No supported channels in public playlist");
  // Publish only a complete successful snapshot; failed refreshes retain the last usable list.
  synchronized(this){
   if(generations.get(id)!=generation)throw new IOException("Public playlist request superseded");
   allowed.put(id,Collections.unmodifiableSet(urls));
  }
  return new JSONObject().put("playlist",new JSONObject().put("items",items));
 }
 String selected(String id,String url) {
  address(id);
  Set<String> urls=allowed.get(id);
  if(urls==null||!urls.contains(url))throw new IllegalArgumentException("Channel unavailable. Refresh the public playlist.");
  return url;
 }
 static boolean supported(String url) {
  try {
   URI u=URI.create(url);String host=u.getHost();
   if(!Arrays.asList("http","https").contains(u.getScheme())||host==null||u.getRawUserInfo()!=null||u.getFragment()!=null)return false;
   // These are web pages requiring a separate player, not direct media streams.
   host=host.toLowerCase(Locale.ROOT);
   if(host.equals("youtu.be")||host.equals("youtube.com")||host.endsWith(".youtube.com"))return false;
   return !u.getPath().toLowerCase(Locale.ROOT).endsWith(".mpd");
  } catch(IllegalArgumentException e) { return false; }
 }
}
