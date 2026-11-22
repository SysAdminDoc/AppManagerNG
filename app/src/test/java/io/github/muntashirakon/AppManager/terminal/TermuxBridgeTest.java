// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

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

    /**
     * With allow-external-apps off, Termux turns the command down and says so only in its log and a notice
     * Android may hide, so the tap did nothing at all. The command asks Termux to report back to a receiver
     * here, through a PendingIntent Termux can add its result to.
     */
    @Test
    public void aCommandAsksTermuxToReportBack() {
        Intent intent = TermuxBridge.withResult(mApp, TermuxBridge.buildRunCommandIntent("ls"));

        PendingIntent result = intent.getParcelableExtra("com.termux.RUN_COMMAND_PENDING_INTENT");
        assertNotNull(result);
        assertTrue(shadowOf(result).isBroadcast());
        assertEquals(new ComponentName(mApp, TermuxResultReceiver.class),
                shadowOf(result).getSavedIntent().getComponent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue("Termux couldn't add its result", (shadowOf(result).getFlags() & PendingIntent.FLAG_MUTABLE) != 0);
        }
        // The command itself is unchanged
        assertArrayEquals(new String[]{"-c", "ls"}, intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"));
    }

    @Test
    public void termuxsResultSaysWhetherTheCommandWasTurnedDown() {
        assertNull(TermuxBridge.getRefusal(null));
        assertNull(TermuxBridge.getRefusal(termuxResult(Activity.RESULT_OK, null)));

        String refusal = TermuxBridge.getRefusal(termuxResult(2, EXTERNAL_APPS_OFF));
        assertEquals(EXTERNAL_APPS_OFF, refusal);
        assertTrue(TermuxBridge.isExternalAppsOff(refusal));
        assertFalse(TermuxBridge.isExternalAppsOff("Mandatory extra missing"));
        // A failure Termux gave no words for still counts as one
        assertEquals("", TermuxBridge.getRefusal(termuxResult(2, null)));
    }

    @Test
    public void aRefusalWithNoScreenListeningShowsAToast() {
        ShadowToast.reset();

        new TermuxResultReceiver().onReceive(mApp, termuxResultIntent(2, EXTERNAL_APPS_OFF));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(mApp.getString(R.string.termux_external_apps_off), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aRefusalGoesToTheOpenScreenInsteadOfAToast() {
        ShadowToast.reset();
        List<String> seen = new ArrayList<>();
        Observer<String> screen = seen::add;
        TermuxBridge.getRefusals().observeForever(screen);
        try {
            new TermuxResultReceiver().onReceive(mApp, termuxResultIntent(2, EXTERNAL_APPS_OFF));
            shadowOf(Looper.getMainLooper()).idle();
        } finally {
            TermuxBridge.getRefusals().removeObserver(screen);
            TermuxBridge.getRefusals().setValue(null);
        }

        assertTrue(seen.contains(EXTERNAL_APPS_OFF));
        assertNull(ShadowToast.getLatestToast());
    }

    @Test
    public void aCommandThatRanReportsNothing() {
        ShadowToast.reset();

        new TermuxResultReceiver().onReceive(mApp, termuxResultIntent(Activity.RESULT_OK, null));
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(ShadowToast.getLatestToast());
        assertNull(TermuxBridge.getRefusals().getValue());
    }

    // Termux 0.118.3's own wording, from RunCommandService
    private static final String EXTERNAL_APPS_OFF = "RunCommandService requires `allow-external-apps` property to be "
            + "set to `true` in `~/.termux/termux.properties` file.";

    private static Bundle termuxResult(int err, String errmsg) {
        Bundle result = new Bundle();
        result.putString("stdout", "");
        result.putString("stderr", "");
        result.putInt("err", err);
        result.putString("errmsg", errmsg);
        return result;
    }

    private static Intent termuxResultIntent(int err, String errmsg) {
        return new Intent().putExtra("result", termuxResult(err, errmsg));
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
