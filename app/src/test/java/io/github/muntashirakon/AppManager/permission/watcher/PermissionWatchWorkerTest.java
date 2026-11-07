// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
public class PermissionWatchWorkerTest {
    private static final String ONE = "com.example.one";
    private static final String TWO = "com.example.two";

    private final WatchRule mOneCamera = permission(ONE, 0, "android.permission.CAMERA", WatchRule.STATE_DENY);
    private final WatchRule mOneLocation = permission(ONE, 0, "android.permission.ACCESS_FINE_LOCATION",
            WatchRule.STATE_ALLOW);
    private final WatchRule mTwoMic = permission(TWO, 0, "android.permission.RECORD_AUDIO", WatchRule.STATE_DENY);
    private final WatchRule mOneWorkProfile = permission(ONE, 10, "android.permission.CAMERA", WatchRule.STATE_DENY);
    private final WatchRule mTwoIgnored = permission(TWO, 0, "android.permission.READ_CONTACTS",
            WatchRule.STATE_IGNORE);

    private PermissionWatchDb mDb;
    private WatchRuleDao mDao;
    private FakeWatchBackend mBackend;

    @Before
    public void setUp() {
        mDb = WatchTestDb.newInMemory(RuntimeEnvironment.getApplication());
        mDao = mDb.ruleDao();
        mDao.upsertAll(Arrays.asList(mOneCamera, mOneLocation, mTwoMic, mOneWorkProfile, mTwoIgnored));
        // Every watched permission is the opposite of its rule.
        mBackend = new FakeWatchBackend()
                .set(mOneCamera, true)
                .set(mOneLocation, false)
                .set(mTwoMic, true)
                .set(mOneWorkProfile, true)
                .set(mTwoIgnored, true);
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @Test
    public void theScheduledCheckWalksEveryRuleAndReportsDrift() {
        mBackend.privileged = false;

        PermissionWatcher.Report report = PermissionWatchWorker.runCheck(mDao, mBackend, true, null, 0);

        assertEquals(4, report.checked);
        assertEquals(4, report.drifts.size());
        assertEquals(4, report.count(PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE));
        assertEquals(new HashSet<>(Arrays.asList(FakeWatchBackend.key(mOneCamera),
                        FakeWatchBackend.key(mOneLocation), FakeWatchBackend.key(mTwoMic),
                        FakeWatchBackend.key(mOneWorkProfile))),
                new HashSet<>(mBackend.reads));
        assertTrue(mBackend.applied.isEmpty());
    }

    @Test
    public void theScheduledCheckPutsEverythingBackWhenPrivileged() {
        mBackend.privileged = true;

        PermissionWatcher.Report report = PermissionWatchWorker.runCheck(mDao, mBackend, true, null, 0);

        assertEquals(4, report.count(PermissionWatcher.OUTCOME_ENFORCED));
        assertEquals(4, mBackend.applied.size());
        assertTrue(PermissionWatchWorker.runCheck(mDao, mBackend, true, null, 0).drifts.isEmpty());
    }

    @Test
    public void anUpdateChecksOnlyThatAppForThatUser() {
        mBackend.privileged = true;

        PermissionWatcher.Report report = PermissionWatchWorker.runCheck(mDao, mBackend, true, ONE, 0);

        assertEquals(2, report.checked);
        assertEquals(Arrays.asList(FakeWatchBackend.key(mOneLocation), FakeWatchBackend.key(mOneCamera)),
                mBackend.reads);
        assertEquals(Arrays.asList("grant " + FakeWatchBackend.key(mOneLocation),
                "revoke " + FakeWatchBackend.key(mOneCamera)), mBackend.applied);
    }
}
