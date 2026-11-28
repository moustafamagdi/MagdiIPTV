package com.magdi.iptv;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import org.json.*;
import java.io.*;
import java.util.*;

/** Private durable catalogue. Android owns transfers; only complete validated files are playable. */
final class OfflineLibrary {
 static final Object LOCK=new Object();
 private final Context context;
 private final DownloadManager manager;
 OfflineLibrary(Context c){context=c.getApplicationContext();manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);}
 private File directory() throws IOException {File base=context.getExternalFilesDir(null);if(base==null)throw new IOException();File dir=new File(base,"offline");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException();return dir;}
 private JSONObject read() throws JSONException {return new JSONObject(context.getSharedPreferences("offline_library",0).getString("index","{}"));}
 private void save(JSONObject data) throws IOException {if(!context.getSharedPreferences("offline_library",0).edit().putString("index",data.toString()).commit())throw new IOException();}
 static String extension(String ext){String e=ext.toLowerCase(Locale.ROOT);if(!Arrays.asList("mp4","m4v","webm","mkv","ts","m3u8").contains(e))throw new IllegalArgumentException("Unsupported download format");return e;}
 private File file(JSONObject row) throws Exception {String id=row.getString("token");if(!id.matches("[a-f0-9-]{36}"))throw new IOException();return new File(directory(),id+"."+extension(row.getString("ext")));}
 JSONObject enqueue(String account,String url,JSONObject item) throws Exception {
  synchronized(LOCK){
   String ext=extension(item.optString("ext","mp4")),type=item.getString("type"),id=item.getString("id");
   if(!Arrays.asList("vod","episode").contains(type)||!id.matches("[0-9]+"))throw new IllegalArgumentException();
   JSONObject data=read();String key=account+":"+type+":"+id;
   for(Iterator<String> i=data.keys();i.hasNext();)if(key.equals(data.getJSONObject(i.next()).optString("key")))return new JSONObject().put("added",false);
   if(data.length()>=500||directory().getUsableSpace()<256L*1024*1024)throw new IOException();
   String token=UUID.randomUUID().toString();
   JSONObject row=new JSONObject().put("token",token).put("key",key).put("ext",ext).put("type",type)
    .put("name",shortText(item.optString("name","Video"))).put("group",shortText(item.optString("group","")))
    .put("groupKey",account+":"+item.optString("group",""))
    .put("url",url).put("metered",context.getSharedPreferences("iptv",0).getBoolean("offlineMetered",false)).put("state","queued").put("created",System.currentTimeMillis());
   // Persist intent before enqueue. A crash here leaves a retryable item, never a false completion.
   data.put(token,row);save(data);
   try{row.put("download",start(row));save(data);}catch(Exception failure){row.put("state","failed");save(data);throw failure;}
   return new JSONObject().put("added",true);
  }
 }
 private static String shortText(String text){return text.substring(0,Math.min(250,text.length()));}
 private long start(JSONObject row) throws Exception {
  Uri uri=Uri.parse(row.getString("url"));if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException();
  DownloadManager.Request request=new DownloadManager.Request(uri)
   .setTitle("MM IPTV · "+row.getString("name")).setDescription("Offline download / تنزيل للمشاهدة بدون إنترنت")
   .setDestinationUri(Uri.fromFile(file(row))).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setAllowedOverMetered(context.getSharedPreferences("iptv",0).getBoolean("offlineMetered",false))
   .setAllowedOverRoaming(false).addRequestHeader("User-Agent","VLC/3.0.18 LibVLC/3.0.18");
  return manager.enqueue(request);
 }
 JSONObject list() throws Exception {synchronized(LOCK){
  JSONObject data=read();JSONArray result=new JSONArray();long used=0;
  List<String> ids=new ArrayList<>();data.keys().forEachRemaining(ids::add);
  ids.sort((a,b)->Long.compare(data.optJSONObject(b).optLong("created"),data.optJSONObject(a).optLong("created")));
  for(String id:ids){JSONObject row=data.getJSONObject(id);refresh(row);File f=file(row);used+=f.exists()?f.length():0;if(row.has("segments")){for(int part=0;part<row.getJSONArray("segments").length();part++)used+=OfflineHls.part(directory(),id,part).length();}
   JSONObject safe=new JSONObject();for(String field:new String[]{"token","name","type","ext","group","groupKey","state","bytes","total","reason","segmentsDone","segmentsTotal"})if(row.has(field))safe.put(field,row.get(field));result.put(safe);
  }save(data);return new JSONObject().put("items",result).put("used",used).put("free",directory().getUsableSpace())
   .put("metered",context.getSharedPreferences("iptv",0).getBoolean("offlineMetered",false));
 }}
 private void refresh(JSONObject row) throws Exception {
  if(row.has("segments")){if(Arrays.asList("failed","missing","cancelled").contains(row.optString("state")))return;OfflineHls.refresh(context,manager,directory(),row);return;}
  if("ready".equals(row.optString("state"))){if(!file(row).isFile()||file(row).length()!=row.optLong("bytes"))row.put("state","missing");return;}
  if(Arrays.asList("invalid","cancelled","missing").contains(row.optString("state")))return;
  try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(row.optLong("download",-1)))){
   if(cursor==null||!cursor.moveToFirst()){row.put("state","failed");return;}
   long bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
   long total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
   row.put("bytes",Math.max(0,bytes)).put("total",total);
   int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
   if(status==DownloadManager.STATUS_SUCCESSFUL){File f=file(row);
    if("m3u8".equals(row.getString("ext"))){try{if(f.length()>2*1024*1024)throw new OfflineHls.Unsupported("hlsLayout");JSONObject parsed=OfflineHls.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8),row.getString("url"),row.getString("token"));row.put("segments",parsed.getJSONArray("segments")).put("playlist",parsed.getString("playlist"));OfflineHls.refresh(context,manager,directory(),row);}catch(OfflineHls.Unsupported unsupported){row.put("state","invalid").put("reason",unsupported.reason);}catch(Exception malformed){row.put("state","invalid").put("reason","hlsLayout");}return;}
