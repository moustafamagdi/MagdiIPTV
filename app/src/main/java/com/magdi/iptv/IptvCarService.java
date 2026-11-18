package com.magdi.iptv;
import com.google.android.apps.auto.sdk.*;
public final class IptvCarService extends CarActivityService {
 public Class<? extends CarActivity> getCarActivity(){return IptvCarActivity.class;}
 @Override public boolean onUnbind(android.content.Intent intent){CarPlaybackSession.connectionLost();return super.onUnbind(intent);}
 @Override public void onDestroy(){CarPlaybackSession.connectionLost();super.onDestroy();}
}
