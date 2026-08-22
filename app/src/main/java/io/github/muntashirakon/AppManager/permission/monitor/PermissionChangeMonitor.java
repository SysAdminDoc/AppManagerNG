// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.content.pm.PermissionInfoCompat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Engine for the Permission Change Monitor (T9). Public surface used both by
 * the {@link PermissionChangeReceiver} (live `PACKAGE_REPLACED` broadcasts)
 * and by unit tests / one-shot rescans.
 *
 * <p>Behaviour on each replace:
 * <ol>
 *   <li>Read the cached snapshot for the package (if any) from
 *       {@link PermissionSnapshotStore}.</li>
 *   <li>Compute the current dangerous-permission set by walking
 *       {@code PackageInfo.requestedPermissions} and filtering on
 *       {@code PROTECTION_DANGEROUS}.</li>
 *   <li>Diff old vs new via {@link PermissionChangeDiff}.</li>
 *   <li>Post a notification iff the diff is "interesting" (at least one
 *       added dangerous permission) AND the version actually moved forward.</li>
 *   <li>Persist the new snapshot.</li>
 * </ol>
 *
 * <p>No-op if the package is the AppManagerNG process itself — self-update
 * notifications are confusing.
 */
public final class PermissionChangeMonitor {
    public static final String TAG = "PermissionChangeMonitor";

    /** Monotonic source of unique PendingIntent request codes (collision-free across packages). */
    private static final AtomicInteger sRequestCode = new AtomicInteger();

    private PermissionChangeMonitor() {
    }

    /**
     * Inspect a package that was just replaced. If a previous snapshot exists
     * and the new version declares additional dangerous permissions, post a
     * notification. Always updates the snapshot at the end so subsequent
     * updates compare against the freshest known state.
     *
     * @return the diff result (may be empty), or {@code null} if the package
     *         no longer exists or is self.
     */
    @WorkerThread
    @Nullable
    public static PermissionChangeDiff.Result onPackageReplaced(@NonNull Context appContext,
                                                                @NonNull String packageName) {
        return onPackageReplaced(appContext, packageName, true);
    }

