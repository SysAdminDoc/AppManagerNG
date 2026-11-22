// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
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
                false, false, false, false, false, true);

        assertEquals(InstallerPrivilegeCascade.ROUTE_SYSTEM_CONFIRMATION, plan.selectedRoute);
        assertNull(plan.selectedMode);
        assertEquals(Arrays.asList(InstallerPrivilegeCascade.ROUTE_SYSTEM_CONFIRMATION,
                InstallerPrivilegeCascade.ROUTE_MIUI_NUDGE), routes(plan));
    }

    @Test
    public void dhizukuInstallsWhenNoShellRouteIsReady() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, false, false, false, true, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_DHIZUKU, plan.selectedRoute);
        // Dhizuku isn't an operating mode, so nothing switches
        assertNull(plan.selectedMode);
        assertEquals(Collections.singletonList(InstallerPrivilegeCascade.ROUTE_DHIZUKU), routes(plan));
        assertTrue(plan.steps.get(0).selected);
        assertEquals(R.string.installer_privilege_cascade_summary_elevated, plan.getSummaryRes());
    }

    @Test
    public void dhizukuComesAfterAdbShizukuAndRoot() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, true, true, true, true, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_ADB, plan.selectedRoute);
        assertEquals(Arrays.asList(InstallerPrivilegeCascade.ROUTE_ADB, InstallerPrivilegeCascade.ROUTE_SHIZUKU,
                InstallerPrivilegeCascade.ROUTE_ROOT, InstallerPrivilegeCascade.ROUTE_DHIZUKU), routes(plan));
        assertFalse(plan.steps.get(3).selected);
    }

    @Test
    public void dhizukuIsSelectedBeforeTheSystemPromptButAfterRoot() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                false, false, false, true, true, false);

        assertEquals(InstallerPrivilegeCascade.ROUTE_ROOT, plan.selectedRoute);
        assertEquals(Arrays.asList(InstallerPrivilegeCascade.ROUTE_ROOT, InstallerPrivilegeCascade.ROUTE_DHIZUKU),
                routes(plan));
    }

    @Test
    public void currentPrivilegedModeNeedsNoDhizukuStep() {
        InstallerPrivilegeCascade.Plan plan = InstallerPrivilegeCascade.buildPlan(
                true, false, false, false, true, false);

        assertEquals(Collections.singletonList(InstallerPrivilegeCascade.ROUTE_CURRENT), routes(plan));
    }

    private static List<String> routes(InstallerPrivilegeCascade.Plan plan) {
        List<String> routes = new ArrayList<>();
        for (InstallerPrivilegeCascade.Step step : plan.steps) {
            routes.add(step.route);
        }
        return routes;
    }
}
