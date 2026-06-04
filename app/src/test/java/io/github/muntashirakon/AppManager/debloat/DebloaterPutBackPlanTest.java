// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class DebloaterPutBackPlanTest {
    @Test
    public void fromSelectionKeepsRemovedRowsAndSkipsInstalledRows() {
        DebloatObject removed = debloatObject("com.example.removed", false);
        DebloatObject installed = debloatObject("com.example.installed", true);

        DebloaterPutBackPlan plan = DebloaterPutBackPlan.fromSelection(Arrays.asList(removed, installed));

        assertTrue(plan.hasRestorableTargets());
        assertEquals(1, plan.getRestorableCount());
        assertEquals(1, plan.getSkippedInstalledCount());
        assertEquals("com.example.removed", plan.getRestorableObjects().get(0).packageName);
        assertTrue(DebloaterPutBackPlan.isRestorable(removed));
        assertFalse(DebloaterPutBackPlan.isRestorable(installed));
    }

    @Test
    public void confirmationMessageReportsRestoredAndSkippedCounts() {
        Context context = RuntimeEnvironment.getApplication();
        DebloaterPutBackPlan plan = DebloaterPutBackPlan.fromSelection(Arrays.asList(
                debloatObject("com.example.removed", false),
                debloatObject("com.example.installed", true)));

        String message = plan.buildConfirmationMessage(context);

        assertTrue(message.contains("Put back 1 removed app"));
        assertTrue(message.contains("1 selected app is already installed"));
        assertTrue(message.contains("com.example.installed"));
    }

    @Test
    public void installedOnlySelectionHasNoRestorableTargets() {
        DebloaterPutBackPlan plan = DebloaterPutBackPlan.fromSelection(Arrays.asList(
                debloatObject("com.example.one", true),
                debloatObject("com.example.two", true)));

        assertFalse(plan.hasRestorableTargets());
        assertEquals(0, plan.getRestorableCount());
        assertEquals(2, plan.getSkippedInstalledCount());
    }

    private static DebloatObject debloatObject(String packageName, boolean installed) {
        DebloatObject debloatObject = new DebloatObject();
        debloatObject.packageName = packageName;
        debloatObject.setInstalledForTesting(installed);
        return debloatObject;
    }
}
