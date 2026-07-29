// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private InstallerManifestSummary() {
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
