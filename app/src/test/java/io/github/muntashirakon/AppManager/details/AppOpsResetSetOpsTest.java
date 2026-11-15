// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.app.AppOpsManager;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

/**
 * Reset images every op the package has set. Before Android 8 the package's entries don't list UID modes, and the
 * entries can fail to load, so those cases read every op instead of trusting the list.
 */
@RunWith(RobolectricTestRunner.class)
public class AppOpsResetSetOpsTest {
    private static final int OP_CAMERA = 26;

    private static int cameraDenied(int op) {
        return op == OP_CAMERA ? AppOpsManager.MODE_IGNORED : AppOpsManagerCompat.opToDefaultMode(op);
    }

    @Test
    public void onAndroid7AUidModeMissingFromTheEntriesIsStillReset() throws Exception {
        assertEquals(AppOpsManager.MODE_ALLOWED, AppOpsManagerCompat.opToDefaultMode(OP_CAMERA));

        int[] ops = AppDetailsViewModel.setOps(Collections.emptyList(), Build.VERSION_CODES.N_MR1,
                AppOpsResetSetOpsTest::cameraDenied);

        assertArrayEquals(new int[]{OP_CAMERA}, ops);
    }

    @Test
    public void entriesThatCantBeReadFallBackToReadingEachOp() throws Exception {
        int[] ops = AppDetailsViewModel.setOps(null, Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                AppOpsResetSetOpsTest::cameraDenied);

        assertArrayEquals(new int[]{OP_CAMERA}, ops);
    }

    @Test
    public void onAndroid8AndLaterTheEntriesAreTrusted() throws Exception {
        int[] ops = AppDetailsViewModel.setOps(Collections.emptyList(), Build.VERSION_CODES.O, op -> {
            throw new AssertionError("Read op " + op + " though the entries already list UID modes");
        });

        assertArrayEquals(new int[0], ops);
    }
}
