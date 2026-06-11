// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class InstallDependencyCheckerTest {
    @Test
    public void checkMinSdkReturnsNullWhenDeviceMeetsFloor() {
        assertNull(InstallDependencyChecker.checkMinSdk(21, 21));
        assertNull(InstallDependencyChecker.checkMinSdk(24, 34));
        assertNull(InstallDependencyChecker.checkMinSdk(33, 36));
    }

    @Test
    public void checkMinSdkReturnsIssueWhenDeviceBelowFloor() {
        InstallDependencyChecker.Issue issue = InstallDependencyChecker.checkMinSdk(34, 21);
        assertNotNull(issue);
        assertEquals(InstallDependencyChecker.IssueKind.MIN_SDK_TOO_HIGH, issue.kind);
        assertEquals(34, issue.requiredVersion);
        assertEquals(21, issue.actualVersion);
    }

    @Test
    public void checkMinSdkSkipsWhenAnyVersionUnknown() {
        // Pre-API-24 devices cannot read applicationInfo.minSdkVersion → input is 0.
        assertNull(InstallDependencyChecker.checkMinSdk(0, 34));
        assertNull(InstallDependencyChecker.checkMinSdk(-1, 34));
        // Defensive: if deviceApi is somehow zero / negative, treat as unknown and skip.
        assertNull(InstallDependencyChecker.checkMinSdk(34, 0));
        assertNull(InstallDependencyChecker.checkMinSdk(34, -1));
    }

    @Test
    public void checkAggregateReturnsSingleMinSdkIssueWhenAboveFloor() {
        List<InstallDependencyChecker.Issue> issues = InstallDependencyChecker.check(34, 21);
        assertEquals(1, issues.size());
        assertEquals(InstallDependencyChecker.IssueKind.MIN_SDK_TOO_HIGH, issues.get(0).kind);
    }

    @Test
    public void checkAggregateReturnsEmptyWhenDeviceMeetsAllFloors() {
        assertTrue(InstallDependencyChecker.check(21, 36).isEmpty());
        assertTrue(InstallDependencyChecker.check(0, 36).isEmpty());
    }

    @Test
    public void checkSharedLibrariesReturnsNullWhenAllPresent() {
        assertNull(InstallDependencyChecker.checkSharedLibraries(
                InstallDependencyChecker.namesList("org.apache.http.legacy"),
                InstallDependencyChecker.namesList("android.test.runner", "org.apache.http.legacy")));
    }

    @Test
    public void checkSharedLibrariesReturnsIssueListingOnlyMissingNames() {
        InstallDependencyChecker.Issue issue = InstallDependencyChecker.checkSharedLibraries(
                InstallDependencyChecker.namesList("org.apache.http.legacy", "androidx.window.extensions"),
                InstallDependencyChecker.namesList("org.apache.http.legacy"));
        assertNotNull(issue);
        assertEquals(InstallDependencyChecker.IssueKind.MISSING_SHARED_LIBRARY, issue.kind);
        assertEquals(1, issue.missingNames.size());
        assertEquals("androidx.window.extensions", issue.missingNames.get(0));
    }

    @Test
    public void checkSharedLibrariesSkipsWhenInputsAreUnavailable() {
        assertNull(InstallDependencyChecker.checkSharedLibraries(null, null));
        assertNull(InstallDependencyChecker.checkSharedLibraries(
                InstallDependencyChecker.namesList("foo"), null));
        assertNull(InstallDependencyChecker.checkSharedLibraries(
                null, InstallDependencyChecker.namesList("foo")));
        assertNull(InstallDependencyChecker.checkSharedLibraries(
                InstallDependencyChecker.namesList(), InstallDependencyChecker.namesList("foo")));
    }

    @Test
    public void checkSharedLibrariesDedupesAndTrimsRequiredNames() {
        InstallDependencyChecker.Issue issue = InstallDependencyChecker.checkSharedLibraries(
                InstallDependencyChecker.namesList(
                        "  androidx.window.extensions  ",
                        "androidx.window.extensions",
                        "",
                        null,
                        "org.apache.http.legacy"),
                InstallDependencyChecker.namesList("org.apache.http.legacy"));
        assertNotNull(issue);
        assertEquals(1, issue.missingNames.size());
        assertEquals("androidx.window.extensions", issue.missingNames.get(0));
    }

    @Test
    public void checkAggregateChainsMinSdkAndLibraryIssues() {
        java.util.List<InstallDependencyChecker.Issue> issues = InstallDependencyChecker.check(
                34, 30,
                InstallDependencyChecker.namesList("androidx.window.extensions"),
                InstallDependencyChecker.namesList("org.apache.http.legacy"));
        assertEquals(2, issues.size());
        assertEquals(InstallDependencyChecker.IssueKind.MIN_SDK_TOO_HIGH, issues.get(0).kind);
        assertEquals(InstallDependencyChecker.IssueKind.MISSING_SHARED_LIBRARY, issues.get(1).kind);
    }

    @Test
    public void checkAbiSplitsReturnsIssueForSelectedUnsupportedAbi() {
        InstallDependencyChecker.Issue issue = InstallDependencyChecker.checkAbiSplits(
                Arrays.asList(
                        InstallDependencyChecker.SplitInfo.abi("base", "config.arm64_v8a",
                                null, true, "arm64-v8a"),
                        InstallDependencyChecker.SplitInfo.abi("x86", "config.x86",
                                null, true, "x86")),
                InstallDependencyChecker.namesList("arm64-v8a"));

        assertNotNull(issue);
        assertEquals(InstallDependencyChecker.IssueKind.INCOMPATIBLE_ABI_SPLIT, issue.kind);
        assertEquals(1, issue.missingNames.size());
        assertEquals("config.x86 (x86)", issue.missingNames.get(0));
    }

    @Test
    public void checkAbiSplitsIgnoresUnselectedUnsupportedAbi() {
        assertNull(InstallDependencyChecker.checkAbiSplits(
                Arrays.asList(
                        InstallDependencyChecker.SplitInfo.abi("x86", "config.x86",
                                null, false, "x86")),
                InstallDependencyChecker.namesList("arm64-v8a")));
    }

    @Test
    public void checkDensitySplitsReturnsIssueWhenSelectedDensityIsNotClosestAvailable() {
        InstallDependencyChecker.Issue issue = InstallDependencyChecker.checkDensitySplits(
                Arrays.asList(
                        InstallDependencyChecker.SplitInfo.density("hdpi", "config.hdpi",
                                null, false, 240),
                        InstallDependencyChecker.SplitInfo.density("xxhdpi", "config.xxhdpi",
                                null, true, 480)),
                260);

        assertNotNull(issue);
        assertEquals(InstallDependencyChecker.IssueKind.MISMATCHED_DENSITY_SPLIT, issue.kind);
        assertEquals(1, issue.missingNames.size());
        assertEquals("config.xxhdpi (480 dpi; closest is config.hdpi, 240 dpi)",
                issue.missingNames.get(0));
    }

    @Test
    public void checkDensitySplitsAcceptsOnlyAvailableDensityEvenWhenNotExact() {
        assertNull(InstallDependencyChecker.checkDensitySplits(
                Arrays.asList(
                        InstallDependencyChecker.SplitInfo.density("xxhdpi", "config.xxhdpi",
                                null, true, 480)),
                260));
    }

    @Test
    public void checkAggregateIncludesSplitCompatibilityIssuesLast() {
        List<InstallDependencyChecker.Issue> issues = InstallDependencyChecker.check(
                34, 30,
                InstallDependencyChecker.namesList("androidx.window.extensions"),
                InstallDependencyChecker.namesList("org.apache.http.legacy"),
                Arrays.asList(
                        InstallDependencyChecker.SplitInfo.abi("x86", "config.x86",
                                null, true, "x86"),
                        InstallDependencyChecker.SplitInfo.density("hdpi", "config.hdpi",
                                null, false, 240),
                        InstallDependencyChecker.SplitInfo.density("xxhdpi", "config.xxhdpi",
                                null, true, 480)),
                InstallDependencyChecker.namesList("arm64-v8a"),
                260);

        assertEquals(4, issues.size());
        assertEquals(InstallDependencyChecker.IssueKind.MIN_SDK_TOO_HIGH, issues.get(0).kind);
        assertEquals(InstallDependencyChecker.IssueKind.MISSING_SHARED_LIBRARY, issues.get(1).kind);
        assertEquals(InstallDependencyChecker.IssueKind.INCOMPATIBLE_ABI_SPLIT, issues.get(2).kind);
        assertEquals(InstallDependencyChecker.IssueKind.MISMATCHED_DENSITY_SPLIT, issues.get(3).kind);
    }

    @Test
    public void joinMissingNamesIsCommaSeparated() {
        assertEquals("", InstallDependencyChecker.joinMissingNames(
                InstallDependencyChecker.namesList()));
        assertEquals("foo", InstallDependencyChecker.joinMissingNames(
                InstallDependencyChecker.namesList("foo")));
        assertEquals("foo, bar, baz", InstallDependencyChecker.joinMissingNames(
                InstallDependencyChecker.namesList("foo", "bar", "baz")));
    }
}
