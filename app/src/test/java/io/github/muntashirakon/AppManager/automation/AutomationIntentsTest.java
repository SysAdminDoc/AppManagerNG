// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
