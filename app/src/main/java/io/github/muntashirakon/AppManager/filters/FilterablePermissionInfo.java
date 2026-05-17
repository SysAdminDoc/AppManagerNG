// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_POLICY_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PermissionInfoCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class FilterablePermissionInfo {
    @NonNull
    public final String name;
    public final boolean granted;
    @PermissionCompat.PermissionFlags
    public final int permissionFlags;
    public final boolean known;
    @Nullable
    public final String sourcePackageName;
    public final int declarationFlags;
    public final int protection;
    public final int protectionFlags;

    public FilterablePermissionInfo(@NonNull String name,
                                    boolean granted,
                                    @PermissionCompat.PermissionFlags int permissionFlags,
                                    boolean known,
                                    @Nullable String sourcePackageName,
                                    int declarationFlags,
                                    int protection,
                                    int protectionFlags) {
        this.name = name;
        this.granted = granted;
        this.permissionFlags = permissionFlags;
        this.known = known;
        this.sourcePackageName = sourcePackageName;
        this.declarationFlags = declarationFlags;
        this.protection = protection;
        this.protectionFlags = protectionFlags;
    }

    public boolean isCustom() {
        return sourcePackageName == null || !"android".equals(sourcePackageName);
    }

    public boolean isFixed() {
        return hasAnyPermissionFlags(FLAG_PERMISSION_USER_FIXED
                | FLAG_PERMISSION_SYSTEM_FIXED | FLAG_PERMISSION_POLICY_FIXED);
    }

    public boolean hasAllPermissionFlags(@PermissionCompat.PermissionFlags int flags) {
        return (permissionFlags & flags) == flags;
    }

    public boolean hasAnyPermissionFlags(@PermissionCompat.PermissionFlags int flags) {
        return (permissionFlags & flags) != 0;
    }

    @NonNull
    public static List<FilterablePermissionInfo> fromPackageInfo(@NonNull PackageInfo packageInfo,
                                                                int userId,
                                                                boolean canReadPermissionFlags) {
        if (packageInfo.requestedPermissions == null) {
            return Collections.emptyList();
        }
        List<FilterablePermissionInfo> permissionDetails = new ArrayList<>(packageInfo.requestedPermissions.length);
        int[] requestedPermissionFlags = ArrayUtils.defeatNullable(packageInfo.requestedPermissionsFlags);
        for (int i = 0; i < packageInfo.requestedPermissions.length; ++i) {
            String permissionName = packageInfo.requestedPermissions[i];
            boolean granted = i < requestedPermissionFlags.length
                    && (requestedPermissionFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            int permissionFlags = PermissionCompat.FLAG_PERMISSION_NONE;
            if (canReadPermissionFlags) {
                try {
                    permissionFlags = PermissionCompat.getPermissionFlags(permissionName, packageInfo.packageName, userId);
                } catch (RuntimeException ignore) {
                }
            }
            PermissionInfo permissionInfo = null;
            try {
                permissionInfo = PermissionCompat.getPermissionInfo(permissionName,
                        packageInfo.packageName, PackageManager.GET_META_DATA);
            } catch (RemoteException | RuntimeException ignore) {
            }
            boolean known = permissionInfo != null;
            permissionDetails.add(new FilterablePermissionInfo(permissionName,
                    granted,
                    permissionFlags,
                    known,
                    known ? permissionInfo.packageName : null,
                    known ? permissionInfo.flags : 0,
                    known ? PermissionInfoCompat.getProtection(permissionInfo) : 0,
                    known ? PermissionInfoCompat.getProtectionFlags(permissionInfo) : 0));
        }
        return permissionDetails;
    }
}
