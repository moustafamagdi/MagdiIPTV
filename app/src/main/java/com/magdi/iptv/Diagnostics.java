package com.magdi.iptv;
import android.app.Application;
import android.content.*;
import android.os.Build;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
public final class Diagnostics extends Application {
 private static Context app;
 private static final ExecutorService io=Executors.newSingleThreadExecutor();
 public void onCreate(){super.onCreate();app=this;event("Process started; version="+version(getPackageName()));InstallSetup.observe(this,"process start");}
 static void event(String message){android.util.Log.i("MagdiIPTV",message);final String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+" "+message+"\n";io.execute(()->{try{File f=new File(app.getFilesDir(),"diagnostics.txt");if(f.length()>262144){File old=new File(app.getFilesDir(),"diagnostics.previous.txt");if(old.exists()&&!old.delete())return;if(!f.renameTo(old))return;}try(FileOutputStream out=new FileOutputStream(f,true)){out.write(line.getBytes("UTF-8"));}}catch(IOException ignored){}});}
 static String version(String pkg){try{return app.getPackageManager().getPackageInfo(pkg,0).versionName;}catch(Exception e){return "unavailable";}}
 // Both actions use the same bounded report, including device and installation details.
 // Keep the clipboard payload comfortably below Android's Binder transaction limit.
 static String report() throws IOException {
  String header="MM IPTV "+version(app.getPackageName())+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT+"\nSecurity patch: "+Build.VERSION.SECURITY_PATCH+"\nAndroid Auto: "+version("com.google.android.projection.gearhead")+"\nWebView: "+(android.webkit.WebView.getCurrentWebViewPackage()==null?"unknown":android.webkit.WebView.getCurrentWebViewPackage().versionName)+"\nLanguage: "+Locale.getDefault().toLanguageTag()+"\n"+InstallSetup.report(app)+"App-only diagnostic events; not Android Auto/system logcat. Video samples are page observations, not proof of audible sound. Blocked navigation logs include scheme and hostname only. No full URLs, titles, cookies or credentials recorded.\n\n";
  StringBuilder events=new StringBuilder();
  for(String name:new String[]{"diagnostics.previous.txt","diagnostics.txt"}){
   File file=new File(app.getFilesDir(),name);
   if(file.exists())try(Reader reader=new InputStreamReader(new FileInputStream(file),java.nio.charset.StandardCharsets.UTF_8)){char[] buffer=new char[8192];int count;while((count=reader.read(buffer))!=-1)events.append(buffer,0,count);}
  }
  if(events.length()>180000){int start=events.length()-180000;int newline=events.indexOf("\n",start);if(newline>=0&&newline<events.length()-1)start=newline+1;else if(Character.isLowSurrogate(events.charAt(start)))start++;return header+"[Older log entries omitted to keep the report within clipboard limits.]\n"+events.substring(start);}
  return header+events;
 }
 static void copy(android.app.Activity activity,boolean ar){deliver(activity,true,ar);}
 static void export(android.app.Activity activity,boolean ar){deliver(activity,false,ar);}
 private static void toast(android.app.Activity activity,String text){android.widget.Toast.makeText(activity,text,android.widget.Toast.LENGTH_LONG).show();}
 private static void deliver(android.app.Activity activity,boolean copy,boolean ar){
  InstallSetup.observe(activity,copy?"copy":"export");event(copy?"Copy requested":"Export requested");
  io.execute(()->{try{
   String text=report();File file=new File(app.getCacheDir(),"reports/Magdi-IPTV-diagnostics.txt");
   if(!copy){File directory=file.getParentFile();if(!directory.exists()&&!directory.mkdirs())throw new IOException();try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8)){writer.write(text);}}
   activity.runOnUiThread(()->{
    if(activity.isFinishing()||activity.isDestroyed())return;
    try{
     if(copy){ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("MM IPTV diagnostics",text));toast(activity,ar?"تم نسخ سجل التشخيص":"Diagnostics copied");}
     else{android.net.Uri uri=android.support.v4.content.FileProvider.getUriForFile(activity,activity.getPackageName()+".reports",file);Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("Diagnostics",uri));activity.startActivity(Intent.createChooser(share,ar?"تصدير سجل التشخيص":"MM IPTV diagnostics"));}
    }catch(RuntimeException e){toast(activity,copy?(ar?"تعذّر النسخ. جرّب تصدير السجل.":"Copy failed. Try exporting the log."):(ar?"تعذّر تصدير السجل. حاول مجددًا.":"Export failed. Please retry."));}
   });
  }catch(Exception e){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())toast(activity,ar?"تعذّر تجهيز السجل. حاول مجددًا.":"Unable to prepare diagnostics. Please retry.");});}});
 }
}
