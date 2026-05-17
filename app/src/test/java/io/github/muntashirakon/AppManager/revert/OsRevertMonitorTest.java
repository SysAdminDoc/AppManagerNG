// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.pm.PackageManager;

import org.junit.Test;

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
}
