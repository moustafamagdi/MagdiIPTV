package com.magdi.iptv;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import android.graphics.*;
import android.webkit.WebResourceResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArtworkProxyTest {
 private byte[] picture(){Bitmap bitmap=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();return bytes.toByteArray();}
 private String token(String url){return url.substring(url.lastIndexOf('/')+1);}
 @Test public void onlyHttpArtworkWithoutUserInfoIsAccepted()throws Exception{ArtworkProxy p=new ArtworkProxy();for(String url:new String[]{"javascript:alert(1)","file:///private","data:image/png;base64,abc","ftp://example.com/a","https://user:pass@example.com/a"})try{p.register(url);fail();}catch(IOException expected){}String opaque=p.register("https://example.com/private/path.png?token=secret");assertFalse(opaque.contains("secret"));assertFalse(opaque.contains("example.com"));assertEquals(opaque,p.register("https://example.com/private/path.png?token=secret"));}
 @Test public void rasterIsResizedAndCannotServeHtmlOrVideo()throws Exception{byte[] png=ArtworkProxy.thumbnail(picture());Bitmap decoded=BitmapFactory.decodeByteArray(png,0,png.length);assertTrue(decoded.getWidth()<=768);assertTrue(decoded.getHeight()<=768);decoded.recycle();for(String payload:new String[]{"<html><script>alert(1)</script></html>","#EXTM3U\nvideo.ts","not a picture"})try{ArtworkProxy.thumbnail(payload.getBytes());fail();}catch(IOException expected){}}
 @Test public void artworkSurvivesStreamResetAndClearInvalidatesIt()throws Exception{ArtworkProxy p=new ArtworkProxy();String id=token(p.register("https://example.com/a.png"));new StreamProxy().clear();java.lang.reflect.Field targets=ArtworkProxy.class.getDeclaredField("targets");targets.setAccessible(true);assertTrue(((Map<?,?>)targets.get(p)).containsKey(id));p.clear();assertEquals(404,p.open(id).getStatusCode());}
 @Test public void successfulRasterIsCachedAndNeverForwardsCookiesOrAuthorization()throws Exception{
  byte[] body=picture();AtomicInteger requests=new AtomicInteger();List<String> headers=Collections.synchronizedList(new ArrayList<>());ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
  Thread worker=new Thread(()->{try{while(!server.isClosed())try(Socket socket=server.accept()){requests.incrementAndGet();BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));String line;while((line=reader.readLine())!=null&&!line.isEmpty())headers.add(line);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes());socket.getOutputStream().write(body);}}catch(IOException ignored){}});worker.setDaemon(true);worker.start();
  try{ArtworkProxy p=new ArtworkProxy();String id=token(p.register("http://127.0.0.1:"+server.getLocalPort()+"/poster.png"));WebResourceResponse first=p.open(id);assertEquals(200,first.getStatusCode());assertEquals("image/png",first.getMimeType());byte[] loaded=first.getData().readAllBytes();assertArrayEquals(loaded,p.open(id).getData().readAllBytes());assertEquals(1,requests.get());for(String h:headers){assertFalse(h.toLowerCase(Locale.ROOT).startsWith("authorization:"));assertFalse(h.toLowerCase(Locale.ROOT).startsWith("cookie:"));assertFalse(h.toLowerCase(Locale.ROOT).startsWith("referer:"));}}
  finally{server.close();worker.join(1000);}
 }
 @Test public void excessiveDownloadLengthIsRejected()throws Exception{ServerSocket server=new ServerSocket(0);Thread worker=new Thread(()->{try(Socket socket=server.accept()){BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));while(!"".equals(reader.readLine())){}socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+(ArtworkProxy.MAX_DOWNLOAD+1)+"\r\nConnection: close\r\n\r\n").getBytes());}catch(IOException ignored){}});worker.setDaemon(true);worker.start();try{try{ArtworkProxy.download(new URL("http://127.0.0.1:"+server.getLocalPort()+"/large"));fail();}catch(IOException expected){}}finally{server.close();worker.join(1000);}}
}
