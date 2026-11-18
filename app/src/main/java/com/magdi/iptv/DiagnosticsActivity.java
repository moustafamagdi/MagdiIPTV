package com.magdi.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Phone-only selector, adapted from Magdi Tube. No file operations run on the UI thread. */
public final class DiagnosticsActivity extends Activity {
 private Spinner sessions,parts;
 private Button copy,export,refresh,start;
 private TextView status,migrationWarning;
 private boolean ar,rebinding;
 private int generation;
 private final List<String> sessionIds=new ArrayList<>();
 private final List<Diagnostics.Part> all=new ArrayList<>(),visible=new ArrayList<>();
 private Diagnostics.Snapshot selected;
 public void onCreate(Bundle state){super.onCreate(state);String language=getIntent().getStringExtra("language");ar="ar".equals(language==null?getResources().getConfiguration().getLocales().get(0).getLanguage():language);
  LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
  TextView title=text(body,ar?"جلسات اللوج":"Log sessions");title.setTextSize(24);
  text(body,ar?"تبدأ جلسة عند بدء عملية التطبيق أو اتصال شاشة السيارة. إعادة الاتصال خلال 5 دقائق تُجمع في نفس الجلسة؛ التغطية المؤقتة مثل الكاميرا لا تبدأ جلسة جديدة.\n\nنحتفظ بآخر 40 جزءًا لمدة تصل إلى 7 أيام. يُحذف الأقدم عند بلوغ أي حد. حد الجزء 128 KiB (131072 بايت)، بدون تقسيم سطر الحدث. بدء جلسة جديدة لا يمسح السابقة ولا يوقف التشغيل. الملفات المُصدّرة التي تحفظها خارج التطبيق لا تُحذف.\n\nCopy وExport يستخدمان نسخة كاملة واحدة من الجزء عند اختياره، مع معلومات التشخيص. حدّث القائمة لأحدث الأحداث.":"A new session starts with the app process or car connection. Reconnections within 5 minutes stay in the same session. Temporary covers such as the camera do not start sessions.\n\nKeeps the latest 40 parts for up to 7 days; older parts are removed when either limit is reached. Each part is at most 128 KiB (131072 bytes), without splitting event lines. A new session keeps earlier logs and does not stop playback. Export files saved outside the app are not deleted.\n\nCopy and Export share one complete snapshot of the selected part, with diagnostic information. Refresh the list for newer events.");
  text(body,ar?"الجلسة":"Session");sessions=new Spinner(this);sessions.setContentDescription(ar?"الجلسة":"Session");body.addView(sessions);
  text(body,ar?"الجزء":"Part");parts=new Spinner(this);parts.setContentDescription(ar?"الجزء":"Part");body.addView(parts);
  migrationWarning=text(body,"");migrationWarning.setVisibility(View.GONE);
  status=text(body,"");status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  copy=button(body,ar?"نسخ الجزء المختار":"Copy selected part");export=button(body,ar?"تصدير الجزء المختار":"Export selected part");
  copy.setOnClickListener(v->Diagnostics.copy(this,selected,ar));export.setOnClickListener(v->Diagnostics.export(this,selected,ar));
  start=button(body,ar?"بدء جلسة جديدة":"Start new session");start.setOnClickListener(v->{disable();start.setEnabled(false);Diagnostics.newSession(this,ok->{start.setEnabled(true);if(ok)reload();else status.setText(ar?"تعذر بدء الجلسة":"Unable to start session");});});
  refresh=button(body,ar?"تحديث القائمة":"Refresh list");refresh.setOnClickListener(v->reload());button(body,ar?"رجوع":"Back").setOnClickListener(v->finish());
  sessions.setOnItemSelectedListener(listener(()->bindParts(sessions.getSelectedItemPosition())));parts.setOnItemSelectedListener(listener(this::loadSelected));reload();
 }
 private TextView text(LinearLayout body,String value){TextView t=new TextView(this);t.setText(value);t.setPadding(0,10,0,10);body.addView(t);return t;}
 private Button button(LinearLayout body,String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);body.addView(b);return b;}
 private AdapterView.OnItemSelectedListener listener(Runnable action){return new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(!rebinding)action.run();}public void onNothingSelected(AdapterView<?> p){}};}
 private void adapter(Spinner spinner,List<String> labels){ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(a);}
 static String sessionLabel(String id,boolean ar){String[] p=id.split("-");if(p.length!=5)return id;String kind=p[3];if(ar)kind=kind.equals("Car")?"السيارة":kind.equals("Manual")?"يدوية":kind.equals("Imported")?"اللوج القديم":"بدء التطبيق";return kind+" · "+p[0].substring(0,4)+"/"+p[0].substring(4,6)+"/"+p[0].substring(6)+" "+p[1].substring(0,2)+":"+p[1].substring(2,4)+":"+p[1].substring(4)+"."+p[2];}
 static String partLabel(Diagnostics.Part p,boolean ar){String number=p.name.substring(p.name.lastIndexOf("-part")+5,p.name.length()-4).replaceFirst("^0+(?!$)","");return (ar?"الجزء ":"Part ")+number+" · "+String.format(Locale.US,"%.1f KiB (%d bytes)",p.bytes/1024.0,p.bytes)+(p.current?(ar?" · الحالي":" · Current"):"");}
 private void disable(){selected=null;copy.setEnabled(false);export.setEnabled(false);}
 private void reload(){int request=++generation;disable();refresh.setEnabled(false);sessions.setEnabled(false);parts.setEnabled(false);Diagnostics.list(this,result->{if(request!=generation)return;refresh.setEnabled(true);sessions.setEnabled(true);parts.setEnabled(true);rebinding=true;all.clear();all.addAll(result.parts);sessionIds.clear();List<String> labels=new ArrayList<>();int current=0;for(Diagnostics.Part p:all){if(!sessionIds.contains(p.session)){sessionIds.add(p.session);labels.add(sessionLabel(p.session,ar));}if(p.current)current=sessionIds.indexOf(p.session);}adapter(sessions,labels);sessions.setSelection(current);rebinding=false;migrationWarning.setVisibility(result.migrationPending?View.VISIBLE:View.GONE);migrationWarning.setText(result.migrationPending?(ar?"اللوج القديم محفوظ؛ لم يكتمل الترحيل. تحديث القائمة يعيد المحاولة.":"Legacy logs are retained; import is incomplete. Refresh retries it."):all.isEmpty()?(ar?"لا توجد أجزاء محفوظة":"No saved log parts"):"");bindParts(current);});}
 private void bindParts(int index){disable();visible.clear();List<String> labels=new ArrayList<>();int current=0;if(index>=0&&index<sessionIds.size())for(Diagnostics.Part p:all)if(p.session.equals(sessionIds.get(index))){visible.add(p);labels.add(partLabel(p,ar));if(p.current)current=visible.size()-1;}rebinding=true;adapter(parts,labels);parts.setSelection(current);rebinding=false;loadSelected();}
 private void loadSelected(){int request=++generation;disable();int index=parts.getSelectedItemPosition();if(index<0||index>=visible.size())return;Diagnostics.load(this,visible.get(index).name,result->{if(request!=generation)return;selected=result;copy.setEnabled(result!=null);export.setEnabled(result!=null);if(result==null)status.setText(ar?"الجزء غير متاح أو انتهت مدة حفظه؛ حدّث القائمة":"Part unavailable or expired; refresh the list");else status.setText((ar?"تم تحميل الجزء بالكامل: ":"Complete part loaded: ")+result.bytes+(ar?" بايت. حدّث للحصول على أحدث الأحداث.":" bytes. Refresh for newer events."));});}
}
