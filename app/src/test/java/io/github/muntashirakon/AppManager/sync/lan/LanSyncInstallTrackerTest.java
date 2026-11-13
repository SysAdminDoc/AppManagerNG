// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

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
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;

@RunWith(RobolectricTestRunner.class)
public class LanSyncInstallTrackerTest {
    private static final String PKG = "com.example.sent";
    private static final String REASON = "Installation failed: incompatible";
    private static final String DETAIL = "INSTALL_FAILED_NO_MATCHING_ABIS: no native code for arm64-v8a";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private Application mApp;
    private ReceivedApkStore mStore;

    @Before
    public void setUp() throws Exception {
        mApp = ApplicationProvider.getApplicationContext();
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mStore = new ReceivedApkStore(mTemp.getRoot());
        LanSyncInstallTracker.resetForTesting();
    }

    @After
    public void tearDown() throws Exception {
        LanSyncInstallTracker.resetForTesting();
    }

    @Test
    public void aFailedApkSaysWhyAndOffersAndroidsInstallerAndDownloads() throws Exception {
        LanSyncDownloader.Result result = result("base.apk");
        LanSyncInstallTracker.track(result);

        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE,
                REASON, DETAIL);
        shadowOf(Looper.getMainLooper()).idle();

        Notification notification = failureNotification();
        assertNotNull(notification);
        assertEquals(mApp.getString(R.string.lan_sync_install_failed_title, PKG),
                String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE)));
        String body = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        assertTrue(body, body.contains(REASON));
        assertTrue("Android's own words reach the user: " + body, body.contains(DETAIL));
        assertTrue(body, body.contains(mApp.getString(R.string.lan_sync_install_failed_try_system)));
        assertEquals(Arrays.asList(mApp.getString(R.string.received_apks_system_installer),
                mApp.getString(R.string.received_apks_save_to_downloads)), actionTitles(notification));
        assertAction(notification.actions[0], ReceivedApksActivity.ACTION_SYSTEM_INSTALLER, result.entryKey);
        assertAction(notification.actions[1], ReceivedApksActivity.ACTION_SAVE, result.entryKey);

        LanSyncInstallTracker.Failure failure = LanSyncInstallTracker.getFailures().getValue();
        assertNotNull("The open screen hears about it too", failure);
        assertEquals(result.entryKey, failure.entryKey);
        assertFalse(failure.bundle);
        assertEquals(DETAIL, failure.detail);
        assertTrue(failure.isRecent(SystemClock.elapsedRealtime()));
        assertFalse(failure.isRecent(SystemClock.elapsedRealtime() + LanSyncInstallTracker.RECENT_MILLIS + 1));
        assertFalse(LanSyncInstallTracker.isTracked(PKG));
    }

    @Test
    public void aFailedSplitSetOffersDownloadsOnly() throws Exception {
        LanSyncDownloader.Result result = result("base.apk", "split_config.arm64_v8a.apk");
        assertEquals(LanSyncDownloader.MIME_APKS, result.mimeType);
        LanSyncInstallTracker.track(result);

        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_FAILURE_INVALID,
                REASON, null);
        shadowOf(Looper.getMainLooper()).idle();

        Notification notification = failureNotification();
        assertNotNull(notification);
        String body = String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        assertTrue(body, body.contains(mApp.getString(R.string.lan_sync_install_failed_try_save)));
        assertEquals(Collections.singletonList(mApp.getString(R.string.received_apks_save_to_downloads)),
                actionTitles(notification));
        assertAction(notification.actions[0], ReceivedApksActivity.ACTION_SAVE, result.entryKey);
        LanSyncInstallTracker.Failure failure = LanSyncInstallTracker.getFailures().getValue();
        assertNotNull(failure);
        assertTrue(failure.bundle);
    }

    @Test
    public void successACancelAndOtherAppsStayQuiet() throws Exception {
        LanSyncInstallTracker.track(result("base.apk"));
        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_SUCCESS, "Installed", null);
        assertFalse(LanSyncInstallTracker.isTracked(PKG));

        LanSyncInstallTracker.track(result("base.apk"));
        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_FAILURE_ABORTED,
                "Cancelled", null);
        assertFalse(LanSyncInstallTracker.isTracked(PKG));

        // An install that didn't come from Local sync
        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_FAILURE_INVALID,
                REASON, null);
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(failureNotification());
        assertNull(LanSyncInstallTracker.getFailures().getValue());
    }

    @Test
    public void waitingForTheUserIsNotTheEnd() throws Exception {
        LanSyncInstallTracker.track(result("base.apk"));

        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstaller.STATUS_PENDING_USER_ACTION, "", null);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(LanSyncInstallTracker.isTracked(PKG));
        assertNull(failureNotification());

        LanSyncInstallTracker.onInstallFinished(mApp, PKG, PackageInstallerCompat.STATUS_FAILURE_CONFLICT,
                REASON, null);
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(failureNotification());
    }

    @NonNull
    private LanSyncDownloader.Result result(@NonNull String... names) throws IOException {
        SyncApp.ApkFileInfo[] files = new SyncApp.ApkFileInfo[names.length];
        for (int i = 0; i < names.length; ++i) {
            files[i] = new SyncApp.ApkFileInfo(names[i], 10);
        }
        SyncApp app = SyncFixtures.app(PKG, 2, Collections.singletonList(SyncFixtures.SIGNER_A),
                Collections.<String>emptyList(), Arrays.asList(files));
        ReceivedApkStore.Entry entry = mStore.open(app, System.currentTimeMillis());
        boolean bundle = names.length > 1;
        return new LanSyncDownloader.Result(new File(entry.dir, bundle ? "sync.apks" : names[0]),
                bundle ? LanSyncDownloader.MIME_APKS : LanSyncDownloader.MIME_APK, bundle, entry);
    }

    @Nullable
    private Notification failureNotification() {
        String tag = LanSyncInstallTracker.notificationTag(PKG);
        for (StatusBarNotification sbn : mApp.getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (tag.equals(sbn.getTag())) {
                return sbn.getNotification();
            }
        }
        return null;
    }

    @NonNull
    private static List<String> actionTitles(@NonNull Notification notification) {
        String[] titles = new String[notification.actions == null ? 0 : notification.actions.length];
        for (int i = 0; i < titles.length; ++i) {
            titles[i] = String.valueOf(notification.actions[i].title);
        }
        return Arrays.asList(titles);
    }

    private static void assertAction(@NonNull Notification.Action action, @NonNull String expectedAction,
                                     @NonNull String entryKey) {
        Intent intent = shadowOf(action.actionIntent).getSavedIntent();
        assertNotNull(intent.getComponent());
        assertEquals(ReceivedApksActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(expectedAction, intent.getAction());
        assertEquals(entryKey, intent.getStringExtra(ReceivedApksActivity.EXTRA_ENTRY_KEY));
        assertEquals(PKG, intent.getStringExtra(ReceivedApksActivity.EXTRA_PACKAGE_NAME));
        assertTrue("Started from a notification", (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }
}
