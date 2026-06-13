// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;

public final class DestructiveActionConfirmation {
    private DestructiveActionConfirmation() {
    }

    @NonNull
    public static MaterialAlertDialogBuilder forBatchOp(@NonNull Context context,
                                                        @BatchOpsManager.OpType int op,
                                                        @NonNull String selectedCountText,
                                                        @Nullable DialogInterface.OnClickListener onConfirm) {
        OpDescriptor desc = getOpDescriptor(op);
        String message = context.getString(desc.messageRes, selectedCountText);
        if (desc.reversible) {
            message += "\n\n" + context.getString(R.string.destructive_confirm_reversible_note);
        } else if (desc.risk == OperationJournalMetadata.RISK_HIGH) {
            message += "\n\n" + context.getString(R.string.destructive_confirm_irreversible_warning);
        }
        return new MaterialAlertDialogBuilder(context)
                .setTitle(desc.titleRes)
                .setPositiveButton(desc.actionRes, onConfirm)
                .setNegativeButton(R.string.cancel, null)
                .setMessage(message);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forKill(@NonNull Context context, int processCount) {
        String countText = context.getResources().getQuantityString(
                R.plurals.running_apps_kill_count, processCount, processCount);
        String message = context.getString(R.string.running_apps_kill_dialog_message, countText);
        message += "\n\n" + context.getString(R.string.destructive_confirm_irreversible_warning);
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.running_apps_kill_dialog_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null);
    }

    @NonNull
    private static OpDescriptor getOpDescriptor(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_FORCE_STOP:
                return new OpDescriptor(
                        R.string.batch_force_stop_dialog_title,
                        R.string.batch_force_stop_dialog_message,
                        R.string.force_stop,
                        OperationJournalMetadata.RISK_MEDIUM,
                        false);
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
                return new OpDescriptor(
                        R.string.batch_disable_background_dialog_title,
                        R.string.batch_disable_background_dialog_message,
                        R.string.disable_background_run,
                        OperationJournalMetadata.RISK_MEDIUM,
                        true);
            case BatchOpsManager.OP_UNINSTALL:
                return new OpDescriptor(
                        R.string.batch_uninstall_dialog_title,
                        R.string.batch_uninstall_dialog_message,
                        R.string.uninstall,
                        OperationJournalMetadata.RISK_HIGH,
                        false);
            case BatchOpsManager.OP_CLEAR_DATA:
                return new OpDescriptor(
                        R.string.batch_clear_data_dialog_title,
                        R.string.batch_clear_data_dialog_message,
                        R.string.clear_data,
                        OperationJournalMetadata.RISK_HIGH,
                        false);
            case BatchOpsManager.OP_CLEAR_CACHE:
                return new OpDescriptor(
                        R.string.batch_clear_cache_dialog_title,
                        R.string.batch_clear_cache_dialog_message,
                        R.string.clear_cache,
                        OperationJournalMetadata.RISK_LOW,
                        false);
            case BatchOpsManager.OP_DELETE_BACKUP:
                return new OpDescriptor(
                        R.string.batch_delete_backup_dialog_title,
                        R.string.batch_delete_backup_dialog_message,
                        R.string.delete_backup,
                        OperationJournalMetadata.RISK_HIGH,
                        false);
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_ADVANCED_FREEZE:
                return new OpDescriptor(
                        R.string.batch_freeze_dialog_title,
                        R.string.batch_freeze_dialog_message,
                        R.string.freeze,
                        OperationJournalMetadata.RISK_MEDIUM,
                        true);
            case BatchOpsManager.OP_BLOCK_TRACKERS:
                return new OpDescriptor(
                        R.string.batch_block_trackers_dialog_title,
                        R.string.batch_block_trackers_dialog_message,
                        R.string.block_trackers,
                        OperationJournalMetadata.RISK_MEDIUM,
                        true);
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
                return new OpDescriptor(
                        R.string.batch_block_components_dialog_title,
                        R.string.batch_block_components_dialog_message,
                        R.string.block_components_dots,
                        OperationJournalMetadata.RISK_MEDIUM,
                        true);
            case BatchOpsManager.OP_REVOKE_PERMISSIONS:
                return new OpDescriptor(
                        R.string.batch_revoke_permissions_dialog_title,
                        R.string.batch_revoke_permissions_dialog_message,
                        R.string.revoke,
                        OperationJournalMetadata.RISK_MEDIUM,
                        true);
            default:
                return new OpDescriptor(
                        R.string.are_you_sure,
                        R.string.batch_generic_dialog_message,
                        R.string.yes,
                        OperationJournalMetadata.RISK_MEDIUM,
                        false);
        }
    }

    private static final class OpDescriptor {
        @StringRes
        final int titleRes;
        @StringRes
        final int messageRes;
        @StringRes
        final int actionRes;
        @OperationJournalMetadata.Risk
        final int risk;
        final boolean reversible;

        OpDescriptor(@StringRes int titleRes, @StringRes int messageRes,
                     @StringRes int actionRes,
                     @OperationJournalMetadata.Risk int risk,
                     boolean reversible) {
            this.titleRes = titleRes;
            this.messageRes = messageRes;
            this.actionRes = actionRes;
            this.risk = risk;
            this.reversible = reversible;
        }
    }
}
