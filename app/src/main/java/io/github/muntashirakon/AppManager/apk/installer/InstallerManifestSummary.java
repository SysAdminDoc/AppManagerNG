// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

/**
 * What the confirmation prompt can tell a user about an APK before they commit to installing it,
 * read straight from the parsed manifest.
 *
 * <p>Split out as pure functions over already-parsed data so the grouping and SDK rules are
 * testable without a device: the prompt is the last point at which an install can be declined, so
 * getting "which of these are dangerous" wrong there is worse than not showing it at all.
 */
public final class InstallerManifestSummary {
    /** A declared SDK level that the manifest did not carry. */
    public static final int SDK_UNKNOWN = 0;

    public static final class Permissions {
        /**
         * Permissions the platform classifies as dangerous — the runtime prompts a user would
         * otherwise only meet after the app is installed.
         */
        @NonNull
        public final List<String> dangerous;
        /** Everything else the manifest requests, including custom and unrecognised names. */
        @NonNull
        public final List<String> other;

        Permissions(@NonNull List<String> dangerous, @NonNull List<String> other) {
            this.dangerous = Collections.unmodifiableList(dangerous);
            this.other = Collections.unmodifiableList(other);
        }

        public int getTotal() {
            return dangerous.size() + other.size();
        }

        public boolean isEmpty() {
            return dangerous.isEmpty() && other.isEmpty();
        }
    }

    /** The manifest entries an update can add or remove, in the order the prompt lists them. */
    public enum Kind {
        PERMISSIONS,
        ACTIVITIES,
        SERVICES,
        RECEIVERS,
        PROVIDERS,
    }

    /** What one {@link Kind} looks like in the APK compared with the installed copy. */
    public static final class KindChange {
        @NonNull
        public final Kind kind;
        /** How many the APK declares. */
        public final int total;
        /** In the APK but not in the installed copy, sorted. Empty for a new install. */
        @NonNull
        public final List<String> added;
        /** In the installed copy but not in the APK, sorted. Empty for a new install. */
        @NonNull
        public final List<String> removed;

        KindChange(@NonNull Kind kind, int total, @NonNull List<String> added, @NonNull List<String> removed) {
            this.kind = kind;
            this.total = total;
            this.added = Collections.unmodifiableList(added);
            this.removed = Collections.unmodifiableList(removed);
        }

        public boolean hasChanges() {
            return !added.isEmpty() || !removed.isEmpty();
        }
    }

    /** An APK set against the copy already installed, or against nothing for a new install. */
    public static final class Changes {
        public final boolean newInstall;
        @Nullable
        public final String oldVersionName;
        public final long oldVersionCode;
        @Nullable
        public final String newVersionName;
        public final long newVersionCode;
        /** One entry per {@link Kind}, in declaration order. */
        @NonNull
        public final List<KindChange> kinds;

        Changes(@Nullable PackageInfo installed, @NonNull PackageInfo apk, @NonNull List<KindChange> kinds) {
            this.newInstall = installed == null;
            this.oldVersionName = installed != null ? installed.versionName : null;
            this.oldVersionCode = installed != null ? PackageInfoCompat.getLongVersionCode(installed) : 0;
            this.newVersionName = apk.versionName;
            this.newVersionCode = PackageInfoCompat.getLongVersionCode(apk);
            this.kinds = Collections.unmodifiableList(kinds);
        }

        @NonNull
        public KindChange get(@NonNull Kind kind) {
            return kinds.get(kind.ordinal());
        }
    }

    /** Everything {@link #compare} reads, disabled components included since they can be enabled later. */
    public static final int CHANGE_FLAGS = PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES
            | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
            | PackageManagerCompat.MATCH_DISABLED_COMPONENTS;

    private InstallerManifestSummary() {
    }

