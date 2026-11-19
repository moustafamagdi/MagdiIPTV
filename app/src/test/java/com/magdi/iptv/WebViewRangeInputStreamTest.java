package com.magdi.iptv;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebViewRangeInputStreamTest {
 @Test public void webviewSeekDoesNotSkipTheHttpRangeTwice()throws Exception {
  InputStream stream=new WebViewRangeInputStream(new ByteArrayInputStream(new byte[]{40,41,42}),4000000);
  assertEquals(0,stream.available());assertEquals(4000000,stream.skip(4000000));assertEquals(40,stream.read());assertEquals(41,stream.read());
 }
 @Test public void chunkedSkipCreditsOnlyOriginalOffset()throws Exception {
  InputStream stream=new WebViewRangeInputStream(new ByteArrayInputStream(new byte[]{40,41,42}),4000000000L);
  assertEquals(2000000000L,stream.skip(2000000000L));assertEquals(2000000001L,stream.skip(2000000001L));assertEquals(41,stream.read());
 }
 @Test public void callerWithoutInitialSkipReadsCorrectRangeAndLaterSkipsNormally()throws Exception {
  InputStream stream=new WebViewRangeInputStream(new ByteArrayInputStream(new byte[]{40,41,42}),1000);
  byte[] b=new byte[1];assertEquals(1,stream.read(b));assertEquals(40,b[0]);assertEquals(1,stream.skip(1));assertEquals(42,stream.read());
 }
 @Test public void fullStreamAndNegativeSkipsAreUnchanged()throws Exception {
  InputStream stream=new WebViewRangeInputStream(new ByteArrayInputStream(new byte[]{0,1,2}),0);
  assertEquals(0,stream.skip(-1));assertEquals(1,stream.skip(1));assertEquals(1,stream.read());assertEquals(2,stream.read());assertEquals(-1,stream.read());
 }
}
