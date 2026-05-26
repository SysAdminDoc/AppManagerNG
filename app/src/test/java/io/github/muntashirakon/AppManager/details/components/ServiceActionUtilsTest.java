// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ServiceActionUtilsTest {
    @Test
    public void buildServiceIntentTargetsExplicitService() {
        Intent intent = ServiceActionUtils.buildServiceIntent("com.example", ".SyncService");

        assertEquals(new ComponentName("com.example", "com.example.SyncService"), intent.getComponent());
    }

    @Test
    public void unprivilegedRouteRequiresSameUserExportAndPermissionAccess() {
        assertTrue(ServiceActionUtils.canUseUnprivilegedRoute(true, null, false, 0, 0));
        assertTrue(ServiceActionUtils.canUseUnprivilegedRoute(true, "com.example.PERMISSION", true, 0, 0));
        assertFalse(ServiceActionUtils.canUseUnprivilegedRoute(true, "com.example.PERMISSION", false, 0, 0));
        assertFalse(ServiceActionUtils.canUseUnprivilegedRoute(false, null, false, 0, 0));
        assertFalse(ServiceActionUtils.canUseUnprivilegedRoute(true, null, false, 10, 0));
    }

    @Test
    public void privilegedDispatchNeededWhenUnprivilegedRouteIsUnavailable() {
        assertFalse(ServiceActionUtils.needsPrivilegedDispatch(true, null, false, 0, 0));
        assertTrue(ServiceActionUtils.needsPrivilegedDispatch(true, "com.example.PERMISSION", false, 0, 0));
        assertTrue(ServiceActionUtils.needsPrivilegedDispatch(false, null, false, 0, 0));
        assertTrue(ServiceActionUtils.needsPrivilegedDispatch(true, null, false, 10, 0));
    }
}
