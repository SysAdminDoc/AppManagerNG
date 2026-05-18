// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.github.muntashirakon.AppManager.settings.Ops;

public class InstallerPrivilegeCascadeTest {
    @Test
    public void currentPrivilegedModeWinsWithoutTemporarySwitch() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                true, true, true, true, false, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_CURRENT, plan.selectedRoute);
        assertNull(plan.selectedMode);
        assertEquals(1, plan.steps.size());
    }

    @Test
    public void adbIsLowestElevatedRouteBeforeShizukuAndRoot() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, true, true, true, false, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_ADB, plan.selectedRoute);
        assertEquals(Ops.MODE_ADB_OVER_TCP, plan.selectedMode);
        assertEquals(InstallerPrivilegeCascade.ROUTE_ADB, plan.steps.get(0).route);
        assertEquals(InstallerPrivilegeCascade.ROUTE_SHIZUKU, plan.steps.get(1).route);
        assertEquals(InstallerPrivilegeCascade.ROUTE_ROOT, plan.steps.get(2).route);
    }

    @Test
    public void shizukuIsSelectedWhenAdbIsUnavailable() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, false, true, true, false, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_SHIZUKU, plan.selectedRoute);
        assertEquals(Ops.MODE_SHIZUKU, plan.selectedMode);
        assertEquals(InstallerPrivilegeCascade.ROUTE_SHIZUKU, plan.steps.get(0).route);
        assertEquals(InstallerPrivilegeCascade.ROUTE_ROOT, plan.steps.get(1).route);
    }

    @Test
    public void rootIsSelectedWhenOnlyRootIsAvailable() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, false, false, true, false, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_ROOT, plan.selectedRoute);
        assertEquals(Ops.MODE_ROOT, plan.selectedMode);
    }

    @Test
    public void systemConfirmationIsFallbackWhenNoElevatedProviderIsReady() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, false, false, false, true, true);

        assertEquals(InstallerPrivilegeCascade.ROUTE_SYSTEM_CONFIRMATION, plan.selectedRoute);
        assertNull(plan.selectedMode);
        assertEquals(InstallerPrivilegeCascade.ROUTE_SYSTEM_CONFIRMATION, plan.steps.get(0).route);
        assertEquals(InstallerPrivilegeCascade.ROUTE_DHIZUKU_INFO, plan.steps.get(1).route);
        assertEquals(InstallerPrivilegeCascade.ROUTE_MIUI_NUDGE, plan.steps.get(2).route);
    }
}
