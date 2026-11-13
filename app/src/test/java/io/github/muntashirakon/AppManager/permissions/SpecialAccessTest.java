// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;

import android.app.AppOpsManager;

import org.junit.Test;

public class SpecialAccessTest {
    private static final int REJECT = 1; // POLICY_REJECT_METERED_BACKGROUND
    private static final int ALLOW = 4; // POLICY_ALLOW_METERED_BACKGROUND
    private static final int OEM_BIT = 1 << 16;

    @Test
    public void appOpModesReadLikePermissionChecker() {
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.fromAppOpMode(AppOpsManager.MODE_ALLOWED, false));
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.fromAppOpMode(4 /* MODE_FOREGROUND */, false));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.fromAppOpMode(AppOpsManager.MODE_IGNORED, true));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.fromAppOpMode(AppOpsManager.MODE_ERRORED, true));
        // The default mode defers to the permission itself
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.fromAppOpMode(AppOpsManager.MODE_DEFAULT, true));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.fromAppOpMode(AppOpsManager.MODE_DEFAULT, false));
        assertEquals(SpecialAccess.UNKNOWN, SpecialAccess.fromAppOpMode(42, true));
    }

    @Test
    public void backgroundActivityIsAllowedUntilRestricted() {
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.fromBackgroundMode(AppOpsManager.MODE_ALLOWED));
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.fromBackgroundMode(AppOpsManager.MODE_DEFAULT));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.fromBackgroundMode(AppOpsManager.MODE_IGNORED));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.fromBackgroundMode(AppOpsManager.MODE_ERRORED));
    }

    @Test
    public void networkPolicyBitsReadAsDataSaverAndBackgroundData() {
        assertEquals(SpecialAccess.DENIED, SpecialAccess.unrestrictedDataStatus(0));
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.unrestrictedDataStatus(ALLOW));
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.backgroundDataStatus(0));
        assertEquals(SpecialAccess.DENIED, SpecialAccess.backgroundDataStatus(REJECT));
        assertEquals(SpecialAccess.GRANTED, SpecialAccess.backgroundDataStatus(ALLOW | OEM_BIT));
    }

    @Test
    public void unrestrictedDataClearsTheBlockAndKeepsOtherBits() {
        assertEquals(ALLOW | OEM_BIT, SpecialAccess.withUnrestrictedData(REJECT | OEM_BIT, true));
        assertEquals(OEM_BIT, SpecialAccess.withUnrestrictedData(ALLOW | OEM_BIT, false));
        // Turning it off leaves a background data block alone
        assertEquals(REJECT, SpecialAccess.withUnrestrictedData(REJECT, false));
    }

    @Test
    public void blockingBackgroundDataDropsUnrestrictedData() {
        assertEquals(REJECT | OEM_BIT, SpecialAccess.withBackgroundData(ALLOW | OEM_BIT, false));
        assertEquals(OEM_BIT, SpecialAccess.withBackgroundData(REJECT | OEM_BIT, true));
        // Allowing it again doesn't grant unrestricted data on its own
        assertEquals(0, SpecialAccess.withBackgroundData(REJECT, true));
        assertEquals(ALLOW, SpecialAccess.withBackgroundData(ALLOW, true));
    }
}
