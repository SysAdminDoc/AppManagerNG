// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Process;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.safety.AppOpsUidGuard;

@RunWith(RobolectricTestRunner.class)
public class RunningAppsUidReviewTest {
    @Test
    public void backgroundRunPreflightDisclosesEveryOperation() {
        int[] operations = new int[]{63, 70};

        AppOpsUidGuard.UnsafeUidMutationException error = assertThrows(
                AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> RunningAppsViewModel.requireBackgroundRunAppOpsAllowed(
                        Process.FIRST_APPLICATION_UID + 9, "com.example.player", operations,
                        AppOpsUidGuard.MutationSource.DIRECT,
                        uid -> new String[]{"com.example.player", "com.example.widget"}));

        assertEquals(Arrays.asList(63, 70), error.getImpact().getOperations());
    }
}
