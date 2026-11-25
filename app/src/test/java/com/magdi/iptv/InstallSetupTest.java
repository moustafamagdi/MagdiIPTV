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
 private final Uri uri=Uri.parse("content://com.magdi.iptv.reports/installer/MM-IPTV.apk");
 
 
 @Test public void shellInstallerDoesNotCountAsMatching(){assertFalse(new InstallSetup.Snapshot("com.android.vending","com.android.shell",1,null).matches());assertTrue(new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",1,null).matches());assertFalse(new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",1,"unavailable").matches());}
 @Test public void completedDialogAloneDoesNotProveReinstall(){InstallSetup.Snapshot before=new InstallSetup.Snapshot(null,null,1,null),after=new InstallSetup.Snapshot("com.android.vending","com.google.android.packageinstaller",2,null);assertEquals("NO_REINSTALL_OBSERVED",InstallSetup.outcome(before,after,false));assertEquals("REINSTALL_OBSERVED_METADATA_NOW_MATCH",InstallSetup.outcome(before,after,true));}
 
}
