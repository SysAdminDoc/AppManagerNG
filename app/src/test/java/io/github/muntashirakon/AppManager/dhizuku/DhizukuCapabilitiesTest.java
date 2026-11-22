// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.dhizuku.DhizukuCapabilities.Operation;

public class DhizukuCapabilitiesTest {
    @Test
    public void stateFollowsInstallOwnerConnectionAndConsent() {
        assertEquals(DhizukuState.UNSUPPORTED_ANDROID, DhizukuState.of(22, true, true, true, true));
        assertEquals(DhizukuState.NOT_INSTALLED, DhizukuState.of(36, false, false, false, false));
        assertEquals(DhizukuState.NO_OWNER, DhizukuState.of(36, true, false, false, false));
        assertEquals(DhizukuState.NOT_CONNECTED, DhizukuState.of(36, true, true, false, false));
        assertEquals(DhizukuState.NOT_PERMITTED, DhizukuState.of(36, true, true, true, false));
        assertEquals(DhizukuState.PERMITTED, DhizukuState.of(36, true, true, true, true));
    }

    @Test
    public void forkThatAnswersCountsEvenWithoutTheOfficialApp() {
        // OwnDroid and other forks can serve the Dhizuku protocol under their own package
        assertEquals(DhizukuState.PERMITTED, DhizukuState.of(36, false, false, true, true));
        assertEquals(DhizukuState.NOT_PERMITTED, DhizukuState.of(36, false, false, true, false));
    }

    @Test
    public void nothingRunsUntilDhizukuAllowsThisApp() {
        for (DhizukuState state : DhizukuState.values()) {
            if (state == DhizukuState.PERMITTED) continue;
            assertEquals(state.name(), Collections.emptyList(), DhizukuCapabilities.runnable(state, 36));
            for (Operation operation : Operation.values()) {
                assertFalse(state + " " + operation, DhizukuCapabilities.canRun(state, 36, operation));
            }
        }
        assertEquals(Collections.emptyList(), DhizukuCapabilities.runnable(null, 36));
    }

    @Test
    public void permittedOperationsFollowTheirAndroidVersions() {
        assertEquals(Arrays.asList(Operation.HIDE_APPS, Operation.RUNTIME_PERMISSIONS,
                        Operation.INSTALL_APPS, Operation.UNINSTALL_APPS),
                DhizukuCapabilities.runnable(DhizukuState.PERMITTED, 23));
        assertEquals(Arrays.asList(Operation.HIDE_APPS, Operation.SUSPEND_APPS, Operation.RUNTIME_PERMISSIONS,
                        Operation.INSTALL_APPS, Operation.UNINSTALL_APPS),
                DhizukuCapabilities.runnable(DhizukuState.PERMITTED, 24));
        assertEquals(Arrays.asList(Operation.values()),
                DhizukuCapabilities.runnable(DhizukuState.PERMITTED, 28));
        assertFalse(DhizukuCapabilities.canRun(DhizukuState.PERMITTED, 27, Operation.CLEAR_APP_DATA));
        assertTrue(DhizukuCapabilities.canRun(DhizukuState.PERMITTED, 28, Operation.CLEAR_APP_DATA));
    }
}
