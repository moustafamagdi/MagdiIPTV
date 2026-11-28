package com.magdi.iptv;
import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class PlaybackPreferencesTest {
 @Test public void fullscreenDefaultsOnAndExplicitChoiceSurvivesRecreation()throws Exception{
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("iptv",0).edit().remove("startFullscreen").commit();
  Portal p=new Portal(c,null);assertTrue(((JSONObject)p.call("playprefs",new JSONObject())).getBoolean("fullscreen"));
  p.call("playprefs",new JSONObject().put("fullscreen",false));
  assertFalse(((JSONObject)new Portal(c,null).call("playprefs",new JSONObject())).getBoolean("fullscreen"));
  p.call("playprefs",new JSONObject().put("fullscreen",true));
  assertTrue(((JSONObject)new Portal(c,null).call("playprefs",new JSONObject())).getBoolean("fullscreen"));
 }
}
