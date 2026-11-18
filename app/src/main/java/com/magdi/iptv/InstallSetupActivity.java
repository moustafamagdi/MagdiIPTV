package com.magdi.iptv;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;

/** Phone-only setup; the APK is copied from our installed package, never downloaded. */
public final class InstallSetupActivity extends Activity {
 private TextView status; private Button install,king; private boolean ar,busy;
 protected void onCreate(Bundle b){super.onCreate(b);String language=getIntent().getStringExtra("language");ar="ar".equals(language==null?getResources().getConfiguration().getLocales().get(0).getLanguage():language);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad,pad,pad);
  panel.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(pad+bars.left,pad+bars.top,pad+bars.right,pad+bars.bottom);return insets;});
  ScrollView scroll=new ScrollView(this);scroll.addView(panel);setContentView(scroll);
  TextView title=new TextView(this);title.setText(ar?"King Installer • إعداد Android Auto":"King Installer • Android Auto setup");title.setTextSize(22);panel.addView(title);
  TextView help=new TextView(this);help.setText(ar?"طريقة King Installer الكلاسيكية مدمجة هنا. أعد تثبيت Magdi IPTV، ثم افتحه وافحص النتيجة. ستحتاج موافقة نافذة تثبيت أندرويد.\n\nللطرق المتقدمة يمكنك إرسال نفس النسخة إلى تطبيق King Installer الكامل، إذا كان مثبتًا.":"The classic King Installer method is built in. Reinstall Magdi IPTV, then reopen it and check the result. Android will ask you to confirm installation.\n\nFor advanced methods, send this version to the full King Installer app if installed.");help.setTextSize(16);panel.addView(help);
  status=new TextView(this);status.setTextSize(16);panel.addView(status);
  install=action(panel,ar?"إعادة التثبيت • King المدمج":"Reinstall • built-in King method",()->prepare(false));
  king=action(panel,"King Installer",()->{if(InstallSetup.kingAvailable(this)){new android.app.AlertDialog.Builder(this).setMessage(ar?"سيُرسل ملف التطبيق الحالي إلى King Installer. إذا كانت نسختك شخصية، فالملف يتضمن إعداد حسابك الأول.":"Send the current APK to King Installer. A personal APK includes its first-run account settings.").setNegativeButton(ar?"إلغاء":"Cancel",null).setPositiveButton(ar?"متابعة":"Continue",(d,w)->prepare(true)).show();}else{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(InstallSetup.KING_RELEASES)));}catch(RuntimeException e){fail(e);}}});
  action(panel,ar?"فحص النتيجة":"Check result",()->{InstallSetup.observe(this,"manual check");refresh();});
  action(panel,ar?"جلسات اللوج":"Log sessions",()->Diagnostics.open(this,ar));
  action(panel,ar?"رجوع":"Back",this::finish);
 }
 private Button action(LinearLayout panel,String label,Runnable run){Button b=new Button(this);b.setText(label);b.setTextSize(18);b.setAllCaps(false);b.setMinHeight((int)(60*getResources().getDisplayMetrics().density));panel.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->run.run());return b;}
 protected void onResume(){super.onResume();InstallSetup.observe(this,"setup opened");refresh();}
 private void refresh(){if(status!=null)status.setText(InstallSetup.summary(this,ar));if(king!=null)king.setText(InstallSetup.kingAvailable(this)?(ar?"إرسال النسخة إلى King Installer":"Send APK to King Installer"):(ar?"تحميل King Installer • الموقع الرسمي":"Get King Installer • official releases"));}
 private void prepare(boolean useKing){
  if(busy)return;
  if(!useKing&&!getPackageManager().canRequestPackageInstalls()){
   Diagnostics.event("Install setup: waiting for user to allow installs from this app");
   Toast.makeText(this,ar?"اسمح بالتثبيت من Magdi IPTV ثم ارجع واضغط إعادة تثبيت":"Allow installs from Magdi IPTV, then return and tap Reinstall",Toast.LENGTH_LONG).show();
   try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){fail(e);}return;
  }
  busy=true;install.setEnabled(false);king.setEnabled(false);
  final Context app=getApplicationContext();
  new Thread(()->{try{
   if(app.getApplicationInfo().splitSourceDirs!=null&&app.getApplicationInfo().splitSourceDirs.length>0)throw new IOException("Split APK unsupported");
   File dir=new File(app.getCacheDir(),"installer");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create cache");
   File apk=new File(dir,"Magdi-IPTV.apk");
   try(InputStream in=new FileInputStream(app.getApplicationInfo().sourceDir);OutputStream out=new FileOutputStream(apk)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
   Uri uri=android.support.v4.content.FileProvider.getUriForFile(app,getPackageName()+".reports",apk);
   runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;install.setEnabled(true);king.setEnabled(true);launch(uri,useKing);});
  }catch(Exception e){runOnUiThread(()->{busy=false;if(!isFinishing()&&!isDestroyed()){install.setEnabled(true);king.setEnabled(true);fail(e);}});}},"Magdi-install-prepare").start();
 }
 private void launch(Uri uri,boolean useKing){try{
  Intent intent=useKing?InstallSetup.kingIntent(uri):InstallSetup.intent(uri);ComponentName target;
  if(useKing){target=intent.resolveActivity(getPackageManager());}else{target=InstallSetup.systemInstaller(this,intent);}
  if(target==null)throw new ActivityNotFoundException("No installer");intent.setComponent(target);
  InstallSetup.Snapshot before=InstallSetup.read(this);if(before.error!=null)throw new IllegalStateException("Cannot record baseline");
  if(!InstallSetup.begin(this,before))throw new IOException("Cannot persist attempt");
  Diagnostics.event("Install setup launched: "+(useKing?"King Installer handoff":"built-in King classic method")+"; version="+Diagnostics.version(getPackageName())+"; handler="+target.getPackageName()+"; before={"+before.text()+"}");
  if(useKing)startActivity(intent);else startActivityForResult(intent,501);
 }catch(Exception e){InstallSetup.mark(this,"LAUNCH_FAILED_"+e.getClass().getSimpleName());fail(e);}}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==501){Diagnostics.event("Install dialog returned: resultCode="+result+" (not proof of metadata change)");InstallSetup.observe(this,"installer returned");if(result!=RESULT_OK&&getSharedPreferences(InstallSetup.PREFS,0).getBoolean("pending",false))InstallSetup.mark(this,"CANCELLED_OR_FAILED_NO_REINSTALL_OBSERVED");refresh();}}
 private void fail(Exception e){Diagnostics.event("Install setup error: "+e.getClass().getSimpleName());Toast.makeText(this,ar?"تعذّر فتح التثبيت. صدّر اللوج لفحص السبب.":"Unable to prepare installation. Export the log for diagnosis.",Toast.LENGTH_LONG).show();refresh();}
}
