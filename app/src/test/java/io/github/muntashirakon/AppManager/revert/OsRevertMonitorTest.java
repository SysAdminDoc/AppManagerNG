// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import java.time.Duration;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class OsRevertMonitorTest {
    @Test
    public void booleanStateMatchesOnlyWhenEqual() {
        assertTrue(OsRevertMonitor.stateMatches(true, true));
        assertTrue(OsRevertMonitor.stateMatches(false, false));
        assertFalse(OsRevertMonitor.stateMatches(true, false));
        assertFalse(OsRevertMonitor.stateMatches(false, true));
    }

    @Test
    public void componentStateMatchesRawPackageManagerState() {
        assertTrue(OsRevertMonitor.componentStateMatches(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED));
        assertFalse(OsRevertMonitor.componentStateMatches(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT));
    }

    @Test
    public void appOpModeMatchesRawMode() {
        assertTrue(OsRevertMonitor.appOpModeMatches(AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_IGNORED));
        assertFalse(OsRevertMonitor.appOpModeMatches(AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ALLOWED));
    }

    @Test
    public void appOpRevertEventNamesTargetOpAndModes() {
        Context context = ApplicationProvider.getApplicationContext();
        int op = AppOpsManagerCompat.OP_RUN_IN_BACKGROUND != 0
                ? AppOpsManagerCompat.OP_RUN_IN_BACKGROUND
                : AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME;

        OsRevertMonitor.RevertEvent event = OsRevertMonitor.buildAppOpRevertEvent(context,
                "com.example.storage",
                op,
                AppOpsManager.MODE_ALLOWED,
                AppOpsManager.MODE_IGNORED);

        String detail = event.getDetailMessage();
        assertTrue(detail, detail.contains("Target: com.example.storage"));
        assertTrue(detail, detail.contains("Operation: AppOp " + AppOpsManagerCompat.opToName(op)));
        assertTrue(detail, detail.contains("Expected: " + AppOpsManagerCompat.modeToName(AppOpsManager.MODE_ALLOWED)));
        assertTrue(detail, detail.contains("Current: " + AppOpsManagerCompat.modeToName(AppOpsManager.MODE_IGNORED)));
        assertTrue(detail, detail.contains("AppOps mode no longer matches"));
    }

    @Test
    public void freezingThenUnfreezingBeforeTheCheckIsNotARevert() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        OsRevertCountTracker tracker = OsRevertCountTracker.getInstance();
        tracker.clear();
        // Neither package exists here, so both read as not frozen when checked
        OsRevertMonitor.watchFreeze(context, "com.example.refrozen", 0, true);
        OsRevertMonitor.watchFreeze(context, "com.example.refrozen", 0, false);
        OsRevertMonitor.watchFreeze(context, "com.example.reverted", 0, true);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));

        long deadline = System.currentTimeMillis() + 5_000L;
        while (tracker.countRecent("com.example.reverted", System.currentTimeMillis(), 60_000L) == 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        assertEquals(1, tracker.countRecent("com.example.reverted", System.currentTimeMillis(), 60_000L));
        // Give any check of the first freeze the same time to land
        Thread.sleep(500L);
        assertEquals(0, tracker.countRecent("com.example.refrozen", System.currentTimeMillis(), 60_000L));
        tracker.clear();
    }
}
