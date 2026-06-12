// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class BatchOpsCriticalPackageGuardTest {
    @Test
    public void uninstallBlocksCriticalPackageBeforeSystemOperation() {
        BatchOpsManager manager = new BatchOpsManager(null);
        BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(
                BatchOpsManager.OP_UNINSTALL,
                Collections.singletonList("android"),
                Collections.singletonList(0),
                null);

        BatchOpsManager.Result result = manager.performOp(info, null);

        assertFalse(result.isSuccessful());
        assertEquals(Collections.singletonList("android"), result.getFailedPackages());
        assertEquals(Collections.singletonList(0), result.getAssociatedUsers());
    }

    @Test
    public void forceStopBlocksCriticalPackageBeforeSystemOperation() {
        BatchOpsManager manager = new BatchOpsManager(null);
        BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(
                BatchOpsManager.OP_FORCE_STOP,
                Collections.singletonList("android"),
                Collections.singletonList(0),
                null);

        BatchOpsManager.Result result = manager.performOp(info, null);

        assertFalse(result.isSuccessful());
        assertEquals(Collections.singletonList("android"), result.getFailedPackages());
        assertEquals(Collections.singletonList(0), result.getAssociatedUsers());
    }
}
