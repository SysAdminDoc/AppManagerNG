// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * READ_LOGS carries the log groups, and Android kills an app whose groups change. Granted inside
 * the mode switch, it closed Settings before the new mode was saved (seen on Android 17 with
 * Shizuku), so it has to wait until no process of the app is doing anything.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        DeferredLogAccessTest.RootIdentity.class,
        DeferredLogAccessTest.RecordingPermissions.class,
        DeferredLogAccessTest.NoAppOps.class,
})
public class DeferredLogAccessTest {
    private static final int FOREGROUND_SERVICE = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
    private static final int VISIBLE = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    private static final int SERVICE = ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;

    static final List<String> sGranted = new ArrayList<>();

    private final Application mApp = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        DeferredLogAccess.setOwed(false);
    }

    @After
    public void tearDown() {
        sGranted.clear();
        DeferredLogAccess.setOwed(false);
    }

    @Test
    public void onlyAnIdleProcessWithNoModeSwitchRunningGetsTheGrant() {
        assertTrue(DeferredLogAccess.shouldGrantNow(true, DeferredLogAccess.IMPORTANCE_CACHED, false));
        assertFalse("nothing owed", DeferredLogAccess.shouldGrantNow(false, DeferredLogAccess.IMPORTANCE_CACHED, false));
        assertFalse("a mode switch is running", DeferredLogAccess.shouldGrantNow(true, DeferredLogAccess.IMPORTANCE_CACHED, true));
        assertFalse("a backup or install service is running", DeferredLogAccess.shouldGrantNow(true, FOREGROUND_SERVICE, false));
        assertFalse("a job is running", DeferredLogAccess.shouldGrantNow(true, SERVICE, false));
        assertFalse("a screen is visible", DeferredLogAccess.shouldGrantNow(true, VISIBLE, false));
    }

    @Test
    public void theModeSwitchGrantsEverythingElseButOnlyOwesReadLogs() {
        SelfPermissions.init();

        assertFalse("granting READ_LOGS here kills the app before the mode is saved",
                sGranted.contains(Manifest.permission.READ_LOGS));
        assertTrue(sGranted.containsAll(Arrays.asList(Manifest.permission.DUMP,
                ManifestCompat.permission.GET_APP_OPS_STATS, ManifestCompat.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.WRITE_SECURE_SETTINGS)));
        assertTrue(DeferredLogAccess.isOwed());
    }

    @Test
    public void heldReadLogsIsNeitherGrantedNorOwed() {
        shadowOf(mApp).grantPermissions(Manifest.permission.READ_LOGS);

        SelfPermissions.init();

        assertFalse(DeferredLogAccess.isOwed());
        assertFalse(DeferredLogAccess.grantIfIdle(() -> DeferredLogAccess.IMPORTANCE_CACHED));
        assertFalse(sGranted.contains(Manifest.permission.READ_LOGS));
    }

    @Test
    public void anIdleProcessGetsTheOwedGrantOnce() {
        DeferredLogAccess.setOwed(true);

        assertFalse(DeferredLogAccess.grantIfIdle(() -> FOREGROUND_SERVICE));
        assertTrue("still owed while a service runs", DeferredLogAccess.isOwed());
        assertTrue(sGranted.isEmpty());

        assertTrue(DeferredLogAccess.grantIfIdle(() -> DeferredLogAccess.IMPORTANCE_CACHED));
        assertEquals(Arrays.asList(Manifest.permission.READ_LOGS), sGranted);
        assertFalse(DeferredLogAccess.isOwed());

        assertFalse("owed only once", DeferredLogAccess.grantIfIdle(() -> DeferredLogAccess.IMPORTANCE_CACHED));
        assertEquals(1, sGranted.size());
    }

    @Test
    public void aPlayingAudioPlayerKeepsTheMainProcessFromGranting() {
        DeferredLogAccess.setOwed(true);
        ActivityManager am = (ActivityManager) mApp.getSystemService(Context.ACTIVITY_SERVICE);
        // The audio player runs in its own process under the same uid, so the kill reaches it too.
        shadowOf(am).setProcesses(Arrays.asList(
                process(Process.myPid(), Process.myUid(), DeferredLogAccess.IMPORTANCE_CACHED),
                process(Process.myPid() + 1, Process.myUid(), ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)));

        DeferredLogAccess.grantIfIdle();
        assertTrue(sGranted.isEmpty());
        assertTrue(DeferredLogAccess.isOwed());

        shadowOf(am).setProcesses(Arrays.asList(process(Process.myPid(), Process.myUid(), DeferredLogAccess.IMPORTANCE_CACHED)));
        DeferredLogAccess.grantIfIdle();
        assertEquals(Arrays.asList(Manifest.permission.READ_LOGS), sGranted);
    }

    @Test
    public void otherAppsProcessesDontCount() {
        DeferredLogAccess.setOwed(true);
        ActivityManager am = (ActivityManager) mApp.getSystemService(Context.ACTIVITY_SERVICE);
        // Android 5.0 lists every app's processes, and system_server is always in the foreground
        shadowOf(am).setProcesses(Arrays.asList(
                process(Process.myPid(), Process.myUid(), DeferredLogAccess.IMPORTANCE_CACHED),
                process(Process.myPid() + 1, Process.SYSTEM_UID, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)));

        DeferredLogAccess.grantIfIdle();

        assertEquals(Arrays.asList(Manifest.permission.READ_LOGS), sGranted);
    }

    @Test
    public void anEarlierStopsTimerCantGrantSoonAfterALaterStop() {
        int[] grants = new int[1];
        DeferredLogAccess callbacks = new DeferredLogAccess();
        callbacks.mGrant = () -> ++grants[0];
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        DeferredLogAccess.setOwed(true);

        // Home, back from Recents 800 ms later, Home again at 1500 ms
        callbacks.onActivityStarted(activity);
        callbacks.onActivityStopped(activity);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
        callbacks.onActivityStarted(activity);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        callbacks.onActivityStopped(activity);
        // The first stop's timer fires 500 ms after the second stop
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(0, grants[0]);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeferredLogAccess.SETTLE_MILLIS - 500));
        assertEquals(1, grants[0]);
    }

    @Test
    public void theGrantWaitsUntilNoActivityIsStartedForTheSettleTime() {
        int[] grants = new int[1];
        DeferredLogAccess callbacks = new DeferredLogAccess();
        callbacks.mGrant = () -> ++grants[0];
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        DeferredLogAccess.setOwed(true);

        callbacks.onActivityStarted(activity);
        callbacks.onActivityStopped(activity);
        // A configuration change starts the new activity right after the old one stops
        callbacks.onActivityStarted(activity);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeferredLogAccess.SETTLE_MILLIS));
        assertEquals("a started activity must never be killed", 0, grants[0]);

        callbacks.onActivityStopped(activity);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeferredLogAccess.SETTLE_MILLIS - 1));
        assertEquals(0, grants[0]);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1, grants[0]);
    }

    @Test
    public void nothingOwedSchedulesNothing() {
        int[] grants = new int[1];
        DeferredLogAccess callbacks = new DeferredLogAccess();
        callbacks.mGrant = () -> ++grants[0];
        Activity activity = Robolectric.buildActivity(Activity.class).get();

        callbacks.onActivityStarted(activity);
        callbacks.onActivityStopped(activity);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeferredLogAccess.SETTLE_MILLIS * 2));

        assertEquals(0, grants[0]);
    }

    private static ActivityManager.RunningAppProcessInfo process(int pid, int uid, int importance) {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        info.pid = pid;
        info.uid = uid;
        info.importance = importance;
        info.processName = "io.github.muntashirakon.AppManager";
        return info;
    }

    /** Root holds every permission, so the app may grant itself what it lacks. */
    @Implements(Users.class)
    public static class RootIdentity {
        @Implementation
        protected static int getSelfOrRemoteUid() {
            return Ops.ROOT_UID;
        }
    }

    @Implements(PermissionCompat.class)
    public static class RecordingPermissions {
        @Implementation
        protected static void grantPermission(String packageName, String permissionName, int userId) {
            sGranted.add(permissionName);
        }
    }

    /** The usage access app-op goes to the privileged service, which a unit test doesn't have. */
    @Implements(AppOpsManagerCompat.class)
    public static class NoAppOps {
        @Implementation
        protected void __constructor__() {
        }

        @Implementation
        protected void setMode(int op, int uid, String packageName, int mode) {
        }
    }
}
