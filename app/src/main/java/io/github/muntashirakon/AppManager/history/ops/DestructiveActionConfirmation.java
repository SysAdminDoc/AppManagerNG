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
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;

public final class DestructiveActionConfirmation {
    private DestructiveActionConfirmation() {
    }

    @NonNull
    public static MaterialAlertDialogBuilder forBatchOp(@NonNull Context context,
                                                        @BatchOpsManager.OpType int op,
                                                        @NonNull String selectedCountText,
                                                        @Nullable DialogInterface.OnClickListener onConfirm) {
        OpDescriptor desc = getOpDescriptor(op);
        CharSequence message = withSafetyNote(context,
                context.getString(desc.messageRes, selectedCountText), desc.risk, desc.reversible);
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
        CharSequence message = withSafetyNote(context,
                context.getString(R.string.running_apps_kill_dialog_message, countText),
                OperationJournalMetadata.RISK_HIGH, false);
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.running_apps_kill_dialog_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forClearData(@NonNull Context context,
                                                          @NonNull CharSequence appLabel,
                                                          @NonNull CharSequence message,
                                                          @Nullable DialogInterface.OnClickListener onConfirm) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(appLabel)
                .setMessage(withSafetyNote(context, message, OperationJournalMetadata.RISK_HIGH, false))
                .setPositiveButton(R.string.clear, onConfirm)
                .setNegativeButton(R.string.cancel, null);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forDataOnlyPackageClear(@NonNull Context context,
                                                                     @NonNull CharSequence appLabel,
                                                                     int appCount,
                                                                     @Nullable DialogInterface.OnClickListener onConfirm) {
        return forClearData(context, appLabel, context.getResources().getQuantityString(
                R.plurals.clear_uninstalled_app_data_confirmation, appCount, appCount), onConfirm);
    }

    @NonNull
    public static ScrollableDialogBuilder forUninstall(@NonNull Context context,
                                                       @NonNull CharSequence appLabel,
                                                       boolean isSystemApp) {
        return new ScrollableDialogBuilder(context, withSafetyNote(context,
                context.getString(isSystemApp ? R.string.uninstall_system_app_message
                        : R.string.uninstall_app_message),
                OperationJournalMetadata.RISK_HIGH, false))
                .setTitle(appLabel)
                .setCheckboxLabel(R.string.keep_data_and_app_signing_signatures);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forBackupDelete(@NonNull Context context,
                                                             int backupCount,
                                                             @Nullable DialogInterface.OnClickListener onConfirm) {
        CharSequence message = context.getResources().getQuantityString(
                R.plurals.delete_selected_backups_confirmation, backupCount, backupCount);
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_backup)
                .setMessage(withSafetyNote(context, message, OperationJournalMetadata.RISK_HIGH, false))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, onConfirm);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forBaseBackupDelete(@NonNull Context context,
                                                                 @Nullable DialogInterface.OnClickListener onConfirm) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_backup)
                .setMessage(withSafetyNote(context, context.getString(R.string.delete_base_backups_confirmation),
                        OperationJournalMetadata.RISK_HIGH, false))
                .setPositiveButton(R.string.delete, onConfirm)
                .setNegativeButton(R.string.cancel, null);
    }

    @NonNull
    public static MaterialAlertDialogBuilder forProfileDelete(@NonNull Context context,
                                                              @NonNull CharSequence profileName,
                                                              @Nullable DialogInterface.OnClickListener onConfirm) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.delete_filename, profileName))
                .setMessage(withSafetyNote(context, context.getString(R.string.profile_delete_confirmation),
                        OperationJournalMetadata.RISK_HIGH, false))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, onConfirm);
    }

    @NonNull
    public static CharSequence withSafetyNote(@NonNull Context context,
                                              @NonNull CharSequence message,
                                              @OperationJournalMetadata.Risk int risk,
                                              boolean reversible) {
        StringBuilder builder = new StringBuilder(message);
        if (reversible) {
            builder.append("\n\n").append(context.getString(R.string.destructive_confirm_reversible_note));
        } else if (risk == OperationJournalMetadata.RISK_HIGH) {
            builder.append("\n\n").append(context.getString(R.string.destructive_confirm_irreversible_warning));
        }
        return builder;
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
