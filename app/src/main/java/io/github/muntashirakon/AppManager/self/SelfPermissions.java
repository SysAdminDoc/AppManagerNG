// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Paths;

public class SelfPermissions {
    public static final String SHELL_PACKAGE_NAME = "com.android.shell";

    public static void init() {
        if (!canModifyPermissions()) {
            return;
        }
        String[] permissions = new String[]{
                Manifest.permission.DUMP,
                ManifestCompat.permission.GET_APP_OPS_STATS,
                ManifestCompat.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.READ_LOGS,
                Manifest.permission.WRITE_SECURE_SETTINGS
        };
        int userId = UserHandleHidden.myUserId();
        for (String permission : permissions) {
            if (!checkSelfPermission(permission)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, permission, userId);
                } catch (Exception e) {
                    Log.w("SelfPermissions", "Could not grant self permission %s.", e, permission);
                }
            }
        }
        // Grant usage stats permission (both permission and app op needs to be granted)
        if (FeatureController.isUsageAccessEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, Manifest.permission.PACKAGE_USAGE_STATS, userId);
                } catch (Exception e) {
                    Log.w("SelfPermissions", "Could not grant the usage stats permission.", e);
                }
            }
            try {
                AppOpsManagerCompat appOps = new AppOpsManagerCompat();
                appOps.setMode(AppOpsManagerHidden.OP_GET_USAGE_STATS, Process.myUid(), BuildConfig.APPLICATION_ID, AppOpsManager.MODE_ALLOWED);
            } catch (RemoteException e) {
                Log.w("SelfPermissions", "Could not allow the usage stats app-op.", e);
            }
        }
    }

    public static boolean canBlockByIFW() {
        return Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH).canWrite();
    }

    public static boolean canWriteToDataData() {
        return Paths.get("/data/data").canWrite();
    }

    public static boolean canModifyAppComponentStates(@UserIdInt int userId, @Nullable String packageName,
                                                      boolean testOnlyApp) {
        if (!checkCrossUserPermission(userId, false)) {
            return false;
        }
        final int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Since Oreo, shell can only disable components of test only apps
            if (callingUid == Ops.SHELL_UID && !testOnlyApp) {
                return false;
            }
        }
        if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            // We can change components for this package
            return true;
        }
        return checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid);
    }

    public static boolean canModifyAppOpMode() {
        int callingUid = Users.getSelfOrRemoteUid();
        boolean canModify = checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_APP_OPS_STATS, callingUid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APP_OPS_MODES, callingUid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                canModify &= checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APPOPS, callingUid);
            }
        }
        return canModify;
    }

    public static boolean canModifyPermissions() {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid);
    }

    public static boolean checkGetGrantRevokeRuntimePermissions() {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkSelfOrRemotePermission(ManifestCompat.permission.GET_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid);
    }

    public static boolean canInstallExistingPackages() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES, callingUid)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INSTALL_EXISTING_PACKAGES, callingUid);
        }
        return false;
    }

    public static boolean canFreezeUnfreezePackages() {
        // 1. Suspend (7+): MANAGE_USERS (<= 9), SUSPEND_APPS (>= 9)
        // 2. Disable: CHANGE_COMPONENT_ENABLED_STATE
        // 2. HIDE: MANAGE_USERS
        int callingUid = Users.getSelfOrRemoteUid();
        boolean canFreezeUnfreeze = checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS, callingUid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canFreezeUnfreeze |= checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS, callingUid);
        }
        return canFreezeUnfreeze;
    }

    public static boolean canClearAppCache() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERNAL_DELETE_CACHE_FILES, callingUid);
        }
        return checkSelfOrRemotePermission(Manifest.permission.DELETE_CACHE_FILES, callingUid);
    }

    public static boolean canKillUid() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.KILL_UID, callingUid);
        }
        return callingUid == Ops.SYSTEM_UID;
    }

    public static boolean checkNotificationListenerAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return false;
        }
        int callingUid = Users.getSelfOrRemoteUid();
        if (checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NOTIFICATION_LISTENERS, callingUid)) {
            return true;
        }
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.PHONE_UID;
    }

    /**
     * Returned by {@link #getUsageStatsQueryUid(int)} when no identity can read the requested user's
     * usage statistics.
     */
    public static final int USAGE_STATS_UNAVAILABLE = -1;

    /**
     * Whether usage statistics of the current user can be read, either by the privileged identity or
     * through AppManagerNG's own grant. {@link #getUsageStatsQueryUid(int)} picks the identity a query
     * uses, so a privileged identity without Usage Access never hides a grant the user already gave.
     */
    public static boolean checkUsageStatsPermission() {
        return getUsageStatsQueryUid(UserHandleHidden.myUserId()) != USAGE_STATS_UNAVAILABLE;
    }

    /**
     * Whether AppManagerNG's own UID holds the Usage Access grant the user controls in Settings. This
     * is the only grant that screen can change: a privileged identity such as Shizuku's shell
     * (UID 2000) has a separate one.
     */
    public static boolean hasAppUsageAccessGrant() {
        Context context = ContextUtils.getContext();
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                    context.getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                    context.getPackageName());
        }
        return isUsageAccessGranted(mode, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mode == AppOpsManager.MODE_DEFAULT
                && checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS));
    }

    /**
     * Whether {@code executionUid} may read usage statistics of {@code userId}. Root and system read
     * every user. Any other identity needs its own Usage Access, and cross-user access for another
     * user's data. A grant that cannot be read counts as absent.
     */
    public static boolean canQueryUsageStats(int executionUid, @UserIdInt int userId) {
        if (executionUid == Ops.ROOT_UID || executionUid == Ops.SYSTEM_UID) {
            return true;
        }
        boolean granted;
        if (executionUid == Process.myUid()) {
            granted = hasAppUsageAccessGrant();
        } else {
            try {
                int mode = new AppOpsManagerCompat().checkOpNoThrow(AppOpsManagerHidden.OP_GET_USAGE_STATS,
                        executionUid, getCallingPackage(executionUid));
                granted = isUsageAccessGranted(mode, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && mode == AppOpsManager.MODE_DEFAULT
                        && checkSelfOrRemotePermission(Manifest.permission.PACKAGE_USAGE_STATS, executionUid));
            } catch (RuntimeException e) {
                Log.w("SelfPermissions", "Could not read the Usage Access state of uid %d.", e, executionUid);
                granted = false;
            }
        }
        return granted && checkCrossUserPermission(userId, false, executionUid);
    }

    /**
     * The identity a usage-statistics query for {@code userId} should run as: the privileged identity
     * when it can query, otherwise AppManagerNG itself when its own grant covers that user, otherwise
     * {@link #USAGE_STATS_UNAVAILABLE}. Some OEM builds deny Usage Access to Shizuku's shell even
     * though the user granted it to AppManagerNG (fork issue #16).
     */
    public static int getUsageStatsQueryUid(@UserIdInt int userId) {
        int executionUid = Users.getSelfOrRemoteUid();
        if (canQueryUsageStats(executionUid, userId)) {
            return executionUid;
        }
        int appUid = Process.myUid();
        if (executionUid != appUid && canQueryUsageStats(appUid, userId)) {
            return appUid;
        }
        return USAGE_STATS_UNAVAILABLE;
    }

    private static boolean isUsageAccessGranted(int opMode, boolean permissionGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && opMode == AppOpsManager.MODE_DEFAULT) {
            return permissionGranted;
        }
        if (opMode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && opMode == AppOpsManager.MODE_FOREGROUND;
    }

    public static boolean checkSelfStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Utils.isRoboUnitTest()) {
                return false;
            }
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean checkStoragePermission() {
        int callingUid = Users.getSelfOrRemoteUid();
        if (callingUid == Ops.ROOT_UID) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String packageName = getCallingPackage(callingUid);
            AppOpsManagerCompat appOps = new AppOpsManagerCompat();
            int opMode = appOps.checkOpNoThrow(AppOpsManagerHidden.OP_MANAGE_EXTERNAL_STORAGE, callingUid, packageName);
            switch (opMode) {
                case AppOpsManager.MODE_DEFAULT:
                    return checkSelfOrRemotePermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE, callingUid);
                case AppOpsManager.MODE_ALLOWED:
                    return true;
                case AppOpsManager.MODE_ERRORED:
                case AppOpsManager.MODE_IGNORED:
                    return false;
                default:
                    throw new IllegalStateException("Unknown AppOpsManager mode " + opMode);
            }
        }
        return checkSelfOrRemotePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callingUid);
    }

    public static boolean checkCrossUserPermission(@UserIdInt int userId, boolean requireFullPermission) {
        int callingUid = Users.getSelfOrRemoteUid();
        return checkCrossUserPermission(userId, requireFullPermission, callingUid);
    }

    public static boolean checkCrossUserPermission(@UserIdInt int userId, boolean requireFullPermission, int callingUid) {
        if (userId == UserHandleHidden.USER_NULL) {
            userId = UserHandleHidden.myUserId();
        }
        if (userId < 0 && userId != UserHandleHidden.USER_ALL) {
            throw new IllegalArgumentException("Invalid userId " + userId);
        }
        if (isSystemOrRootOrShell(callingUid) || userId == UserHandleHidden.getUserId(callingUid)) {
            return true;
        }
        if (requireFullPermission) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid);
        }
        return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid)
                || checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS, callingUid);
    }

    public static boolean isShell() {
        return Users.getSelfOrRemoteUid() == Ops.SHELL_UID;
    }

    public static boolean isSystem() {
        return Users.getSelfOrRemoteUid() == Ops.SYSTEM_UID;
    }

    public static boolean isSystemOrRoot() {
        int callingUid = Users.getSelfOrRemoteUid();
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID;
    }

    public static boolean isSystemOrRootOrShell() {
        return isSystemOrRootOrShell(Users.getSelfOrRemoteUid());
    }

    private static boolean isSystemOrRootOrShell(int callingUid) {
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.SHELL_UID;
    }

    public static boolean checkSelfOrRemotePermission(@NonNull String permissionName) {
        return checkSelfOrRemotePermission(permissionName, Users.getSelfOrRemoteUid());
    }

    public static boolean checkSelfOrRemotePermission(@NonNull String permissionName, int uid) {
        if (uid == Ops.ROOT_UID) {
            // Root UID has all the permissions granted
            return true;
        }
        if (uid != Process.myUid()) {
            try {
                return PackageManagerCompat.getPackageManager().checkUidPermission(permissionName, uid)
                        == PackageManager.PERMISSION_GRANTED;
            } catch (RemoteException e) {
                Log.w("SelfPermissions", "Could not check permission %s for uid %d.", e, permissionName, uid);
            }
        }
        return checkSelfPermission(permissionName);
    }

    public static boolean checkSelfPermission(@NonNull String permissionName) {
        return ContextCompat.checkSelfPermission(ContextUtils.getContext(), permissionName)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requireSelfPermission(@NonNull String permissionName) throws SecurityException {
        if (!checkSelfPermission(permissionName)) {
            throw new SecurityException("App Manager does not have the required permission " + permissionName);
        }
    }

    @NonNull
    public static String getCallingPackage(int callingUid) {
        if (callingUid == Ops.ROOT_UID || callingUid == Ops.SHELL_UID) {
            return SHELL_PACKAGE_NAME;
        }
        if (callingUid == Ops.SYSTEM_UID) {
            return "android";
        }
        return BuildConfig.APPLICATION_ID;
    }
}
