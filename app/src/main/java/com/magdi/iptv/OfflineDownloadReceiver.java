package com.magdi.iptv;
import android.content.*;
import android.app.DownloadManager;
public final class OfflineDownloadReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))return;PendingResult result=goAsync();new Thread(()->{try{new OfflineLibrary(context).list();}catch(Exception ignored){}finally{result.finish();}},"offline-completion").start();}
}
