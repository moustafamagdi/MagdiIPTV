package com.magdi.iptv;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class Diagnostics extends Application {
 private static Context app;
 private static DiagnosticStore store;
 private static DiagnosticConnections connections;
 private static boolean migrationPending;
 private static final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"IPTVDiagnostics"));
 private static final SimpleDateFormat timestamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US);
 public void onCreate(){super.onCreate();app=this;io.execute(()->{
  try{store=new DiagnosticStore(getFilesDir());connections=new DiagnosticConnections();migrate();store.start("App",System.currentTimeMillis());}
  catch(IOException e){android.util.Log.e("MagdiIPTV","Unable to initialize diagnostic sessions");}
 });event("Process started; version="+version(getPackageName()));InstallSetup.observe(this,"process start");}
 static void event(String message){final long when=System.currentTimeMillis();io.execute(()->{
  String safe=DiagnosticPrivacy.clean(message);android.util.Log.i("MagdiIPTV",safe);
  try{if(store!=null)store.append(timestamp.format(new Date(when))+" "+safe+"\n",when);}catch(IOException e){android.util.Log.e("MagdiIPTV","Unable to append diagnostic event");}
 });}
 static void startSession(String kind){final long when=System.currentTimeMillis();io.execute(()->{try{if(store!=null){store.start(kind,when);store.append("Version: "+version(app.getPackageName())+"\n",when);}}catch(IOException e){android.util.Log.e("MagdiIPTV","Unable to start diagnostic session");}});}
 static void carConnected(){final long elapsed=android.os.SystemClock.elapsedRealtime(),when=System.currentTimeMillis();io.execute(()->{try{if(connections!=null&&connections.connected(elapsed)&&store!=null)store.start("Car",when);}catch(IOException e){android.util.Log.e("MagdiIPTV","Unable to start car log session");}});}
 static void carDisconnected(){final long elapsed=android.os.SystemClock.elapsedRealtime();io.execute(()->{if(connections!=null)connections.disconnected(elapsed);});}
 private static void migrate(){try{store.migrateLegacy(app.getFilesDir(),System.currentTimeMillis());migrationPending=false;}catch(IOException e){migrationPending=true;android.util.Log.e("MagdiIPTV","Legacy logs retained; migration will be retried");}}
 static String version(String pkg){try{return app.getPackageManager().getPackageInfo(pkg,0).versionName;}catch(Exception e){return "unavailable";}}
 static void open(Activity activity,boolean ar){activity.startActivity(new Intent(activity,DiagnosticsActivity.class).putExtra("language",ar?"ar":"en"));}
 static final class Part {
  final String name,session;final long bytes;final boolean current;
  Part(File file,String currentName){name=file.getName();session=name.substring(0,name.lastIndexOf("-part"));bytes=file.length();current=name.equals(currentName);}
 }
 static final class Listing {
  final List<Part> parts;final boolean migrationPending;
  Listing(List<Part> parts,boolean pending){this.parts=Collections.unmodifiableList(parts);migrationPending=pending;}
 }
 static final class Snapshot {
  final String name,text;final long capturedAt;final int bytes;
  Snapshot(String name,String text,long when,int bytes){this.name=name;this.text=text;capturedAt=when;this.bytes=bytes;}
 }
 private static void ui(Activity a,Runnable task){a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())task.run();});}
 static void list(Activity a,Consumer<Listing> done){io.execute(()->{List<Part> parts=new ArrayList<>();if(store!=null){if(migrationPending)migrate();store.prune(System.currentTimeMillis());String current=store.currentName();for(File file:store.list())parts.add(new Part(file,current));}Listing listing=new Listing(parts,migrationPending);ui(a,()->done.accept(listing));});}
 private static String header(String part,long when){return "MM IPTV "+version(app.getPackageName())+"\nSelected log part: "+part+"\nSnapshot captured: "+timestamp.format(new Date(when))+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT+"\nSecurity patch: "+Build.VERSION.SECURITY_PATCH+"\nAndroid Auto: "+version("com.google.android.projection.gearhead")+"\nWebView: "+(android.webkit.WebView.getCurrentWebViewPackage()==null?"unknown":android.webkit.WebView.getCurrentWebViewPackage().versionName)+"\nLanguage: "+Locale.getDefault().toLanguageTag()+"\n"+InstallSetup.report(app)+"App-only diagnostic events; not Android Auto/system logcat. Video samples are page observations, not proof of audible sound. No provider addresses, full stream URLs, titles, cookies, tokens or account credentials recorded.\nRetention: last 40 parts, up to 7 days; 128 KiB per part. Header describes the device at snapshot time. Copy and Export use this same complete selected-part snapshot; refresh for newer events. Exported files saved outside the app are not deleted by retention.\n\n";}
 static void load(Activity a,String name,Consumer<Snapshot> done){io.execute(()->{Snapshot result=null;try{if(store==null)throw new IOException();store.prune(System.currentTimeMillis());String selected=name==null?store.currentName():name;String body=store.read(selected);long when=System.currentTimeMillis();result=new Snapshot(selected,header(selected,when)+body,when,body.getBytes(StandardCharsets.UTF_8).length);}catch(Exception e){/* UI keeps actions disabled for expired/unavailable parts. */}Snapshot snapshot=result;ui(a,()->done.accept(snapshot));});}
 static void newSession(Activity a,Consumer<Boolean> done){final long when=System.currentTimeMillis();io.execute(()->{boolean ok=false;try{if(store!=null){store.start("Manual",when);store.append("Version: "+version(app.getPackageName())+"\n",when);ok=true;}}catch(IOException ignored){}boolean result=ok;ui(a,()->done.accept(result));});}
 static void copy(Activity a,Snapshot snapshot,boolean ar){if(snapshot==null)return;try{ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("MM IPTV diagnostics",snapshot.text));toast(a,ar?"تم نسخ الجزء المختار بالكامل":"Complete selected part copied");}catch(RuntimeException e){toast(a,ar?"تعذر النسخ؛ جرّب التصدير":"Copy failed; try Export");}}
 static void export(Activity a,Snapshot snapshot,boolean ar){if(snapshot==null)return;io.execute(()->{try{
  File dir=new File(app.getCacheDir(),"reports");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException();
  File[] old=dir.listFiles();if(old!=null)for(File f:old)if(f.getName().startsWith("MM-IPTV-log-")&&System.currentTimeMillis()-f.lastModified()>=DiagnosticStore.MAX_AGE_MS)f.delete();
  File output=new File(dir,"MM-IPTV-log-"+snapshot.name.replace(".txt","")+"-"+UUID.randomUUID().toString().substring(0,8)+".txt");
  try(OutputStream out=new FileOutputStream(output)){out.write(snapshot.text.getBytes(StandardCharsets.UTF_8));}
  ui(a,()->{try{android.net.Uri uri=android.support.v4.content.FileProvider.getUriForFile(a,a.getPackageName()+".reports",output);Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("Diagnostics",uri));a.startActivity(Intent.createChooser(share,ar?"تصدير الجزء المختار":"Export selected part"));}catch(RuntimeException e){toast(a,ar?"تعذر مشاركة الجزء":"Unable to share log part");}});
 }catch(IOException e){ui(a,()->toast(a,ar?"تعذر تجهيز الملف؛ حاول مجددًا":"Unable to prepare export; retry"));}});}
 private static void toast(Context c,String text){android.widget.Toast.makeText(c,text,android.widget.Toast.LENGTH_LONG).show();}
}
