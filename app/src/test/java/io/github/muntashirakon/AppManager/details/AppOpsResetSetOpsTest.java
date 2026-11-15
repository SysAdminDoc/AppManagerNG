// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.app.AppOpsManager;
import android.os.Build;
import android.os.Parcelable;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

/**
 * Reset images every op the package has set. Before Android 8 the package's entries don't list UID modes, and the
 * entries can fail to load, so those cases read every op instead of trusting the list.
 */
@RunWith(RobolectricTestRunner.class)
public class AppOpsResetSetOpsTest {
    private static final int OP_COARSE_LOCATION = 0;
    private static final int OP_FINE_LOCATION = 1;
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
    public void anOpThatCantBeReadIsSkippedAndTheRestStillCount() throws Exception {
        int[] ops = AppDetailsViewModel.setOps(null, Build.VERSION_CODES.UPSIDE_DOWN_CAKE, op -> {
            if (op == OP_COARSE_LOCATION) {
                throw new IllegalArgumentException("Unknown operation number " + op);
            }
            if (op == OP_FINE_LOCATION) {
                throw new SecurityException("uid 10031 does not have android.permission.UPDATE_APP_OPS_STATS");
            }
            return cameraDenied(op);
        });

        assertArrayEquals(new int[]{OP_CAMERA}, ops);
    }

    @Test
    public void whenNothingCanBeReadTheResetStops() {
        assertThrows(RemoteException.class, () -> AppDetailsViewModel.setOps(null,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, op -> {
                    throw new SecurityException("uid 10031 does not have android.permission.GET_APP_OPS_STATS");
                }));
    }

    @Test
    public void onAndroid7EntriesStillCountWhenNoOpCanBeRead() throws Exception {
        Parcelable cameraIgnored = (Parcelable) Class.forName("android.app.AppOpsManager$OpEntry")
                .getConstructor(int.class, int.class, Map.class)
                .newInstance(OP_CAMERA, AppOpsManager.MODE_IGNORED, Collections.emptyMap());
        List<AppOpsManagerCompat.OpEntry> entries = Collections.singletonList(
                new AppOpsManagerCompat.OpEntry(cameraIgnored));

        int[] ops = AppDetailsViewModel.setOps(entries, Build.VERSION_CODES.N_MR1, op -> {
            throw new SecurityException("uid 10031 does not have android.permission.GET_APP_OPS_STATS");
        });

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