boolean valid=f.isFile()&&f.length()>0&&(total<0||f.length()==total)&&validHeader(f,row.getString("ext"));
    row.put("state",valid?"ready":"invalid");if(valid)row.put("bytes",f.length());else {manager.remove(row.optLong("download",-1));if(f.exists())f.delete();}
   }else row.put("state",status==DownloadManager.STATUS_FAILED?"failed":status==DownloadManager.STATUS_PAUSED?"waiting":status==DownloadManager.STATUS_RUNNING?"downloading":"queued");
  }
 }
 static boolean validHeader(File f,String ext) throws IOException {try(InputStream in=new FileInputStream(f)){byte[] bytes=new byte[565];int count=0,n;while(count<bytes.length&&(n=in.read(bytes,count,bytes.length-count))>0)count+=n;if(count<12)return false;if("ts".equals(ext))return count>=377&&(bytes[0]&255)==0x47&&(bytes[188]&255)==0x47&&(bytes[376]&255)==0x47;if("webm".equals(ext)||"mkv".equals(ext))return (bytes[0]&255)==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3;return bytes[4]=='f'&&bytes[5]=='t'&&bytes[6]=='y'&&bytes[7]=='p';}}
 boolean action(String token,String action) throws Exception {synchronized(LOCK){JSONObject data=read();JSONObject row=data.optJSONObject(token);if(row==null)return false;
  if(!Arrays.asList("delete","cancel","retry").contains(action))throw new IllegalArgumentException();
  if("retry".equals(action)){refresh(row);if(Arrays.asList("ready","downloading","queued","waiting").contains(row.optString("state")))return false;}
  OfflineHls.remove(manager,directory(),row);row.remove("reason");manager.remove(row.optLong("download",-1));File f=file(row);if(f.exists()&&!f.delete())throw new IOException();
  if("delete".equals(action))data.remove(token);
  else {row.put("state","cancelled").remove("download");row.put("bytes",0);save(data);if("retry".equals(action)){row.put("download",start(row)).put("state","queued");}}
  save(data);return true;
 }}
 String play(String token) throws Exception {synchronized(LOCK){JSONObject data=read(),row=data.getJSONObject(token);refresh(row);save(data);if(!"ready".equals(row.optString("state")))throw new IOException();return StreamProxy.ORIGIN+"/offline/"+token;}}
 WebResourceResponse open(String token,Map<String,String> headers){try{
  File f;String ext;synchronized(LOCK){String[] path=token.split("/",-1);JSONObject row=read().getJSONObject(path[0]);if(!"ready".equals(row.optString("state")))return StreamProxy.reply(404,"text/plain","Not available");f=file(row);ext=row.getString("ext");if(row.has("segments")){if(path.length==2){int index=Integer.parseInt(path[1]);JSONObject segment=row.getJSONArray("segments").getJSONObject(index);f=OfflineHls.part(directory(),path[0],index);ext="ts";if(!segment.optBoolean("ready")||f.length()!=segment.optLong("bytes"))throw new IOException();}else if(path.length!=1||f.length()!=row.optLong("playlistBytes"))throw new IOException();}else if(path.length!=1||f.length()!=row.optLong("bytes"))throw new IOException();}
  long length=f.length();String requested=null;for(Map.Entry<String,String> h:headers.entrySet())if("Range".equalsIgnoreCase(h.getKey()))requested=h.getValue();
  long[] range=range(requested,length);if(range==null)return new WebResourceResponse("text/plain",null,416,"Range Not Satisfiable",Collections.singletonMap("Content-Range","bytes */"+length),new ByteArrayInputStream(new byte[0]));
  long start=range[0],end=range[1];RandomAccessFile input=new RandomAccessFile(f,"r");input.seek(start);
  InputStream stream=new InputStream(){long remaining=end-start+1;public int read()throws IOException{if(remaining<=0)return -1;int b=input.read();if(b>=0)remaining--;return b;}public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;if(remaining<=0)return -1;int n=input.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}public void close()throws IOException{input.close();}};
  Map<String,String> out=new HashMap<>();out.put("Accept-Ranges","bytes");out.put("Content-Length",Long.toString(end-start+1));out.put("Cache-Control","no-store");
  if(requested!=null)out.put("Content-Range","bytes "+start+"-"+end+"/"+length);
  return new WebResourceResponse(mime(ext),null,requested==null?200:206,requested==null?"OK":"Partial Content",out,new WebViewRangeInputStream(stream,start));
 }catch(Exception e){return StreamProxy.reply(404,"text/plain","Downloaded file unavailable");}}
 static String mime(String ext){switch(ext){case "mkv":return "video/x-matroska";case "ts":return "video/mp2t";case "m3u8":return "application/vnd.apple.mpegurl";case "webm":return "video/webm";default:return "video/mp4";}}
 static long[] range(String request,long length){try{if(length<=0)return null;if(request==null)return new long[]{0,length-1};java.util.regex.Matcher m=java.util.regex.Pattern.compile("bytes=([0-9]*)-([0-9]*)").matcher(request);if(!m.matches()||m.group(1).isEmpty()&&m.group(2).isEmpty())return null;long start,end;
  if(m.group(1).isEmpty()){long suffix=Long.parseLong(m.group(2));if(suffix<=0)return null;start=Math.max(0,length-suffix);end=length-1;}
  else {start=Long.parseLong(m.group(1));end=m.group(2).isEmpty()?length-1:Math.min(length-1,Long.parseLong(m.group(2)));}
  return start>=length||end<start?null:new long[]{start,end};
 }catch(NumberFormatException e){return null;}}
}
