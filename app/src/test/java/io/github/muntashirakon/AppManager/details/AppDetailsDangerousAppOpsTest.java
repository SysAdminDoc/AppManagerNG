// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Process;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.safety.AppOpsUidGuard;

@RunWith(RobolectricTestRunner.class)
public class AppDetailsDangerousAppOpsTest {
    @Test
    public void dangerousAppOpsPreflightReportsEveryPendingOperationBeforeMutation() {
        int[] operations = new int[]{26, 27};

        AppOpsUidGuard.UnsafeUidMutationException error = assertThrows(
                AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppDetailsViewModel.requireDangerousAppOpsAllowed(
                        Process.FIRST_APPLICATION_UID + 4,
                        "com.example.camera", operations,
                        uid -> new String[]{"com.example.camera", "com.example.recorder"}));

        assertEquals(Arrays.asList(26, 27), error.getImpact().getOperations());
    }
}
