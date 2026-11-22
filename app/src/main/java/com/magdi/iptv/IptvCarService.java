package com.magdi.iptv;
import com.google.android.apps.auto.sdk.*;
public final class IptvCarService extends CarActivityService {
 public Class<? extends CarActivity> getCarActivity(){return IptvCarActivity.class;}
 @Override public android.os.IBinder onBind(android.content.Intent intent){android.os.IBinder binder=super.onBind(intent);if(binder!=null)CarPlaybackSession.connectionAvailable(this);return binder;}
 @Override public void onRebind(android.content.Intent intent){super.onRebind(intent);CarPlaybackSession.connectionAvailable(this);}
 @Override public boolean onUnbind(android.content.Intent intent){CarPlaybackSession.connectionLost(this);return super.onUnbind(intent);}
 @Override public void onDestroy(){CarPlaybackSession.connectionLost(this);super.onDestroy();}
}
