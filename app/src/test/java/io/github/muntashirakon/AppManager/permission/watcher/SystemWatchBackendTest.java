// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SystemWatchBackendTest {
    @Test
    public void allowedAndForegroundOnlyCountAsAllowed() {
        assertTrue(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_ALLOWED, false));
        assertTrue(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_FOREGROUND, false));
        assertFalse(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_IGNORED, true));
        assertFalse(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_ERRORED, true));
    }

    @Test
    public void theDefaultModeFollowsThePermission() {
        assertTrue(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_DEFAULT, true));
        assertFalse(SystemWatchBackend.isAllowedMode(AppOpsManager.MODE_DEFAULT, false));
    }
}
