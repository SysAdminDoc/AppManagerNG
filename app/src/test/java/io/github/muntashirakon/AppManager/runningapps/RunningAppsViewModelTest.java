// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import static org.junit.Assert.assertArrayEquals;

import android.os.Build;

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
}
