// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/** Small, host-testable classifiers for recent process exit diagnostics. */
public final class ExitHistoryDiagnostics {
    private static final String MEMORY_LIMITER_MARKER = "MemoryLimiter";

    private ExitHistoryDiagnostics() {
    }

    @VisibleForTesting
    static boolean isMemoryLimiterDescription(@Nullable String description) {
        return description != null && description.contains(MEMORY_LIMITER_MARKER);
    }
}
