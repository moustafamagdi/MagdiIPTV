package com.magdi.iptv;
import org.junit.Test;
import org.json.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class OfflineHlsTest {
 @Test public void rewritesFiniteSegmentsToPrivateLocalPaths()throws Exception {
  JSONObject value=OfflineHls.parse("#EXTM3U\n#EXTINF:2,\na.ts?secret=token\n#EXT-X-DISCONTINUITY\n#EXTINF:3,\n../b.ts\n#EXT-X-ENDLIST","https://example.invalid/path/index.m3u8","token");
  assertEquals(2,value.getJSONArray("segments").length());assertFalse(value.getString("playlist").contains("secret"));assertTrue(value.getString("playlist").contains("/offline/token/1"));assertTrue(value.getString("playlist").contains("#EXT-X-DISCONTINUITY"));assertEquals("https://example.invalid/b.ts",value.getJSONArray("segments").getJSONObject(1).getString("url"));
 }
 @Test public void rejectsLiveEncryptedAndUnsupportedLayouts()throws Exception {
  for(String content:new String[]{"#EXTM3U\n#EXTINF:2,\na.ts","#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=key\n#EXTINF:2,\na.ts\n#EXT-X-ENDLIST","#EXTM3U\n#EXT-X-MAP:URI=init.mp4\n#EXTINF:2,\na.m4s\n#EXT-X-ENDLIST","#EXTM3U\n#EXTINF:2,\nfile:///private\n#EXT-X-ENDLIST"})try{OfflineHls.parse(content,"https://example.invalid/index.m3u8","token");fail();}catch(OfflineHls.Unsupported expected){}
 }
}
