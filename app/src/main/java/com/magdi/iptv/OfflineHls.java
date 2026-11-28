package com.magdi.iptv;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Finite, unencrypted TS media playlists. Files stay separate for HLS timeline/seeking. */
final class OfflineHls {
 static final class Unsupported extends IOException { final String reason; Unsupported(String s){reason=s;} }
 static JSONObject parse(String text,String base,String token)throws Exception {
  if(!text.trim().startsWith("#EXTM3U")||!text.contains("#EXT-X-ENDLIST"))throw new Unsupported("hlsLive");
  JSONArray parts=new JSONArray();StringBuilder local=new StringBuilder();boolean duration=false;
  for(String line:text.split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;
   if(line.startsWith("#EXT-X-KEY")&&!line.equals("#EXT-X-KEY:METHOD=NONE"))throw new Unsupported("hlsEncrypted");
   if(line.startsWith("#EXT-X-STREAM-INF")||line.startsWith("#EXT-X-MEDIA:")||line.startsWith("#EXT-X-MAP")||line.startsWith("#EXT-X-BYTERANGE")||line.startsWith("#EXT-X-GAP")||line.startsWith("#EXT-X-DEFINE"))throw new Unsupported("hlsLayout");
   if(line.startsWith("#")){if(line.startsWith("#EXTINF:"))duration=true;if(line.equals("#EXTM3U")||line.equals("#EXT-X-ENDLIST")||line.equals("#EXT-X-DISCONTINUITY")||line.startsWith("#EXTINF:")||line.startsWith("#EXT-X-VERSION:")||line.startsWith("#EXT-X-TARGETDURATION:")||line.startsWith("#EXT-X-MEDIA-SEQUENCE:")||line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:")||line.startsWith("#EXT-X-PLAYLIST-TYPE:"))local.append(line).append('\n');continue;}
   if(!duration||parts.length()>=2000)throw new Unsupported("hlsLayout");duration=false;
   URI uri=new URI(base).resolve(line);if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new Unsupported("hlsLayout");
   local.append(StreamProxy.ORIGIN).append("/offline/").append(token).append('/').append(parts.length()).append('\n');parts.put(new JSONObject().put("url",uri.toString()));
  }
  if(parts.length()==0||duration)throw new Unsupported("hlsLayout");
  return new JSONObject().put("segments",parts).put("playlist",local.toString());
 }
 static File part(File dir,String token,int index){return new File(dir,token+"-"+index+".ts");}
 static void refresh(Context context,DownloadManager manager,File dir,JSONObject row)throws Exception {
  JSONArray parts=row.getJSONArray("segments");String token=row.getString("token");long bytes=0;int running=0,done=0;boolean failed=false;
  for(int n=0;n<parts.length();n++){JSONObject p=parts.getJSONObject(n);File f=part(dir,token,n);
   if(p.optBoolean("ready")){if(!f.isFile()||f.length()!=p.optLong("bytes")){row.put("state","missing");return;}done++;bytes+=f.length();continue;}
   if(!p.has("download"))continue;
   try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(p.getLong("download")))){
    if(c==null||!c.moveToFirst()){failed=true;continue;}int status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));bytes+=Math.max(0,c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
    if(status==DownloadManager.STATUS_SUCCESSFUL){long size=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));if(!f.isFile()||f.length()==0||size>=0&&f.length()!=size||!OfflineLibrary.validHeader(f,"ts")){row.put("reason","hlsSegment");failed=true;}else{p.put("ready",true).put("bytes",f.length());done++;}}
    else if(status==DownloadManager.STATUS_FAILED)failed=true;else running++;
   }
  }
  row.put("bytes",bytes).put("total",-1).put("segmentsDone",done).put("segmentsTotal",parts.length());
  if(failed){row.put("state","failed");return;}
  if(done==parts.length()){File output=new File(dir,token+".m3u8");Files.write(output.toPath(),row.getString("playlist").getBytes(StandardCharsets.UTF_8));row.put("state","ready").put("playlistBytes",output.length()).put("total",bytes);return;}
  for(int n=0;n<parts.length()&&running<4;n++){JSONObject p=parts.getJSONObject(n);if(p.has("download"))continue;
   DownloadManager.Request request=new DownloadManager.Request(Uri.parse(p.getString("url"))).setTitle("MM IPTV · "+row.getString("name"))
    .setDestinationUri(Uri.fromFile(part(dir,token,n))).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
    .setAllowedOverMetered(row.optBoolean("metered")).setAllowedOverRoaming(false).addRequestHeader("User-Agent","VLC/3.0.18 LibVLC/3.0.18");
   p.put("download",manager.enqueue(request));running++;
  }
  row.put("state","downloading");
 }
 static void remove(DownloadManager manager,File dir,JSONObject row){JSONArray parts=row.optJSONArray("segments");if(parts==null)return;for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p.has("download"))manager.remove(p.optLong("download"));part(dir,row.optString("token"),i).delete();}row.remove("segments");row.remove("playlist");row.remove("playlistBytes");row.remove("segmentsDone");row.remove("segmentsTotal");}
}
