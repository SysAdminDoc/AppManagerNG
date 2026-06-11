// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions;

@RunWith(RobolectricTestRunner.class)
public class BatchOpsDexOptWarningsTest {
    @Test
    public void dexOptRootOnlySkipWarningNamesPackageAndSkippedActions() {
        DexOptOptions options = DexOptOptions.getDefault();
        options.clearProfileData = true;
        options.forceDexOpt = true;

        String warning = BatchOpsManager.getDexOptRootOnlySkipWarning(
                "com.example.app", options.sanitizeForExecution(false));

        assertTrue(warning, warning.contains("com.example.app"));
        assertTrue(warning, warning.contains("clear profile data"));
        assertTrue(warning, warning.contains("force dexopt"));
        assertTrue(warning, warning.contains("requires root/system privileges"));
    }

    @Test
    public void dexOptRootOnlySkipWarningIsAbsentWhenNothingWasSkipped() {
        DexOptOptions options = DexOptOptions.getDefault();
        options.clearProfileData = true;

        assertNull(BatchOpsManager.getDexOptRootOnlySkipWarning(
                "com.example.app", options.sanitizeForExecution(true)));
    }
}
