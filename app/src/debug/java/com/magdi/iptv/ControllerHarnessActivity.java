package com.magdi.iptv;

/** Debug APK only: two real WebViews and native routing without a physical head unit. */
public final class ControllerHarnessActivity extends android.app.Activity {
 private android.widget.Button toggle;private PlayerView phone,car;private boolean connected=true;
 public void onCreate(android.os.Bundle state){super.onCreate(state);
  android.widget.LinearLayout root=new android.widget.LinearLayout(this);root.setOrientation(android.widget.LinearLayout.VERTICAL);
  toggle=new android.widget.Button(this);toggle.setText("Disconnect test car");root.addView(toggle);
  android.widget.LinearLayout panels=new android.widget.LinearLayout(this);root.addView(panels,new android.widget.LinearLayout.LayoutParams(-1,0,1));
  phone=new PlayerView(this);CarPlaybackSession.connectionAvailable(this);car=CarPlaybackSession.attach(this,this);
  panels.addView(phone,new android.widget.LinearLayout.LayoutParams(0,-1,1));panels.addView(car,new android.widget.LinearLayout.LayoutParams(0,-1,1));setContentView(root);
  try{java.lang.reflect.Field f=PlayerView.class.getDeclaredField("web");f.setAccessible(true);((android.webkit.WebView)f.get(phone)).addJavascriptInterface(new HarnessControls(),"Harness");}catch(Exception e){throw new IllegalStateException(e);}
  toggle.setOnClickListener(v->{connected=!connected;if(connected){CarPlaybackSession.connectionAvailable(this);car.resume();}else CarPlaybackSession.connectionLost(this);toggle.setText(connected?"Disconnect test car":"Connect test car");});
 }
 public final class HarnessControls {
  @android.webkit.JavascriptInterface public void toggleCar(){runOnUiThread(()->toggle.performClick());}
  @android.webkit.JavascriptInterface public void coverCar(boolean covered){runOnUiThread(()->{if(covered){car.coverHost();car.dispatchWindowVisibilityChanged(android.view.View.GONE);}else{car.dispatchWindowVisibilityChanged(android.view.View.VISIBLE);car.resume();}});}
  @android.webkit.JavascriptInterface public String audioCard(String action){java.util.concurrent.FutureTask<String> task=new java.util.concurrent.FutureTask<>(()->{java.lang.reflect.Field r=MediaCardService.class.getDeclaredField("running"),s=MediaCardService.class.getDeclaredField("session");r.setAccessible(true);s.setAccessible(true);Object service=((java.lang.ref.WeakReference<?>)r.get(null)).get();if(service==null)return "unavailable";android.support.v4.media.session.MediaSessionCompat session=(android.support.v4.media.session.MediaSessionCompat)s.get(service);android.support.v4.media.session.MediaControllerCompat.TransportControls c=session.getController().getTransportControls();if("play".equals(action))c.play();else if("pause".equals(action))c.pause();else if("stop".equals(action))c.stop();return "sent";});runOnUiThread(task);try{return task.get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){return "error";}}
  @android.webkit.JavascriptInterface public void setCarViewport(int width,int height){runOnUiThread(()->{float density=getResources().getDisplayMetrics().density;car.setLayoutParams(width>0&&height>0?new android.widget.LinearLayout.LayoutParams(Math.round(width*density),Math.round(height*density)):new android.widget.LinearLayout.LayoutParams(0,-1,1));});}
  @android.webkit.JavascriptInterface public void closePhone(){runOnUiThread(()->{phone.close();((android.view.ViewGroup)phone.getParent()).removeView(phone);});}
 }
 protected void onResume(){super.onResume();phone.resume();if(connected)car.resume();}
 protected void onPause(){phone.pause();if(connected)car.coverHost();super.onPause();}
 protected void onDestroy(){phone.close();CarPlaybackSession.connectionLost(this);CarPlaybackSession.detach(this);CarPlaybackSession.stopDetached();super.onDestroy();}
}
