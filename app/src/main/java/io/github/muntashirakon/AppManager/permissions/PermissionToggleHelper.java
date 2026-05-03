// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.AppOpsManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.permission.DevelopmentPermission;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;
import io.github.muntashirakon.AppManager.permission.RuntimePermission;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/**
 * Shared helper for the Permission Inspector that wraps {@link PermUtils} so we
 * can grant or revoke a single permission for a single (package, user)
 * combination, mirroring what {@code AppDetailsViewModel.togglePermission}
 * does without needing the full app-details state machine.
 */
public final class PermissionToggleHelper {
    private PermissionToggleHelper() {}

    public static final class State {
        public final boolean granted;
        public final boolean modifiable;
        @Nullable public final Permission permission;
        @Nullable public final PackageInfo packageInfo;
        @Nullable public final PermissionInfo permissionInfo;

        State(boolean granted, boolean modifiable, @Nullable Permission permission,
              @Nullable PackageInfo pkg, @Nullable PermissionInfo info) {
            this.granted = granted;
            this.modifiable = modifiable;
            this.permission = permission;
            this.packageInfo = pkg;
            this.permissionInfo = info;
        }
    }

    @WorkerThread
    @Nullable
    public static State load(@NonNull String packageName, int userId, @NonNull String permissionName,
                             @NonNull AppOpsManagerCompat appOpsManager) {
        try {
            PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS, userId);
            if (packageInfo == null || packageInfo.requestedPermissions == null) return null;
            int idx = -1;
            for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
                if (permissionName.equals(packageInfo.requestedPermissions[i])) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) return null;
            boolean isGranted = packageInfo.requestedPermissionsFlags != null
                    && idx < packageInfo.requestedPermissionsFlags.length
                    && (packageInfo.requestedPermissionsFlags[idx]
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            PermissionInfo permissionInfo;
            try {
                permissionInfo = PermissionCompat.getPermissionInfo(permissionName,
                        packageInfo.packageName, PackageManager.GET_META_DATA);
            } catch (Throwable th) {
                permissionInfo = null;
            }
            if (permissionInfo == null) {
                permissionInfo = new PermissionInfo();
                permissionInfo.name = permissionName;
            }
            int appOp = AppOpsManagerCompat.permissionToOpCode(permissionName);
            int permissionFlags = SelfPermissions.checkGetGrantRevokeRuntimePermissions()
                    ? PermissionCompat.getPermissionFlags(permissionName, packageInfo.packageName, userId)
                    : PermissionCompat.FLAG_PERMISSION_NONE;
            boolean appOpAllowed = false;
            if (appOp != AppOpsManagerCompat.OP_NONE) {
                List<AppOpsManagerCompat.OpEntry> entries;
                try {
                    entries = AppOpsManagerCompat.getConfiguredOpsForPackage(appOpsManager,
                            packageInfo.packageName, packageInfo.applicationInfo.uid);
                } catch (Throwable th) {
                    entries = Collections.emptyList();
                }
                if (entries == null) entries = Collections.emptyList();
                int mode = AppOpsManagerCompat.getModeFromOpEntriesOrDefault(appOp, entries);
                appOpAllowed = mode == AppOpsManager.MODE_ALLOWED;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOpAllowed |= mode == AppOpsManager.MODE_FOREGROUND;
                }
            }
            int protection = PermissionInfoCompat.getProtection(permissionInfo);
            int protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
            Permission permission;
            if (protection == PermissionInfo.PROTECTION_DANGEROUS && PermUtils.systemSupportsRuntimePermissions()) {
                permission = new RuntimePermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            } else if ((protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                permission = new DevelopmentPermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            } else {
                permission = new ReadOnlyPermission(permissionName, isGranted, appOp, appOpAllowed, permissionFlags);
            }
            boolean modifiable = PermUtils.isModifiable(permission);
            return new State(isGranted, modifiable, permission, packageInfo, permissionInfo);
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    /**
     * Flip the granted state of a single permission. Returns the new granted
     * state on success, or {@code null} on failure.
     */
    @WorkerThread
    @Nullable
    public static Boolean toggle(@NonNull String packageName, int userId, @NonNull String permissionName,
                                 @NonNull AppOpsManagerCompat appOpsManager) {
        State s = load(packageName, userId, permissionName, appOpsManager);
        if (s == null || !s.modifiable || s.permission == null || s.packageInfo == null) return null;
        try {
            if (s.granted) {
                PermUtils.revokePermission(s.packageInfo, s.permission, appOpsManager, true);
            } else {
                PermUtils.grantPermission(s.packageInfo, s.permission, appOpsManager, true, true);
            }
            persistRule(packageName, userId, permissionName, s.permission);
            return !s.granted;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    /** Force a revoke regardless of current state. Used by the master toggle. */
    @WorkerThread
    public static boolean revoke(@NonNull String packageName, int userId, @NonNull String permissionName,
                                 @NonNull AppOpsManagerCompat appOpsManager) {
        State s = load(packageName, userId, permissionName, appOpsManager);
        if (s == null || !s.modifiable || s.permission == null || s.packageInfo == null) return false;
        if (!s.granted) return true;
        try {
            PermUtils.revokePermission(s.packageInfo, s.permission, appOpsManager, true);
            persistRule(packageName, userId, permissionName, s.permission);
            return true;
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    /** Force a grant regardless of current state. Used by the master toggle. */
    @WorkerThread
    public static boolean grant(@NonNull String packageName, int userId, @NonNull String permissionName,
                                @NonNull AppOpsManagerCompat appOpsManager) {
        State s = load(packageName, userId, permissionName, appOpsManager);
        if (s == null || !s.modifiable || s.permission == null || s.packageInfo == null) return false;
        if (s.granted) return true;
        try {
            PermUtils.grantPermission(s.packageInfo, s.permission, appOpsManager, true, true);
            persistRule(packageName, userId, permissionName, s.permission);
            return true;
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    private static void persistRule(@NonNull String packageName, int userId,
                                    @NonNull String permissionName, @NonNull Permission permission) {
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userId)) {
            cb.setPermission(permissionName, permission.isGranted(), permission.getFlags());
            cb.commit();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
