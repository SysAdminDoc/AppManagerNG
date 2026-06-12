// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class BatchOpsResultGuidanceTest {
    @Test
    public void destructiveOperationsUseRecoveryWarning() {
        assertEquals(R.string.batch_results_guidance_destructive,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_CLEAR_DATA, 1));
        assertEquals(R.string.batch_results_guidance_destructive,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_DELETE_BACKUP, 1));
        assertEquals(R.string.batch_results_guidance_destructive,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_UNINSTALL, 1));
    }

    @Test
    public void backupOperationsUseBackupSpecificGuidance() {
        assertEquals(R.string.batch_results_guidance_backup_restore,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_BACKUP, 1));
        assertEquals(R.string.batch_results_guidance_backup_restore,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_RESTORE_BACKUP, 1));
        assertEquals(R.string.batch_results_guidance_backup_restore,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_IMPORT_BACKUPS, 1));
    }

    @Test
    public void privilegedStateOperationsPointToPrivilegeMode() {
        assertEquals(R.string.batch_results_guidance_privileged_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_FREEZE, 1));
        assertEquals(R.string.batch_results_guidance_privileged_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_SET_APP_OPS, 1));
        assertEquals(R.string.batch_results_guidance_privileged_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_FORCE_STOP, 1));
    }

    @Test
    public void noFailedAppsUsesNoFailedAppsGuidance() {
        assertEquals(R.string.batch_results_guidance_no_failed_apps,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_UNINSTALL, 0));
    }
}
