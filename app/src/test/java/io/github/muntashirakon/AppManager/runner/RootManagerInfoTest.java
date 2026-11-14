// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPackageManager;

import io.github.muntashirakon.AppManager.R;

/**
 * Without root, the manager comes from the installed manager app, and a refused Root switch says
 * where to grant it when the manager never asks. The shadow stands in for the su probe.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = RootManagerInfoTest.SuRunnerUtils.class)
public class RootManagerInfoTest {
    private static final String MAGISK = "com.topjohnwu.magisk";
    private static final String SUKISU = "com.sukisu.ultra";
    /** What su says: null when it's there and refused, false when there's no su at all. */
    static Boolean sRootGrant;
    /** What Magisk's su prints for -V, or null when the su isn't Magisk's. */
    static Long sSuVersionCode;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        sRootGrant = null;
        sSuVersionCode = null;
    }

    @Test
    public void aHiddenMagisk31_1AppStillSaysWhereToGrantRoot() {
        // Hide the Magisk app reinstalls it under a random package, so only its su can tell
        sSuVersionCode = 31100L;

        assertTrue(RootManagerInfo.magiskGrantsAheadOfTime(mContext));
        assertTrue(RootManagerInfo.waitsForMagiskGrant(mContext));
        assertEquals(R.string.root_refused_magisk_superuser, RootManagerInfo.getRootRefusedMessage(mContext));
    }

    @Test
    public void aHiddenOlderMagiskKeepsTheGenericNote() {
        sSuVersionCode = 30700L;

        assertFalse(RootManagerInfo.waitsForMagiskGrant(mContext));
        assertEquals(R.string.failed_to_use_the_current_mode_of_operation,
                RootManagerInfo.getRootRefusedMessage(mContext));
    }

    @Test
    public void everyKernelSuManagerAppMeansKernelSu() {
        // The original, KernelSU-Next and SukiSU Ultra all run on /data/adb/ksu
        for (String packageName : new String[]{"me.weishu.kernelsu", "com.rifsxd.ksunext", SUKISU}) {
            install(packageName, 1);
            RootManagerInfo info = RootManagerInfo.detect(mContext);
            assertEquals(packageName, RootManagerInfo.Manager.KERNELSU, info.manager);
            assertEquals(packageName, RootManagerInfo.Source.PACKAGE, info.source);
            shadowOf(mContext.getPackageManager()).removePackage(packageName);
        }
    }

    @Test
    public void magiskAndAPatchAppsAreFound() {
        install(MAGISK, 30700);
        assertEquals(RootManagerInfo.Manager.MAGISK, RootManagerInfo.detect(mContext).manager);
        shadowOf(mContext.getPackageManager()).removePackage(MAGISK);
        install("me.bmax.apatch", 11282);
        assertEquals(RootManagerInfo.Manager.APATCH, RootManagerInfo.detect(mContext).manager);
    }

    @Test
    public void noManagerAppMeansNone() {
        RootManagerInfo info = RootManagerInfo.detect(mContext);
        assertEquals(RootManagerInfo.Manager.NONE, info.manager);
        assertEquals(RootManagerInfo.Source.NONE, info.source);
    }

    @Test
    public void magisk31_1AndLaterSayWhereToGrantRoot() {
        // Magisk's versionCode is major * 1000 + minor * 100, and 31.1 dropped the prompt
        install(MAGISK, 31100);
        assertTrue(RootManagerInfo.magiskGrantsAheadOfTime(mContext));
        assertTrue(RootManagerInfo.waitsForMagiskGrant(mContext));
        assertEquals(R.string.root_refused_magisk_superuser, RootManagerInfo.getRootRefusedMessage(mContext));
        install(MAGISK, 31200);
        assertEquals(R.string.root_refused_magisk_superuser, RootManagerInfo.getRootRefusedMessage(mContext));
    }

    @Test
    public void magisk31_1WithNoSuKeepsTheGenericNote() {
        // Magisk installed only to patch a boot image, or a KernelSU device hiding su from an app it
        // hasn't granted: nothing in Magisk would help
        sRootGrant = false;
        install(MAGISK, 31100);

        assertFalse(RootManagerInfo.waitsForMagiskGrant(mContext));
        assertEquals(R.string.failed_to_use_the_current_mode_of_operation,
                RootManagerInfo.getRootRefusedMessage(mContext));
    }

    @Test
    public void theMagiskButtonOnlyEverOpensMagisk() throws Exception {
        installLauncher(SUKISU);
        assertNull(RootManagerInfo.getMagiskLaunchIntent(mContext));

        installLauncher(MAGISK);
        Intent magisk = RootManagerInfo.getMagiskLaunchIntent(mContext);
        assertNotNull(magisk);
        assertEquals(MAGISK, magisk.getComponent().getPackageName());
    }

    @Test
    public void olderMagiskOrNoMagiskKeepsTheGenericNote() {
        assertFalse(RootManagerInfo.magiskGrantsAheadOfTime(mContext));
        assertEquals(R.string.failed_to_use_the_current_mode_of_operation,
                RootManagerInfo.getRootRefusedMessage(mContext));
        install(MAGISK, 31000);
        assertEquals(R.string.failed_to_use_the_current_mode_of_operation,
                RootManagerInfo.getRootRefusedMessage(mContext));
        install(MAGISK, 30700);
        assertFalse(RootManagerInfo.magiskGrantsAheadOfTime(mContext));
        assertEquals(R.string.failed_to_use_the_current_mode_of_operation,
                RootManagerInfo.getRootRefusedMessage(mContext));
    }

    @Test
    public void sukiSuUltraOpensAsTheKernelSuManager() throws Exception {
        // Mode doctor and Privilege health used to look for the original and KernelSU-Next only
        assertNull(RootManagerInfo.getKernelSuManagerLaunchIntent(mContext));
        installLauncher(SUKISU);

        Intent kernelSu = RootManagerInfo.getKernelSuManagerLaunchIntent(mContext);
        assertNotNull(kernelSu);
        assertEquals(SUKISU, kernelSu.getComponent().getPackageName());
        assertTrue((kernelSu.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        Intent manager = RootManagerInfo.getManagerLaunchIntent(mContext);
        assertNotNull(manager);
        assertEquals(SUKISU, manager.getComponent().getPackageName());
    }

    @Test
    public void magiskOpensFirstWhenSeveralManagersAreInstalled() throws Exception {
        installLauncher(SUKISU);
        installLauncher(MAGISK);

        Intent manager = RootManagerInfo.getManagerLaunchIntent(mContext);
        assertNotNull(manager);
        assertEquals(MAGISK, manager.getComponent().getPackageName());
    }

    private void install(String packageName, long versionCode) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.setLongVersionCode(versionCode);
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        shadowOf(mContext.getPackageManager()).installPackage(info);
    }

    private void installLauncher(String packageName) throws PackageManager.NameNotFoundException {
        install(packageName, 1);
        ShadowPackageManager pm = shadowOf(mContext.getPackageManager());
        ComponentName main = new ComponentName(packageName, packageName + ".MainActivity");
        pm.addActivityIfNotPresent(main);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        pm.addIntentFilterForActivity(main, filter);
    }

    @Implements(RunnerUtils.class)
    public static class SuRunnerUtils {
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            return sRootGrant;
        }

        @Implementation
        protected static Long getMagiskSuVersionCode() {
            return sSuVersionCode;
        }
    }
}
