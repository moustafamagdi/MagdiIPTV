package com.magdi.iptv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.net.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class)
public class ProxyTest {
 @Test public void seekRangesRequireMatchingPartialResponses(){
  assertNull(StreamProxy.rangeFailure(206,"bytes=1000-","bytes 1000-1999/2000"));
  assertNull(StreamProxy.rangeFailure(206,"bytes=1000-1999","bytes 1000-1499/2000"));
  assertNull(StreamProxy.rangeFailure(200,"bytes=0-",null));
  assertEquals("ignored",StreamProxy.rangeFailure(200,"bytes=1000-",null));
  assertEquals("mismatch",StreamProxy.rangeFailure(206,"bytes=1000-","bytes 0-1999/2000"));
  assertEquals("mismatch",StreamProxy.rangeFailure(206,"bytes=1000-1500","bytes 1000-1999/2000"));
  assertEquals("invalid",StreamProxy.rangeFailure(206,"bytes=1000-",null));
  assertEquals("invalid",StreamProxy.rangeFailure(206,"bytes=1000-","bytes 1000-2000/2000"));
  assertEquals("invalid",StreamProxy.rangeFailure(206,"bytes=1000-","bytes 2000-1000/*"));
 }
 @Test public void tokenHidesCredentialsAndStablePerTarget() {
  StreamProxy p=new StreamProxy();String upstream="https://provider.example/live/test-user/test-password/123.ts";
  String token=p.register(upstream);assertTrue(token.startsWith(StreamProxy.ORIGIN+"/media/"));assertFalse(token.contains("test-user"));assertFalse(token.contains("test-password"));assertEquals(token,p.register(upstream));
 }
 @Test public void rewritesRelativeSegmentsEncryptionKeysAndVariantPlaylists() throws Exception {
  StreamProxy p=new StreamProxy();String source="#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key.bin?token=secret\"\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:5,\nsegment.ts\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nlow/index.m3u8\n";
  String output=p.rewrite(source,new URL("https://provider.example/a/b/master.m3u8"));
  assertFalse(output.contains("secret"));assertFalse(output.contains("provider.example"));assertFalse(output.contains("segment.ts"));assertTrue(output.contains("#EXTINF:5,"));assertEquals(4,output.split("https://app.magdi.local/media/",-1).length-1);
 }
 @Test public void expiredTokensCannotPlayPreviousAccount() {StreamProxy p=new StreamProxy();String token=p.register("https://provider.example/a.ts");p.clear();assertEquals(404,p.open(token.substring(token.lastIndexOf('/')+1),Collections.emptyMap()).getStatusCode());}
 @Test public void longLiveSessionEvictsOldSegmentsWithoutFailingOrLosingActiveManifest(){StreamProxy p=new StreamProxy();String manifest=p.register("https://provider.example/index.m3u8");for(int i=0;i<5000;i++){p.register("https://provider.example/"+i+".ts");assertEquals(manifest,p.register("https://provider.example/index.m3u8"));}}
 @Test public void localFileAndJavascriptAddressesAreRejected() {for(String url:new String[]{"file:///data/data/private","javascript:alert(1)","ftp://provider.example/file"}){try{new StreamProxy().register(url);fail(url);}catch(IllegalArgumentException expected){}}}
 @Test public void portalEncodesSpecialCredentialsAndRejectsInvalidHosts() {assertEquals("a%2Bb%20c%26%2F",Portal.enc("a+b c&/"));assertEquals("http://provider.example:80",Portal.validHost("http://provider.example:80/"));for(String h:new String[]{"file:///etc/passwd","provider.example","https://user:pass@provider.example"}){try{Portal.validHost(h);fail(h);}catch(IllegalArgumentException expected){}}}
 @Test public void httpRangeAndHtmlFailureArePreserved() throws Exception {
  java.net.ServerSocket server=new java.net.ServerSocket(0,2,java.net.InetAddress.getByName("127.0.0.1"));
  Thread worker=new Thread(()->{try{for(int i=0;i<2;i++){try(java.net.Socket socket=server.accept()){java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));String first=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}byte[] body=first.contains("/video")?new byte[]{2,3,4}:"<html>Blocked</html>".getBytes();String header=first.contains("/video")?"HTTP/1.1 206 Partial Content\r\nContent-Type: video/mp4\r\nContent-Range: bytes 2-4/10\r\n":"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n";socket.getOutputStream().write((header+"Content-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes());socket.getOutputStream().write(body);}}}catch(Exception ignored){}});worker.setDaemon(true);worker.start();
  try{StreamProxy p=new StreamProxy();String root="http://127.0.0.1:"+server.getLocalPort();String a=p.register(root+"/video");android.webkit.WebResourceResponse r=p.open(a.substring(a.lastIndexOf('/')+1),Collections.singletonMap("Range","bytes=2-4"));assertEquals(206,r.getStatusCode());assertEquals("bytes 2-4/10",r.getResponseHeaders().get("Content-Range"));assertEquals(2,r.getData().read());r.getData().close();String b=p.register(root+"/blocked");assertEquals(502,p.open(b.substring(b.lastIndexOf('/')+1),Collections.emptyMap()).getStatusCode());}finally{server.close();worker.join(1000);}
 }
 @Test public void xtreamSupportsProviderHostAndPortWithoutChangingExplicitHttps() {
  assertEquals("http://provider.example:2095",Portal.xtreamHost(" provider.example:2095 "));
  assertEquals("https://provider.example:2095",Portal.xtreamHost("https://provider.example:2095/"));
  assertThrows(IllegalArgumentException.class,()->Portal.xtreamHost("provider.example:65536"));
  assertThrows(IllegalArgumentException.class,()->Portal.xtreamHost("user@provider.example:2095"));
  assertThrows(IllegalArgumentException.class,()->Portal.validHost("provider.example:2095"));
 }
}
