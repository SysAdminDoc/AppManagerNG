// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuCapabilities;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuOps;
import io.github.muntashirakon.AppManager.revert.OsRevertMonitor;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class FreezeUtils {
    @IntDef({FREEZE_DISABLE, FREEZE_SUSPEND, FREEZE_HIDE, FREEZE_ADV_SUSPEND})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FreezeMethod {
    }

    public static final int FREEZE_DISABLE = 1;
    public static final int FREEZE_SUSPEND = 1 << 1;
    public static final int FREEZE_HIDE = 1 << 2;
    public static final int FREEZE_ADV_SUSPEND = 1 << 3;

    /**
     * Whether {@code method} is exactly one of the defined freeze methods. Used to reject an
     * out-of-range value from an imported snapshot before it is written to the database.
     */
    public static boolean isValidFreezeMethod(int method) {
        return method == FREEZE_DISABLE || method == FREEZE_SUSPEND
                || method == FREEZE_HIDE || method == FREEZE_ADV_SUSPEND;
    }

    @WorkerThread
    public static void storeFreezeMethod(@NonNull String packageName, @FreezeMethod int freezeType) {
        AppsDb.getInstance().freezeTypeDao().insert(new FreezeType(packageName, freezeType));
    }

    @WorkerThread
    public static void deleteFreezeMethod(@NonNull String packageName) {
        AppsDb.getInstance().freezeTypeDao().delete(packageName);
    }

    @WorkerThread
    @FreezeMethod
    @Nullable
    public static Integer loadFreezeMethod(@Nullable String packageName) {
        if (packageName != null) {
            FreezeType freezeType;
            freezeType = AppsDb.getInstance().freezeTypeDao().get(packageName);
            if (freezeType != null) {
                return freezeType.type;
            }
        }
        // No package-specific freezing method exists
        return null;
    }

    public static boolean isFrozen(@NonNull ApplicationInfo applicationInfo) {
        // An app is frozen if one of the following operations return true: suspend, disable or hide
        if (!applicationInfo.enabled) {
            return true;
        }
        if (ApplicationInfoCompat.isSuspended(applicationInfo)) {
            return true;
        }
        return ApplicationInfoCompat.isHidden(applicationInfo);
    }

    public static boolean isFrozen(@NonNull String packageName, @UserIdInt int userId) {
        try {
            ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(packageName,
                    PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            return isFrozen(applicationInfo);
        } catch (Throwable e) {
            return false;
        }
    }

    @Deprecated
    public static void freeze(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        freeze(packageName, userId, Prefs.Blocking.getDefaultFreezingMethod());
    }

    public static void freeze(@NonNull String packageName, @UserIdInt int userId, @FreezeMethod int freezeType)
            throws RemoteException {
        if (BuildConfig.APPLICATION_ID.equals(packageName) && userId == UserHandleHidden.myUserId()) {
            throw new RemoteException("Could not freeze myself.");
        }
        Integer dhizukuMethod = getDhizukuFreezeMethod(freezeType, userId);
        if (dhizukuMethod != null) {
            if (freezeType == FREEZE_ADV_SUSPEND
                    && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                PackageManagerCompat.forceStopPackage(packageName, userId);
            }
            if (dhizukuMethod == FREEZE_SUSPEND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                DhizukuOps.setSuspended(packageName, userId, true);
            } else {
                DhizukuOps.setHidden(packageName, userId, true);
            }
            OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, true);
            return;
        }
        if (freezeType == FREEZE_HIDE) {
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                PackageManagerCompat.hidePackage(packageName, userId, true);
                OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, true);
                return;
            }
            // No permission, fall-through
        } else if ((freezeType == FREEZE_SUSPEND || freezeType == FREEZE_ADV_SUSPEND) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (freezeType == FREEZE_ADV_SUSPEND) {
                // Force-stop app
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                    PackageManagerCompat.forceStopPackage(packageName, userId);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS)) {
                    PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, true);
                    OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, true);
                    return;
                }
                // No permission, fall-through
            } else {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                    PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, true);
                    OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, true);
                    return;
                }
                // No permission, fall-through
            }
        }
        PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0, userId);
        OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, true);
    }

    public static void unfreeze(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)
                && DhizukuOps.canRun(DhizukuCapabilities.Operation.HIDE_APPS, userId)) {
            unfreezeThroughDhizuku(packageName, userId);
            OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, false);
            return;
        }
        // Ignore checking preference, unfreeze for all types
        if (PackageManagerCompat.isPackageHidden(packageName, userId)) {
            PackageManagerCompat.hidePackage(packageName, userId, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && PackageManagerCompat.isPackageSuspended(packageName, userId)) {
            PackageManagerCompat.suspendPackages(new String[]{packageName}, userId, false);
        }
        if (PackageManagerCompat.getApplicationEnabledSetting(packageName, userId) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userId);
        }
        OsRevertMonitor.watchFreeze(ContextUtils.getContext(), packageName, userId, false);
    }

    /**
     * The method to freeze with through Dhizuku, or {@code null} when the current mode can do
     * {@code freezeType} itself or Dhizuku can't help. Dhizuku can hide and suspend apps but can't
     * disable them, so the disable method hides when the mode can't disable.
     */
    @Nullable
    private static Integer getDhizukuFreezeMethod(@FreezeMethod int freezeType, @UserIdInt int userId) {
        if (!DhizukuOps.canRun(DhizukuCapabilities.Operation.HIDE_APPS, userId)) {
            return null;
        }
        if ((freezeType == FREEZE_SUSPEND || freezeType == FREEZE_ADV_SUSPEND)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean modeCanSuspend = SelfPermissions.checkSelfOrRemotePermission(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            ? ManifestCompat.permission.SUSPEND_APPS
                            : ManifestCompat.permission.MANAGE_USERS);
            if (modeCanSuspend) {
                return null;
            }
            if (DhizukuOps.canRun(DhizukuCapabilities.Operation.SUSPEND_APPS, userId)) {
                return FREEZE_SUSPEND;
            }
        } else if (freezeType == FREEZE_HIDE) {
            return SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)
                    ? null : FREEZE_HIDE;
        }
        // The old path disables the app when the mode can
        if (SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)) {
            return null;
        }
        return FREEZE_HIDE;
    }

    private static void unfreezeThroughDhizuku(@NonNull String packageName, @UserIdInt int userId)
            throws RemoteException {
        if (DhizukuOps.isHidden(packageName, userId)) {
            DhizukuOps.setHidden(packageName, userId, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && DhizukuOps.canRun(DhizukuCapabilities.Operation.SUSPEND_APPS, userId)
                && DhizukuOps.isSuspended(packageName, userId)) {
            DhizukuOps.setSuspended(packageName, userId, false);
        }
        int enabledSetting;
        try {
            enabledSetting = ContextUtils.getContext().getPackageManager().getApplicationEnabledSetting(packageName);
        } catch (IllegalArgumentException e) {
            // Not visible to this app, so there's nothing to re-enable that could be seen here
            return;
        }
        if (enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
            if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)) {
                throw new RemoteException(packageName + " is disabled. Re-enabling a disabled app needs root, ADB or Shizuku.");
            }
            PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userId);
        }
    }
}
