// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

final class BatchOpsResultGuidance {
    private BatchOpsResultGuidance() {
    }

    @StringRes
    static int getMessageRes(@BatchOpsManager.OpType int op, int failedAppCount) {
        if (failedAppCount <= 0) {
            return R.string.batch_results_guidance_no_failed_apps;
        }
        switch (op) {
            case BatchOpsManager.OP_CLEAR_DATA:
            case BatchOpsManager.OP_DELETE_BACKUP:
            case BatchOpsManager.OP_UNINSTALL:
                return R.string.batch_results_guidance_destructive;
            case BatchOpsManager.OP_BACKUP:
            case BatchOpsManager.OP_BACKUP_APK:
            case BatchOpsManager.OP_IMPORT_BACKUPS:
            case BatchOpsManager.OP_RESTORE_BACKUP:
                return R.string.batch_results_guidance_backup_restore;
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_ARCHIVE:
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_INSTALL_EXISTING:
            case BatchOpsManager.OP_UNARCHIVE:
            case BatchOpsManager.OP_UNFREEZE:
                return R.string.batch_results_guidance_package_state;
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
            case BatchOpsManager.OP_BLOCK_TRACKERS:
            case BatchOpsManager.OP_FORCE_STOP:
            case BatchOpsManager.OP_GRANT_PERMISSIONS:
            case BatchOpsManager.OP_NET_POLICY:
            case BatchOpsManager.OP_REVOKE_PERMISSIONS:
            case BatchOpsManager.OP_SET_APP_OPS:
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
                return R.string.batch_results_guidance_privileged_state;
            case BatchOpsManager.OP_CLEAR_CACHE:
            case BatchOpsManager.OP_DEXOPT:
            case BatchOpsManager.OP_EXPORT_RULES:
            case BatchOpsManager.OP_NONE:
            default:
                return R.string.batch_results_guidance_generic;
        }
    }
}
