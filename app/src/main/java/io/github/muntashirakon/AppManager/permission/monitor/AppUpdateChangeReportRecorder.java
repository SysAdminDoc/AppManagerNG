// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;

import java.util.Collections;
import java.util.Set;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;
import io.github.muntashirakon.AppManager.logs.Log;

/** Persists one combined permission/component report for an app replacement. */
public final class AppUpdateChangeReportRecorder {
    public static final String TAG = "AppUpdateChangeReport";

    private AppUpdateChangeReportRecorder() {
    }

    @WorkerThread
    public static void record(@Nullable PermissionChangeDiff.Result permissionDiff,
                              @Nullable ComponentChangeDiff.Result componentDiff) {
        AppUpdateChangeReport report = build(permissionDiff, componentDiff);
        if (report == null) return;
        try {
            AppsDb.getInstance().appUpdateChangeReportDao().insert(report);
        } catch (Exception e) {
            // A report must never make PACKAGE_REPLACED handling fail. The existing monitor feed
            // remains available even when a stale or corrupt apps.db needs to be rebuilt.
            Log.w(TAG, "Could not persist app update report for " + report.packageName, e);
        }
    }

    @VisibleForTesting
    @Nullable
    static AppUpdateChangeReport build(@Nullable PermissionChangeDiff.Result permissionDiff,
                                       @Nullable ComponentChangeDiff.Result componentDiff) {
        if (permissionDiff == null && componentDiff == null) return null;
        AppUpdateChangeReport report = new AppUpdateChangeReport();
        report.packageName = permissionDiff != null
                ? permissionDiff.packageName : componentDiff.packageName;
        report.timestampMillis = System.currentTimeMillis();
        report.beforeVersionCode = permissionDiff != null
                ? permissionDiff.beforeVersionCode : componentDiff.beforeVersionCode;
        report.afterVersionCode = permissionDiff != null
                ? permissionDiff.afterVersionCode : componentDiff.afterVersionCode;
        report.addedPermissions = toJson(permissionDiff != null
                ? permissionDiff.added : Collections.emptySet());
        report.removedPermissions = toJson(permissionDiff != null
                ? permissionDiff.removed : Collections.emptySet());
        report.addedTrackers = toJson(componentDiff != null
                ? componentDiff.addedTrackers : Collections.emptySet());
        report.removedTrackers = toJson(componentDiff != null
                ? componentDiff.removedTrackers : Collections.emptySet());
        report.addedComponents = toJson(componentDiff != null
                ? componentDiff.addedComponents : Collections.emptySet());
        report.removedComponents = toJson(componentDiff != null
                ? componentDiff.removedComponents : Collections.emptySet());
        if (!AppUpdateChangeReportFormatter.hasChanges(report)) return null;
        return report;
    }

    @NonNull
    private static String toJson(@NonNull Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            if (value != null && !value.isEmpty()) array.put(value);
        }
        return array.toString();
    }

}
