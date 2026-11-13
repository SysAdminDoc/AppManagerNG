// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

@RunWith(RobolectricTestRunner.class)
public class PackageInstallerServiceTest {
    @Test
    public void finalProgressNotificationUsesConcreteSuccessSubject() {
        Context context = RuntimeEnvironment.getApplication();
        FakeProgressHandler progressHandler = new FakeProgressHandler();
        String subject = PackageInstallerService.getStringFromStatus(context, STATUS_SUCCESS, "Example", null);

        PackageInstallerService.prepareFinalProgressNotification(progressHandler, "Example", subject, null);

        assertEquals("Example", progressHandler.lastInfo.getTitle());
        assertEquals(subject, progressHandler.lastInfo.getBody());
        assertNotEquals(context.getString(R.string.done), progressHandler.lastInfo.getBody());
        assertTrue(progressHandler.progressTextCleared);
    }

    @Test
    public void finalProgressNotificationUsesConcreteFailureSubjectAndDetails() {
        Context context = RuntimeEnvironment.getApplication();
        FakeProgressHandler progressHandler = new FakeProgressHandler();
        String subject = PackageInstallerService.getStringFromStatus(context, STATUS_FAILURE_SECURITY, "Example", null);
        NotificationCompat.Style details = new NotificationCompat.BigTextStyle()
                .bigText(subject + "\n\nINSTALL_FAILED_USER_RESTRICTED");

        PackageInstallerService.prepareFinalProgressNotification(progressHandler, "Example", subject, details);

        assertEquals("Example", progressHandler.lastInfo.getTitle());
        assertEquals(subject, progressHandler.lastInfo.getBody());
        assertEquals(details, progressHandler.lastInfo.getStyle());
        assertNotEquals(context.getString(R.string.done), progressHandler.lastInfo.getBody());
    }

    @Test
    public void recoveryHintFromStatusIsUserFacingText() {
        Context context = RuntimeEnvironment.getApplication();
        String hint = PackageInstallerService.getRecoveryHintFromStatus(context, STATUS_FAILURE_SECURITY);

        assertTrue(hint.contains("APK"));
        assertFalse(hint.contains("STATUS_FAILURE_SECURITY"));
    }

    @Test
    public void staleInstallSessionRequiresPositivePastTimestampBeyondTimeout() {
        long now = 60_000L;
        long timeout = 30_000L;

        assertTrue(PackageInstallerService.isStaleInstallSession(30_000L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(30_001L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(0L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(70_000L, now, timeout));
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void aQueuedItemsNotificationGoesAwayWhenThatItemStarts() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<PackageInstallerService> controller = Robolectric.buildService(PackageInstallerService.class).create();
        PackageInstallerService service = controller.get();
        try {
            // The first install is running, so the next two wait in the queue
            service.onStartCommand(new Intent(app, PackageInstallerService.class), 0, 1);
            Intent first = queuedIntent(app, "First");
            Intent second = queuedIntent(app, "Second");
            service.onQueued(first);
            service.onQueued(second);
            assertEquals(Arrays.asList("First", "Second"), queueTitles(app));

            // The user starts the first queued install by hand
            service.onStartIntent(first);
            assertEquals(Collections.singletonList("Second"), queueTitles(app));

            service.onStartIntent(second);
            assertEquals(Collections.emptyList(), queueTitles(app));
        } finally {
            controller.destroy();
        }
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void queueNotificationsGoAwayWhenTheInstallerStopsBeforeReachingThem() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ServiceController<PackageInstallerService> controller = Robolectric.buildService(PackageInstallerService.class).create();
        PackageInstallerService service = controller.get();
        service.onStartCommand(new Intent(app, PackageInstallerService.class), 0, 1);
        service.onQueued(queuedIntent(app, "Waiting"));
        assertEquals(Collections.singletonList("Waiting"), queueTitles(app));

        controller.destroy();

        assertEquals(Collections.emptyList(), queueTitles(app));
    }

    @NonNull
    private static Intent queuedIntent(@NonNull Context context, @NonNull String label) {
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(
                Uri.parse("file:///sdcard/Download/" + label + ".apk"),
                "application/vnd.android.package-archive"));
        item.setAppLabel(label);
        Intent intent = new Intent(context, PackageInstallerService.class);
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, item);
        return intent;
    }

    @NonNull
    private static List<String> queueTitles(@NonNull Context context) {
        String queued = context.getString(R.string.added_to_queue);
        List<String> titles = new ArrayList<>();
        for (StatusBarNotification sbn : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
            Bundle extras = sbn.getNotification().extras;
            if (queued.contentEquals(String.valueOf(extras.getCharSequence(Notification.EXTRA_TEXT)))) {
                titles.add(String.valueOf(extras.getCharSequence(Notification.EXTRA_TITLE)));
            }
        }
        Collections.sort(titles);
        return titles;
    }

    private static final class FakeProgressHandler extends QueuedProgressHandler {
        final NotificationInfo lastInfo = new NotificationInfo()
                .setTitle("Installing Example")
                .setBody("Installing");
        boolean progressTextCleared;

        @Override
        public void onQueue(@Nullable Object message) {
        }

        @Override
        public void onAttach(@Nullable Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onResult(@Nullable Object message) {
        }

        @Override
        public void onDetach(@Nullable Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return this;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return lastInfo;
        }

        @Override
        public int getLastMax() {
            return 0;
        }

        @Override
        public float getLastProgress() {
            return 0;
        }

        @Override
        public void setProgressTextInterface(@Nullable ProgressTextInterface progressTextInterface) {
            progressTextCleared = progressTextInterface == null;
            super.setProgressTextInterface(progressTextInterface);
        }
    }
}
