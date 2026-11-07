// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.PermissionWatcher.ACTION_GRANT;
import static io.github.muntashirakon.AppManager.permission.watcher.PermissionWatcher.ACTION_NONE;
import static io.github.muntashirakon.AppManager.permission.watcher.PermissionWatcher.ACTION_REVOKE;
import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PermissionWatcherTest {
    private static final String PKG = "com.example.app";

    private final WatchRule mDenyCamera = permission(PKG, 0, "android.permission.CAMERA", WatchRule.STATE_DENY);
    private final WatchRule mAllowLocation = permission(PKG, 0, "android.permission.ACCESS_FINE_LOCATION",
            WatchRule.STATE_ALLOW);
    private final WatchRule mDenyMic = permission(PKG, 0, "android.permission.RECORD_AUDIO", WatchRule.STATE_DENY);

    /** Camera and location are the opposite of their rules. The microphone already matches. */
    private FakeWatchBackend drifted() {
        return new FakeWatchBackend().set(mDenyCamera, true).set(mAllowLocation, false).set(mDenyMic, false);
    }

    private List<WatchRule> threeRules() {
        return Arrays.asList(mDenyCamera, mAllowLocation, mDenyMic);
    }

    @Test
    public void anAllowRuleGrantsOnlyWhatIsDenied() {
        assertEquals(ACTION_NONE, PermissionWatcher.evaluate(WatchRule.STATE_ALLOW, true));
        assertEquals(ACTION_GRANT, PermissionWatcher.evaluate(WatchRule.STATE_ALLOW, false));
    }

    @Test
    public void aDenyRuleRevokesOnlyWhatIsGranted() {
        assertEquals(ACTION_REVOKE, PermissionWatcher.evaluate(WatchRule.STATE_DENY, true));
        assertEquals(ACTION_NONE, PermissionWatcher.evaluate(WatchRule.STATE_DENY, false));
    }

    @Test
    public void anIgnoreRuleNeverAsksForAnything() {
        assertEquals(ACTION_NONE, PermissionWatcher.evaluate(WatchRule.STATE_IGNORE, true));
        assertEquals(ACTION_NONE, PermissionWatcher.evaluate(WatchRule.STATE_IGNORE, false));
    }

    @Test
    public void withAPrivilegedModeMismatchesArePutBack() {
        FakeWatchBackend backend = drifted();
        backend.privileged = true;

        PermissionWatcher.Report report = PermissionWatcher.check(threeRules(), backend, true);

        assertEquals(3, report.checked);
        assertEquals(0, report.skipped);
        assertEquals(Arrays.asList("revoke " + FakeWatchBackend.key(mDenyCamera),
                "grant " + FakeWatchBackend.key(mAllowLocation)), backend.applied);
        assertEquals(2, report.drifts.size());
        assertEquals(2, report.count(PermissionWatcher.OUTCOME_ENFORCED));
        PermissionWatcher.Drift camera = report.drifts.get(0);
        assertEquals(mDenyCamera, camera.rule);
        assertTrue(camera.wasAllowed);
        assertNull(camera.failure);
        assertFalse(report.drifts.get(1).wasAllowed);
    }

    @Test
    public void withoutAPrivilegedModeNothingIsChanged() {
        FakeWatchBackend backend = drifted();
        backend.privileged = false;

        PermissionWatcher.Report report = PermissionWatcher.check(threeRules(), backend, true);

        assertTrue(backend.applied.isEmpty());
        assertEquals(2, report.drifts.size());
        assertEquals(2, report.count(PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE));
        assertEquals(0, report.count(PermissionWatcher.OUTCOME_ENFORCED));
    }

    @Test
    public void withEnforcementOffMismatchesAreOnlyReported() {
        FakeWatchBackend backend = drifted();
        backend.privileged = true;

        PermissionWatcher.Report report = PermissionWatcher.check(threeRules(), backend, false);

        assertTrue(backend.applied.isEmpty());
        assertEquals(2, report.count(PermissionWatcher.OUTCOME_REPORTED));
    }

    @Test
    public void anIgnoredRuleIsNeitherReadNorChanged() {
        WatchRule ignoreCamera = permission(PKG, 0, "android.permission.CAMERA", WatchRule.STATE_IGNORE);
        FakeWatchBackend backend = new FakeWatchBackend().set(ignoreCamera, true);
        backend.privileged = true;

        PermissionWatcher.Report report = PermissionWatcher.check(Collections.singletonList(ignoreCamera), backend,
                true);

        assertTrue(backend.reads.isEmpty());
        assertTrue(backend.applied.isEmpty());
        assertEquals(0, report.checked);
        assertEquals(0, report.skipped);
        assertTrue(report.drifts.isEmpty());
    }

    @Test
    public void rulesThatMatchProduceNoDrift() {
        FakeWatchBackend backend = new FakeWatchBackend().set(mDenyCamera, false).set(mAllowLocation, true);
        backend.privileged = true;

        PermissionWatcher.Report report = PermissionWatcher.check(Arrays.asList(mDenyCamera, mAllowLocation), backend,
                true);

        assertEquals(2, report.checked);
        assertTrue(report.drifts.isEmpty());
        assertTrue(backend.applied.isEmpty());
    }

    @Test
    public void aRuleForAMissingAppOrPermissionIsSkipped() {
        FakeWatchBackend backend = new FakeWatchBackend();
        backend.privileged = true;

        PermissionWatcher.Report report = PermissionWatcher.check(Collections.singletonList(mDenyCamera), backend,
                true);

        assertEquals(0, report.checked);
        assertEquals(1, report.skipped);
        assertTrue(report.drifts.isEmpty());
        assertTrue(backend.applied.isEmpty());
    }

    @Test
    public void aRefusedChangeIsReportedWithItsReasonAndTheRestStillRun() {
        FakeWatchBackend backend = drifted();
        backend.privileged = true;
        backend.refused.add(FakeWatchBackend.key(mDenyCamera));

        PermissionWatcher.Report report = PermissionWatcher.check(threeRules(), backend, true);

        PermissionWatcher.Drift camera = report.drifts.get(0);
        assertEquals(PermissionWatcher.OUTCOME_FAILED, camera.outcome);
        assertEquals("refused android.permission.CAMERA", camera.failure);
        assertEquals(PermissionWatcher.OUTCOME_ENFORCED, report.drifts.get(1).outcome);
        assertEquals(Collections.singletonList("grant " + FakeWatchBackend.key(mAllowLocation)), backend.applied);
    }
}
