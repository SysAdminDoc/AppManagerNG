// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INVALID;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

@RunWith(RobolectricTestRunner.class)
public class PackageInstallerServiceTest {
    private static final String REVIEWED = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void thePackageAsReviewedPassesTheCheck() {
        assertNull(PackageInstallerService.describePayloadMismatch(REVIEWED, 4096, REVIEWED, 4096));
        // Digests are compared without regard to case
        assertNull(PackageInstallerService.describePayloadMismatch(REVIEWED.toUpperCase(), 4096, REVIEWED, 4096));
        // Nothing was reviewed: install-existing, or a history entry older than the check
        assertNull(PackageInstallerService.describePayloadMismatch(null, -1, REVIEWED, 4096));
    }

    @Test
    public void aPackageThatChangedSinceTheReviewIsRefused() {
        String otherDigest = REVIEWED.replace('9', '8');
        String changedBytes = PackageInstallerService.describePayloadMismatch(REVIEWED, 4096, otherDigest, 4096);
        String changedSize = PackageInstallerService.describePayloadMismatch(REVIEWED, 4096, REVIEWED, 4097);
        String unreadable = PackageInstallerService.describePayloadMismatch(REVIEWED, 4096, null, -1);

        assertNotNull(changedBytes);
        assertTrue(changedBytes.contains(REVIEWED));
        assertTrue(changedBytes.contains(otherDigest));
        assertNotNull(changedSize);
        assertTrue(changedSize.contains("4097"));
        assertNotNull(unreadable);
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void theServiceRefusesAnInstallWhoseFileChangedAfterTheReview() throws IOException, InterruptedException {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        File shared = temporaryFolder.newFile("shared.apk");
        try (ZipFile bundle = new ZipFile(new File(PackageInstallerServiceTest.class.getClassLoader()
                .getResource("AppManager_v2.5.22.apks").getFile()));
             InputStream in = bundle.getInputStream(bundle.getEntry("base.apk"));
             OutputStream out = new FileOutputStream(shared)) {
            IoUtils.copy(in, out);
        }
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(Uri.fromFile(shared),
                "application/vnd.android.package-archive"));
        item.setAppLabel("Example");
        item.setSelectedSplits(new ArrayList<>());
        // What the user saw: same size, other bytes
        item.setReviewedPayload(REVIEWED, shared.length());
        Intent intent = new Intent(app, PackageInstallerService.class);
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, item);

        ServiceController<PackageInstallerService> controller = Robolectric.buildService(PackageInstallerService.class).create();
        PackageInstallerService service = controller.get();
        AtomicInteger status = new AtomicInteger(Integer.MIN_VALUE);
        AtomicReference<String> message = new AtomicReference<>();
        try {
            service.onStartCommand(new Intent(app, PackageInstallerService.class), 0, 1);
            service.setOnInstallFinished((packageName, result, blockingPackage, statusMessage) -> {
                status.set(result);
                message.set(statusMessage);
            });

            // The service handles each queued item on its worker thread, which may write history
            Thread worker = new Thread(() -> service.onHandleIntent(intent));
            worker.start();
            worker.join(60_000);
            assertFalse(worker.isAlive());
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(STATUS_FAILURE_INVALID, status.get());
            assertNotNull(message.get());
            assertTrue(message.get(), message.get().contains("changed after it was reviewed"));
            assertTrue(message.get(), message.get().contains(REVIEWED));
        } finally {
            controller.destroy();
        }
    }

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
