// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

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
}
