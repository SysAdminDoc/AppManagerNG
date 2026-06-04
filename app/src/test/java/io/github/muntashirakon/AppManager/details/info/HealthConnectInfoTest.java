// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

public class HealthConnectInfoTest {
    @Test
    public void belowAndroid14DoesNotReportHealthConnectPermissions() {
        HealthConnectInfo info = HealthConnectInfo.fromRaw(Build.VERSION_CODES.TIRAMISU,
                new String[]{"android.permission.health.READ_STEPS"});

        assertFalse(info.isSupported());
        assertFalse(info.hasRequestedHealthPermissions());
        assertTrue(info.requestedPermissions.isEmpty());
    }

    @Test
    public void android14KeepsOnlyHealthPermissionPrefix() {
        HealthConnectInfo info = HealthConnectInfo.fromRaw(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new String[]{
                        "android.permission.INTERNET",
                        "android.permission.health.WRITE_STEPS",
                        "android.permission.health.READ_HEART_RATE",
                        "android.permission.health.READ_HEART_RATE"
                });

        assertTrue(info.isSupported());
        assertTrue(info.hasRequestedHealthPermissions());
        assertEquals(2, info.requestedPermissions.size());
        assertEquals(1, info.readPermissionCount);
        assertEquals(1, info.writePermissionCount);
        assertEquals("read heart rate", info.requestedPermissions.get(0).toDisplayString());
    }

    @Test
    public void specialHealthPermissionsAreKeptWithoutReadWriteCounts() {
        HealthConnectInfo info = HealthConnectInfo.fromRaw(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new String[]{"android.permission.health.READ_HEALTH_DATA_HISTORY"});

        assertEquals(1, info.requestedPermissions.size());
        assertEquals(1, info.readPermissionCount);
        assertEquals(0, info.writePermissionCount);
    }
}
