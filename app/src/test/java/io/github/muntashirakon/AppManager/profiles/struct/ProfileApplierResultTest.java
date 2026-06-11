// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Contract for {@link ProfileApplierResult} failure aggregation (2026-06-11 audit fix): a profile
 * is "successful" only when every sub-operation succeeded for every package. Previously failures
 * were merely logged and the profile always reported success.
 */
public class ProfileApplierResultTest {

    @Test
    public void freshResultIsSuccessful() {
        ProfileApplierResult result = new ProfileApplierResult();
        assertTrue(result.isSuccessful());
        assertTrue(result.getFailedPackages().isEmpty());
    }

    @Test
    public void recordingFailedPackagesMarksFailureAndCollectsThem() {
        ProfileApplierResult result = new ProfileApplierResult();
        result.recordFailedPackages(Arrays.asList("com.a", "com.b"));
        assertFalse(result.isSuccessful());
        assertTrue(result.getFailedPackages().contains("com.a"));
        assertTrue(result.getFailedPackages().contains("com.b"));
        assertEquals(2, result.getFailedPackages().size());
    }

    @Test
    public void duplicatePackagesAcrossOpsAreDeduped() {
        ProfileApplierResult result = new ProfileApplierResult();
        // Same package failing multiple sub-operations (components + app ops + permissions).
        result.recordFailedPackages(Collections.singletonList("com.a"));
        result.recordFailedPackages(Collections.singletonList("com.a"));
        result.recordFailedPackages(Collections.singletonList("com.b"));
        assertEquals(2, result.getFailedPackages().size());
    }

    @Test
    public void markFailedWithoutPackagesStillReportsFailure() {
        // e.g. an invalid state resolves every op to OP_NONE: all-pairs-failed with no names.
        ProfileApplierResult result = new ProfileApplierResult();
        result.markFailed();
        assertFalse(result.isSuccessful());
    }

    @Test
    public void emptyFailureListLeavesResultSuccessful() {
        ProfileApplierResult result = new ProfileApplierResult();
        result.recordFailedPackages(Collections.emptyList());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void skippedOperationsMarkPartialFailureAndAreDeduped() {
        ProfileApplierResult result = new ProfileApplierResult();
        result.recordSkippedOperations(Arrays.asList(
                AppsBaseProfile.PROFILE_OP_FREEZE,
                AppsBaseProfile.PROFILE_OP_FREEZE,
                AppsBaseProfile.PROFILE_OP_PERMISSIONS));

        assertFalse(result.isSuccessful());
        assertTrue(result.hasSkippedOperations());
        assertEquals(2, result.getSkippedOperations().size());
        assertTrue(result.getSkippedOperations().contains(AppsBaseProfile.PROFILE_OP_FREEZE));
        assertTrue(result.getSkippedOperations().contains(AppsBaseProfile.PROFILE_OP_PERMISSIONS));
    }

    @Test
    public void requiresRestartIsIndependentOfFailureState() {
        ProfileApplierResult result = new ProfileApplierResult();
        result.setRequiresRestart(true);
        assertTrue(result.requiresRestart());
        assertTrue(result.isSuccessful());
    }
}
