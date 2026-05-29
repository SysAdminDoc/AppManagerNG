// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApkDuplicateSelectorTest {

    private static ApkDuplicateSelector.Candidate apk(String path, String pkg, long versionCode,
                                                       String cert, long size) {
        return new ApkDuplicateSelector.Candidate(new File(path), pkg, versionCode, cert, size);
    }

    @Test
    public void emptyListIsEmpty() {
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                Collections.<ApkDuplicateSelector.Candidate>emptyList(),
                ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void uniqueCandidatesProduceNoGroups() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/a.apk", "com.foo", 1L, "AA", 1000L),
                apk("/dl/b.apk", "com.bar", 1L, "AA", 1000L));
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                candidates, ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void largestStrategyKeepsBiggestFile() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/small.apk", "com.foo", 1L, "AA", 500L),
                apk("/dl/large.apks", "com.foo", 1L, "AA", 4500L),
                apk("/dl/medium.apk", "com.foo", 1L, "AA", 1500L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.LARGEST);
        assertEquals(1, groups.size());
        assertEquals(4500L, groups.get(0).keeper.sizeBytes);
        assertEquals(2, groups.get(0).drop.size());
    }

    @Test
    public void smallestStrategyKeepsSmallestFile() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/small.apk", "com.foo", 1L, "AA", 500L),
                apk("/dl/large.apks", "com.foo", 1L, "AA", 4500L),
                apk("/dl/medium.apk", "com.foo", 1L, "AA", 1500L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.SMALLEST);
        assertEquals(1, groups.size());
        assertEquals(500L, groups.get(0).keeper.sizeBytes);
    }

    @Test
    public void signingCertDimensionSeparatesForkedBuilds() {
        // Same package and versionCode but different signing cert: NOT a duplicate
        // because installing one over the other would fail signature mismatch.
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/official.apk", "com.foo", 1L, "AA", 1000L),
                apk("/dl/fork.apk", "com.foo", 1L, "BB", 1000L));
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                candidates, ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void versionCodeDimensionSeparatesUpgrades() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/v1.apk", "com.foo", 1L, "AA", 1000L),
                apk("/dl/v2.apk", "com.foo", 2L, "AA", 1000L));
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                candidates, ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void candidatesWithoutVersionCodeAreSkipped() {
        // No way to compare them safely - they are not in any duplicate group.
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/x.apk", "com.foo", 0L, "AA", 1000L),
                apk("/dl/y.apk", "com.foo", 0L, "AA", 1000L));
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                candidates, ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void candidatesWithEmptyPackageNameAreSkipped() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/x.apk", "", 1L, "AA", 1000L),
                apk("/dl/y.apk", "", 1L, "AA", 1000L));
        assertTrue(ApkDuplicateSelector.selectDuplicates(
                candidates, ApkDuplicateSelector.KeepStrategy.LARGEST).isEmpty());
    }

    @Test
    public void nullSigningCertGroupsTogether() {
        // Candidates with no signing-cert information still bucket by package
        // and version, falling back to the empty-string slot. This is the
        // legacy / unsigned-archive path.
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/a.apk", "com.foo", 1L, null, 1000L),
                apk("/dl/b.apk", "com.foo", 1L, null, 1500L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.LARGEST);
        assertEquals(1, groups.size());
        assertEquals(1500L, groups.get(0).keeper.sizeBytes);
    }

    @Test
    public void sizeTieBreaksOnAbsolutePathDeterministically() {
        ApkDuplicateSelector.Candidate a = apk("/dl/alpha.apk", "com.foo", 1L, "AA", 1000L);
        ApkDuplicateSelector.Candidate b = apk("/dl/beta.apk", "com.foo", 1L, "AA", 1000L);
        ApkDuplicateSelector.Candidate c = apk("/dl/gamma.apk", "com.foo", 1L, "AA", 1000L);
        List<ApkDuplicateSelector.DuplicateGroup> g1 = ApkDuplicateSelector.selectDuplicates(
                Arrays.asList(a, b, c), ApkDuplicateSelector.KeepStrategy.LARGEST);
        List<ApkDuplicateSelector.DuplicateGroup> g2 = ApkDuplicateSelector.selectDuplicates(
                Arrays.asList(c, b, a), ApkDuplicateSelector.KeepStrategy.LARGEST);
        assertEquals(1, g1.size());
        assertEquals(1, g2.size());
        // Tie-breaker prefers the lexicographically smallest path - alpha.apk.
        // Compare the basename so the assertion is independent of the host's
        // absolute-path form (a Windows getAbsolutePath() adds a "C:\" drive
        // prefix that the original Unix-only literal did not account for).
        assertEquals("alpha.apk", g1.get(0).keeper.path.getName());
        assertEquals(g1.get(0).keeper.path.getAbsolutePath(),
                g2.get(0).keeper.path.getAbsolutePath());
    }

    @Test
    public void multiplePackagesProduceMultipleGroups() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/foo1.apk", "com.foo", 1L, "AA", 1000L),
                apk("/dl/foo2.apk", "com.foo", 1L, "AA", 2000L),
                apk("/dl/bar1.apk", "com.bar", 1L, "AA", 500L),
                apk("/dl/bar2.apk", "com.bar", 1L, "AA", 1500L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.LARGEST);
        assertEquals(2, groups.size());
        Set<String> packages = new HashSet<>();
        for (ApkDuplicateSelector.DuplicateGroup g : groups) packages.add(g.packageName);
        assertTrue(packages.contains("com.foo"));
        assertTrue(packages.contains("com.bar"));
    }

    @Test
    public void reclaimableBytesSumsDropOnly() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/big.apk", "com.foo", 1L, "AA", 10_000L),
                apk("/dl/small.apk", "com.foo", 1L, "AA", 1_000L),
                apk("/dl/tiny.apk", "com.foo", 1L, "AA", 500L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.LARGEST);
        // Keep big (10000), drop small+tiny -> reclaim 1500.
        assertEquals(1_500L, ApkDuplicateSelector.reclaimableBytes(groups));
        assertNotEquals(11_500L, ApkDuplicateSelector.reclaimableBytes(groups));
    }

    @Test
    public void unknownSizesContributeZeroToReclaim() {
        List<ApkDuplicateSelector.Candidate> candidates = Arrays.asList(
                apk("/dl/known.apk", "com.foo", 1L, "AA", 5_000L),
                apk("/dl/unknown.apk", "com.foo", 1L, "AA", -1L),
                apk("/dl/zero.apk", "com.foo", 1L, "AA", 0L));
        List<ApkDuplicateSelector.DuplicateGroup> groups =
                ApkDuplicateSelector.selectDuplicates(candidates,
                        ApkDuplicateSelector.KeepStrategy.LARGEST);
        assertEquals(0L, ApkDuplicateSelector.reclaimableBytes(groups));
    }
}
