// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.text.format.DateUtils;
import android.text.format.Formatter;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.IntervalType;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.TimeInterval;

/**
 * NF-17 — Runtime telemetry helper. Wraps the existing
 * {@link AppUsageStatsManager} into a one-call "summary for the inspected
 * package over the last 24h" string suitable for inlining into a
 * MaterialAlertDialog body.
 *
 * <p>Reuses the public PackageUsageInfo flow used by AppUsageActivity so the
 * numbers stay in sync. No new permission is requested — the caller is
 * expected to handle the {@code PACKAGE_USAGE_STATS} missing-permission case
 * before invoking this helper.</p>
 */
public final class RuntimeTelemetryHelper {

    /** Result struct rendered by {@link #renderSummary}. Plain data so tests can assert it. */
    public static final class Snapshot {
        public final long screenTimeMillis;
        public final long lastUsageTime;
        public final int timesOpened;
        public final long mobileTxBytes;
        public final long mobileRxBytes;
        public final long wifiTxBytes;
        public final long wifiRxBytes;

        @VisibleForTesting
        public Snapshot(long screenTimeMillis, long lastUsageTime, int timesOpened,
                         long mobileTxBytes, long mobileRxBytes,
                         long wifiTxBytes, long wifiRxBytes) {
            this.screenTimeMillis = screenTimeMillis;
            this.lastUsageTime = lastUsageTime;
            this.timesOpened = timesOpened;
            this.mobileTxBytes = mobileTxBytes;
            this.mobileRxBytes = mobileRxBytes;
            this.wifiTxBytes = wifiTxBytes;
            this.wifiRxBytes = wifiRxBytes;
        }
    }

    private RuntimeTelemetryHelper() {
    }

    /**
     * Compute the snapshot over the last 24 hours for {@code packageName}.
     * Throws on missing-permission / no-such-package — the caller should
     * surface those to the user. Blocking; call from a worker thread.
     */
    @WorkerThread
    @NonNull
    public static Snapshot collectLast24h(@NonNull String packageName, int userId) throws Exception {
        long now = System.currentTimeMillis();
        long start = now - TimeUnit.DAYS.toMillis(1);
        TimeInterval interval = new TimeInterval(IntervalType.INTERVAL_DAILY, start, now);
        PackageUsageInfo info = AppUsageStatsManager.getInstance()
                .getUsageStatsForPackage(packageName, interval, userId);
        long mobileTx = 0, mobileRx = 0, wifiTx = 0, wifiRx = 0;
        if (info.mobileData != null) {
            mobileTx = info.mobileData.getTx();
            mobileRx = info.mobileData.getRx();
        }
        if (info.wifiData != null) {
            wifiTx = info.wifiData.getTx();
            wifiRx = info.wifiData.getRx();
        }
        return new Snapshot(info.screenTime, info.lastUsageTime, info.timesOpened,
                mobileTx, mobileRx, wifiTx, wifiRx);
    }

    /**
     * Format the snapshot as a multi-line body for inclusion in a
     * MaterialAlertDialog#setMessage. Public for testing.
     */
    @AnyThread
    @NonNull
    public static String renderSummary(@NonNull Context context, @NonNull Snapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.runtime_telemetry_screen_time,
                formatDuration(snapshot.screenTimeMillis)));
        sb.append('\n').append(context.getString(R.string.runtime_telemetry_times_opened,
                snapshot.timesOpened));
        if (snapshot.lastUsageTime > 0) {
            sb.append('\n').append(context.getString(R.string.runtime_telemetry_last_used,
                    DateUtils.getRelativeTimeSpanString(snapshot.lastUsageTime,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS)));
        }
        sb.append('\n').append(context.getString(R.string.runtime_telemetry_mobile_data,
                Formatter.formatFileSize(context, snapshot.mobileRxBytes),
                Formatter.formatFileSize(context, snapshot.mobileTxBytes)));
        sb.append('\n').append(context.getString(R.string.runtime_telemetry_wifi_data,
                Formatter.formatFileSize(context, snapshot.wifiRxBytes),
                Formatter.formatFileSize(context, snapshot.wifiTxBytes)));
        return sb.toString();
    }

    /**
     * Format milliseconds as "Hh Mm Ss" / "Mm Ss" / "Ss" depending on
     * magnitude. Pure-JVM testable.
     */
    @VisibleForTesting
    @NonNull
    static String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
                - TimeUnit.HOURS.toSeconds(hours)
                - TimeUnit.MINUTES.toSeconds(minutes);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%ds", seconds);
    }
}
