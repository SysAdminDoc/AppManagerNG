// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/** One alert for the latest check, replaced by the next one, listing what didn't match and what was done. */
public final class PermissionWatchNotifier {
    public static final String NOTIFICATION_TAG = "permission_watcher";
    @VisibleForTesting
    static final int MAX_LINES = 8;

    public interface LabelResolver {
        @NonNull
        String label(@NonNull String packageName);
    }

    private PermissionWatchNotifier() {
    }

    @WorkerThread
    public static void notify(@NonNull Context context, @NonNull PermissionWatcher.Report report) {
        if (report.drifts.isEmpty()) {
            return;
        }
        Context appContext = context.getApplicationContext();
        LabelResolver labels = packageName -> resolveLabel(appContext, packageName);
        NotificationUtils.displayHighPriorityNotification(appContext, NOTIFICATION_TAG,
                builder -> build(appContext, builder, report, labels));
    }

    @VisibleForTesting
    @NonNull
    static Notification build(@NonNull Context context, @NonNull NotificationCompat.Builder builder,
                              @NonNull PermissionWatcher.Report report, @NonNull LabelResolver labels) {
        String title = title(context, report);
        String body = body(context, report, labels);
        PendingIntent open = PendingIntentCompat.getActivity(context, 0,
                new Intent(context, WatchedPermissionsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT, false);
        return builder.setSmallIcon(R.drawable.ic_security)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .build();
    }

    @VisibleForTesting
    @NonNull
    static String title(@NonNull Context context, @NonNull PermissionWatcher.Report report) {
        int total = report.drifts.size();
        if (report.count(PermissionWatcher.OUTCOME_ENFORCED) == total) {
            return context.getResources().getQuantityString(R.plurals.permission_watcher_notification_enforced,
                    total, total);
        }
        return context.getResources().getQuantityString(R.plurals.permission_watcher_notification_title, total,
                total);
    }

    @VisibleForTesting
    @NonNull
    static String body(@NonNull Context context, @NonNull PermissionWatcher.Report report,
                       @NonNull LabelResolver labels) {
        StringBuilder body = new StringBuilder();
        int shown = 0;
        for (PermissionWatcher.Drift drift : report.drifts) {
            if (shown == MAX_LINES) {
                int rest = report.drifts.size() - shown;
                body.append('\n').append(context.getResources().getQuantityString(
                        R.plurals.permission_watcher_more, rest, rest));
                break;
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(line(context, drift, labels.label(drift.rule.packageName)));
            ++shown;
        }
        if (report.count(PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE) > 0) {
            body.append("\n\n").append(context.getString(R.string.permission_watcher_reason_no_privilege));
        }
        if (report.count(PermissionWatcher.OUTCOME_REPORTED) > 0) {
            body.append("\n\n").append(context.getString(R.string.permission_watcher_reason_enforce_off));
        }
        return body.toString();
    }

    @NonNull
    private static String line(@NonNull Context context, @NonNull PermissionWatcher.Drift drift,
                               @NonNull String label) {
        String name = drift.rule.shortName();
        // The rule asks for the opposite of what the app had.
        boolean grant = !drift.wasAllowed;
        switch (drift.outcome) {
            case PermissionWatcher.OUTCOME_ENFORCED:
                return context.getString(grant ? R.string.permission_watcher_line_allowed_again
                        : R.string.permission_watcher_line_denied_again, label, name);
            case PermissionWatcher.OUTCOME_FAILED:
                String reason = drift.failure != null ? drift.failure
                        : context.getString(R.string.permission_watcher_change_refused);
                return context.getString(grant ? R.string.permission_watcher_line_allow_failed
                        : R.string.permission_watcher_line_deny_failed, label, name, reason);
            case PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE:
            case PermissionWatcher.OUTCOME_REPORTED:
            default:
                return context.getString(grant ? R.string.permission_watcher_line_should_be_allowed
                        : R.string.permission_watcher_line_should_be_denied, label, name);
        }
    }

    @NonNull
    static String resolveLabel(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = info.loadLabel(pm);
            return label.length() > 0 ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
