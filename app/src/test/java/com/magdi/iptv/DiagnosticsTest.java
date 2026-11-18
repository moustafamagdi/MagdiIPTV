package com.magdi.iptv;
import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32)
public class DiagnosticsTest {
 private Activity activity;
 @Before public void setup()throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();drain();Diagnostics.startSession("Manual");drain();}
 @After public void cleanup(){activity.finish();}
 private ExecutorService queue()throws Exception{java.lang.reflect.Field f=Diagnostics.class.getDeclaredField("io");f.setAccessible(true);return (ExecutorService)f.get(null);}
 private void drain()throws Exception{for(int i=0;i<3;i++){queue().submit(()->{}).get(10,TimeUnit.SECONDS);shadowOf(android.os.Looper.getMainLooper()).idle();}}
 private Diagnostics.Snapshot snapshot(String name)throws Exception{AtomicReference<Diagnostics.Snapshot> result=new AtomicReference<>();Diagnostics.load(activity,name,result::set);drain();assertNotNull(result.get());return result.get();}
 private String copied(){return ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString();}
 @Test public void copyAndExportUseSameWholeSnapshotWhileRecordingContinues()throws Exception{
  Diagnostics.event("SELECTED_RECORD العربية 🚗");drain();Diagnostics.Snapshot selected=snapshot(null);Diagnostics.event("LATER_RECORD");drain();
  Diagnostics.copy(activity,selected,true);assertEquals(selected.text,copied());assertFalse(copied().contains("LATER_RECORD"));assertTrue(copied().contains("Device:"));assertTrue(copied().contains("Installation source:"));assertTrue(copied().contains("SELECTED_RECORD العربية 🚗"));
  Diagnostics.export(activity,selected,false);drain();Intent chooser=shadowOf(activity).getNextStartedActivity();Intent send=chooser.getParcelableExtra(Intent.EXTRA_INTENT);assertEquals(Intent.ACTION_SEND,send.getAction());assertEquals(0,send.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);assertNotEquals(0,send.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
  android.net.Uri uri=send.getParcelableExtra(Intent.EXTRA_STREAM);try(InputStream in=activity.getContentResolver().openInputStream(uri)){assertEquals(copied(),new String(in.readAllBytes(),StandardCharsets.UTF_8));}
  assertTrue(snapshot(null).text.contains("LATER_RECORD"));
 }
 @Test public void selectingEarlierSessionExcludesLaterEvents()throws Exception{Diagnostics.event("OLD_SELECTED");drain();String old=snapshot(null).name;Diagnostics.startSession("Manual");Diagnostics.event("NEW_UNSELECTED");drain();String text=snapshot(old).text;assertTrue(text.contains("OLD_SELECTED"));assertFalse(text.contains("NEW_UNSELECTED"));}
 @Test public void queuedEventsAndSessionSwitchKeepSubmissionOrder()throws Exception{for(int i=0;i<200;i++)Diagnostics.event("ORDER-"+i+";");drain();String old=snapshot(null).name;Diagnostics.startSession("Manual");Diagnostics.event("AFTER_SWITCH");drain();String body=snapshot(old).text;int previous=-1;for(int i=0;i<200;i++){int next=body.indexOf("ORDER-"+i+";");assertTrue(next>previous);previous=next;}assertFalse(body.contains("AFTER_SWITCH"));assertTrue(snapshot(null).text.contains("AFTER_SWITCH"));}
 @Test public void nearLimitPartIsCopiedWithoutTruncation()throws Exception{String event="تشغيل 🚗 "+"x".repeat(1800);for(int i=0;i<70;i++)Diagnostics.event("ROW-"+i+" "+event);drain();AtomicReference<Diagnostics.Listing> result=new AtomicReference<>();Diagnostics.list(activity,result::set);drain();assertTrue(result.get().parts.size()>=2);Diagnostics.Part part=result.get().parts.stream().max(Comparator.comparingLong(p->p.bytes)).get();assertTrue(part.bytes>120000);Diagnostics.Snapshot s=snapshot(part.name);Diagnostics.copy(activity,s,false);assertEquals(s.text,copied());assertFalse(copied().contains("omitted"));assertFalse(copied().contains("\ufffd"));}
 @Test public void sensitiveAccountDataNeverReachesLogOrClipboard()throws Exception{Diagnostics.event("Stream https://provider-private.example/live/private-user/private-pass/42.m3u8?token=secret-token");Diagnostics.event("username=private-user");Diagnostics.event("password=private-pass");Diagnostics.event("Authorization: Bearer secret-token");Diagnostics.event("host=provider-private.example");drain();Diagnostics.Snapshot s=snapshot(null);Diagnostics.copy(activity,s,false);for(String secret:new String[]{"provider-private.example","private-user","private-pass","secret-token"})assertFalse(copied().contains(secret));}
 @Test public void screenDefaultsToCurrentPartAndManualStartDoesNotPausePlayback()throws Exception{
  Diagnostics.event("BEFORE_SCREEN");drain();String before=snapshot(null).name;DiagnosticsActivity screen=Robolectric.buildActivity(DiagnosticsActivity.class,new Intent().putExtra("language","en")).setup().get();drain();
  try{List<Spinner> pickers=new ArrayList<>();collect(screen.getWindow().getDecorView(),Spinner.class,pickers);assertEquals(2,pickers.size());assertTrue(pickers.get(1).getSelectedItem().toString().contains("Current"));assertEquals(before,snapshot(null).name);
   Button start=findButton(screen,"Start new session");assertNotNull(start);start.performClick();drain();assertNotEquals(before,snapshot(null).name);assertTrue(snapshot(before).text.contains("BEFORE_SCREEN"));assertTrue(findButton(screen,"Copy selected part").isEnabled());
   assertNull(shadowOf(screen).getNextStartedService());
  }finally{screen.finish();}
 }
 @Test public void listAndLoadCallbacksReturnOnMainThread()throws Exception{AtomicReference<Thread> t=new AtomicReference<>();Diagnostics.list(activity,result->t.set(Thread.currentThread()));drain();assertEquals(android.os.Looper.getMainLooper().getThread(),t.get());Diagnostics.load(activity,null,result->t.set(Thread.currentThread()));drain();assertEquals(android.os.Looper.getMainLooper().getThread(),t.get());}
 @Test public void fileReadsAndWritesRunOnOneBackgroundThread()throws Exception{
  java.lang.reflect.Field field=Diagnostics.class.getDeclaredField("store");field.setAccessible(true);DiagnosticStore original=queue().submit(()->(DiagnosticStore)field.get(null)).get();
  List<Thread> touched=Collections.synchronizedList(new ArrayList<>());File root=new File(activity.getCacheDir(),"io-test-"+UUID.randomUUID());
  try{queue().submit(()->{try{DiagnosticStore tracking=new DiagnosticStore(root){
   @Override void append(String text,long now)throws IOException{touched.add(Thread.currentThread());super.append(text,now);}
   @Override List<File> list(){touched.add(Thread.currentThread());return super.list();}
   @Override String read(String name)throws IOException{touched.add(Thread.currentThread());return super.read(name);}
  };field.set(null,tracking);}catch(Exception e){throw new RuntimeException(e);}}).get();
   Diagnostics.newSession(activity,ok->assertTrue(ok));Diagnostics.event("BACKGROUND_IO");drain();Diagnostics.list(activity,result->assertFalse(result.parts.isEmpty()));drain();assertTrue(snapshot(null).text.contains("BACKGROUND_IO"));
   assertTrue(touched.size()>=5);assertEquals(1,new HashSet<>(touched).size());assertNotEquals(android.os.Looper.getMainLooper().getThread(),touched.get(0));
  }finally{queue().submit(()->{try{field.set(null,original);}catch(Exception e){throw new RuntimeException(e);}}).get();}
 }
 @Test public void manualSessionLeavesCarPlayerAndMediaTargetUntouched()throws Exception{
  PlayerView car=new PlayerView(new ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar));
  try{car.resume();MediaCardService.select(car);drain();java.lang.reflect.Field field=PlayerView.class.getDeclaredField("web");field.setAccessible(true);android.webkit.WebView web=(android.webkit.WebView)field.get(car);String before=shadowOf(web).getLastEvaluatedJavascript();
   Diagnostics.newSession(activity,ok->assertTrue(ok));drain();assertSame(car,MediaCardService.target());assertEquals(before,shadowOf(web).getLastEvaluatedJavascript());assertFalse(shadowOf(web).wasOnPauseCalled());
  }finally{car.close();}
 }
 private <T> void collect(View view,Class<T> type,List<T> out){if(type.isInstance(view))out.add(type.cast(view));if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),type,out);}}
 private Button findButton(Activity a,String text){List<Button> buttons=new ArrayList<>();collect(a.getWindow().getDecorView(),Button.class,buttons);return buttons.stream().filter(b->text.contentEquals(b.getText())).findFirst().orElse(null);}
}
