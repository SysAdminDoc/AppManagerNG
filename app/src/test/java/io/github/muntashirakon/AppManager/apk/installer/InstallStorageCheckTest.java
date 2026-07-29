// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.storage.StorageManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The installer promises not to start an install it cannot finish. The gate has to block on
 * evidence and only on evidence: an unknown size must not silently read as zero, and a sum that
 * cannot be represented must not wrap into a small, satisfiable number.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallStorageCheckTest {
    private static final long MB = 1024L * 1024L;

    @Test
    public void aSelectionThatFitsIsAllowed() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{40 * MB, 10 * MB}, 0, 1);
        assertEquals(50 * MB, estimate.totalBytes);
        assertEquals(InstallStorageCheck.Status.OK,
                InstallStorageCheck.check(estimate, 500 * MB).status);
    }

    @Test
    public void aSelectionLargerThanTheFreeSpaceIsRefused() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{400 * MB}, 0, 1);
        InstallStorageCheck.Result result = InstallStorageCheck.check(estimate, 100 * MB);
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT, result.status);
        assertTrue(result.isBlocking());
        assertEquals(400 * MB, result.getRequiredBytes());
        assertEquals(100 * MB, result.freeBytes);
    }

    @Test
    public void requiringExactlyTheFreeSpaceIsAllowed() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(new long[]{128 * MB}, 0, 1);
        assertEquals(InstallStorageCheck.Status.OK,
                InstallStorageCheck.check(estimate, 128 * MB).status);
    }

    @Test
    public void aZeroFreeVolumeCannotHoldAnything() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(new long[]{1}, 0, 1);
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT,
                InstallStorageCheck.check(estimate, 0).status);
    }

    // --- unknown ---

    @Test
    public void oneUnsizedSplitMakesTheWholeEstimateUnknown() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{40 * MB, -1, 10 * MB}, 0, 1);
        assertTrue(estimate.isUnknown());
        assertEquals(InstallStorageCheck.UNKNOWN, estimate.apkBytes);
    }

    @Test
    public void anUnsizedExpansionFileMakesTheEstimateUnknownRatherThanZero() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{40 * MB}, InstallStorageCheck.UNKNOWN, 1);
        assertTrue(estimate.isUnknown());
        // The known half is still reported so the failure can be explained.
        assertEquals(40 * MB, estimate.apkBytes);
    }

    @Test
    public void anUnknownRequirementDoesNotGateTheInstall() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(new long[]{-1}, 0, 1);
        InstallStorageCheck.Result result = InstallStorageCheck.check(estimate, 1);
        assertEquals(InstallStorageCheck.Status.UNKNOWN, result.status);
        assertFalse(result.isBlocking());
    }

    @Test
    public void unknownFreeSpaceDoesNotGateTheInstall() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(new long[]{400 * MB}, 0, 1);
        InstallStorageCheck.Result result = InstallStorageCheck.check(estimate, InstallStorageCheck.UNKNOWN);
        assertEquals(InstallStorageCheck.Status.UNKNOWN, result.status);
        assertFalse(result.isBlocking());
        assertEquals(InstallStorageCheck.UNKNOWN, result.freeBytes);
    }

    @Test
    public void anEmptySelectionNeedsNothing() {
        assertEquals(InstallStorageCheck.Status.OK,
                InstallStorageCheck.check(InstallStorageCheck.estimate(new long[0], 0, 1), 0).status);
        assertEquals(InstallStorageCheck.Status.OK,
                InstallStorageCheck.check(InstallStorageCheck.estimate(null, 0, 1), 0).status);
    }

    // --- overflow ---

    @Test
    public void splitsThatSumPastTheLongRangeAreRefusedNotWrapped() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{Long.MAX_VALUE, Long.MAX_VALUE}, 0, 1);
        assertTrue(estimate.isOverflow());
        InstallStorageCheck.Result result = InstallStorageCheck.check(estimate, 100 * MB);
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT, result.status);
        assertTrue(result.isBlocking());
    }

    @Test
    public void expansionFilesMultipliedPastTheLongRangeAreRefusedNotWrapped() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{MB}, Long.MAX_VALUE / 2, 8);
        assertTrue(estimate.isOverflow());
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT,
                InstallStorageCheck.check(estimate, Long.MAX_VALUE).status);
    }

    @Test
    public void overflowIsRefusedEvenAgainstAnUnknownVolume() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{Long.MAX_VALUE, 1}, 0, 1);
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT,
                InstallStorageCheck.check(estimate, InstallStorageCheck.UNKNOWN).status);
    }

    @Test
    public void checkedArithmeticReportsOverflowInsteadOfWrapping() {
        assertEquals(3, InstallStorageCheck.addChecked(1, 2));
        assertEquals(InstallStorageCheck.OVERFLOW, InstallStorageCheck.addChecked(Long.MAX_VALUE, 1));
        assertEquals(0, InstallStorageCheck.multiplyChecked(0, Long.MAX_VALUE));
        assertEquals(6, InstallStorageCheck.multiplyChecked(2, 3));
        assertEquals(InstallStorageCheck.OVERFLOW,
                InstallStorageCheck.multiplyChecked(Long.MAX_VALUE, 2));
    }

    // --- multi-user ---

    @Test
    public void expansionFilesAreCountedOncePerUserPlusTheStagingCopy() {
        // 10 MB of OBBs installed for 3 users: one private staging copy + three activated copies.
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(
                new long[]{20 * MB}, 10 * MB, 3);
        assertEquals(3, estimate.userCount);
        assertEquals(20 * MB + (4 * 10 * MB), estimate.totalBytes);
    }

    @Test
    public void moreUsersCanTurnASufficientInstallIntoAnInsufficientOne() {
        long free = 100 * MB;
        InstallStorageCheck.Estimate single = InstallStorageCheck.estimate(new long[]{20 * MB}, 20 * MB, 1);
        assertEquals(InstallStorageCheck.Status.OK, InstallStorageCheck.check(single, free).status);

        InstallStorageCheck.Estimate many = InstallStorageCheck.estimate(new long[]{20 * MB}, 20 * MB, 5);
        assertEquals(InstallStorageCheck.Status.INSUFFICIENT, InstallStorageCheck.check(many, free).status);
    }

    @Test
    public void aUserCountBelowOneIsTreatedAsASingleUser() {
        InstallStorageCheck.Estimate estimate = InstallStorageCheck.estimate(new long[]{MB}, MB, 0);
        assertEquals(1, estimate.userCount);
        assertEquals(MB + (2 * MB), estimate.totalBytes);
    }

    @Test
    public void withoutExpansionFilesTheUserCountDoesNotChangeTheRequirement() {
        assertEquals(InstallStorageCheck.estimate(new long[]{50 * MB}, 0, 1).totalBytes,
                InstallStorageCheck.estimate(new long[]{50 * MB}, 0, 9).totalBytes);
    }

    // --- platform surface ---

    @Test
    public void theVolumeProbeReturnsAUsableFigureOrAnHonestUnknown() {
        Context context = ApplicationProvider.getApplicationContext();
        long free = InstallStorageCheck.getAllocatableBytesForInstall(context);
        assertTrue("free space must be a byte count or UNKNOWN",
                free > 0 || free == InstallStorageCheck.UNKNOWN);
    }

    @Test
    public void theRecoveryOfferCarriesTheRequiredByteCount() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = InstallStorageCheck.buildManageStorageIntent(context, 512 * MB);
        // The test runs on a modern SDK level, so the offer must exist.
        assertTrue(InstallStorageCheck.canOfferManageStorage());
        assertNotNull(intent);
        assertEquals(StorageManager.ACTION_MANAGE_STORAGE, intent.getAction());
        assertEquals(512 * MB, intent.getLongExtra(StorageManager.EXTRA_REQUESTED_BYTES, -1));
    }
}
