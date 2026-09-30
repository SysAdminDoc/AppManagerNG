// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Upstream 62161f4ff: a bad ADB port, once stored, was tried on every start.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbPortTest {
    private SharedPreferences mPreferences;

    @Before
    public void setUp() {
        // The context ServerConfig itself reads from
        mPreferences = ContextUtils.getContext().getSharedPreferences("server_config", Context.MODE_PRIVATE);
        mPreferences.edit().clear().commit();
    }

    @Test
    public void aBadStoredPortFallsBackToTheDefault() {
        for (int port : new int[]{0, -1, 65536, Integer.MAX_VALUE}) {
            mPreferences.edit().putInt("adb_port", port).commit();
            assertEquals(String.valueOf(port), ServerConfig.DEFAULT_ADB_PORT, ServerConfig.getAdbPort());
        }
        mPreferences.edit().putInt("adb_port", 37119).commit();
        assertEquals(37119, ServerConfig.getAdbPort());
    }

    @Test
    public void aBadPortIsNeverStored() {
        ServerConfig.setAdbPort(65535);

        for (int port : new int[]{0, -1, 65536}) {
            assertThrows(String.valueOf(port), IllegalArgumentException.class, () -> ServerConfig.setAdbPort(port));
        }
        assertEquals(65535, ServerConfig.getAdbPort());
        assertTrue(ServerConfig.isValidAdbPort(1));
        assertFalse(ServerConfig.isValidAdbPort(0));
    }

    @Test
    public void connectingToABadPortFailsWithoutTouchingTheMode() {
        Context context = ContextUtils.getContext();
        boolean adb = Ops.isAdb();

        assertEquals(Ops.STATUS_FAILURE, Ops.connectAdb(context, 0, Ops.STATUS_FAILURE));
        assertEquals(Ops.STATUS_FAILURE, Ops.connectAdb(context, 70000, Ops.STATUS_FAILURE));
        assertEquals(adb, Ops.isAdb());
        assertEquals(ServerConfig.DEFAULT_ADB_PORT, ServerConfig.getAdbPort());
    }
}
