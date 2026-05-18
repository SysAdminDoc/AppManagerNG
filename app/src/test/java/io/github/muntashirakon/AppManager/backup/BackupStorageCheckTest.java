// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackupStorageCheckTest {

    @Test
    public void okWhenFreeGreatlyExceedsEstimate() {
        assertEquals(BackupStorageCheck.Status.OK,
                BackupStorageCheck.classify(1_000_000L, 10_000_000_000L, 64L * 1024 * 1024));
    }

    @Test
    public void warnsWhenHeadroomBelowSafetyMargin() {
        // 200MB estimated, 250MB free, 64MB safety margin -> headroom = 50MB -> WARN.
        assertEquals(BackupStorageCheck.Status.WARN_LOW_HEADROOM,
                BackupStorageCheck.classify(200L * 1024 * 1024,
                        250L * 1024 * 1024,
                        64L * 1024 * 1024));
    }

    @Test
    public void insufficientWhenFreeBelowEstimate() {
        assertEquals(BackupStorageCheck.Status.INSUFFICIENT,
                BackupStorageCheck.classify(500L * 1024 * 1024,
                        100L * 1024 * 1024,
                        64L * 1024 * 1024));
    }

    @Test
    public void unknownEitherSideReturnsOk() {
        // estimated == 0 (size unknown — caller couldn't read storage stats):
        assertEquals(BackupStorageCheck.Status.OK,
                BackupStorageCheck.classify(0, 100_000_000L, 64L * 1024 * 1024));
        // free == -1 (volume read failed):
        assertEquals(BackupStorageCheck.Status.OK,
                BackupStorageCheck.classify(100_000_000L, -1, 64L * 1024 * 1024));
    }

    @Test
    public void boundaryExactlyAtSafetyMargin() {
        // Exactly at margin counts as OK (we only WARN when strictly below).
        long est = 100L * 1024 * 1024;
        long margin = 64L * 1024 * 1024;
        long free = est + margin;
        assertEquals(BackupStorageCheck.Status.OK,
                BackupStorageCheck.classify(est, free, margin));
        // One byte less -> WARN.
        assertEquals(BackupStorageCheck.Status.WARN_LOW_HEADROOM,
                BackupStorageCheck.classify(est, free - 1, margin));
    }

    @Test
    public void zeroSafetyMarginNeverWarnsAsLongAsFreeMeetsEstimate() {
        assertEquals(BackupStorageCheck.Status.OK,
                BackupStorageCheck.classify(100L, 100L, 0L));
        assertEquals(BackupStorageCheck.Status.INSUFFICIENT,
                BackupStorageCheck.classify(101L, 100L, 0L));
    }
}
