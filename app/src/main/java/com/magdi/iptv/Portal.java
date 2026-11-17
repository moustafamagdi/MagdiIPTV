package com.magdi.iptv;

import android.content.Context;
import org.json.*;
import org.tvshell.iptvnator.backend.WebBackend;
import org.tvshell.iptvnator.backend.HttpFetcher;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Adapt IPTVnator's embedded backend without passing account credentials to the page. */
final class Portal {
 private final AccountStore store;
 private final WebBackend backend=new WebBackend(new HttpFetcher(),"/api");
 private final StreamProxy streams;
 private final PublicPlaylists publicLists=new PublicPlaylists(this::loadPlaylist);
 Portal(Context c,StreamProxy p){store=new AccountStore(c);streams=p;}
 static String enc(String value){try{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
 static String validHost(String text){URI u=URI.create(text.trim());if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getRawUserInfo()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Enter a valid HTTP or HTTPS server address");return text.trim().replaceAll("/+$","");}
 private String target(String url) throws Exception {WebBackend.Response r=backend.handle("POST","/provider-targets",Collections.emptyMap(),new JSONObject().put("url",url).toString());return new JSONObject(r.bodyText()).getString("targetId");}
 private Object xtream(JSONObject a,String action,JSONObject args) throws Exception {
  Map<String,List<String>> query=new LinkedHashMap<>();query.put("targetId",Collections.singletonList(target(validHost(a.getString("host")))));query.put("username",Collections.singletonList(a.getString("username")));query.put("password",Collections.singletonList(a.getString("password")));
  if(!action.isEmpty())query.put("action",Collections.singletonList(action));
  for(String k:new String[]{"category_id","series_id","stream_id","limit"})if(args.has(k))query.put(k,Collections.singletonList(args.getString(k)));
  JSONObject result=new JSONObject(backend.handle("GET","/xtream",query,null).bodyText());
  if(result.has("error"))throw new java.io.IOException("Provider request failed");
  Object payload=result.opt("payload");if(!(payload instanceof JSONObject)&&!(payload instanceof JSONArray))throw new java.io.IOException("Provider returned a web page instead of IPTV data. Check the address or network.");
  return payload;
 }
 Object call(String method,JSONObject args) throws Exception {
  if("publicplaylist".equals(method))return publicLists.load(args.getString("preset"));
  if("publicplay".equals(method)){
   String url=publicLists.selected(args.getString("preset"),args.getString("url"));
   streams.clear();return streams.register(url);
  }
  JSONObject a=store.read();
  switch(method){
   case "config":return new JSONObject().put("configured",a.length()>0).put("host",a.optString("host")).put("username",a.optString("username")).put("mode",a.optString("mode","xtream")).put("playlist",a.optString("playlist")).put("hasPassword",!a.optString("password").isEmpty());
   case "save": {
    String mode=args.optString("mode","xtream");JSONObject next=new JSONObject().put("mode",mode);
    if("m3u".equals(mode)){next.put("playlist",validHost(args.getString("playlist")));}
    else {next.put("host",validHost(args.getString("host"))).put("username",args.getString("username").trim());String password=args.optString("password");if(password.isEmpty()&&a.optString("host").equals(next.getString("host"))&&a.optString("username").equals(next.getString("username")))password=a.optString("password");if(password.isEmpty()||next.getString("username").isEmpty())throw new IllegalArgumentException("Username and password are required");next.put("password",password);}
    store.save(next);return true;
   }
   case "forget":store.clear();streams.clear();return true;
   case "connect": {
    if("m3u".equals(a.optString("mode")))return new JSONObject().put("status","M3U").put("mode","m3u").put("source",sourceId(a));
    JSONObject result=(JSONObject)xtream(a,"",new JSONObject());JSONObject user=result.optJSONObject("user_info");
    if(user==null||user.optInt("auth",0)!=1)throw new java.io.IOException("Login rejected. Check your username and password.");
    if(!"Active".equalsIgnoreCase(user.optString("status","Active")))throw new java.io.IOException("Subscription is not active");
    return new JSONObject().put("status",user.optString("status","Active")).put("mode","xtream").put("source",sourceId(a)).put("formats",user.optJSONArray("allowed_output_formats")).put("expires",user.optString("exp_date"));
   }
   case "catalog": {
    String type=args.optString("type","live"),action;
    if(!Arrays.asList("live","vod","series").contains(type))throw new IllegalArgumentException("Invalid catalog");
    action="get_"+type+(args.optBoolean("categories")?"_categories":type.equals("series")?"":"_streams");
    return xtream(a,action,args);
   }
   case "episodes":return xtream(a,"get_series_info",args);
   case "epg":return xtream(a,"get_short_epg",args);
   case "playlist": {
    return loadPlaylist(a.getString("playlist"));
   }
   case "play": {
    String url;
    if("m3u".equals(a.optString("mode")))url=validHost(args.getString("url"));
    else {
     String type=args.getString("type"),id=args.getString("id"),ext=args.optString("ext","m3u8");
     if(!Arrays.asList("live","movie","series").contains(type)||!id.matches("[0-9]+")||!ext.matches("[A-Za-z0-9]{1,8}"))throw new IllegalArgumentException("Invalid stream selection");
     url=validHost(a.getString("host"))+"/"+type+"/"+enc(a.getString("username"))+"/"+enc(a.getString("password"))+"/"+id+"."+ext;
    }
    streams.clear();return streams.register(url);
   }
   default:throw new IllegalArgumentException("Unknown request");
  }
 }
 private JSONObject loadPlaylist(String url) throws Exception {
  Map<String,List<String>> q=new HashMap<>();q.put("targetId",Collections.singletonList(target(url)));
  JSONObject result=new JSONObject(backend.handle("GET","/parse",q,null).bodyText());
  if(result.has("error"))throw new java.io.IOException("Unable to load playlist");return result;
 }
 private static String sourceId(JSONObject a) throws Exception {byte[] data=java.security.MessageDigest.getInstance("SHA-256").digest((a.optString("host")+"|"+a.optString("username")+"|"+a.optString("playlist")).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(int i=0;i<8;i++)s.append(String.format(java.util.Locale.ROOT,"%02x",data[i]));return s.toString();}
}
