// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Commands handed to Termux go through its documented RUN_COMMAND service (#55).
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxBridgeTest {
    private Application mApp;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void runCommandTargetsTermuxsServiceWithTheLoginShellAndTheWholeCommand() {
        String command = "ls -la \"/sdcard/My Files\" | grep '.apk' && echo done";

        Intent intent = TermuxBridge.buildRunCommandIntent(command);

        assertEquals("com.termux.RUN_COMMAND", intent.getAction());
        assertEquals(new ComponentName("com.termux", "com.termux.app.RunCommandService"), intent.getComponent());
        // Termux's login runs the user's own shell with their profile
        assertEquals("/data/data/com.termux/files/usr/bin/login",
                intent.getStringExtra("com.termux.RUN_COMMAND_PATH"));
        // One argument for the shell's -c, so quotes, pipes and && reach the shell untouched
        assertArrayEquals(new String[]{"-c", command},
                intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"));
        assertEquals("/data/data/com.termux/files/home", intent.getStringExtra("com.termux.RUN_COMMAND_WORKDIR"));
        assertFalse(intent.getBooleanExtra("com.termux.RUN_COMMAND_BACKGROUND", true));
        // Open the new session in front so the output is visible
        assertEquals("0", intent.getStringExtra("com.termux.RUN_COMMAND_SESSION_ACTION"));
    }

    @Test
    public void theRunCommandPermissionIsTermuxsOwn() {
        assertEquals("com.termux.permission.RUN_COMMAND", TermuxBridge.PERMISSION_RUN_COMMAND);
    }

    @Test
    public void stateFollowsInstallAndPermission() {
        assertEquals(TermuxBridge.STATE_NOT_INSTALLED, TermuxBridge.getState(mApp));
        assertFalse(TermuxBridge.isInstalled(mApp));

        installTermux();
        assertTrue(TermuxBridge.isInstalled(mApp));
        assertEquals(TermuxBridge.STATE_NO_PERMISSION, TermuxBridge.getState(mApp));
        assertFalse(TermuxBridge.hasRunCommandPermission(mApp));

        shadowOf(mApp).grantPermissions(TermuxBridge.PERMISSION_RUN_COMMAND);
        assertTrue(TermuxBridge.hasRunCommandPermission(mApp));
        assertEquals(TermuxBridge.STATE_READY, TermuxBridge.getState(mApp));
    }

    @Test
    public void aDisabledTermuxCountsAsMissing() {
        PackageInfo info = installTermux();
        info.applicationInfo.enabled = false;
        shadowOf(mApp.getPackageManager()).installPackage(info);

        assertFalse(TermuxBridge.isInstalled(mApp));
    }

    @Test
    public void openingTermuxStartsItsMainScreenInANewTask() {
        installTermux();

        Intent intent = TermuxBridge.getLaunchIntent(mApp);

        assertNotNull(intent);
        assertEquals("com.termux", intent.getComponent().getPackageName());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void theInstallLinkIsOfferedOnlyWhenOnlineFeaturesAreAllowed() {
        assertEquals("https://f-droid.org/packages/com.termux/", TermuxBridge.getInstallUrl(true));
        assertNull(TermuxBridge.getInstallUrl(false));
    }

    private PackageInfo installTermux() {
        PackageInfo info = new PackageInfo();
        info.packageName = TermuxBridge.PACKAGE_NAME;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = TermuxBridge.PACKAGE_NAME;
        info.applicationInfo.enabled = true;
        shadowOf(mApp.getPackageManager()).installPackage(info);
        return info;
    }
}
