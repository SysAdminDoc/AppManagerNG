// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackageManagerCompatClearDataTest {
    @Test
    public void clearDataFallsBackWhenMeasuredDataDoesNotDropEnough() {
        long preSize = 512L * 1024L * 1024L;

        assertTrue(PackageManagerCompat.shouldFallbackToShellAfterClearData(
                preSize, preSize));
        assertTrue(PackageManagerCompat.shouldFallbackToShellAfterClearData(
                preSize, preSize - 1024L));
    }

    @Test
    public void clearDataTrustsIpcWhenMeasuredDataDropsPastTolerance() {
        long preSize = 512L * 1024L * 1024L;

        assertFalse(PackageManagerCompat.shouldFallbackToShellAfterClearData(
                preSize, 32L * 1024L));
    }

    @Test
    public void clearDataDoesNotFallBackWhenStatsAreUnavailable() {
        assertFalse(PackageManagerCompat.shouldFallbackToShellAfterClearData(
                -1L, 1024L));
        assertFalse(PackageManagerCompat.shouldFallbackToShellAfterClearData(
                1024L, -1L));
    }

    @Test
    public void pmClearOutputAcceptsOnlySuccessPrefix() {
        assertTrue(PackageManagerCompat.isSuccessfulPmClearOutput("Success\n"));
        assertTrue(PackageManagerCompat.isSuccessfulPmClearOutput("Success: cleared"));

        assertFalse(PackageManagerCompat.isSuccessfulPmClearOutput(null));
        assertFalse(PackageManagerCompat.isSuccessfulPmClearOutput(""));
        assertFalse(PackageManagerCompat.isSuccessfulPmClearOutput("Failed"));
        assertFalse(PackageManagerCompat.isSuccessfulPmClearOutput("Error: Success not granted"));
    }
}
