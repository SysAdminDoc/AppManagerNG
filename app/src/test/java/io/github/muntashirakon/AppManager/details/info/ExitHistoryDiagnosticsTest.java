// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExitHistoryDiagnosticsTest {
    @Test
    public void recognizesAndroid17MemoryLimiterDescriptions() {
        assertTrue(ExitHistoryDiagnostics.isMemoryLimiterDescription(
                "MemoryLimiter:AnonSwap anon_rss=512M swap=256M"));
        assertFalse(ExitHistoryDiagnostics.isMemoryLimiterDescription("low memory"));
        assertFalse(ExitHistoryDiagnostics.isMemoryLimiterDescription(null));
    }
}
