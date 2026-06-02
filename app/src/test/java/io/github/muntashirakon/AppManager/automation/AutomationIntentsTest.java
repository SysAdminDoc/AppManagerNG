// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;

public class AutomationIntentsTest {
    @Test
    public void recognizesDocumentedAutomationActions() {
        assertTrue(AutomationIntents.isAutomationAction(AutomationIntents.ACTION_FREEZE));
        assertTrue(AutomationIntents.isAutomationAction(AutomationIntents.ACTION_INSTALL_FROM_URI));
        assertTrue(AutomationIntents.isAutomationAction(AutomationIntents.ACTION_SCAN_TRACKERS));
    }

    @Test
    public void mapsBatchActionsToExistingBatchOps() {
        assertEquals(Integer.valueOf(BatchOpsManager.OP_FREEZE),
                AutomationIntents.getBatchOpForAction(AutomationIntents.ACTION_FREEZE));
        assertEquals(Integer.valueOf(BatchOpsManager.OP_UNFREEZE),
                AutomationIntents.getBatchOpForAction(AutomationIntents.ACTION_UNFREEZE));
        assertEquals(Integer.valueOf(BatchOpsManager.OP_BLOCK_COMPONENTS),
                AutomationIntents.getBatchOpForAction(AutomationIntents.ACTION_DISABLE_COMPONENT));
        assertNull(AutomationIntents.getBatchOpForAction(AutomationIntents.ACTION_RUN_PROFILE));
    }

    @Test
    public void normalizesRelativeAndFlattenedComponentNames() {
        assertEquals("com.example.app.Receiver",
                AutomationIntents.normalizeComponentName("com.example.app", ".Receiver"));
        assertEquals("com.example.app.Receiver",
                AutomationIntents.normalizeComponentName("com.example.app", "Receiver"));
        assertEquals("com.example.app.Receiver",
                AutomationIntents.normalizeComponentName("com.example.app", "com.example.app/.Receiver"));
        assertEquals("com.other.Receiver",
                AutomationIntents.normalizeComponentName("com.example.app", "com.other.Receiver"));
    }

    @Test
    public void coerceBooleanAcceptsStringFormsAutomationToolsSend() {
        // The dry-run safety bug: a String "true" extra must coerce to true, not
        // fall back to the (execute-for-real) default. Tasker/am-broadcast send
        // extras as strings.
        assertTrue(AutomationIntents.coerceBoolean("true", false));
        assertTrue(AutomationIntents.coerceBoolean("1", false));
        assertTrue(AutomationIntents.coerceBoolean("YES", false));
        assertTrue(AutomationIntents.coerceBoolean(" on ", false));
        assertTrue(AutomationIntents.coerceBoolean(Boolean.TRUE, false));
        assertFalse(AutomationIntents.coerceBoolean("false", true));
        assertFalse(AutomationIntents.coerceBoolean("0", true));
        assertFalse(AutomationIntents.coerceBoolean("off", true));
        // null and unrecognised values fall back.
        assertTrue(AutomationIntents.coerceBoolean(null, true));
        assertFalse(AutomationIntents.coerceBoolean(null, false));
        assertTrue(AutomationIntents.coerceBoolean("maybe", true));
    }

    @Test
    public void coerceIntAcceptsStringAndNumericForms() {
        assertEquals(7, AutomationIntents.coerceInt("7", 0));
        assertEquals(7, AutomationIntents.coerceInt(" 7 ", 0));
        assertEquals(7, AutomationIntents.coerceInt(7, 0));
        assertEquals(7, AutomationIntents.coerceInt(7L, 0));
        assertEquals(-1, AutomationIntents.coerceInt(null, -1));
        assertEquals(-1, AutomationIntents.coerceInt("not-a-number", -1));
    }

    @Test
    public void splitValuesHandlesCommaAndNewlineSeparatedStrings() {
        List<String> values = AutomationIntents.splitValues("a.b, c.d\n e.f ");
        assertEquals(3, values.size());
        assertEquals("a.b", values.get(0));
        assertEquals("c.d", values.get(1));
        assertEquals("e.f", values.get(2));
        assertTrue(AutomationIntents.splitValues(null).isEmpty());
        assertTrue(AutomationIntents.splitValues("  ").isEmpty());
    }
}
