// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.os.Build;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

@RunWith(RobolectricTestRunner.class)
public class RunningAppsViewModelTest {
    @Test
    public void getBackgroundRunAppOpsForSdk_returnsNoOpsBeforeNougat() {
        assertArrayEquals(new int[0],
                RunningAppsViewModel.getBackgroundRunAppOpsForSdk(Build.VERSION_CODES.M));
    }

    @Test
    public void getBackgroundRunAppOpsForSdk_returnsRunInBackgroundOnNougat() {
        assertArrayEquals(new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND},
                RunningAppsViewModel.getBackgroundRunAppOpsForSdk(Build.VERSION_CODES.N));
    }

    @Test
    public void getBackgroundRunAppOpsForSdk_returnsBothBackgroundOpsOnPie() {
        assertArrayEquals(new int[]{
                        AppOpsManagerCompat.OP_RUN_IN_BACKGROUND,
                        AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND,
                },
                RunningAppsViewModel.getBackgroundRunAppOpsForSdk(Build.VERSION_CODES.P));
    }

    @Test
    public void canRunInBackground_returnsFalseWhenAllBackgroundOpsRestricted() {
        assertFalse(BackgroundRunAppOpPlan.canRunInBackground(new int[]{
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED,
        }));
    }

    @Test
    public void canRunInBackground_returnsTrueWhenAnyBackgroundOpAllowed() {
        assertTrue(BackgroundRunAppOpPlan.canRunInBackground(new int[]{
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ALLOWED,
        }));
    }

    @Test
    public void hasBackgroundRestriction_returnsTrueForMixedBackgroundModes() {
        assertTrue(BackgroundRunAppOpPlan.hasBackgroundRestriction(new int[]{
                AppOpsManager.MODE_ALLOWED,
                AppOpsManager.MODE_IGNORED,
        }));
    }

    @Test
    public void createRestorePlan_restoresNougatBackgroundOpToDefault() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND},
                new int[]{AppOpsManager.MODE_IGNORED},
                null);

        assertEquals(1, plan.size());
        assertEquals(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, plan.get(0).op);
        assertEquals(AppOpsManager.MODE_IGNORED, plan.get(0).previousMode);
        assertEquals(AppOpsManager.MODE_DEFAULT, plan.get(0).restoreMode);
    }

    @Test
    public void createRestorePlan_restoresBothPieBackgroundOpsToDefault() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{
                        AppOpsManagerCompat.OP_RUN_IN_BACKGROUND,
                        AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND,
                },
                new int[]{
                        AppOpsManager.MODE_IGNORED,
                        AppOpsManager.MODE_ERRORED,
                },
                null);

        assertEquals(2, plan.size());
        assertEquals(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, plan.get(0).op);
        assertEquals(AppOpsManager.MODE_DEFAULT, plan.get(0).restoreMode);
        assertEquals(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, plan.get(1).op);
        assertEquals(AppOpsManager.MODE_DEFAULT, plan.get(1).restoreMode);
    }

    @Test
    public void createRestorePlan_keepsUnrestrictedModesUntouched() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{
                        AppOpsManagerCompat.OP_RUN_IN_BACKGROUND,
                        AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND,
                },
                new int[]{
                        AppOpsManager.MODE_ALLOWED,
                        AppOpsManager.MODE_IGNORED,
                },
                null);

        assertEquals(1, plan.size());
        assertEquals(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, plan.get(0).op);
        assertEquals(AppOpsManager.MODE_DEFAULT, plan.get(0).restoreMode);
    }

    @Test
    public void createRestorePlan_reusesKnownUnrestrictedPreviousMode() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND},
                new int[]{AppOpsManager.MODE_IGNORED},
                new int[]{AppOpsManager.MODE_ALLOWED});

        assertEquals(1, plan.size());
        assertEquals(AppOpsManager.MODE_ALLOWED, plan.get(0).restoreMode);
    }

    @Test
    public void createRestorePlan_ignoresRestrictedPreviousMode() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND},
                new int[]{AppOpsManager.MODE_IGNORED},
                new int[]{AppOpsManager.MODE_ERRORED});

        assertEquals(1, plan.size());
        assertEquals(AppOpsManager.MODE_DEFAULT, plan.get(0).restoreMode);
    }

    @Test
    public void formatBackgroundRunRestoreDetail_listsChangedOpsAndModes() {
        List<BackgroundRunAppOpPlan.OpModeChange> plan = BackgroundRunAppOpPlan.createRestorePlan(
                new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND},
                new int[]{AppOpsManager.MODE_IGNORED},
                null);

        String details = RunningAppsViewModel.formatBackgroundRunRestoreDetail(plan);

        assertTrue(details.contains(AppOpsManagerCompat.opToName(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND)));
        assertTrue(details.contains(AppOpsManagerCompat.modeToName(AppOpsManager.MODE_IGNORED)));
        assertTrue(details.contains(AppOpsManagerCompat.modeToName(AppOpsManager.MODE_DEFAULT)));
    }
}
