// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_SET_APP_OPS, 1));
        assertEquals(R.string.batch_results_guidance_privileged_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_FORCE_STOP, 1));
    }

    @Test
    public void packageStateOperationsPointToVerificationLogs() {
        assertEquals(R.string.batch_results_guidance_package_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_FREEZE, 1));
        assertEquals(R.string.batch_results_guidance_package_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_DISABLE_BACKGROUND, 1));
        assertEquals(R.string.batch_results_guidance_package_state,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_INSTALL_EXISTING, 1));
    }

    @Test
    public void noFailedAppsUsesNoFailedAppsGuidance() {
        assertEquals(R.string.batch_results_guidance_no_failed_apps,
                BatchOpsResultGuidance.getMessageRes(BatchOpsManager.OP_UNINSTALL, 0));
    }

    @Test
    public void resultsScreenLinksToOperationHistory() throws IOException {
        Path repoRoot = findRepoRoot();
        String layout = read(repoRoot.resolve("app/src/main/res/layout/activity_batch_ops_results.xml"));
        String source = read(repoRoot.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/batchops/BatchOpsResultsActivity.java"));

        assertTrue("Batch result layout should expose an operation history action",
                layout.contains("android:id=\"@+id/action_view_history\"")
                        && layout.contains("android:text=\"@string/op_history\""));
        assertTrue("Batch result activity should open operation history from the results screen",
                source.contains("OpHistoryManager.HISTORY_TYPE_BATCH_OPS")
                        && source.contains("OpHistoryManager.STATUS_FAILURE"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
