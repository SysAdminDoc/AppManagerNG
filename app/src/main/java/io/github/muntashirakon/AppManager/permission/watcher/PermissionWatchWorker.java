// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

/** Runs a check over every rule, or over one app's rules when the input names a package. */
public class PermissionWatchWorker extends Worker {
    private static final String TAG = "PermissionWatchWorker";

    public static final String INPUT_PACKAGE = "package";
    public static final String INPUT_USER = "user";
    public static final String OUTPUT_CHECKED = "checked";
    public static final String OUTPUT_DRIFTED = "drifted";
    public static final String OUTPUT_ENFORCED = "enforced";

    public PermissionWatchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
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

    /** Checks on the caller's thread and posts the alert for whatever drifted. */
    @WorkerThread
    @NonNull
    static PermissionWatcher.Report checkAndNotify(@NonNull Context context, @Nullable String packageName,
                                                   int userId) {
        PermissionWatcher.Report report = runCheck(PermissionWatchDb.getInstance(context).ruleDao(),
                new SystemWatchBackend(context), Prefs.Privacy.isPermissionWatcherEnforceEnabled(), packageName,
                userId);
        PermissionWatchNotifier.notify(context, report);
        return report;
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
