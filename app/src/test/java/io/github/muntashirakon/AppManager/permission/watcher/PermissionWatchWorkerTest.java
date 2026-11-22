// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedEntry;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedStore;

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

    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

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
    public void everyDriftGoesInTheChangeFeedWithWhatWasFoundAndWhatWasDone() {
        mBackend.privileged = true;
        mBackend.refused.add(FakeWatchBackend.key(mTwoMic));
        AppChangeFeedStore feed = new AppChangeFeedStore(new File(mTemp.getRoot(), "feed.json"));

        PermissionWatcher.Report report = check(feed);

        assertEquals(4, report.drifts.size());
        assertEquals(sorted(
                        ONE + " | com.example.one: CAMERA didn't match your rule"
                                + " | It was allowed, and your rule says deny. It was changed back.",
                        ONE + " | com.example.one: ACCESS_FINE_LOCATION didn't match your rule"
                                + " | It was denied, and your rule says allow. It was changed back.",
                        TWO + " | com.example.two: RECORD_AUDIO didn't match your rule"
                                + " | It was allowed, and your rule says deny."
                                + " Changing it back didn't work: refused android.permission.RECORD_AUDIO",
                        ONE + " | com.example.one (user 10): CAMERA didn't match your rule"
                                + " | It was allowed, and your rule says deny. It was changed back."),
                watchEntries(feed));
    }

    @Test
    public void aDriftLeftAsItIsGoesInTheFeedOnceNotOnEveryCheck() {
        mBackend.privileged = false;
        AppChangeFeedStore feed = new AppChangeFeedStore(new File(mTemp.getRoot(), "feed.json"));

        check(feed);
        check(feed);

        List<String> entries = watchEntries(feed);
        assertEquals(entries.toString(), 4, entries.size());
        assertTrue(entries.contains(TWO + " | com.example.two: RECORD_AUDIO didn't match your rule"
                + " | It was allowed, and your rule says deny. Root or ADB mode isn't running, so nothing was changed."));

        // Once it can be changed back, that goes on record too
        mBackend.privileged = true;
        check(feed);
        assertEquals(8, watchEntries(feed).size());
    }

    @Test
    public void aPermissionAndAnAppOpOfTheSameNameAreRecordedApart() {
        mBackend.privileged = false;
        WatchRule cameraOp = WatchTestDb.appOp(ONE, 0, 26, "CAMERA", WatchRule.STATE_DENY);
        mDao.upsertAll(Collections.singletonList(cameraOp));
        mBackend.set(cameraOp, true);
        AppChangeFeedStore feed = new AppChangeFeedStore(new File(mTemp.getRoot(), "feed.json"));

        check(feed);

        List<String> entries = watchEntries(feed);
        assertEquals(entries.toString(), 5, entries.size());
        assertTrue(entries.toString(), entries.contains(ONE + " | com.example.one: CAMERA didn't match your rule"
                + " | It was allowed, and your rule says deny. Root or ADB mode isn't running, so nothing was changed."));
        assertTrue(entries.toString(), entries.contains(ONE + " | com.example.one: app op CAMERA didn't match"
                + " your rule | It was allowed, and your rule says deny. Root or ADB mode isn't running, so nothing"
                + " was changed."));
    }

    @Test
    public void aDriftThatComesBackAfterTheRuleWasKeptGoesInTheFeedAgain() {
        mBackend.privileged = false;
        AppChangeFeedStore feed = new AppChangeFeedStore(new File(mTemp.getRoot(), "feed.json"));
        check(feed);
        assertEquals(4, watchEntries(feed).size());

        // The user turns the camera off themselves, so the next check finds the rule kept
        mBackend.set(mOneCamera, false);
        check(feed);
        assertEquals(4, watchEntries(feed).size());

        // Days later something turns it on again: that's a new drift, not the old one
        mBackend.set(mOneCamera, true);
        check(feed);
        List<String> entries = watchEntries(feed);
        assertEquals(entries.toString(), 5, entries.size());
        String camera = ONE + " | com.example.one: CAMERA didn't match your rule | It was allowed, and your rule"
                + " says deny. Root or ADB mode isn't running, so nothing was changed.";
        assertEquals(entries.toString(), 2, Collections.frequency(entries, camera));

        // The others never changed, so they're still on record once
        check(feed);
        assertEquals(5, watchEntries(feed).size());
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

    @NonNull
    private PermissionWatcher.Report check(@NonNull AppChangeFeedStore feed) {
        // Labels are looked up elsewhere; the package name keeps the entries easy to read here
        return PermissionWatchWorker.checkAndRecord(RuntimeEnvironment.getApplication(), mDao, mBackend, true,
                null, 0, feed, packageName -> packageName);
    }

    @NonNull
    private static List<String> watchEntries(@NonNull AppChangeFeedStore feed) {
        List<String> entries = new ArrayList<>();
        for (AppChangeFeedEntry entry : feed.readAll()) {
            if (PermissionWatchWorker.FEED_KIND.equals(entry.kind)) {
                entries.add(entry.packageName + " | " + entry.title + " | " + entry.body);
            }
        }
        Collections.sort(entries);
        return entries;
    }

    @NonNull
    private static List<String> sorted(@NonNull String... entries) {
        List<String> list = new ArrayList<>(Arrays.asList(entries));
        Collections.sort(list);
        return list;
    }
}