    /**
     * Sets the APK against the installed copy, kind by kind.
     *
     * @param installed The installed copy read with {@link #CHANGE_FLAGS}, or {@code null} when the
     *                  package isn't installed. A new install has nothing to add to or remove from,
     *                  so it only gets totals.
     * @param apk       The base APK read with {@link #CHANGE_FLAGS}.
     */
    @NonNull
    public static Changes compare(@Nullable PackageInfo installed, @NonNull PackageInfo apk) {
        List<KindChange> kinds = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            Set<String> now = namesOf(apk, kind);
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            if (installed != null) {
                Set<String> before = namesOf(installed, kind);
                for (String name : now) {
                    if (!before.contains(name)) {
                        added.add(name);
                    }
                }
                for (String name : before) {
                    if (!now.contains(name)) {
                        removed.add(name);
                    }
                }
            }
            kinds.add(new KindChange(kind, now.size(), added, removed));
        }
        return new Changes(installed, apk, kinds);
    }

    /**
     * Reads the base APK and the installed copy and compares them.
     *
     * @return {@code null} when the APK can't be parsed.
     */
    @WorkerThread
    @Nullable
    public static Changes load(@NonNull PackageManager packageManager, @NonNull File baseApk) {
        PackageInfo apk = packageManager.getPackageArchiveInfo(baseApk.getAbsolutePath(), CHANGE_FLAGS);
        if (apk == null || apk.packageName == null) {
            return null;
        }
        PackageInfo installed;
        try {
            installed = packageManager.getPackageInfo(apk.packageName, CHANGE_FLAGS);
        } catch (PackageManager.NameNotFoundException e) {
            installed = null;
        }
        return compare(installed, apk);
    }

    /**
     * A component name as the prompt shows it: relative to its package when it lives there, the way
     * a manifest writes it, so a list of them reads by class name.
     */
    @NonNull
    public static String displayName(@Nullable String packageName, @NonNull String name) {
        if (packageName != null && name.length() > packageName.length() + 1
                && name.startsWith(packageName) && name.charAt(packageName.length()) == '.') {
            return name.substring(packageName.length());
        }
        return name;
    }

    @NonNull
    private static Set<String> namesOf(@NonNull PackageInfo info, @NonNull Kind kind) {
        Set<String> names = new TreeSet<>();
        switch (kind) {
            case PERMISSIONS:
                if (info.requestedPermissions != null) {
                    for (String permission : info.requestedPermissions) {
                        if (permission != null && !permission.trim().isEmpty()) {
                            names.add(permission.trim());
                        }
                    }
                }
                return names;
            case ACTIVITIES:
                addComponents(names, info.activities);
                return names;
            case SERVICES:
                addComponents(names, info.services);
                return names;
            case RECEIVERS:
                addComponents(names, info.receivers);
                return names;
            case PROVIDERS:
            default:
                addComponents(names, info.providers);
                return names;
        }
    }

    private static void addComponents(@NonNull Set<String> names, @Nullable ComponentInfo[] components) {
        if (components == null) {
            return;
        }
        for (ComponentInfo component : components) {
            if (component != null && component.name != null) {
                names.add(component.name);
            }
        }
    }

    /**
     * Groups the requested permissions, flagging the dangerous ones.
     *
     * <p>An unresolvable permission is never assumed harmless: it is a name this device's platform
     * does not define, so it is reported alongside the rest rather than dropped.
     *
     * @param packageManager Used to resolve protection levels; may be {@code null}, in which case
     *                       nothing can be classified and everything is reported as other.
     */
    @NonNull
    public static Permissions summarizePermissions(@Nullable PackageManager packageManager,
                                                   @Nullable PackageInfo packageInfo) {
        List<String> dangerous = new ArrayList<>();
        List<String> other = new ArrayList<>();
        if (packageInfo == null || packageInfo.requestedPermissions == null) {
            return new Permissions(dangerous, other);
        }
        // A manifest may legitimately repeat a permission; the prompt should not.
        Set<String> seen = new LinkedHashSet<>();
        for (String permission : packageInfo.requestedPermissions) {
            if (permission == null || permission.trim().isEmpty()) {
                continue;
            }
            seen.add(permission.trim());
        }
        for (String permission : seen) {
            if (isDangerous(packageManager, permission)) {
                dangerous.add(permission);
            } else {
                other.add(permission);
            }
        }
        return new Permissions(dangerous, other);
    }

    @VisibleForTesting
    static boolean isDangerous(@Nullable PackageManager packageManager, @NonNull String permission) {
        if (packageManager == null) {
            return false;
        }
        try {
            PermissionInfo info = packageManager.getPermissionInfo(permission, 0);
            int protection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getProtection()
                    : (info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
            return protection == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            // Custom or platform-unknown permission: it cannot be classified, so it is not
            // claimed to be safe either.
            return false;
        }
    }

    /**
     * The APK's declared {@code minSdkVersion}, or {@link #SDK_UNKNOWN} when the field is not
     * populated (it only exists from API 24, and only when the manifest declared it).
     */
    public static int getMinSdk(@Nullable PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.applicationInfo == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return SDK_UNKNOWN;
        }
        int minSdk = packageInfo.applicationInfo.minSdkVersion;
        return minSdk > 0 ? minSdk : SDK_UNKNOWN;
    }

    /** The APK's declared {@code targetSdkVersion}, or {@link #SDK_UNKNOWN}. */
    public static int getTargetSdk(@Nullable PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return SDK_UNKNOWN;
        }
        int targetSdk = packageInfo.applicationInfo.targetSdkVersion;
        return targetSdk > 0 ? targetSdk : SDK_UNKNOWN;
    }
}
