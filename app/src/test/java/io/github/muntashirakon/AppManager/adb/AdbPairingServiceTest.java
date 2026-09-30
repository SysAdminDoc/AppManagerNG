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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

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

    @Test
    public void thePairingItselfIsNotCutOffByTheSearchTimeout() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<AdbPairingService> controller = Robolectric.buildService(AdbPairingService.class).create();
        AdbPairingService service = controller.get();
        try {
            service.onStartCommand(AdbPairingService.getStartSearchingIntent(app), 0, 1);
            ShadowLooper.idleMainLooper(9, TimeUnit.MINUTES);
            // Port 9 refuses at once, so the pairing fails and leaves its Retry notification up
            AdbPairingRequest request = AdbPairingRequest.create(9, "123456");
            assertNotNull(request);
            service.onStartCommand(AdbPairingService.getStartPairingIntent(app, request), 0, 2);

            ShadowLooper.idleMainLooper(11, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());
        } finally {
            controller.destroy();
        }
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
