// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedEntry;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedStore;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ScheduledWorker;

/** Runs a check over every rule, or over one app's rules when the input names a package. */
public class PermissionWatchWorker extends ScheduledWorker {
    private static final String TAG = "PermissionWatchWorker";

    public static final String INPUT_PACKAGE = "package";
    public static final String INPUT_USER = "user";
    public static final String OUTPUT_CHECKED = "checked";
    public static final String OUTPUT_DRIFTED = "drifted";
    public static final String OUTPUT_ENFORCED = "enforced";
    /** The kind of app change feed entry a drift is kept as. */
    public static final String FEED_KIND = "permission_watch";

    public PermissionWatchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    protected Result doScheduledWork() {
        Context context = getApplicationContext();
        if (!PermissionWatchDb.mightHaveRules(context)) {
            return Result.success();
        }
        // Brings root or ADB mode up if the user runs in one; without it, mismatches are only reported.
        int status = Ops.init(context, false);
        if (status != Ops.STATUS_SUCCESS) {
            Log.w(TAG, "Privileged mode isn't ready (%d), so mismatches will only be reported.", status);
        } else {
            // A check follows every rule change, so this keeps the live watch on the current rules
            PermissionWatchRelayClient.push(context);
        }
        String packageName = getInputData().getString(INPUT_PACKAGE);
        int userId = getInputData().getInt(INPUT_USER, 0);
        try {
            PermissionWatcher.Report report = checkAndNotify(context, packageName, userId);
            return Result.success(new Data.Builder()
                    .putInt(OUTPUT_CHECKED, report.checked)
                    .putInt(OUTPUT_DRIFTED, report.drifts.size())
                    .putInt(OUTPUT_ENFORCED, report.count(PermissionWatcher.OUTCOME_ENFORCED))
                    .build());
        } catch (Exception e) {
            Log.e(TAG, "Permission watcher check failed.", e);
            return Result.failure();
        }
    }

    /** Checks on the caller's thread, puts what drifted in the app change feed and posts the alert for it. */
    @WorkerThread
    @NonNull
    static PermissionWatcher.Report checkAndNotify(@NonNull Context context, @Nullable String packageName,
                                                   int userId) {
        Context appContext = context.getApplicationContext();
        PermissionWatcher.Report report = checkAndRecord(appContext, PermissionWatchDb.getInstance(context).ruleDao(),
                new SystemWatchBackend(context), Prefs.Privacy.isPermissionWatcherEnforceEnabled(), packageName,
                userId, new AppChangeFeedStore(appContext),
                name -> PermissionWatchNotifier.resolveLabel(appContext, name));
        PermissionWatchNotifier.notify(context, report);
        return report;
    }

    /**
     * Runs the check and keeps each drift in the app change feed, which outlasts the alert. A drift
     * that's left as it is would be found again on every check, so it goes in once, and again only
     * when what was found or done changes. Each time one is changed back is its own entry.
     */
    @VisibleForTesting
    @WorkerThread
    @NonNull
    static PermissionWatcher.Report checkAndRecord(@NonNull Context context, @NonNull WatchRuleDao dao,
                                                   @NonNull PermissionWatcher.Backend backend, boolean enforce,
                                                   @Nullable String packageName, int userId,
                                                   @NonNull AppChangeFeedStore feed,
                                                   @NonNull PermissionWatchNotifier.LabelResolver labels) {
        PermissionWatcher.Report report = runCheck(dao, backend, enforce, packageName, userId);
        if (report.drifts.isEmpty()) {
            return report;
        }
        List<AppChangeFeedEntry> recorded = new ArrayList<>(feed.readAll());
        for (PermissionWatcher.Drift drift : report.drifts) {
            WatchRule rule = drift.rule;
            String label = labels.label(rule.packageName);
            String title = rule.userId == 0
                    ? context.getString(R.string.permission_watcher_feed_title, label, rule.shortName())
                    : context.getString(R.string.permission_watcher_feed_title_user, label, rule.shortName(),
                    rule.userId);
            String body = feedBody(context, drift);
            if (drift.outcome != PermissionWatcher.OUTCOME_ENFORCED
                    && body.equals(latestBody(recorded, rule.packageName, title))) {
                continue;
            }
            AppChangeFeedEntry entry = AppChangeFeedEntry.now(FEED_KIND, rule.packageName, title, body);
            if (feed.append(entry)) {
                recorded.add(0, entry);
            }
        }
        return report;
    }

    @NonNull
    private static String feedBody(@NonNull Context context, @NonNull PermissionWatcher.Drift drift) {
        String found = context.getString(drift.wasAllowed ? R.string.permission_watcher_feed_found_allowed
                : R.string.permission_watcher_feed_found_denied);
        String done;
        switch (drift.outcome) {
            case PermissionWatcher.OUTCOME_ENFORCED:
                done = context.getString(R.string.permission_watcher_feed_changed_back);
                break;
            case PermissionWatcher.OUTCOME_FAILED:
                done = context.getString(R.string.permission_watcher_feed_change_failed, drift.failure != null
                        ? drift.failure : context.getString(R.string.permission_watcher_change_refused));
                break;
            case PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE:
                done = context.getString(R.string.permission_watcher_reason_no_privilege);
                break;
            case PermissionWatcher.OUTCOME_REPORTED:
            default:
                done = context.getString(R.string.permission_watcher_reason_enforce_off);
                break;
        }
        return found + " " + done;
    }

    /** The body of the newest watch entry with this title, newest first as the feed keeps them. */
    @Nullable
    private static String latestBody(@NonNull List<AppChangeFeedEntry> entries, @NonNull String packageName,
                                     @NonNull String title) {
        for (AppChangeFeedEntry entry : entries) {
            if (FEED_KIND.equals(entry.kind) && packageName.equals(entry.packageName) && title.equals(entry.title)) {
                return entry.body;
            }
        }
        return null;
    }

    /**
     * @param packageName {@code null} to walk every rule, as the scheduled check does
     */
    @WorkerThread
    @NonNull
    public static PermissionWatcher.Report runCheck(@NonNull WatchRuleDao dao,
                                                    @NonNull PermissionWatcher.Backend backend, boolean enforce,
                                                    @Nullable String packageName, int userId) {
        List<WatchRule> rules = packageName != null ? dao.getForPackage(packageName, userId) : dao.getAll();
        return PermissionWatcher.check(rules, backend, enforce);
    }
}
