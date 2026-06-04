// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Target manifest Health Connect permission posture.
 *
 * <p>This deliberately reports declared {@code android.permission.health.*}
 * permissions only. Reading a target app's granted Health Connect permissions
 * requires platform/Health Connect APIs that are not target-scoped for ordinary
 * callers.</p>
 */
public final class HealthConnectInfo {
    public static final int MIN_SDK_INT = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    @VisibleForTesting
    static final String HEALTH_PERMISSION_PREFIX = "android.permission.health.";

    public final int sdkInt;
    @NonNull
    public final List<HealthPermission> requestedPermissions;
    public final int readPermissionCount;
    public final int writePermissionCount;

    private HealthConnectInfo(int sdkInt, @NonNull List<HealthPermission> requestedPermissions) {
        this.sdkInt = sdkInt;
        this.requestedPermissions = Collections.unmodifiableList(requestedPermissions);
        int readCount = 0;
        int writeCount = 0;
        for (HealthPermission permission : requestedPermissions) {
            if (permission.isReadPermission()) {
                ++readCount;
            } else if (permission.isWritePermission()) {
                ++writeCount;
            }
        }
        readPermissionCount = readCount;
        writePermissionCount = writeCount;
    }

    @NonNull
    public static HealthConnectInfo from(@NonNull PackageInfo packageInfo) {
        return fromRaw(Build.VERSION.SDK_INT, packageInfo.requestedPermissions);
    }

    @NonNull
    static HealthConnectInfo unsupported(int sdkInt) {
        return new HealthConnectInfo(sdkInt, Collections.emptyList());
    }

    public boolean isSupported() {
        return sdkInt >= MIN_SDK_INT;
    }

    public boolean hasRequestedHealthPermissions() {
        return !requestedPermissions.isEmpty();
    }

    @NonNull
    @VisibleForTesting
    static HealthConnectInfo fromRaw(int sdkInt, @Nullable String[] requestedPermissions) {
        if (sdkInt < MIN_SDK_INT || requestedPermissions == null || requestedPermissions.length == 0) {
            return new HealthConnectInfo(sdkInt, Collections.emptyList());
        }
        List<String> permissionNames = new ArrayList<>();
        for (String permission : requestedPermissions) {
            if (permission == null || !permission.startsWith(HEALTH_PERMISSION_PREFIX)
                    || permissionNames.contains(permission)) {
                continue;
            }
            permissionNames.add(permission);
        }
        Collections.sort(permissionNames);
        List<HealthPermission> healthPermissions = new ArrayList<>(permissionNames.size());
        for (String permissionName : permissionNames) {
            healthPermissions.add(new HealthPermission(permissionName));
        }
        return new HealthConnectInfo(sdkInt, healthPermissions);
    }

    public static final class HealthPermission {
        @NonNull
        public final String name;
        @NonNull
        public final String shortName;

        private HealthPermission(@NonNull String name) {
            this.name = name;
            shortName = name.substring(HEALTH_PERMISSION_PREFIX.length());
        }

        public boolean isReadPermission() {
            return shortName.startsWith("READ_");
        }

        public boolean isWritePermission() {
            return shortName.startsWith("WRITE_");
        }

        @NonNull
        public String toDisplayString() {
            return shortName.toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }
}
