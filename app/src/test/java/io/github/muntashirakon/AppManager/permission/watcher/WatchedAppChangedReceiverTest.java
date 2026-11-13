// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import io.github.muntashirakon.AppManager.BuildConfig;

@RunWith(RobolectricTestRunner.class)
public class WatchedAppChangedReceiverTest {
    private static final String PKG = "com.example.app";
    private static final int PROFILE_UID = 1010050;

    private Application mApp;
    private PermissionWatchDb mDb;
    private RecordingWorkSink mSink;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mSink = RecordingWorkSink.install();
    }

    @After
    public void tearDown() {
        RecordingWorkSink.uninstall();
        WatchTestDb.uninstall(mDb);
    }

    @Test
    public void theRelayIntentNamesThisReceiverAndCarriesTheApp() {
        Intent intent = WatchedAppChangedReceiver.intentFor(PKG, PROFILE_UID);

        assertEquals(new ComponentName(BuildConfig.APPLICATION_ID, WatchedAppChangedReceiver.class.getName()),
                intent.getComponent());
        assertTrue((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);
        assertEquals(PKG, WatchedAppChangedReceiver.packageOf(intent));
        assertEquals(10, WatchedAppChangedReceiver.userOf(intent));
    }

    @Test
    public void malformedIntentsAreIgnored() {
        assertNull(WatchedAppChangedReceiver.packageOf(new Intent("other.ACTION")
                .putExtra(WatchedAppChangedReceiver.EXTRA_PACKAGE, PKG)
                .putExtra(WatchedAppChangedReceiver.EXTRA_UID, 10050)));
        assertNull(WatchedAppChangedReceiver.packageOf(new Intent(WatchedAppChangedReceiver.ACTION_WATCHED_APP_CHANGED)
                .putExtra(WatchedAppChangedReceiver.EXTRA_PACKAGE, PKG)));
        assertNull(WatchedAppChangedReceiver.packageOf(new Intent(WatchedAppChangedReceiver.ACTION_WATCHED_APP_CHANGED)
                .putExtra(WatchedAppChangedReceiver.EXTRA_PACKAGE, "")
                .putExtra(WatchedAppChangedReceiver.EXTRA_UID, 10050)));
    }

    @Test
    public void aChangeQueuesACheckOfThatAppForItsUser() {
        mDb = WatchTestDb.install(mApp);
        Intent intent = WatchedAppChangedReceiver.intentFor(PKG, PROFILE_UID);

        WatchedAppChangedReceiver.handle(mApp, WatchedAppChangedReceiver.packageOf(intent),
                WatchedAppChangedReceiver.userOf(intent));

        // No privileged services in a unit test, so the check goes through WorkManager
        assertEquals(Collections.singletonList(PermissionWatchScheduler.uniqueCheckName(PKG, 10)),
                mSink.enqueuedNames);
    }

    @Test
    public void nothingRunsWithoutRules() {
        WatchedAppChangedReceiver.handle(mApp, PKG, 0);

        assertTrue(mSink.enqueuedNames.isEmpty());
    }

    @Test
    public void onlyAPrivilegedSenderMayReachIt() throws PackageManager.NameNotFoundException {
        ActivityInfo info = mApp.getPackageManager().getReceiverInfo(
                new ComponentName(mApp, WatchedAppChangedReceiver.class), 0);

        assertTrue(info.exported);
        assertEquals("android.permission.UPDATE_APP_OPS_STATS", info.permission);
    }
}
