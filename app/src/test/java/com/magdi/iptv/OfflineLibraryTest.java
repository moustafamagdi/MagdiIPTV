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
 @Test public void duplicateSelectionIsAccountScopedAndLiveIsRejected()throws Exception{
  JSONObject item=new JSONObject().put("id","123").put("type","vod").put("name","Movie").put("ext","mp4");
  assertTrue(library.enqueue("account-a","https://example.invalid/movie.mp4",item).getBoolean("added"));
  assertFalse(library.enqueue("account-a","https://example.invalid/movie.mp4",item).getBoolean("added"));
  assertTrue(library.enqueue("account-b","https://example.invalid/movie.mp4",item).getBoolean("added"));
  try{library.enqueue("account-a","https://example.invalid/live.mp4",item.put("type","live"));fail();}catch(IllegalArgumentException expected){}
  for(String unsupported:new String[]{"m3u8","mpd","../mp4","mkv"})try{OfflineLibrary.extension(unsupported);fail();}catch(IllegalArgumentException expected){}
 }
}
