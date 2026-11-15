package com.magdi.iptv;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public class InstallSetupTest {
 private final Uri uri=Uri.parse("content://com.magdi.iptv.reports/installer/Magdi-IPTV.apk");
 @Test public void classicInstallPassesReadableApkAndSourceHints(){Intent i=InstallSetup.intent(uri);assertEquals(Intent.ACTION_INSTALL_PACKAGE,i.getAction());assertEquals(uri,i.getData());assertEquals("com.android.vending",i.getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME));assertTrue(i.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE,false));assertEquals(1,i.getIntExtra("android.intent.extra.INSTALL_REASON",-1));assertEquals(uri,i.getClipData().getItemAt(0).getUri());assertNotEquals(0,i.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}
 @Test public void kingHandoffIsExplicitAndReadOnly(){Intent i=InstallSetup.kingIntent(uri);assertEquals(Intent.ACTION_SEND,i.getAction());assertEquals("com.example.kinginstaller",i.getPackage());assertEquals(uri,i.getParcelableExtra(Intent.EXTRA_STREAM));assertEquals(uri,i.getClipData().getItemAt(0).getUri());assertEquals(0,i.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
 @Test public void shellInstallerDoesNotCountAsMatching(){assertFalse(new InstallSetup.Snapshot("com.android.vending","com.android.shell",1,null).matches());assertTrue(new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",1,null).matches());assertFalse(new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",1,"unavailable").matches());}
 @Test public void completedDialogAloneDoesNotProveReinstall(){InstallSetup.Snapshot before=new InstallSetup.Snapshot(null,null,1,null),after=new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",2,null);assertEquals("NO_REINSTALL_OBSERVED",InstallSetup.outcome(before,after,false));assertEquals("REINSTALL_OBSERVED_METADATA_NOW_MATCH",InstallSetup.outcome(before,after,true));}
 @Test public void classicPathDoesNotSendApkToThirdPartyInstaller(){Context c=RuntimeEnvironment.getApplication();Intent i=InstallSetup.intent(uri);ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.name="Installer";r.activityInfo.packageName="untrusted.installer";r.activityInfo.exported=true;r.activityInfo.applicationInfo=new ApplicationInfo();shadowOf(c.getPackageManager()).addResolveInfoForIntent(i,r);assertNull(InstallSetup.systemInstaller(c,i));}
}
