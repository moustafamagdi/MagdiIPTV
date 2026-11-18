package com.magdi.iptv;

/** Debug APK only: two real WebViews and native routing without a physical head unit. */
public final class ControllerHarnessActivity extends android.app.Activity {
 private PlayerView phone,car;private boolean connected=true;
 public void onCreate(android.os.Bundle state){super.onCreate(state);
  android.widget.LinearLayout root=new android.widget.LinearLayout(this);root.setOrientation(1);
  android.widget.Button toggle=new android.widget.Button(this);toggle.setText("Disconnect test car");root.addView(toggle);
  android.widget.LinearLayout panels=new android.widget.LinearLayout(this);root.addView(panels,new android.widget.LinearLayout.LayoutParams(-1,0,1));
  phone=new PlayerView(this);car=new PlayerView(new android.view.ContextThemeWrapper(this,android.R.style.Theme_Material_NoActionBar));
  panels.addView(phone,new android.widget.LinearLayout.LayoutParams(0,-1,1));panels.addView(car,new android.widget.LinearLayout.LayoutParams(0,-1,1));setContentView(root);
  try{java.lang.reflect.Field f=PlayerView.class.getDeclaredField("web");f.setAccessible(true);((android.webkit.WebView)f.get(phone)).addJavascriptInterface(new HarnessControls(),"Harness");}catch(Exception e){throw new IllegalStateException(e);}
  toggle.setOnClickListener(v->{connected=!connected;if(connected)car.resume();else car.pause();toggle.setText(connected?"Disconnect test car":"Connect test car");});
 }
 public final class HarnessControls {
  @android.webkit.JavascriptInterface public void setCarViewport(int width,int height){runOnUiThread(()->{float density=getResources().getDisplayMetrics().density;car.setLayoutParams(width>0&&height>0?new android.widget.LinearLayout.LayoutParams(Math.round(width*density),Math.round(height*density)):new android.widget.LinearLayout.LayoutParams(0,-1,1));});}
  @android.webkit.JavascriptInterface public void closePhone(){runOnUiThread(()->{phone.close();((android.view.ViewGroup)phone.getParent()).removeView(phone);});}
 }
 protected void onResume(){super.onResume();phone.resume();if(connected)car.resume();}
 protected void onPause(){phone.pause();car.pause();super.onPause();}
 protected void onDestroy(){phone.close();car.close();super.onDestroy();}
}
