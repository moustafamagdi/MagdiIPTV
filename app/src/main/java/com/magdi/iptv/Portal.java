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
 private final Context context;
 private final WebBackend backend=new WebBackend(new HttpFetcher(),"/api");
 private final StreamProxy streams;
 private final PublicPlaylists publicLists=new PublicPlaylists(this::loadPlaylist);
 Portal(Context c,StreamProxy p){context=c.getApplicationContext();store=new AccountStore(c);streams=p;}
 static String enc(String value){try{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
 static String validHost(String text){URI u=URI.create(text.trim());if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getRawUserInfo()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Enter a valid HTTP or HTTPS server address");return text.trim().replaceAll("/+$","");}
 // Scheme-less addresses are valid input; HTTPS is their first transport.
 static String xtreamHost(String text) {
  String address=text.trim();
  boolean explicit=address.regionMatches(true,0,"http://",0,7)
    ||address.regionMatches(true,0,"https://",0,8);
  if(!explicit){
   if(address.contains("://"))throw new IllegalArgumentException("Enter a valid HTTP or HTTPS server address");
   address="https://"+address;
  }
  else {
   int schemeEnd=address.indexOf(':');
   address=address.substring(0,schemeEnd).toLowerCase(Locale.ROOT)+address.substring(schemeEnd);
  }
  address=validHost(address);
  int port=URI.create(address).getPort();
  if(port==0||port>65535)throw new IllegalArgumentException("Enter a valid server port");
  return address;
 }
 static final String HTTP_RETRY="HTTPS connection failed. HTTP retry requires confirmation.";
 private String target(String url) throws Exception {WebBackend.Response r=backend.handle("POST","/provider-targets",Collections.emptyMap(),new JSONObject().put("url",url).toString());return new JSONObject(r.bodyText()).getString("targetId");}
 private Object xtream(JSONObject a,String action,JSONObject args) throws Exception {
  Map<String,List<String>> query=new LinkedHashMap<>();query.put("targetId",Collections.singletonList(target(xtreamHost(a.getString("host")))));query.put("username",Collections.singletonList(a.getString("username")));query.put("password",Collections.singletonList(a.getString("password")));
  if(!action.isEmpty())query.put("action",Collections.singletonList(action));
  else query.put("connectionTest",Collections.singletonList("true"));
  for(String k:new String[]{"category_id","vod_id","series_id","stream_id","limit"})if(args.has(k))query.put(k,Collections.singletonList(args.getString(k)));
  JSONObject result=new JSONObject(backend.handle("GET","/xtream",query,null).bodyText());
  if(result.has("connectionFailure"))throw ProviderConnectionFailure.from(result.getJSONObject("connectionFailure"));
  if(result.has("error"))throw new java.io.IOException("Provider request failed");
  Object payload=result.opt("payload");if(!(payload instanceof JSONObject)&&!(payload instanceof JSONArray))throw new java.io.IOException("Provider returned a web page instead of IPTV data. Check the address or network.");
  return payload;
 }
 Object call(String method,JSONObject args) throws Exception {
  if("downloads".equals(method))return new OfflineLibrary(context).list();
  if("downloadaction".equals(method))return new OfflineLibrary(context).action(args.getString("token"),args.getString("action"));
  if("downloadsettings".equals(method)){context.getSharedPreferences("iptv",0).edit().putBoolean("offlineMetered",args.getBoolean("metered")).apply();return true;}
  if("playprefs".equals(method)){if(args.has("fullscreen"))context.getSharedPreferences("iptv",0).edit().putBoolean("startFullscreen",args.getBoolean("fullscreen")).apply();return new JSONObject().put("fullscreen",context.getSharedPreferences("iptv",0).getBoolean("startFullscreen",true));}
  if("offlineplay".equals(method))return new OfflineLibrary(context).play(args.getString("id"));
  if("publicplaylist".equals(method))return publicLists.load(args.getString("preset"));
  if("publicplay".equals(method)){
   String url=publicLists.selected(args.getString("preset"),args.getString("url"));
   streams.clear();return streams.register(url);
  }
  if (Arrays.asList("config","save","forget","switchAccount","restoreAccount","rememberAccount").contains(method)) {
   synchronized (AccountStore.class) { return accountRequest(method,args); }
  }
  AccountProfiles profiles;
  synchronized (AccountStore.class) { profiles=new AccountProfiles(store.read()); }
  JSONObject a=profiles.current();
  switch(method){
   case "download": {
    if(!profiles.activeId().equals(args.getString("account"))||"m3u".equals(a.optString("mode")))throw new IllegalArgumentException();
    JSONArray selection=args.getJSONArray("items");if(selection.length()<1||selection.length()>200)throw new IllegalArgumentException();
    int added=0,skipped=0,failed=0;OfflineLibrary library=new OfflineLibrary(context);
    for(int n=0;n<selection.length();n++)try{
     JSONObject item=selection.getJSONObject(n);String type=item.getString("type"),id=item.getString("id"),ext=OfflineLibrary.extension(item.optString("ext","mp4"));
     if(!Arrays.asList("vod","episode").contains(type)||!id.matches("[0-9]+"))throw new IllegalArgumentException();
     String url=validHost(a.getString("host"))+"/"+(type.equals("vod")?"movie":"series")+"/"+enc(a.getString("username"))+"/"+enc(a.getString("password"))+"/"+id+"."+ext;
     if(library.enqueue(profiles.activeId(),url,item,args.optBoolean("metered",context.getSharedPreferences("iptv",0).getBoolean("offlineMetered",false))).getBoolean("added"))added++;else skipped++;
    }catch(Exception rejected){failed++;}
    return new JSONObject().put("added",added).put("skipped",skipped).put("failed",failed);
   }
   case "connect": return connect(a,profiles.activeId(),args);
   case "catalog": {
    String type=args.optString("type","live"),action;
    if(!Arrays.asList("live","vod","series").contains(type))throw new IllegalArgumentException("Invalid catalog");
    action="get_"+type+(args.optBoolean("categories")?"_categories":type.equals("series")?"":"_streams");
    return xtream(a,action,args);
   }
   case "details": {
    String id=args.getString("vod_id");if(!id.matches("[0-9]+"))throw new IllegalArgumentException("Invalid movie selection");
    JSONObject payload=(JSONObject)xtream(a,"get_vod_info",args),info=payload.optJSONObject("info"),safe=new JSONObject();
    if(info!=null)for(String field:new String[]{"movie_image","cover","plot","description","rating","releasedate","releaseDate","year","genre","cast","director","duration"})if(info.has(field))safe.put(field,info.get(field));
    return safe;
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
 private JSONObject connect(JSONObject account,String id,JSONObject args) throws Exception {
  if(account.length()==0)throw new IllegalArgumentException("Saved account was not found.");
  if(args.has("activeId")&&!id.equals(args.getString("activeId")))
   throw new IllegalArgumentException("Active account changed. Open settings again.");
  if("m3u".equals(account.optString("mode")))
   return new JSONObject().put("status","M3U").put("mode","m3u").put("source",sourceId(account));
  String host=xtreamHost(account.getString("host"));
  boolean secure=host.startsWith("https:");
  boolean approvedHttp=secure&&args.optBoolean("retryHttp");
  JSONObject candidate=account;
  if(approvedHttp)candidate=alternate(account,host,"http");
  Object payload;
  try {
   payload=xtream(candidate,"",new JSONObject());
  } catch(java.io.IOException failure) {
   if(approvedHttp)throw failure;
   if(failure instanceof ProviderConnectionFailure && !((ProviderConnectionFailure)failure).retryProtocol)throw failure;
   if(secure)throw new java.io.IOException(HTTP_RETRY,failure);
   candidate=alternate(account,host,"https");
   payload=xtream(candidate,"",new JSONObject());
  }
  JSONObject result=payload instanceof JSONObject?(JSONObject)payload:null;
  JSONObject user=result==null?null:result.optJSONObject("user_info");
  if(user==null||user.optInt("auth",0)!=1)throw new java.io.IOException("Login rejected. Check your username and password.");
  if(!"Active".equalsIgnoreCase(user.optString("status","Active")))throw new java.io.IOException("Subscription is not active");
  synchronized(AccountStore.class) {
   AccountProfiles current=new AccountProfiles(store.read());
   if(!id.equals(current.activeId())||!account.toString().equals(current.current().toString()))
    throw new IllegalArgumentException("Active account changed. Open settings again.");
   if(candidate!=account) {
    candidate.put("historySource",sourceId(account));
    current.save(id,false,candidate);
    store.save(current.encoded());
   }
  }
  return new JSONObject().put("status",user.optString("status","Active")).put("mode","xtream")
   .put("source",sourceId(candidate)).put("formats",user.optJSONArray("allowed_output_formats"))
   .put("expires",user.optString("exp_date"));
 }
 private static JSONObject alternate(JSONObject account,String host,String scheme) throws JSONException {
  return new JSONObject(account.toString()).put("host",scheme+host.substring(host.indexOf(':')));
 }
 static int connectionFailure(Exception error) {
  if(error instanceof ProviderConnectionFailure)return ((ProviderConnectionFailure)error).code;
  String message=error.getMessage();
  if(HTTP_RETRY.equals(message)&&error.getCause() instanceof Exception)return connectionFailure((Exception)error.getCause());
  if ("Provider returned a web page instead of IPTV data. Check the address or network.".equals(message)) return 1;
  if ("Login rejected. Check your username and password.".equals(message)) return 2;
  if ("Subscription is not active".equals(message)) return 3;
  if ("Provider request failed".equals(message)) return 4;
  if (error instanceof java.io.IOException) return 5;
  return 0;
 }
 private Object accountRequest(String method, JSONObject args) throws Exception {
  AccountProfiles book=new AccountProfiles(store.read());
  if ("rememberAccount".equals(method)) {
   if(book.remember(args.getString("activeId")))store.save(book.encoded());
   return true;
  }
  if ("restoreAccount".equals(method)) {
   if(book.restore()){store.save(book.encoded());streams.clear();return true;}
   return false;
  }
  JSONObject account=book.current();
  if ("config".equals(method)) {
   return new JSONObject().put("configured",account.length()>0).put("activeId",book.activeId())
    .put("profiles",book.labels()).put("label",account.optString("label"))
    .put("host",account.optString("host")).put("username",account.optString("username"))
    .put("mode",account.optString("mode","xtream")).put("playlist",account.optString("playlist"))
    .put("hasPassword",!account.optString("password").isEmpty());
  }
  if ("switchAccount".equals(method)) {
   book.select(args.getString("id"));
  } else if ("forget".equals(method)) {
   book.remove(args.getString("activeId"));
  } else {
   boolean adding=args.optBoolean("adding");
   String mode=args.optString("mode","xtream");
   String label=args.optString("label").trim();
   if (label.length()>60) throw new IllegalArgumentException("Account name is too long.");
   JSONObject next=new JSONObject().put("mode",mode).put("label",label);
   if ("m3u".equals(mode)) {
    next.put("playlist",validHost(args.getString("playlist")));
   } else if ("xtream".equals(mode)) {
    String host=xtreamHost(args.getString("host")),user=args.getString("username").trim();
    String password=args.optString("password");
    if (!adding && password.isEmpty() && host.equals(account.optString("host"))
        && user.equals(account.optString("username"))) password=account.optString("password");
    if (password.isEmpty()||user.isEmpty()) throw new IllegalArgumentException("Username and password are required");
    next.put("host",host).put("username",user).put("password",password);
    if(!adding&&host.equals(account.optString("host"))&&user.equals(account.optString("username"))&&account.has("historySource"))
     next.put("historySource",account.getString("historySource"));
   } else throw new IllegalArgumentException("Invalid account type.");
   book.save(args.getString("activeId"),adding,next);
  }
  store.save(book.encoded());
  streams.clear();
  return true;
 }
 private static String sourceId(JSONObject a) throws Exception {if(a.has("historySource"))return a.getString("historySource");byte[] data=java.security.MessageDigest.getInstance("SHA-256").digest((a.optString("host")+"|"+a.optString("username")+"|"+a.optString("playlist")).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(int i=0;i<8;i++)s.append(String.format(java.util.Locale.ROOT,"%02x",data[i]));return s.toString();}
}