    /**
     * Variant used by the combined update report. It refreshes the same snapshot and computes the
     * same diff, but can suppress the high-priority notification when the user only opted into
     * local reports.
     */
    @WorkerThread
    @Nullable
    static PermissionChangeDiff.Result onPackageReplaced(@NonNull Context appContext,
                                                         @NonNull String packageName,
                                                         boolean notify) {
        if (appContext.getPackageName().equals(packageName)) {
            // Self-update — don't alarm the user about NG's own perms.
            return null;
        }
        PermissionSnapshotStore store = new PermissionSnapshotStore(appContext);
        PermissionSnapshot before = store.get(packageName);
        PermissionSnapshot after;
        try {
            after = computeCurrentSnapshot(appContext, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // Package is gone (uninstalled). Drop cached entry.
            store.remove(packageName);
            return null;
        }
        if (before == null) {
            // First time we've seen this package — just cache.
            store.put(packageName, after);
            return null;
        }
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(packageName, before, after,
                collectForeignDeclarations(store, packageName), collectKnownPermissionNames(appContext));
        store.put(packageName, after);
        reportCustomPermissionSignals(appContext, packageName, diff);
        // Notify on any newly-added dangerous permission regardless of the
        // version-code delta. A re-install/sideload that gains dangerous perms
        // while keeping or *lowering* its versionCode is the more suspicious
        // case, not a less suspicious one; gating on versionCode silently
        // dropped exactly that escalation while still caching the escalated
        // set (so it never re-surfaced), defeating the monitor.
        if (diff.isInteresting()) {
            String label = resolveLabel(appContext, packageName);
            String title = appContext.getString(R.string.permission_change_monitor_title, label);
            String body = appContext.getResources().getQuantityString(R.plurals.permission_change_monitor_body,
                    diff.added.size(), diff.added.size(), shortJoin(diff.added));
            new AppChangeFeedStore(appContext).append(AppChangeFeedEntry.now("permissions", packageName, title, body));
            if (notify) {
                try {
                    postNotification(appContext, packageName, title, body);
                } catch (Exception t) {
                    Log.w(TAG, "Could not post permission-change notification for " + packageName, t);
                }
            }
        }
        return diff;
    }

    /**
     * Records the custom-permission signals that are not covered by the dangerous-permission
     * notification: an unrelated signer claiming a name we own, a declaration whose protection
     * level dropped, a newly requested non-dangerous permission, and a request nobody declares.
     * These land in the change feed rather than as a high-priority notification — they are audit
     * evidence, not an interrupt.
     */
    @WorkerThread
    private static void reportCustomPermissionSignals(@NonNull Context appContext,
                                                      @NonNull String packageName,
                                                      @NonNull PermissionChangeDiff.Result diff) {
        StringBuilder body = new StringBuilder();
        appendSignal(body, appContext, R.string.app_change_auditor_permission_contested,
                diff.contestedOwnership);
        appendSignal(body, appContext, R.string.app_change_auditor_permission_weakened,
                diff.weakenedDeclarations);
        appendSignal(body, appContext, R.string.app_change_auditor_permission_new_request,
                diff.newlyRequested);
        appendSignal(body, appContext, R.string.app_change_auditor_permission_declared,
                diff.newlyDeclared);
        appendSignal(body, appContext, R.string.app_change_auditor_permission_orphaned,
                diff.orphanedRequests);
        if (body.length() == 0) {
            return;
        }
        String title = appContext.getString(R.string.app_change_auditor_permission_title,
                resolveLabel(appContext, packageName));
        new AppChangeFeedStore(appContext).append(
                AppChangeFeedEntry.now("custom_permissions", packageName, title, body.toString()));
    }

    private static void appendSignal(@NonNull StringBuilder body, @NonNull Context appContext,
                                     int labelRes, @NonNull Set<String> values) {
        if (values.isEmpty()) {
            return;
        }
        if (body.length() > 0) body.append('\n');
        body.append(appContext.getString(labelRes, shortJoin(values)));
    }

    /**
     * One-shot rescan: walk every installed package and refresh its cached
     * snapshot without notifying. Useful as a first-run primer so subsequent
     * `PACKAGE_REPLACED` events have something to diff against.
     */
    @WorkerThread
    public static int primeSnapshotsForAllPackages(@NonNull Context appContext) {
        PermissionSnapshotStore store = new PermissionSnapshotStore(appContext);
        int seen = 0;
        try {
            PackageManager pm = appContext.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                if (pi == null || pi.packageName == null) continue;
                if (appContext.getPackageName().equals(pi.packageName)) continue;
                store.put(pi.packageName, computeSnapshotFromPackageInfo(appContext, pi));
                ++seen;
            }
        } catch (Exception t) {
            Log.w(TAG, "primeSnapshotsForAllPackages failed", t);
        }
        return seen;
    }

    @WorkerThread
    @NonNull
    private static PermissionSnapshot computeCurrentSnapshot(@NonNull Context appContext,
                                                             @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = appContext.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        return computeSnapshotFromPackageInfo(appContext, pi);
    }

