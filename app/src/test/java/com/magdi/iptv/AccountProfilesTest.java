package com.magdi.iptv;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class)
public class AccountProfilesTest {
 @Test public void legacyCredentialsSurviveAddSwitchAndRemove() throws Exception {
  JSONObject legacy=new JSONObject().put("host","https://old.example").put("password","synthetic-first");
  AccountProfiles profiles=new AccountProfiles(legacy);
  String original=profiles.activeId();
  profiles.save(original,true,new JSONObject().put("mode","m3u").put("playlist","https://new.example/list"));
  String second=profiles.activeId();
  assertNotEquals(original,second);
  profiles=new AccountProfiles(new JSONObject(profiles.encoded().toString()));
  assertEquals(2,profiles.labels().length());
  assertFalse(profiles.labels().toString().contains("synthetic-first"));
  profiles.select(original);
  assertEquals("synthetic-first",profiles.current().getString("password"));
  profiles.remove(original);
  assertEquals(second,profiles.activeId());
  assertEquals("m3u",profiles.current().getString("mode"));
 }
 @Test public void staleEditorCannotOverwriteSwitchedAccount() throws Exception {
  AccountProfiles profiles=new AccountProfiles(new JSONObject().put("mode","xtream"));
  String original=profiles.activeId();
  profiles.save(original,true,new JSONObject().put("mode","m3u"));
  assertThrows(IllegalArgumentException.class,()->profiles.save(original,false,new JSONObject()));
  assertEquals("m3u",profiles.current().getString("mode"));
 }
 @Test public void connectDiagnosticsRejectArbitraryText() throws Exception {
  JSONObject event=SupportPolicy.event("IPTV provider connect failed=1",123L);
  assertEquals("provider-connect",event.getString("kind"));
  assertEquals("failed",event.getString("state"));
  assertEquals(1,event.getInt("code"));
  assertNull(SupportPolicy.event("IPTV provider connect failed=https://unsafe.example",123L));
 }
 @Test public void startupRestoresLastConnectedRatherThanUnverifiedNewAccount() throws Exception {
  AccountProfiles book=new AccountProfiles(new JSONObject().put("mode","xtream"));
  String original=book.activeId();
  book.save(original,true,new JSONObject().put("mode","m3u"));
  String second=book.activeId();
  assertTrue(book.restore());
  assertEquals(original,book.activeId());
  book.select(second);
  assertTrue(book.remember(second));
  book.select(original);
  book=new AccountProfiles(new JSONObject(book.encoded().toString()));
  assertTrue(book.restore());
  assertEquals(second,book.activeId());
 }
}
