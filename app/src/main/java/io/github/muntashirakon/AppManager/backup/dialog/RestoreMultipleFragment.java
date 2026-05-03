// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.widget.MaterialAlertView;

public class RestoreMultipleFragment extends Fragment {
    @NonNull
    public static RestoreMultipleFragment getInstance() {
        return new RestoreMultipleFragment();
    }

    private BackupRestoreDialogViewModel mViewModel;
    private Context mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_restore_multiple, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel.class);
        mContext = requireContext();

        MaterialAlertView messageView = view.findViewById(R.id.message);
        ImageView summaryIcon = view.findViewById(R.id.backup_restore_summary_icon);
        TextView summaryTitle = view.findViewById(R.id.backup_restore_summary_title);
        TextView summaryBody = view.findViewById(R.id.backup_restore_summary_body);
        TextView summaryMeta = view.findViewById(R.id.backup_restore_summary_meta);
        TextView actionStatus = view.findViewById(R.id.action_status);
        MaterialButton restoreButton = view.findViewById(R.id.action_restore);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        int supportedFlags = mViewModel.getWorstBackupFlag();
        // Inject no signatures
        supportedFlags |= BackupFlags.BACKUP_NO_SIGNATURE_CHECK;
        supportedFlags |= BackupFlags.BACKUP_CUSTOM_USERS;
        int checkedFlags = BackupFlags.fromPref().getFlags() & supportedFlags;
        int disabledFlags = 0;
        if (!mViewModel.getUninstalledApps().isEmpty()) {
            checkedFlags |= BackupFlags.BACKUP_APK_FILES;
            disabledFlags |= BackupFlags.BACKUP_APK_FILES;
        }
        FlagsAdapter adapter = new FlagsAdapter(mContext, checkedFlags, supportedFlags, disabledFlags);
        recyclerView.setAdapter(adapter);
        summaryIcon.setImageResource(R.drawable.ic_restore);
        summaryTitle.setText(R.string.restore_dialog_summary_title);
        summaryBody.setText(R.string.restore_dialog_summary_multiple);
        summaryMeta.setText(getString(R.string.restore_dialog_meta,
                getResources().getQuantityString(R.plurals.restore_dialog_restorable_app_count,
                        mViewModel.getRestoreCandidateCount(), mViewModel.getRestoreCandidateCount()),
                getSkippedAppSummary()));
        adapter.setOnSelectionChangeListener((selectedFlags, selectedFlagCount) ->
                updateActionState(restoreButton, actionStatus, selectedFlagCount));

        Set<CharSequence> appsWithoutBackups = mViewModel.getAppsWithoutBackups();
        if (!appsWithoutBackups.isEmpty()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.backup_apps_cannot_be_restored));
            for (CharSequence appLabel : appsWithoutBackups) {
                sb.append("\n● ").append(appLabel);
            }
            messageView.setText(sb);
            messageView.setVisibility(View.VISIBLE);
        }
        restoreButton.setOnClickListener(v -> {
            int newFlags = adapter.getSelectedFlags();
            handleRestore(newFlags);
        });
    }

    private void handleRestore(int flags) {
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.restore)
                .setMessage(R.string.restore_multiple_backups_confirmation)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_RESTORE;
                    operationInfo.op = BatchOpsManager.OP_RESTORE_BACKUP;
                    operationInfo.flags = flags;
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateActionState(@NonNull MaterialButton restoreButton, @NonNull TextView actionStatus,
                                   int selectedFlagCount) {
        boolean hasSelectedContent = selectedFlagCount > 0;
        restoreButton.setEnabled(hasSelectedContent);
        if (hasSelectedContent) {
            actionStatus.setText(getResources().getQuantityString(R.plurals.backup_restore_content_selected_count,
                    selectedFlagCount, selectedFlagCount));
        } else {
            actionStatus.setText(R.string.backup_restore_no_content_selected);
        }
    }

    @NonNull
    private String getSkippedAppSummary() {
        int skippedAppCount = mViewModel.getAppsWithoutBackups().size();
        if (skippedAppCount == 0) {
            return getString(R.string.restore_dialog_no_skipped_apps);
        }
        return getResources().getQuantityString(R.plurals.restore_dialog_skipped_app_count,
                skippedAppCount, skippedAppCount);
    }
}
