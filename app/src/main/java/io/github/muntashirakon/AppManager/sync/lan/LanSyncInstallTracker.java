// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Follows apps that Local sync handed to the installer. When one fails, it says why and offers
 * the ways out: Android's own installer for a single APK, and saving to Downloads.
 */
public final class LanSyncInstallTracker {
    /**
     * An install of a received app that didn't go through.
     */
    public static final class Failure {
        @NonNull
        public final String packageName;
        @NonNull
        public final String label;
        @NonNull
        public final String entryKey;
        public final boolean bundle;
        /**
         * What the installer said, such as "Installation failed: incompatible".
         */
        @NonNull
        public final String reason;
        /**
         * The status message from Android, when there is one.
         */
        @Nullable
        public final String detail;
        /**
         * {@link SystemClock#elapsedRealtime()} when it failed.
         */
        public final long at;

        Failure(@NonNull String packageName, @NonNull Handoff handoff, @NonNull String reason,
                @Nullable String detail, long at) {
            this.packageName = packageName;
            this.label = handoff.label;
            this.entryKey = handoff.entryKey;
            this.bundle = handoff.bundle;
            this.reason = reason;
            this.detail = detail;
            this.at = at;
        }

        /**
         * Whether a screen opened now should still bring it up. An older one stays in the
         * notification only.
         */
        public boolean isRecent(long nowElapsed) {
            return nowElapsed - at <= RECENT_MILLIS;
        }
    }

    static final long RECENT_MILLIS = 2 * 60 * 1000;

    private static final class Handoff {
        @NonNull
        final String entryKey;
        @NonNull
        final String label;
        final boolean bundle;

        Handoff(@NonNull LanSyncDownloader.Result result) {
            entryKey = result.entryKey;
            label = result.label;
            bundle = LanSyncDownloader.MIME_APKS.equals(result.mimeType);
        }
    }

    private static final String NOTIFICATION_TAG_PREFIX = "lan_sync_install:";

    private static final Map<String, Handoff> sHandoffs = new HashMap<>();
    private static final MutableLiveData<Failure> sFailures = new MutableLiveData<>();

    private LanSyncInstallTracker() {
    }

    /**
     * Call just before the installer gets a received app.
     */
    public static void track(@NonNull LanSyncDownloader.Result result) {
        synchronized (sHandoffs) {
            sHandoffs.put(result.packageName, new Handoff(result));
        }
    }

    /**
     * The latest failure for a screen to show. Call {@link #onFailureShown()} once it's up.
     */
    @NonNull
    public static LiveData<Failure> getFailures() {
        return sFailures;
    }

    @MainThread
    public static void onFailureShown() {
        sFailures.setValue(null);
    }

    @NonNull
    public static String notificationTag(@NonNull String packageName) {
        return NOTIFICATION_TAG_PREFIX + packageName;
    }

    /**
     * Called by the installer for every finished install, on any thread. Only apps handed over
     * by {@link #track} count. Success and a cancel by the user end the tracking quietly.
     */
    public static void onInstallFinished(@NonNull Context context, @NonNull String packageName, int status,
                                         @NonNull String reason, @Nullable String detail) {
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Not the end yet: Android is asking the user
            return;
        }
        Handoff handoff;
        synchronized (sHandoffs) {
            handoff = sHandoffs.remove(packageName);
        }
        if (handoff == null || status == PackageInstallerCompat.STATUS_SUCCESS
                || status == PackageInstallerCompat.STATUS_FAILURE_ABORTED) {
            return;
        }
        Failure failure = new Failure(packageName, handoff, reason, detail, SystemClock.elapsedRealtime());
        sFailures.postValue(failure);
        notifyFailure(context.getApplicationContext(), failure);
    }

    @VisibleForTesting
    static boolean isTracked(@NonNull String packageName) {
        synchronized (sHandoffs) {
            return sHandoffs.containsKey(packageName);
        }
    }

    @VisibleForTesting
    @MainThread
    static void resetForTesting() {
        synchronized (sHandoffs) {
            sHandoffs.clear();
        }
        sFailures.setValue(null);
    }

    private static void notifyFailure(@NonNull Context context, @NonNull Failure failure) {
        StringBuilder body = new StringBuilder(failure.reason);
        if (failure.detail != null && !failure.detail.isEmpty()) {
            body.append("\n\n").append(failure.detail);
        }
        body.append("\n\n").append(context.getString(failure.bundle ? R.string.lan_sync_install_failed_try_save
                : R.string.lan_sync_install_failed_try_system));
        PendingIntent open = actionPendingIntent(context, failure, null);
        PendingIntent save = actionPendingIntent(context, failure, ReceivedApksActivity.ACTION_SAVE);
        PendingIntent systemInstaller = failure.bundle ? null
                : actionPendingIntent(context, failure, ReceivedApksActivity.ACTION_SYSTEM_INSTALLER);
        NotificationUtils.displayHighPriorityNotification(context, notificationTag(failure.packageName), builder -> {
            builder.setAutoCancel(true)
                    .setSmallIcon(R.drawable.ic_default_notification)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setContentTitle(context.getString(R.string.lan_sync_install_failed_title, failure.label))
                    .setContentText(failure.reason)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(open);
            if (systemInstaller != null) {
                builder.addAction(0, context.getString(R.string.received_apks_system_installer), systemInstaller);
            }
            builder.addAction(0, context.getString(R.string.received_apks_save_to_downloads), save);
            return builder.build();
        });
    }

    @Nullable
    private static PendingIntent actionPendingIntent(@NonNull Context context, @NonNull Failure failure,
                                                     @Nullable String action) {
        Intent intent = ReceivedApksActivity.getIntent(context, failure, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Extras don't tell PendingIntents apart, so each app and action gets its own code
        int requestCode = (failure.packageName + '/' + action).hashCode();
        return PendingIntentCompat.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT,
                false);
    }
}
