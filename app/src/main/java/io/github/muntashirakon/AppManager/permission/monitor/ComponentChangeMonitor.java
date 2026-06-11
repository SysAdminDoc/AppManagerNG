// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.pm.PackageInfoCompat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

/**
 * Engine for the App Change Auditor component/tracker snapshot diff.
 */
public final class ComponentChangeMonitor {
    public static final String TAG = "ComponentChangeMonitor";

    private static final int COMPONENT_FLAGS = PackageManager.GET_ACTIVITIES
            | PackageManager.GET_RECEIVERS
            | PackageManager.GET_PROVIDERS
            | PackageManager.GET_SERVICES
            | MATCH_DISABLED_COMPONENTS
            | MATCH_UNINSTALLED_PACKAGES;
    private static final AtomicInteger sRequestCode = new AtomicInteger(4000);

    private ComponentChangeMonitor() {
    }

    @WorkerThread
    @Nullable
    public static ComponentChangeDiff.Result onPackageReplaced(@NonNull Context appContext,
                                                               @NonNull String packageName) {
        if (appContext.getPackageName().equals(packageName)) return null;
        ComponentSnapshotStore store = new ComponentSnapshotStore(appContext);
        ComponentSnapshot before = store.get(packageName);
        ComponentSnapshot after;
        try {
            after = computeCurrentSnapshot(appContext, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            store.remove(packageName);
            return null;
        }
        if (before == null) {
            store.put(packageName, after);
            return null;
        }
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(packageName, before, after);
        store.put(packageName, after);
        if (diff.isInteresting()) {
            String label = resolveLabel(appContext, packageName);
            String title = appContext.getString(R.string.app_change_auditor_title, label);
            String body = appContext.getString(R.string.app_change_auditor_body,
                    diff.addedComponents.size(), diff.removedComponents.size(),
                    diff.addedTrackers.size(), diff.removedTrackers.size(), summarize(diff));
            new AppChangeFeedStore(appContext).append(AppChangeFeedEntry.now("components", packageName, title, body));
            try {
                postNotification(appContext, packageName, title, body);
            } catch (Throwable t) {
                Log.w(TAG, "Could not post component-change notification for " + packageName, t);
            }
        }
        return diff;
    }

    @WorkerThread
    public static int primeSnapshotsForAllPackages(@NonNull Context appContext) {
        ComponentSnapshotStore store = new ComponentSnapshotStore(appContext);
        int seen = 0;
        try {
            PackageManager pm = appContext.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(COMPONENT_FLAGS)) {
                if (pi == null || pi.packageName == null) continue;
                if (appContext.getPackageName().equals(pi.packageName)) continue;
                store.put(pi.packageName, computeSnapshotFromPackageInfo(pi));
                ++seen;
            }
        } catch (Throwable t) {
            Log.w(TAG, "primeSnapshotsForAllPackages failed", t);
        }
        return seen;
    }

    @WorkerThread
    @NonNull
    private static ComponentSnapshot computeCurrentSnapshot(@NonNull Context appContext,
                                                            @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        PackageInfo pi = appContext.getPackageManager().getPackageInfo(packageName, COMPONENT_FLAGS);
        return computeSnapshotFromPackageInfo(pi);
    }

    @NonNull
    private static ComponentSnapshot computeSnapshotFromPackageInfo(@NonNull PackageInfo pi) {
        long versionCode = PackageInfoCompat.getLongVersionCode(pi);
        Map<String, RuleType> componentMap = PackageUtils.collectComponentClassNames(pi);
        Set<String> components = new HashSet<>(componentMap.keySet());
        Set<String> trackers = new HashSet<>();
        for (String componentName : components) {
            if (ComponentUtils.isTracker(componentName)) {
                trackers.add(componentName);
            }
        }
        return new ComponentSnapshot(versionCode, components, trackers);
    }

    @WorkerThread
    private static void postNotification(@NonNull Context appContext, @NonNull String packageName,
                                         @NonNull String title, @NonNull String body) {
        Intent contentIntent = AppDetailsActivity.getIntent(appContext, packageName, 0, true);
        PendingIntent pi = PendingIntentCompat.getActivity(appContext, sRequestCode.incrementAndGet(),
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(appContext)
                .setSmallIcon(R.drawable.ic_security)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi);
        NotificationUtils.displayHighPriorityNotification(appContext, builder.build());
    }

    @NonNull
    private static String resolveLabel(@NonNull Context appContext, @NonNull String packageName) {
        try {
            ApplicationInfo info = appContext.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = info.loadLabel(appContext.getPackageManager());
            return label != null && label.length() > 0 ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @NonNull
    private static String summarize(@NonNull ComponentChangeDiff.Result diff) {
        if (!diff.addedTrackers.isEmpty()) return "added trackers: " + shortJoin(diff.addedTrackers);
        if (!diff.removedTrackers.isEmpty()) return "removed trackers: " + shortJoin(diff.removedTrackers);
        if (!diff.addedComponents.isEmpty()) return "added components: " + shortJoin(diff.addedComponents);
        if (!diff.removedComponents.isEmpty()) return "removed components: " + shortJoin(diff.removedComponents);
        return "no component changes";
    }

    @NonNull
    private static String shortJoin(@NonNull Set<String> values) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = values.iterator();
        int i = 0;
        while (it.hasNext() && i < 3) {
            String value = it.next();
            String label = ComponentUtils.getTrackerLabel(value);
            String display = label != null ? label : value;
            int dot = display.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < display.length()) display = display.substring(dot + 1);
            if (sb.length() > 0) sb.append(", ");
            sb.append(display);
            ++i;
        }
        int rest = values.size() - i;
        if (rest > 0) sb.append(" +").append(rest);
        return sb.toString();
    }
}
