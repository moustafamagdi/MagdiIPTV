package com.magdi.iptv;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class)
public class PublicPlaylistsTest {
 private JSONObject list(String... urls) throws Exception {
  JSONArray items=new JSONArray();for(String url:urls)items.put(new JSONObject().put("name","Channel").put("url",url));
  return new JSONObject().put("playlist",new JSONObject().put("items",items));
 }
 private void rejects(Runnable action){try{action.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}}
 @Test public void presetCannotInjectArbitraryPlaylistAddress() throws Exception {
  AtomicInteger calls=new AtomicInteger();PublicPlaylists p=new PublicPlaylists(url->{calls.incrementAndGet();return list();});
  try{p.load("https://attacker.example/list.m3u");fail();}catch(IllegalArgumentException expected){}
  assertEquals(0,calls.get());assertTrue(PublicPlaylists.address("world").startsWith("https://raw.githubusercontent.com/Free-TV/"));
  assertTrue(PublicPlaylists.address("egypt").endsWith("/eg.m3u"));assertTrue(PublicPlaylists.address("saudi").endsWith("/sa.m3u"));
 }
 @Test public void filtersUnsupportedAndDuplicateEntries() throws Exception {
  PublicPlaylists p=new PublicPlaylists(url->list("https://tv.example/live.m3u8","https://tv.example/live.m3u8","file:///private","javascript:alert(1)","https://www.youtube.com/watch?v=test","https://tv.example/live.mpd","https://user:pass@tv.example/x","http://tv.example/live.ts"));
  assertEquals(2,p.load("world").getJSONObject("playlist").getJSONArray("items").length());
 }
 @Test public void playbackRequiresLoadedListAndExactMembership() throws Exception {
  PublicPlaylists p=new PublicPlaylists(url->list("https://tv.example/live.m3u8"));
  rejects(()->p.selected("world","https://tv.example/live.m3u8"));p.load("world");
  assertEquals("https://tv.example/live.m3u8",p.selected("world","https://tv.example/live.m3u8"));
  rejects(()->p.selected("egypt","https://tv.example/live.m3u8"));rejects(()->p.selected("world","https://tv.example/other.m3u8"));
 }
 @Test public void refreshReplacesRemovedChannels() throws Exception {
  AtomicInteger calls=new AtomicInteger();PublicPlaylists p=new PublicPlaylists(url->list("https://tv.example/"+calls.incrementAndGet()+".m3u8"));
  p.load("world");p.load("world");rejects(()->p.selected("world","https://tv.example/1.m3u8"));assertNotNull(p.selected("world","https://tv.example/2.m3u8"));
 }
 @Test public void failedRefreshPreservesUsableSnapshot() throws Exception {
  AtomicInteger calls=new AtomicInteger();PublicPlaylists p=new PublicPlaylists(url->{if(calls.incrementAndGet()>1)throw new IOException();return list("https://tv.example/live.m3u8");});
  p.load("world");try{p.load("world");fail();}catch(IOException expected){}assertNotNull(p.selected("world","https://tv.example/live.m3u8"));
 }
 @Test public void invalidOrEmptyListsDoNotReportSuccess() throws Exception {
  for(JSONObject result:new JSONObject[]{new JSONObject(),list(),list("file:///private")}){
   try{new PublicPlaylists(url->result).load("egypt");fail();}catch(IOException expected){}
  }
 }
 @Test public void lateResponseCannotReplaceNewerList() throws Exception {
  CountDownLatch firstStarted=new CountDownLatch(1),releaseFirst=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  PublicPlaylists p=new PublicPlaylists(url->{if(calls.incrementAndGet()==1){firstStarted.countDown();if(!releaseFirst.await(5,TimeUnit.SECONDS))throw new IOException();return list("https://tv.example/old.m3u8");}return list("https://tv.example/new.m3u8");});
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try{Future<?> first=pool.submit(()->{try{p.load("world");throw new AssertionError("Stale response accepted");}catch(IOException expected){}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(firstStarted.await(5,TimeUnit.SECONDS));p.load("world");releaseFirst.countDown();first.get(5,TimeUnit.SECONDS);
   assertNotNull(p.selected("world","https://tv.example/new.m3u8"));rejects(()->p.selected("world","https://tv.example/old.m3u8"));
  }finally{releaseFirst.countDown();pool.shutdownNow();}
 }
}