    @NonNull
    private static PermissionSnapshot computeSnapshotFromPackageInfo(@NonNull Context appContext,
                                                                     @NonNull PackageInfo pi) {
        PackageManager pm = appContext.getPackageManager();
        long versionCode = PackageInfoCompat.getLongVersionCode(pi);
        Set<String> dangerous = new HashSet<>();
        Set<String> requested = new HashSet<>();
        if (pi.requestedPermissions != null) {
            for (String name : pi.requestedPermissions) {
                if (name == null || name.isEmpty()) continue;
                requested.add(name);
                try {
                    PermissionInfo info = pm.getPermissionInfo(name, 0);
                    if (PermissionInfoCompat.getProtection(info) == PermissionInfo.PROTECTION_DANGEROUS) {
                        dangerous.add(name);
                    }
                } catch (PackageManager.NameNotFoundException ignore) {
                    // Custom permission that no app declares (or signature/normal perm where the
                    // declarer is removed). It stays in `requested` so the orphan check can see it.
                }
            }
        }
        Map<String, DeclaredPermission> declared = new TreeMap<>();
        if (pi.permissions != null) {
            String signer = SigningCertChangeMonitor.currentSignerDigest(appContext, pi.packageName);
            for (PermissionInfo info : pi.permissions) {
                if (info == null || info.name == null || info.name.isEmpty()) continue;
                declared.put(info.name, new DeclaredPermission(info.name,
                        PermissionInfoCompat.getProtection(info), signer));
            }
        }
        return new PermissionSnapshot(versionCode, dangerous, requested, declared);
    }

    /**
     * Custom permissions declared by every package other than {@code packageName}, so a name that
     * two unrelated signers both claim can be spotted.
     */
    @WorkerThread
    @NonNull
    private static Map<String, DeclaredPermission> collectForeignDeclarations(
            @NonNull PermissionSnapshotStore store, @NonNull String packageName) {
        Map<String, DeclaredPermission> out = new TreeMap<>();
        for (Map.Entry<String, PermissionSnapshot> entry : store.readAll().entrySet()) {
            if (packageName.equals(entry.getKey())) continue;
            out.putAll(entry.getValue().declaredPermissions);
        }
        return out;
    }

    @WorkerThread
    @NonNull
    private static Set<String> collectKnownPermissionNames(@NonNull Context appContext) {
        Set<String> out = new HashSet<>();
        try {
            for (PackageInfo pi : appContext.getPackageManager()
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                if (pi == null || pi.permissions == null) continue;
                for (PermissionInfo info : pi.permissions) {
                    if (info != null && info.name != null) out.add(info.name);
                }
            }
        } catch (Exception t) {
            Log.w(TAG, "Could not enumerate declared permissions", t);
            // An empty set disables the orphan check rather than reporting every request as orphaned.
            return new HashSet<>();
        }
        return out;
    }

    @WorkerThread
    private static void postNotification(@NonNull Context appContext,
                                         @NonNull String packageName,
                                         @NonNull String title,
                                         @NonNull String body) {
        Intent contentIntent = AppDetailsActivity.getIntent(appContext, packageName, 0, true);
        // A unique request code per notification: AppDetailsActivity.getIntent
        // produces intents that differ only in their extras, and PendingIntent
        // matching ignores extras, so two packages sharing a request code (e.g.
        // a String.hashCode collision) under FLAG_UPDATE_CURRENT would alias —
        // tapping one alert could open the other package's details.
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
    private static String shortJoin(@NonNull Set<String> perms) {
        // Strip "android.permission." prefix in the notification body so the
        // user sees CAMERA rather than android.permission.CAMERA.
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = perms.iterator();
        int i = 0;
        while (it.hasNext() && i < 4) {
            String p = it.next();
            int dot = p.lastIndexOf('.');
            String s = dot >= 0 ? p.substring(dot + 1) : p;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
            ++i;
        }
        int rest = perms.size() - i;
        if (rest > 0) sb.append(" +").append(rest);
        return sb.toString();
    }

    /**
     * Build the {@link Intent} for a Tap-to-open intent that deep-links into
     * App Details for the given package. Public for callers that want to
     * preview the notification without actually displaying it.
     */
    @NonNull
    public static Intent buildAppDetailsIntent(@NonNull Context appContext, @NonNull String packageName) {
        return AppDetailsActivity.getIntent(appContext, packageName, 0, true);
    }

    @NonNull
    public static Uri toPackageUri(@NonNull String packageName) {
        return Uri.parse("package:" + packageName);
    }
}
