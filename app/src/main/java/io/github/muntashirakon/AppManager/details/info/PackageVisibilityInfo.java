// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * NF-11 — package-visibility inspector. Surfaces three signals on the App
 * Details info card:
 *
 * <ul>
 *     <li>{@link #holdsQueryAllPackages} — whether the inspected package
 *         declared {@code android.permission.QUERY_ALL_PACKAGES}. Privacy/
 *         attack-surface signal: holders can enumerate every installed app.</li>
 *     <li>{@link #queriesPackages} — packages this app explicitly lists in its
 *         {@code <queries>} manifest block (API 30+).</li>
 *     <li>{@link #queriesIntentActions} — intent action strings the app lists
 *         in {@code <queries>}. Useful for understanding what the app expects
 *         to find but cannot fingerprint via direct package lookup.</li>
 * </ul>
 *
 * <p>The inverse query — "which other apps query this package?" — is O(N) on
 * installed packages and is materialised lazily by
 * {@link #findAppsQueryingPackage(PackageManager, String)} only when the user
 * opens the visibility dialog.</p>
 *
 * <p>On API &lt; 30 the {@code <queries>} block does not exist and we cannot
 * surface {@code queriesPackages} / {@code queriesIntentActions}; the helper
 * returns an empty list rather than throwing.</p>
 */
public final class PackageVisibilityInfo {
    public static final String QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES";

    public final boolean holdsQueryAllPackages;
    @NonNull public final List<String> queriesPackages;
    @NonNull public final List<String> queriesIntentActions;

    @VisibleForTesting
    PackageVisibilityInfo(boolean holdsQueryAllPackages,
                          @NonNull List<String> queriesPackages,
                          @NonNull List<String> queriesIntentActions) {
        this.holdsQueryAllPackages = holdsQueryAllPackages;
        this.queriesPackages = Collections.unmodifiableList(new ArrayList<>(queriesPackages));
        this.queriesIntentActions = Collections.unmodifiableList(new ArrayList<>(queriesIntentActions));
    }

    /** True when this package has any non-trivial visibility signal worth surfacing. */
    public boolean hasSignal() {
        return holdsQueryAllPackages
                || !queriesPackages.isEmpty()
                || !queriesIntentActions.isEmpty();
    }

    @AnyThread
    @NonNull
    public static PackageVisibilityInfo from(@NonNull PackageInfo info) {
        boolean queryAll = hasQueryAllPackages(info.requestedPermissions);
        List<String> pkgs = readQueriesPackages(info);
        List<String> actions = readQueriesActions(info);
        return new PackageVisibilityInfo(queryAll, pkgs, actions);
    }

    @VisibleForTesting
    static boolean hasQueryAllPackages(@Nullable String[] requestedPermissions) {
        if (requestedPermissions == null) return false;
        for (String permission : requestedPermissions) {
            if (QUERY_ALL_PACKAGES.equals(permission)) return true;
        }
        return false;
    }

    @NonNull
    private static List<String> readQueriesPackages(@NonNull PackageInfo info) {
        if (Build.VERSION.SDK_INT < 30) return Collections.emptyList();
        try {
            // Reflective access avoids a hard reference to a constant that only
            // exists from API 30 onwards; the field is public + non-hidden.
            java.lang.reflect.Field field = PackageInfo.class.getField("queriesPackages");
            Object value = field.get(info);
            if (value instanceof String[]) {
                return toSortedList((String[]) value);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Collections.emptyList();
    }

    @NonNull
    private static List<String> readQueriesActions(@NonNull PackageInfo info) {
        if (Build.VERSION.SDK_INT < 30) return Collections.emptyList();
        try {
            java.lang.reflect.Field field = PackageInfo.class.getField("queriesIntents");
            Object value = field.get(info);
            if (value instanceof Object[]) {
                Object[] array = (Object[]) value;
                java.util.Set<String> actions = new TreeSet<>();
                for (Object intent : array) {
                    if (intent == null) continue;
                    try {
                        // android.content.Intent#getAction
                        java.lang.reflect.Method getAction = intent.getClass().getMethod("getAction");
                        Object action = getAction.invoke(intent);
                        if (action instanceof String && !((String) action).isEmpty()) {
                            actions.add((String) action);
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
                return new ArrayList<>(actions);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Collections.emptyList();
    }

    /**
     * Find installed apps whose manifest {@code <queries>} block explicitly
     * lists {@code targetPackageName}. O(installed packages); call from a
     * background thread.
     */
    @WorkerThread
    @NonNull
    public static List<String> findAppsQueryingPackage(@NonNull PackageManager pm,
                                                        @NonNull String targetPackageName) {
        if (Build.VERSION.SDK_INT < 30) return Collections.emptyList();
        TreeSet<String> out = new TreeSet<>();
        List<PackageInfo> installed;
        try {
            installed = pm.getInstalledPackages(getQueriesFlag());
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (installed == null) return Collections.emptyList();
        for (PackageInfo other : installed) {
            if (other == null || other.packageName == null) continue;
            if (targetPackageName.equals(other.packageName)) continue;
            List<String> queries = readQueriesPackages(other);
            if (queries.contains(targetPackageName)) {
                out.add(other.packageName);
            }
        }
        return new ArrayList<>(out);
    }

    @VisibleForTesting
    static int getQueriesFlag() {
        // PackageManager.GET_QUERIES = 0x00040000 since API 30, but we use a
        // safe fallback (zero) below API 30 to avoid manifest-parse failures
        // on devices that ignore the flag.
        if (Build.VERSION.SDK_INT >= 30) {
            return 0x00040000;
        }
        return 0;
    }

    @NonNull
    private static List<String> toSortedList(@NonNull String[] array) {
        TreeSet<String> set = new TreeSet<>();
        for (String value : array) {
            if (value != null && !value.isEmpty()) set.add(value);
        }
        return new ArrayList<>(set);
    }
}
