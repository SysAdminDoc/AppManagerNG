// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.work.PeriodicWorkRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchDb;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class PackageEventSyncTest {
    private static final String KEPT = "com.example.kept";
    private static final String UPDATED = "com.example.updated";
    private static final String REMOVED = "com.example.removed";
    private static final String REINSTALLED = "com.example.reinstalled";
    private static final String NEW = "com.example.new";

    private Application mApp;
    private final List<String> mEvents = new ArrayList<>();
    private final List<String> mSchedule = new ArrayList<>();
    private boolean mPermissionMonitorBefore;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mPermissionMonitorBefore = Prefs.Privacy.isPermissionChangeMonitorEnabled();
        PackageEventSync.stateFile(mApp).delete();
        PackageEventSync.setDispatcherForTesting(new PackageEventSync.Dispatcher() {
            @Override
            public void onAdded(@NonNull Context context, @NonNull String packageName, int userId) {
                mEvents.add("added " + packageName);
            }

            @Override
            public void onUpdated(@NonNull Context context, @NonNull String packageName, int userId) {
                mEvents.add("updated " + packageName);
            }

            @Override
            public void onRemoved(@NonNull Context context, @NonNull String packageName, int userId) {
                mEvents.add("removed " + packageName);
            }
        });
        PackageEventSync.setWorkSinkForTesting(new PackageEventSync.WorkSink() {
            @Override
            public void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request) {
                mSchedule.add("schedule");
            }

            @Override
            public void cancel(@NonNull Context context) {
                mSchedule.add("cancel");
            }
        });
    }

    @After
    public void tearDown() {
        PackageEventSync.setDispatcherForTesting(null);
        PackageEventSync.setWorkSinkForTesting(null);
        Prefs.Privacy.setPermissionChangeMonitorEnabled(mPermissionMonitorBefore);
        PackageEventSync.stateFile(mApp).delete();
    }

    @Test
    public void anUpdateIsNoticedWithoutAnyBroadcast() {
        // Android 8+ never delivers PACKAGE_REPLACED to a manifest receiver, so the monitors only hear about an
        // update if the app spots it on its own.
        install(KEPT, 100, 100);
        install(UPDATED, 100, 100);
        PackageEventSync.syncNow(mApp);

        install(UPDATED, 100, 200);
        PackageEventSync.syncNow(mApp);

        assertEquals(Collections.singletonList("updated " + UPDATED), mEvents);
    }

    @Test
    public void installsAndRemovalsAreNoticedToo() {
        install(KEPT, 100, 100);
        install(REMOVED, 100, 100);
        PackageEventSync.syncNow(mApp);

        shadowOf(mApp.getPackageManager()).removePackage(REMOVED);
        install(NEW, 300, 300);
        PackageEventSync.syncNow(mApp);

        assertEquals(Arrays.asList("removed " + REMOVED, "added " + NEW), mEvents);
    }

    @Test
    public void theFirstSyncOnlySavesTheList() {
        install(KEPT, 100, 100);

        PackageEventSync.syncNow(mApp);

        assertTrue(mEvents.isEmpty());
        PackageEventSync.State state = PackageEventSync.readState(PackageEventSync.stateFile(mApp));
        assertNotNull(state);
        assertTrue(state.packages.containsKey(KEPT));
        assertFalse("this app is left out", state.packages.containsKey(mApp.getPackageName()));
    }

    @Test
    public void anAppRemovedWithItsDataKeptCountsAsRemoved() {
        PackageInfo dataKept = packageInfo(REMOVED, 100, 100);
        dataKept.applicationInfo.flags &= ~ApplicationInfo.FLAG_INSTALLED;
        PackageInfo installed = packageInfo(KEPT, 100, 100);

        Map<String, PackageEventSync.Seen> seen = PackageEventSync.installedOnly(
                Arrays.asList(dataKept, installed, packageInfo("self", 1, 1)), "self");

        assertEquals("only apps installed for this user, this app left out", Collections.singleton(KEPT),
                seen.keySet());
    }

    @Test
    public void theDiffSortsEveryKindOfChange() {
        Map<String, PackageEventSync.Seen> before = new TreeMap<>();
        before.put(KEPT, seen(100, 100, false));
        before.put(UPDATED, seen(100, 100, false));
        before.put(REMOVED, seen(100, 100, false));
        before.put(REINSTALLED, seen(100, 100, false));
        Map<String, PackageEventSync.Seen> now = new TreeMap<>();
        now.put(KEPT, seen(100, 100, false));
        now.put(UPDATED, seen(100, 200, false));
        now.put(REINSTALLED, seen(500, 500, false));
        now.put(NEW, seen(600, 600, false));

        PackageEventSync.Changes changes = PackageEventSync.diff(before, now, false);

        assertEquals(Arrays.asList(NEW, REINSTALLED), changes.added);
        assertEquals(Collections.singletonList(UPDATED), changes.updated);
        assertEquals(Arrays.asList(REINSTALLED, REMOVED), changes.removed);
    }

    @Test
    public void aSystemUpdateDoesNotLookLikePreinstalledAppsUpdating() {
        Map<String, PackageEventSync.Seen> before = new TreeMap<>();
        before.put("com.android.preinstalled", seen(100, 100, false));
        before.put(UPDATED, seen(100, 100, false));
        Map<String, PackageEventSync.Seen> now = new TreeMap<>();
        now.put("com.android.preinstalled", seen(100, 900, true));
        now.put(UPDATED, seen(100, 200, false));
        now.put(NEW, seen(600, 600, true));

        PackageEventSync.Changes changes = PackageEventSync.diff(before, now, true);

        assertEquals(Collections.singletonList(UPDATED), changes.updated);
        assertEquals("a new preinstalled app still counts", Collections.singletonList(NEW), changes.added);
        assertTrue(changes.removed.isEmpty());
        assertEquals(Arrays.asList("com.android.preinstalled", UPDATED),
                PackageEventSync.diff(before, now, false).updated);
    }

    @Test
    public void theSavedListReadsBack() throws Exception {
        Map<String, PackageEventSync.Seen> packages = new TreeMap<>();
        packages.put(KEPT, seen(123, 456, true));

        PackageEventSync.State state = PackageEventSync.parseState(PackageEventSync.toJson(
                new PackageEventSync.State(10, "fingerprint", packages)));

        assertNotNull(state);
        assertEquals(10, state.userId);
        assertEquals("fingerprint", state.fingerprint);
        assertEquals(123, state.packages.get(KEPT).firstInstallTime);
        assertEquals(456, state.packages.get(KEPT).lastUpdateTime);
        assertNull("another format starts over", PackageEventSync.parseState("{\"v\":99,\"packages\":{}}"));
    }

    @Test
    public void theJobRunsOnlyWhileSomethingListens() {
        PermissionWatchDb.setInstanceForTesting(null);
        mApp.getDatabasePath(PermissionWatchDb.NAME).delete();
        Prefs.Privacy.setPermissionChangeMonitorEnabled(false);
        PackageEventSync.scheduleOrCancel(mApp);

        Prefs.Privacy.setPermissionChangeMonitorEnabled(true);
        PackageEventSync.scheduleOrCancel(mApp);

        assertEquals(Arrays.asList("cancel", "schedule"), mSchedule);
        PeriodicWorkRequest request = PackageEventSync.buildPeriodicRequest();
        assertEquals(TimeUnit.MINUTES.toMillis(15), request.getWorkSpec().intervalDuration);
        assertEquals(PackageEventSyncWorker.class.getName(), request.getWorkSpec().workerClassName);
    }

    private void install(@NonNull String packageName, long firstInstallTime, long lastUpdateTime) {
        shadowOf(mApp.getPackageManager()).installPackage(packageInfo(packageName, firstInstallTime, lastUpdateTime));
    }

    @NonNull
    private static PackageInfo packageInfo(@NonNull String packageName, long firstInstallTime, long lastUpdateTime) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.firstInstallTime = firstInstallTime;
        info.lastUpdateTime = lastUpdateTime;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        return info;
    }

    @NonNull
    private static PackageEventSync.Seen seen(long firstInstallTime, long lastUpdateTime, boolean system) {
        return new PackageEventSync.Seen(firstInstallTime, lastUpdateTime, system);
    }
}
