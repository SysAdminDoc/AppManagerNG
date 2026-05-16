// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
