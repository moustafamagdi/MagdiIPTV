package com.magdi.iptv;

import android.app.Application;
import android.content.Context;
import android.webkit.WebResourceResponse;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class OfflineLibraryTest {
 Context context;OfflineLibrary library;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("offline_library",0).edit().clear().commit();library=new OfflineLibrary(context);}
 @Test public void explicitNetworkConsentSurvivesRetryWithoutChangingDefaults()throws Exception{
  context.getSharedPreferences("iptv",0).edit().putBoolean("offlineMetered",false).commit();
  JSONObject item=new JSONObject().put("id","909").put("type","vod").put("name","Consent test").put("ext","mp4");
  library.enqueue("qa","https://example.invalid/test.mp4",item,true);
  JSONObject index=new JSONObject(context.getSharedPreferences("offline_library",0).getString("index","{}"));
  String token=index.keys().next();JSONObject row=index.getJSONObject(token);
  assertTrue(row.getBoolean("metered"));assertFalse(context.getSharedPreferences("iptv",0).getBoolean("offlineMetered",true));
  android.app.DownloadManager dm=(android.app.DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
  assertTrue(Shadows.shadowOf(Shadows.shadowOf(dm).getRequest(row.getLong("download"))).getAllowedOverMetered());
  library.action(token,"cancel");new OfflineLibrary(context).action(token,"retry");
  index=new JSONObject(context.getSharedPreferences("offline_library",0).getString("index","{}"));
  assertTrue(Shadows.shadowOf(Shadows.shadowOf(dm).getRequest(index.getJSONObject(token).getLong("download"))).getAllowedOverMetered());
  library.enqueue("qa","https://example.invalid/test.m3u8",item.put("id","910").put("ext","m3u8"),false);
  index=new JSONObject(context.getSharedPreferences("offline_library",0).getString("index","{}"));
  for(Iterator<String> it=index.keys();it.hasNext();){JSONObject entry=index.getJSONObject(it.next());if(entry.getString("ext").equals("m3u8")){assertFalse(entry.getBoolean("metered"));assertFalse(Shadows.shadowOf(Shadows.shadowOf(dm).getRequest(entry.getLong("download"))).getAllowedOverMetered());}}
 }
 @Test public void rangeSupportsSeekSuffixAndRejectsInvalidRanges(){
  assertArrayEquals(new long[]{20,99},OfflineLibrary.range("bytes=20-",100));
  assertArrayEquals(new long[]{90,99},OfflineLibrary.range("bytes=-10",100));
  assertArrayEquals(new long[]{0,99},OfflineLibrary.range("bytes=0-999",100));
  for(String bad:new String[]{"bytes=100-","bytes=20-10","bytes=-0","bytes=1-2,3-4","bytes=99999999999999999999999999-","bytes=-"})assertNull(OfflineLibrary.range(bad,100));
 }
 @Test public void headersRejectPlaylistsAndErrorPages()throws Exception{
  File file=File.createTempFile("offline-test",".mp4",context.getCacheDir());
  try{for(String invalid:new String[]{"<html>server error</html>","#EXTM3U\nhttps://secret/segment.ts"}){try(FileOutputStream out=new FileOutputStream(file)){out.write(invalid.getBytes());}assertFalse(OfflineLibrary.validHeader(file,"mp4"));}}finally{file.delete();}
 }
 @Test public void localPlaybackWorksAfterRecreationWithoutAccountOrServerAndSupportsRanges()throws Exception{
  String token=UUID.randomUUID().toString();File dir=new File(context.getExternalFilesDir(null),"offline");dir.mkdirs();File file=new File(dir,token+".mp4");
  byte[] bytes=new byte[100];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)i;
  try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
  JSONObject row=new JSONObject().put("token",token).put("name","Offline movie").put("type","vod").put("ext","mp4").put("state","ready").put("bytes",100).put("url","https://secret.invalid/user/password/video");
  context.getSharedPreferences("offline_library",0).edit().putString("index",new JSONObject().put(token,row).toString()).commit();
  try{OfflineLibrary restored=new OfflineLibrary(context);assertEquals(StreamProxy.ORIGIN+"/offline/"+token,restored.play(token));assertFalse(restored.list().toString().contains("secret"));
   WebResourceResponse response=restored.open(token,Collections.singletonMap("Range","bytes=20-29"));assertEquals(206,response.getStatusCode());assertEquals("bytes 20-29/100",response.getResponseHeaders().get("Content-Range"));
   try(InputStream in=response.getData()){for(int i=20;i<=29;i++)assertEquals(i,in.read());assertEquals(-1,in.read());}
   assertEquals(416,restored.open(token,Collections.singletonMap("Range","bytes=500-")).getStatusCode());
   assertTrue(restored.action(token,"delete"));assertFalse(file.exists());assertEquals(0,restored.list().getJSONArray("items").length());
  }finally{file.delete();}
 }
 @Test public void missingFilesNeverRemainReady()throws Exception{
  String token=UUID.randomUUID().toString();JSONObject row=new JSONObject().put("token",token).put("ext","mp4").put("state","ready").put("bytes",100);
  context.getSharedPreferences("offline_library",0).edit().putString("index",new JSONObject().put(token,row).toString()).commit();
  assertEquals("missing",library.list().getJSONArray("items").getJSONObject(0).getString("state"));
  try{library.play(token);fail();}catch(IOException expected){}
 }
 @Test public void mkvAndTsHeadersAndMimeTypesAreDistinct()throws Exception{
  File f=File.createTempFile("formats",".bin",context.getCacheDir());try{byte[] b=new byte[565];b[0]=0x47;b[188]=0x47;b[376]=0x47;java.nio.file.Files.write(f.toPath(),b);assertTrue(OfflineLibrary.validHeader(f,"ts"));assertFalse(OfflineLibrary.validHeader(f,"mkv"));b[0]=0x1a;b[1]=0x45;b[2]=(byte)0xdf;b[3]=(byte)0xa3;java.nio.file.Files.write(f.toPath(),b);assertTrue(OfflineLibrary.validHeader(f,"mkv"));assertFalse(OfflineLibrary.validHeader(f,"ts"));assertEquals("video/x-matroska",OfflineLibrary.mime("mkv"));assertEquals("video/mp2t",OfflineLibrary.mime("ts"));}finally{f.delete();}
 }
 @Test public void hlsBundleSurvivesRecreationAndDeletionRemovesSegments()throws Exception {
  String token=UUID.randomUUID().toString();File dir=new File(context.getExternalFilesDir(null),"offline");dir.mkdirs();File segment=OfflineHls.part(dir,token,0);java.nio.file.Files.write(segment.toPath(),new byte[565]);
  JSONObject row=new JSONObject().put("token",token).put("ext","m3u8").put("state","ready").put("playlist","#EXTM3U\n#EXTINF:1,\n"+StreamProxy.ORIGIN+"/offline/"+token+"/0\n#EXT-X-ENDLIST\n").put("segments",new JSONArray().put(new JSONObject().put("ready",true).put("bytes",565)));
  context.getSharedPreferences("offline_library",0).edit().putString("index",new JSONObject().put(token,row).toString()).commit();
  OfflineLibrary restored=new OfflineLibrary(context);assertEquals("ready",restored.list().getJSONArray("items").getJSONObject(0).getString("state"));WebResourceResponse local=restored.open(token+"/0",Collections.emptyMap());assertEquals(200,local.getStatusCode());local.getData().close();assertEquals(404,restored.open(token+"/../0",Collections.emptyMap()).getStatusCode());restored.action(token,"delete");assertFalse(segment.exists());assertFalse(new File(dir,token+".m3u8").exists());
 }
 @Test public void duplicateSelectionIsAccountScopedAndLiveIsRejected()throws Exception{
  JSONObject item=new JSONObject().put("id","123").put("type","vod").put("name","Movie").put("ext","mp4");
  assertTrue(library.enqueue("account-a","https://example.invalid/movie.mp4",item).getBoolean("added"));
  assertFalse(library.enqueue("account-a","https://example.invalid/movie.mp4",item).getBoolean("added"));
  assertTrue(library.enqueue("account-b","https://example.invalid/movie.mp4",item).getBoolean("added"));
  try{library.enqueue("account-a","https://example.invalid/live.mp4",item.put("type","live"));fail();}catch(IllegalArgumentException expected){}
  for(String unsupported:new String[]{"mpd","../mp4","avi"})try{OfflineLibrary.extension(unsupported);fail();}catch(IllegalArgumentException expected){}
 }
}

