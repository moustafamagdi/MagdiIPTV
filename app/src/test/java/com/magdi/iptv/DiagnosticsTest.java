package com.magdi.iptv;

import android.app.Activity;
import android.content.*;
import android.os.Looper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public class DiagnosticsTest {
 private Activity activity;
 private void flush() throws Exception {java.lang.reflect.Field field=Diagnostics.class.getDeclaredField("io");field.setAccessible(true);((ExecutorService)field.get(null)).submit(()->{}).get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
 private File log(String name){return new File(activity.getFilesDir(),name);}
 @Before public void setup()throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();flush();Files.write(log("diagnostics.previous.txt").toPath(),"previous event\n".getBytes(StandardCharsets.UTF_8));Files.write(log("diagnostics.txt").toPath(),"current event\n".getBytes(StandardCharsets.UTF_8));}
 @After public void cleanup(){activity.finish();}
 @Test public void copyPlacesWholePreparedReportInClipboard()throws Exception{Diagnostics.copy(activity,true);flush();ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);assertTrue(clipboard.hasPrimaryClip());String text=clipboard.getPrimaryClip().getItemAt(0).getText().toString();assertEquals(Diagnostics.report(),text);assertTrue(text.contains("Device:"));assertTrue(text.contains("Installation source:"));assertTrue(text.contains("previous event\ncurrent event"));assertEquals("تم نسخ سجل التشخيص",org.robolectric.shadows.ShadowToast.getTextOfLatestToast());assertNull(shadowOf(activity).getNextStartedActivity());}
 @Test public void exportUsesSameReportAndReadOnlyShare()throws Exception{Diagnostics.export(activity,false);flush();File file=new File(activity.getCacheDir(),"reports/Magdi-IPTV-diagnostics.txt");assertEquals(Diagnostics.report(),new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));Intent chooser=shadowOf(activity).getNextStartedActivity();assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);assertEquals(Intent.ACTION_SEND,share.getAction());assertEquals("text/plain",share.getType());assertNotNull(share.getParcelableExtra(Intent.EXTRA_STREAM));assertNotEquals(0,share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);assertEquals(0,share.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
 @Test public void largeReportsKeepHeaderAndNewestEntriesWithinClipboardBudget()throws Exception{StringBuilder large=new StringBuilder();for(int i=0;i<20000;i++)large.append("تشغيل الفيديو test event ").append(i).append('\n');large.append("LATEST EVENT\n");Files.write(log("diagnostics.txt").toPath(),large.toString().getBytes(StandardCharsets.UTF_8));String report=Diagnostics.report();assertTrue(report.startsWith("Magdi IPTV"));assertTrue(report.contains("Older log entries omitted"));assertTrue(report.endsWith("LATEST EVENT\n"));assertTrue(report.length()<190000);assertFalse(report.contains("\ufffd"));}
}
