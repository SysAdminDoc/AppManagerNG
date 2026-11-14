// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;

/**
 * Port of upstream 2f2b31e89. The pairing port used to travel through a LiveData, which handed a
 * search that started again the port from the pairing dialog before, and the service asked for a
 * code for a port that was already gone.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbPairingServiceTest {
    private static final int NOTIFICATION_ID = 1;

    @Test
    public void aPortFromAnEarlierSearchIsIgnored() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        try {
            assertEquals(Service.START_NOT_STICKY,
                    service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1));
            int search = service.getSearchGeneration();

            service.onPairingPortFound(search - 1, 37001);
            assertNull(pairingPortText(app, 37001));

            service.onPairingPortFound(search, 37002);
            String text = pairingPortText(app, 37002);
            assertNotNull(text);
            assertTrue(text, text.contains("37002"));
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aRefusedPairingSearchNeverStartsAndCancelsTheSession() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ShadowToast.reset();
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        try {
            int generation = service.getSearchGeneration();
            // The session is shared by every test, so it must not start out cancelled already
            AdbPairingSession.searching();
            assertEquals(AdbPairingState.Status.SEARCHING, AdbPairingSession.getCurrentState().getStatus());
            // Android 12+ refuses a foreground start from the background (upstream #2066)
            shadowOf(service).setThrowInStartForeground(new ForegroundServiceStartNotAllowedException(
                    "Service.startForeground() not allowed due to mAllowStartForeground false"));

            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals("no search was started", generation, service.getSearchGeneration());
            assertTrue(shadowOf(service).isStoppedBySelf());
            assertEquals("stopped for the start it was given", 1, shadowOf(service).getStopSelfResultId());
            assertEquals(app.getString(R.string.adb_pairing_refused), ShadowToast.getTextOfLatestToast());
            assertEquals(AdbPairingState.Status.CANCELLED, AdbPairingSession.getCurrentState().getStatus());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aStopSentToAServiceOutOfTheForegroundStillCallsStartForeground() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        try {
            // As after a refused pairing: the Stop and swipe-away intents come through startForegroundService(),
            // and Android ends the app if the service stops without calling startForeground() for them
            assertEquals(0, shadowOf(service).getLastForegroundNotificationId());

            service.onStartCommand(AdbPairingService.getStopSearchingIntent(app), 0, 5);
            shadowOf(Looper.getMainLooper()).idle();

            // The stop removes the notification again, but the id shows startForeground() ran
            assertEquals("startForeground was called for the stop", 1, shadowOf(service).getLastForegroundNotificationId());
            assertTrue(shadowOf(service).isForegroundStopped());
            assertEquals(5, shadowOf(service).getStopSelfResultId());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aSearchNobodyFinishesStopsAfterTenMinutesCountedFromTheLastPortFound() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        try {
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            ShadowLooper.idleMainLooper(9, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());

            // Found near the end of the search, the port still leaves the full time to type the code
            service.onPairingPortFound(service.getSearchGeneration(), 37003);
            ShadowLooper.idleMainLooper(9, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());

            ShadowLooper.idleMainLooper(2, TimeUnit.MINUTES);
            assertTrue(shadowOf(service).isStoppedBySelf());
            assertNull(shadowOf(app.getSystemService(NotificationManager.class)).getNotification(NOTIFICATION_ID));
        } finally {
            controller.destroy();
        }
    }

    @Test(timeout = 60_000)
    public void thePairingItselfIsNotCutOffByTheSearchTimeoutButAFailedOneStillEnds() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        CountDownLatch pairingEnds = new CountDownLatch(1);
        AtomicBoolean pairingDone = new AtomicBoolean();
        service.setPairer((host, port, code) -> {
            try {
                pairingEnds.await();
                throw new IOException("Connection refused");
            } finally {
                pairingDone.set(true);
            }
        });
        try {
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            ShadowLooper.idleMainLooper(9, TimeUnit.MINUTES);
            AdbPairingRequest request = AdbPairingRequest.create(37004, "123456");
            assertNotNull(request);
            service.onStartCommand(AdbPairingService.getStartPairingIntent(app, request), 0, 2);

            // Still pairing well past the search's 10 minutes
            ShadowLooper.idleMainLooper(11, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());

            // It fails and leaves its Retry notification up, which used to stay until the hour-long
            // pairing wait ran out
            pairingEnds.countDown();
            long deadline = System.currentTimeMillis() + 20_000;
            while (!(pairingDone.get() && retryOffered(app)) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
                shadowOf(Looper.getMainLooper()).idle();
            }
            assertTrue(retryOffered(app));
            ShadowLooper.idleMainLooper(9, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());
            ShadowLooper.idleMainLooper(2, TimeUnit.MINUTES);
            assertTrue(shadowOf(service).isStoppedBySelf());
        } finally {
            pairingEnds.countDown();
            controller.destroy();
        }
    }

    @Test(timeout = 60_000)
    public void aSearchAskedForDuringThePairingDoesNotKeepThePairedServiceRunning() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        CountDownLatch pairingEnds = new CountDownLatch(1);
        service.setPairer((host, port, code) -> pairingEnds.await());
        try {
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            AdbPairingRequest request = AdbPairingRequest.create(37005, "123456");
            assertNotNull(request);
            service.onStartCommand(AdbPairingService.getStartPairingIntent(app, request), 0, 2);
            // Tapping "Pairing in progress" opens the code screen, which asks for a search. The one already running
            // takes it, so this start leaves nothing behind to stop the service later.
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 3);

            pairingEnds.countDown();
            long deadline = System.currentTimeMillis() + 20_000;
            while (!(shadowOf(service).isStoppedBySelf() && pairedText(app) != null)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
                shadowOf(Looper.getMainLooper()).idle();
            }

            assertEquals("stopped for the start handled last", 3, shadowOf(service).getStopSelfResultId());
            assertTrue(shadowOf(service).isForegroundStopped());
            assertFalse("the success notification is left up", shadowOf(service).getNotificationShouldRemoved());
            assertNotNull(pairedText(app));
        } finally {
            pairingEnds.countDown();
            controller.destroy();
        }
    }

    @Test(timeout = 60_000)
    public void anEmulatorPairsWithItself() throws Exception {
        // Wireless debugging's pairing port is on the device. Pairing an emulator went to the
        // computer (10.0.2.2) and was always refused.
        ShadowBuild.setHardware("ranchu");
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        AtomicReference<String> pairedWith = new AtomicReference<>();
        CountDownLatch paired = new CountDownLatch(1);
        service.setPairer((host, port, code) -> {
            pairedWith.set(host + ":" + port);
            paired.countDown();
        });
        try {
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            AdbPairingRequest request = AdbPairingRequest.create(36353, "666902");
            assertNotNull(request);
            service.onStartCommand(AdbPairingService.getStartPairingIntent(app, request), 0, 2);

            assertTrue(paired.await(20, TimeUnit.SECONDS));
            assertEquals("127.0.0.1:36353", pairedWith.get());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aSearchStoppedTheMomentItStartedIsStoppedOnceItIsUnderWay() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        List<String> calls = new ArrayList<>();
        service.setPortSearchFactory(listener -> new AdbUtils.Scanner() {
            @Override
            public void start() {
                calls.add("start");
            }

            @Override
            public void stop() {
                calls.add("stop");
            }
        });
        try {
            // libadb skips the stop before Android says the discovery started, and it then runs on
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            service.onStartCommand(AdbPairingService.getStopSearchingIntent(app), 0, 2);
            assertEquals(Collections.singletonList("start"), calls);
            ShadowLooper.idleMainLooper(AdbPairingService.SEARCH_SETTLE_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(Arrays.asList("start", "stop"), calls);

            // A search that has been running a while stops at once
            calls.clear();
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 3);
            ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);
            service.onStartCommand(AdbPairingService.getStopSearchingIntent(app), 0, 4);
            assertEquals(Arrays.asList("start", "stop"), calls);
        } finally {
            controller.destroy();
        }
    }

    private static boolean retryOffered(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Notification notification = shadowOf(manager).getNotification(NOTIFICATION_ID);
        if (notification == null || notification.actions == null) {
            return false;
        }
        for (Notification.Action action : notification.actions) {
            if (context.getString(R.string.adb_pairing_retry_pairing).contentEquals(action.title)) {
                return true;
            }
        }
        return false;
    }

    private static String pairedText(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Notification notification = shadowOf(manager).getNotification(NOTIFICATION_ID);
        if (notification == null) {
            return null;
        }
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        return text != null && context.getString(R.string.paired_successfully).contentEquals(text) ? text.toString() : null;
    }

    private static String pairingPortText(Context context, int port) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Notification notification = shadowOf(manager).getNotification(NOTIFICATION_ID);
        if (notification == null) {
            return null;
        }
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        return text != null && text.toString().contains(String.valueOf(port)) ? text.toString() : null;
    }
}
