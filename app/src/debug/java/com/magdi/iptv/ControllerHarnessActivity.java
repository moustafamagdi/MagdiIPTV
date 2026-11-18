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
  toggle.setOnClickListener(v->{connected=!connected;if(connected)car.resume();else car.pause();toggle.setText(connected?"Disconnect test car":"Connect test car");});
 }
 protected void onResume(){super.onResume();phone.resume();if(connected)car.resume();}
 protected void onPause(){phone.pause();car.pause();super.onPause();}
 protected void onDestroy(){phone.close();car.close();super.onDestroy();}
}
