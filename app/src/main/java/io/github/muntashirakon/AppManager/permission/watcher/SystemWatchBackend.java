// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permissions.PermissionToggleHelper;
import io.github.muntashirakon.AppManager.safety.CriticalPackageGuard;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/**
 * Reads and changes permissions through the same helpers App Details and the Permission Inspector use, so a
 * change made here is saved to the app's rules the same way, and records each change in the operation history.
 */
public final class SystemWatchBackend implements PermissionWatcher.Backend {
    private static final String TAG = "SystemWatchBackend";

    @NonNull
    private final Context mContext;
    @NonNull
    private final AppOpsManagerCompat mAppOps = new AppOpsManagerCompat();

    public SystemWatchBackend(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @WorkerThread
    @Nullable
    @Override
    public Boolean isAllowed(@NonNull WatchRule rule) {
        if (rule.type == WatchRule.TYPE_PERMISSION) {
            PermissionToggleHelper.State state = PermissionToggleHelper.load(rule.packageName, rule.userId, rule.name,
                    mAppOps);
            return state != null ? state.effectiveGranted : null;
        }
        if (rule.op == AppOpsManagerCompat.OP_NONE) {
            return null;
        }
        Integer uid = uidOf(rule);
        if (uid == null) {
            return null;
        }
        try {
            int mode = mAppOps.checkOperation(rule.op, uid, rule.packageName);
            boolean permissionGranted = false;
            if (mode == AppOpsManager.MODE_DEFAULT) {
                String permission = AppOpsManagerCompat.opToPermission(rule.op);
                permissionGranted = permission != null && PermissionCompat.checkPermission(permission,
                        rule.packageName, rule.userId) == PackageManager.PERMISSION_GRANTED;
            }
            return isAllowedMode(mode, permissionGranted);
        } catch (Exception e) {
            Log.w(TAG, "Could not read app op %s of %s.", e, rule.name, rule.packageName);
            return null;
        }
    }

    @WorkerThread
    @Override
    public boolean canEnforce(@NonNull WatchRule rule) {
        return rule.type == WatchRule.TYPE_PERMISSION ? SelfPermissions.canModifyPermissions()
                : SelfPermissions.canModifyAppOpMode();
    }

    @WorkerThread
    @Override
    public void apply(@NonNull WatchRule rule, boolean allow) throws Exception {
        if (!allow && CriticalPackageGuard.isCriticalPackage(rule.packageName)) {
            // The same guard bulk actions use: taking permissions from these can break calls or boot.
            throw new IllegalStateException(mContext.getString(R.string.permission_watcher_critical_app));
        }
        if (rule.type == WatchRule.TYPE_PERMISSION) {
            boolean ok = allow ? PermissionToggleHelper.grant(rule.packageName, rule.userId, rule.name, mAppOps)
                    : PermissionToggleHelper.revoke(rule.packageName, rule.userId, rule.name, mAppOps);
            record(rule, allow ? SingleAppActionHistoryItem.ACTION_PERMISSION_GRANT
                            : SingleAppActionHistoryItem.ACTION_PERMISSION_REVOKE,
                    allow ? R.string.op_history_single_action_grant_permission
                            : R.string.op_history_single_action_revoke_permission,
                    mContext.getString(R.string.permission_watcher_title), ok, null);
            if (!ok) {
                throw new IllegalStateException(mContext.getString(R.string.permission_watcher_change_refused));
            }
            return;
        }
        Integer uid = uidOf(rule);
        if (uid == null) {
            throw new IllegalStateException(mContext.getString(R.string.permission_watcher_change_refused));
        }
        int mode = allow ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED;
        try {
            PermUtils.setAppOpMode(mAppOps, rule.op, rule.packageName, uid, mode);
            record(rule, SingleAppActionHistoryItem.ACTION_APP_OP_SET, R.string.op_history_single_action_set_app_op,
                    AppOpsManagerCompat.modeToName(mode), true, null);
        } catch (Exception e) {
            record(rule, SingleAppActionHistoryItem.ACTION_APP_OP_SET, R.string.op_history_single_action_set_app_op,
                    AppOpsManagerCompat.modeToName(mode), false, e);
            throw e;
        }
    }

    /** Mirrors App Details: foreground-only counts as allowed, and the default mode follows the permission. */
    @VisibleForTesting
    static boolean isAllowedMode(int mode, boolean permissionGranted) {
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mode == AppOpsManager.MODE_FOREGROUND) {
            return true;
        }
        return mode == AppOpsManager.MODE_DEFAULT && permissionGranted;
    }

    @Nullable
    private static Integer uidOf(@NonNull WatchRule rule) {
        try {
            ApplicationInfo info = PackageManagerCompat.getApplicationInfo(rule.packageName, 0, rule.userId);
            return info != null ? info.uid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void record(@NonNull WatchRule rule, @NonNull String action, int operationLabel, @Nullable String detail,
                        boolean success, @Nullable Throwable failure) {
        try {
            SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(action,
                    mContext.getString(operationLabel), rule.packageName, rule.userId, rule.name, detail);
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, item, success,
                    OperationJournalMetadata.forSingleAppAction(mContext, item, success,
                            OperationJournalMetadata.RISK_MEDIUM, false, failure));
        } catch (Exception e) {
            Log.e(TAG, "Could not record the watcher's change to %s.", e, rule.packageName);
        }
    }
}
