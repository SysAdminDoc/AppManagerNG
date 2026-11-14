// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeService;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logcat.LogcatRecordingService;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.WifiWaitService;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

/**
 * Android can refuse to put a service in the foreground: a background start on Android 12 and later, or a type
 * whose permission is missing on 14 and later. That used to throw out of {@link ForegroundService#start} and take
 * the app down mid-operation (upstream #2066, freezing on Android 17). Each caller now stops cleanly or carries on,
 * and says what the refusal cost.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowCpuUtils.class)
public class ForegroundServiceRefusedTest {
    private static final String PROGRESS_CHANNEL = "progress_test";

    private Application mApp;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ShadowToast.reset();
    }

    @After
    public void tearDown() {
        AppPref.set(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL, false);
        AppPref.set(AppPref.PrefKey.PREF_ENABLE_AUTO_LOCK_BOOL, false);
    }

    @Test
    public void aRefusalComesBackAsFalseInsteadOfAnException() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        Notification notification = new NotificationCompat.Builder(service, "test").build();
        try {
            assertTrue(ForegroundService.start(service, 1, notification, ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));

            shadowOf(service).setThrowInStartForeground(new ForegroundServiceStartNotAllowedException("background start"));
            assertFalse(ForegroundService.start(service, 1, notification, ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));

            shadowOf(service).setThrowInStartForeground(new SecurityException("missing FOREGROUND_SERVICE_DATA_SYNC"));
            assertFalse(ForegroundService.start(service, 1, notification, ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aRefusedFreezeMonitorStopsAndPointsAtTheNotification() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        FreezeUnfreezeService service = controller.get();
        refuse(service);

        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(new Intent(), 0, 1));
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(mApp.getString(R.string.freeze_on_lock_refused), ShadowToast.getTextOfLatestToast());
        // Its screen receiver was never registered, so tearing down must not try to unregister it
        controller.destroy();
    }

    @Test
    public void aRefusedSessionMonitorWithAutoLockSaysTheAppWontLockItself() {
        AppPref.set(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL, true);
        AppPref.set(AppPref.PrefKey.PREF_ENABLE_AUTO_LOCK_BOOL, true);
        ServiceController<SessionMonitoringService> controller = Robolectric.buildService(SessionMonitoringService.class).create();
        SessionMonitoringService service = controller.get();
        refuse(service);

        service.onStartCommand(new Intent(), 0, 1);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(mApp.getString(R.string.auto_lock_refused), ShadowToast.getTextOfLatestToast());
        controller.destroy();
    }

    @Test
    public void aRefusedSessionMonitorWithTheAppLockSaysTheLockButtonIsGone() {
        AppPref.set(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL, true);
        ServiceController<SessionMonitoringService> controller = Robolectric.buildService(SessionMonitoringService.class).create();
        SessionMonitoringService service = controller.get();
        refuse(service);

        service.onStartCommand(new Intent(), 0, 1);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(mApp.getString(R.string.session_lock_refused), ShadowToast.getTextOfLatestToast());
        controller.destroy();
    }

    @Test
    public void aRefusedSessionMonitorWithoutTheAppLockSaysTheAppWontStayInTheBackground() {
        ServiceController<SessionMonitoringService> controller = Robolectric.buildService(SessionMonitoringService.class).create();
        SessionMonitoringService service = controller.get();
        refuse(service);

        service.onStartCommand(new Intent(), 0, 1);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(shadowOf(service).isStoppedBySelf());
        // Without the app lock it's the "Run AppManagerNG in the background" setting, which the refusal undid
        assertEquals(mApp.getString(R.string.persistent_session_refused), ShadowToast.getTextOfLatestToast());
        controller.destroy();
    }

    @Test
    public void aRefusedWifiWaitStopsWithoutWatchingTheNetworkAndSaysSo() throws Exception {
        String before = Ops.getMode();
        int portBefore = Prefs.Misc.getAdbLocalServerPort();
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        try {
            // Without INTERNET the mode reads back as auto, and there's nothing to wait for
            shadowOf(mApp).grantPermissions(Manifest.permission.INTERNET);
            Ops.setMode(Ops.MODE_ADB_WIFI);
            // Anything listening on the server port counts as a running server, so use a port nothing holds
            Prefs.Misc.setAdbLocalServerPort(freePort());
            assertEquals(Ops.MODE_ADB_WIFI, Ops.getMode());
            assertFalse("nothing listens on the server port", LocalServer.alive(mApp));
            refuse(service);

            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();

            assertTrue(shadowOf(service).isStoppedBySelf());
            assertEquals(1, shadowOf(service).getStopSelfResultId());
            assertFalse((Boolean) getField(service, "mCallbackRegistered"));
            ConnectivityManager connectivityManager = (ConnectivityManager) mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
            assertTrue("never watched the network", shadowOf(connectivityManager).getNetworkCallbacks().isEmpty());
            // It runs at boot, where nobody sees a toast
            assertNotNull(notificationWithText(mApp.getString(R.string.wifi_wait_refused)));
        } finally {
            controller.destroy();
            Ops.setMode(before);
            Prefs.Misc.setAdbLocalServerPort(portBefore);
        }
    }

    @Test
    public void aRefusedWifiWaitWithNothingToDoSaysNothing() {
        String before = Ops.getMode();
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        try {
            Ops.setMode(Ops.MODE_ROOT);
            refuse(service);

            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();

            assertTrue(shadowOf(service).isStoppedBySelf());
            assertEquals(0, ShadowToast.shownToastCount());
            // It reports through a notification, so the toast count alone would miss one
            assertNull(notificationWithText(mApp.getString(R.string.wifi_wait_refused)));
        } finally {
            controller.destroy();
            Ops.setMode(before);
        }
    }

    // The pairing search is covered in AdbPairingServiceTest, which can see its search generation.

    @Test
    public void aRefusedProgressServiceKeepsWorkingWithAPlainNotification() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        NotificationProgressHandler.NotificationManagerInfo info = new NotificationProgressHandler.NotificationManagerInfo(
                PROGRESS_CHANNEL, "Progress", NotificationManager.IMPORTANCE_LOW);
        NotificationProgressHandler handler = new NotificationProgressHandler(mApp, info, info, null);
        try {
            refuse(service);

            handler.onAttach(service, new NotificationProgressHandler.NotificationInfo().setTitle("Backing up"));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(mApp.getString(R.string.foreground_service_refused_keep_open), ShadowToast.getTextOfLatestToast());

            handler.onProgressUpdate(10, 3, null);
            StatusBarNotification progress = progressNotification();
            assertNotNull("progress still shows", progress);
            assertEquals("a killed process can't leave it stuck on screen", 0,
                    progress.getNotification().flags & Notification.FLAG_ONGOING_EVENT);

            handler.onResult(null);
            assertNull("a progress notification that isn't the service's own is removed at the end", progressNotification());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void eachLaterStartOfARefusedServiceTriesTheForegroundAgainQuietly() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        NotificationProgressHandler.NotificationManagerInfo info = new NotificationProgressHandler.NotificationManagerInfo(
                PROGRESS_CHANNEL, "Progress", NotificationManager.IMPORTANCE_LOW);
        NotificationProgressHandler handler = new NotificationProgressHandler(mApp, info, info, null);
        try {
            refuse(service);
            handler.onAttach(service, new NotificationProgressHandler.NotificationInfo().setTitle("Backing up"));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, ShadowToast.shownToastCount());

            // Still refused: no second toast, and no crash
            handler.onStartedAgain(service);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, ShadowToast.shownToastCount());
            assertNull(shadowOf(service).getLastForegroundNotification());

            // Allowed now, as with the app back on screen: the progress becomes the service's own
            shadowOf(service).setThrowInStartForeground(null);
            handler.onStartedAgain(service);
            assertNotNull(shadowOf(service).getLastForegroundNotification());
            handler.onProgressUpdate(10, 3, null);
            assertTrue("ongoing again as the service's notification",
                    (progressNotification().getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0);

            // Once in the foreground there's nothing to retry
            Notification foreground = shadowOf(service).getLastForegroundNotification();
            handler.onStartedAgain(service);
            assertTrue(foreground == shadowOf(service).getLastForegroundNotification());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aQueuedStartOnARefusedQueueServiceCallsStartForegroundAgain() {
        ServiceController<ProfileApplierService> controller = Robolectric.buildService(ProfileApplierService.class).create();
        ProfileApplierService service = controller.get();
        try {
            refuse(service);
            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(mApp.getString(R.string.foreground_service_refused_keep_open), ShadowToast.getTextOfLatestToast());
            assertNull(shadowOf(service).getLastForegroundNotification());

            // A second start while it works only queued its item and never called startForeground, and Android
            // ended the app when that start's time ran out
            shadowOf(service).setThrowInStartForeground(null);
            service.onStartCommand(new Intent(), 0, 2);
            assertNotNull(shadowOf(service).getLastForegroundNotification());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aRefusedServiceStoppedWithWorkLeftSaysWhatDidntFinishAndDropsTheQueue() throws Exception {
        ServiceController<BlockingService> controller = Robolectric.buildService(BlockingService.class).create();
        BlockingService service = controller.get();
        try {
            refuse(service);
            assertFalse(ForegroundService.start(service, 1, new NotificationCompat.Builder(service, "test").build(),
                    ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
            service.onStartCommand(new Intent(), 0, 1);
            assertTrue("the first job is running", service.started.await(5, TimeUnit.SECONDS));
            service.onStartCommand(new Intent(), 0, 2);

            // Android stops a service that isn't in the foreground once the app leaves the screen
            controller.destroy();
            assertEquals(Boolean.TRUE, service.stoppedUnfinished);

            // quitSafely() alone still ran every start already queued, after the notice said they didn't start
            service.release.countDown();
            Thread worker = serviceLooper(service).getThread();
            worker.join(5000);
            assertFalse("the worker finished", worker.isAlive());
            assertEquals("only the running job ran", 1, service.handled.get());
        } finally {
            service.release.countDown();
        }
    }

    @Test
    public void aServiceStoppedFromOutsideMidJobReportsEvenInTheForeground() throws Exception {
        // Android ignores startForeground() without throwing for an app whose background use is restricted, so
        // being stopped with work left is what counts, not whether the start was refused
        ServiceController<BlockingService> controller = Robolectric.buildService(BlockingService.class).create();
        BlockingService service = controller.get();
        try {
            assertTrue(ForegroundService.start(service, 1, new NotificationCompat.Builder(service, "test").build(),
                    ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
            service.onStartCommand(new Intent(), 0, 1);
            assertTrue(service.started.await(5, TimeUnit.SECONDS));

            controller.destroy();

            assertEquals("a running job and nothing queued", Boolean.FALSE, service.stoppedUnfinished);
        } finally {
            service.release.countDown();
        }
    }

    @Test
    public void aRefusedServiceThatFinishesItsWorkReportsNothing() throws Exception {
        ServiceController<BlockingService> controller = Robolectric.buildService(BlockingService.class).create();
        BlockingService service = controller.get();
        refuse(service);
        assertFalse(ForegroundService.start(service, 1, new NotificationCompat.Builder(service, "test").build(),
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
        service.release.countDown();
        service.onStartCommand(new Intent(), 0, 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!shadowOf(service).isStoppedBySelf() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("the job ran and the service stopped itself", shadowOf(service).isStoppedBySelf());

        controller.destroy();

        assertNull(service.stoppedUnfinished);
    }

    @Test
    public void aStartTheLooperHasntReachedYetStillCountsAsUnfinished() throws Exception {
        ServiceController<BlockingService> controller = Robolectric.buildService(BlockingService.class).create();
        BlockingService service = controller.get();
        Looper looper = serviceLooper(service);
        // Holds the job in the queue, as when Android stops the service right after the start
        CountDownLatch gate = holdThread(looper);
        try {
            service.onStartCommand(new Intent(), 0, 1);

            controller.destroy();

            assertEquals("its job was still waiting", Boolean.TRUE, service.stoppedUnfinished);
        } finally {
            service.release.countDown();
            letGo(gate, looper);
        }
        assertEquals("the dropped job never ran", 0, service.handled.get());
    }

    @Test
    public void theUnfinishedNoticeReplacesTheProgressAndTheQueue() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        NotificationProgressHandler.NotificationManagerInfo info = new NotificationProgressHandler.NotificationManagerInfo(
                PROGRESS_CHANNEL, "Progress", NotificationManager.IMPORTANCE_LOW);
        NotificationProgressHandler.NotificationManagerInfo alerts = new NotificationProgressHandler.NotificationManagerInfo(
                "alerts_test", "Alerts", NotificationManager.IMPORTANCE_HIGH);
        NotificationProgressHandler handler = new NotificationProgressHandler(mApp, info, alerts, alerts);
        try {
            refuse(service);
            handler.onAttach(service, new NotificationProgressHandler.NotificationInfo().setTitle("Backing up"));
            handler.onProgressUpdate(10, 3, null);
            handler.onQueue(new NotificationProgressHandler.NotificationInfo().setTitle("Waiting"));
            assertNotNull(notificationWithTitle("Waiting"));

            handler.onStoppedUnfinished("Batch operations", true);

            assertNull(progressNotification());
            assertNull("the queued item won't start", notificationWithTitle("Waiting"));
            StatusBarNotification notice = notificationWithTitle(mApp.getString(R.string.foreground_service_stopped_title));
            assertNotNull(notice);
            assertEquals(mApp.getString(R.string.foreground_service_stopped_with_queue),
                    notice.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());

            // The job Android left running keeps reporting, and its progress mustn't come back
            handler.onProgressUpdate(10, 4, null);
            assertNull(progressNotification());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void eachAppsProgressInABatchFollowsTheRefusedServiceAndGoesWithIt() {
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        NotificationProgressHandler.NotificationManagerInfo info = new NotificationProgressHandler.NotificationManagerInfo(
                PROGRESS_CHANNEL, "Progress", NotificationManager.IMPORTANCE_LOW);
        NotificationProgressHandler.NotificationManagerInfo alerts = new NotificationProgressHandler.NotificationManagerInfo(
                "alerts_test", "Alerts", NotificationManager.IMPORTANCE_HIGH);
        NotificationProgressHandler handler = new NotificationProgressHandler(mApp, info, alerts, null);
        try {
            refuse(service);
            handler.onAttach(service, new NotificationProgressHandler.NotificationInfo().setTitle("Backing up"));
            ProgressHandler perApp = handler.newSubProgressHandler();
            perApp.onProgressStart(-1, 0, new NotificationProgressHandler.NotificationInfo().setTitle("Backing up Calculator"));
            StatusBarNotification appProgress = notificationWithTitle("Backing up Calculator");
            assertNotNull(appProgress);
            assertEquals("a killed process can't leave it stuck on screen", 0,
                    appProgress.getNotification().flags & Notification.FLAG_ONGOING_EVENT);

            handler.onStoppedUnfinished("Batch operations", false);

            assertNull("taken down with the batch's own progress", notificationWithTitle("Backing up Calculator"));
            perApp.onProgressUpdate(-1, 0, null);
            assertNull("and the app the batch was on doesn't put it back", notificationWithTitle("Backing up Calculator"));
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aRefusalWithNobodyInTheAppShowsNoToast() {
        // A routine applying a profile, for one: asking to stay in the app means nothing there
        ActivityManager activityManager = (ActivityManager) mApp.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo(
                mApp.getPackageName(), android.os.Process.myPid(), null);
        process.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
        shadowOf(activityManager).setProcesses(Collections.singletonList(process));
        ServiceController<FreezeUnfreezeService> controller = Robolectric.buildService(FreezeUnfreezeService.class).create();
        Service service = controller.get();
        NotificationProgressHandler.NotificationManagerInfo info = new NotificationProgressHandler.NotificationManagerInfo(
                PROGRESS_CHANNEL, "Progress", NotificationManager.IMPORTANCE_LOW);
        NotificationProgressHandler handler = new NotificationProgressHandler(mApp, info, info, null);
        try {
            refuse(service);

            handler.onAttach(service, new NotificationProgressHandler.NotificationInfo().setTitle("Applying profile"));
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(0, ShadowToast.shownToastCount());
            handler.onProgressUpdate(10, 3, null);
            assertNotNull("the work goes on and shows its progress", progressNotification());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aRefusedBatchOperationCarriesOnAndSaysWhatDidntFinish() throws Exception {
        ServiceController<BatchOpsService> controller = Robolectric.buildService(BatchOpsService.class).create();
        BatchOpsService service = controller.get();
        Looper looper = serviceLooper(service);
        // Keeps both jobs waiting, as when Android stops the service before they run
        CountDownLatch gate = holdThread(looper);
        try {
            refuse(service);
            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(mApp.getString(R.string.foreground_service_refused_keep_open), ShadowToast.getTextOfLatestToast());
            assertNull(shadowOf(service).getLastForegroundNotification());

            shadowOf(service).setThrowInStartForeground(null);
            service.onStartCommand(new Intent(), 0, 2);
            assertNotNull("a queued start tries the foreground again", shadowOf(service).getLastForegroundNotification());

            controller.destroy();

            StatusBarNotification notice = notificationWithTitle(mApp.getString(R.string.foreground_service_stopped_title));
            assertNotNull(notice);
            assertEquals(mApp.getString(R.string.foreground_service_stopped_with_queue), textOf(notice));
        } finally {
            letGo(gate, looper);
        }
    }

    @Test
    public void aRefusedInstallCarriesOnAndSaysWhatDidntFinish() throws Exception {
        ServiceController<PackageInstallerService> controller = Robolectric.buildService(PackageInstallerService.class).create();
        PackageInstallerService service = controller.get();
        Looper looper = serviceLooper(service);
        CountDownLatch gate = holdThread(looper);
        try {
            refuse(service);
            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(mApp.getString(R.string.foreground_service_refused_keep_open), ShadowToast.getTextOfLatestToast());

            shadowOf(service).setThrowInStartForeground(null);
            service.onStartCommand(new Intent(), 0, 2);
            assertNotNull("a queued start tries the foreground again", shadowOf(service).getLastForegroundNotification());

            controller.destroy();

            StatusBarNotification notice = notificationWithTitle(mApp.getString(R.string.foreground_service_stopped_title));
            assertNotNull(notice);
            assertEquals(mApp.getString(R.string.foreground_service_stopped_with_queue), textOf(notice));
        } finally {
            letGo(gate, looper);
        }
    }

    @Test
    public void aRefusedLogRecordingSaysItStopsWhenYouLeave() throws Exception {
        ServiceController<LogcatRecordingService> controller = Robolectric.buildService(LogcatRecordingService.class).create();
        LogcatRecordingService service = controller.get();
        Looper looper = serviceLooper(service);
        CountDownLatch gate = holdThread(looper);
        try {
            refuse(service);

            service.onStartCommand(new Intent(), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(mApp.getString(R.string.log_recording_refused), ShadowToast.getTextOfLatestToast());
            assertNull(shadowOf(service).getLastForegroundNotification());
            // A recording that ends saves what it caught, so there's no "Didn't finish" for it
            controller.destroy();
            assertNull(notificationWithTitle(mApp.getString(R.string.foreground_service_stopped_title)));
        } finally {
            letGo(gate, looper);
        }
    }

    /**
     * Runs one job at a time until released, and records whether it was told it was stopped unfinished.
     */
    public static class BlockingService extends ForegroundService {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger handled = new AtomicInteger();
        volatile Boolean stoppedUnfinished;

        public BlockingService() {
            super("BlockingService");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            handled.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        protected void onStoppedUnfinished(boolean queueDropped) {
            stoppedUnfinished = queueDropped;
        }
    }

    private static void refuse(@NonNull Service service) {
        shadowOf(service).setThrowInStartForeground(new ForegroundServiceStartNotAllowedException(
                "Service.startForeground() not allowed due to mAllowStartForeground false"));
    }

    @Nullable
    private StatusBarNotification progressNotification() {
        NotificationManager manager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (PROGRESS_CHANNEL.equals(notification.getNotification().getChannelId())) {
                return notification;
            }
        }
        return null;
    }

    @Nullable
    private StatusBarNotification notificationWithTitle(@NonNull String title) {
        return notificationWithExtra(Notification.EXTRA_TITLE, title);
    }

    @Nullable
    private StatusBarNotification notificationWithText(@NonNull String text) {
        return notificationWithExtra(Notification.EXTRA_TEXT, text);
    }

    @Nullable
    private static String textOf(@NonNull StatusBarNotification notification) {
        CharSequence text = notification.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        return text != null ? text.toString() : null;
    }

    @Nullable
    private StatusBarNotification notificationWithExtra(@NonNull String key, @NonNull String value) {
        NotificationManager manager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            CharSequence extra = notification.getNotification().extras.getCharSequence(key);
            if (extra != null && value.equals(extra.toString())) {
                return notification;
            }
        }
        return null;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @NonNull
    private static Looper serviceLooper(@NonNull ForegroundService service) throws ReflectiveOperationException {
        Field field = ForegroundService.class.getDeclaredField("mServiceLooper");
        field.setAccessible(true);
        return (Looper) field.get(service);
    }

    /**
     * Keeps the service's thread busy until the returned latch opens, so starts sent meanwhile wait in its queue, as
     * when Android stops the service before they run. A paused looper can't stand in: Robolectric won't unpause one
     * that onDestroy() has quit.
     */
    @NonNull
    private static CountDownLatch holdThread(@NonNull Looper looper) throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch holding = new CountDownLatch(1);
        new Handler(looper).post(() -> {
            holding.countDown();
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue("the service's thread picked up the hold", holding.await(5, TimeUnit.SECONDS));
        return gate;
    }

    private static void letGo(@NonNull CountDownLatch gate, @NonNull Looper looper) throws InterruptedException {
        gate.countDown();
        looper.getThread().join(5000);
    }

    private static Object getField(@NonNull Object target, @NonNull String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
