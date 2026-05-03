// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class RestoreTasksDialogFragment extends DialogFragment {
    public static final String TAG = "RestoreTasksDialogFragment";

    private OneClickOpsActivity mActivity;
    private Future<?> mFuture;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mActivity = (OneClickOpsActivity) requireActivity();
        View view = View.inflate(mActivity, R.layout.dialog_restore_tasks, null);
        // Restore all apps
        view.findViewById(R.id.restore_all).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (item.backup != null) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels,
                            R.string.restore_empty_all_apps_title, R.string.restore_empty_all_apps_summary));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        // Restore not installed
        view.findViewById(R.id.restore_not_installed).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (!item.isInstalled && item.backup != null) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels,
                            R.string.restore_empty_not_installed_title, R.string.restore_empty_not_installed_summary));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        // Restore latest versions only
        view.findViewById(R.id.restore_latest).setOnClickListener(v -> {
            mActivity.progressIndicator.show();
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            WeakReference<PowerManager.WakeLock> wakeLockRef = new WeakReference<>(mActivity.wakeLock);
            mFuture = ThreadUtils.postOnBackgroundThread(() -> {
                PowerManager.WakeLock wakeLock = wakeLockRef.get();
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                try {
                    List<ApplicationItem> applicationItems = new ArrayList<>();
                    List<CharSequence> applicationLabels = new ArrayList<>();
                    for (ApplicationItem item : PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true)) {
                        if (ThreadUtils.isInterrupted()) return;
                        if (item.isInstalled && item.backup != null
                                && item.versionCode < item.backup.versionCode) {
                            applicationItems.add(item);
                            applicationLabels.add(item.label);
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return;
                    ThreadUtils.postOnMainThread(() -> runMultiChoiceDialog(applicationItems, applicationLabels,
                            R.string.restore_empty_latest_title, R.string.restore_empty_latest_summary));
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock);
                }
            });
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(view)
                .setTitle(R.string.restore)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @Override
    public void onDestroy() {
        if (mFuture != null) {
            mFuture.cancel(true);
        }
        super.onDestroy();
    }

    @UiThread
    private void runMultiChoiceDialog(List<ApplicationItem> applicationItems, List<CharSequence> applicationLabels,
                                      @StringRes int emptyTitleRes, @StringRes int emptyMessageRes) {
        if (isDetached()) return;
        mActivity.progressIndicator.hide();
        if (applicationItems.isEmpty()) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.drawable.ic_information_circle)
                    .setTitle(emptyTitleRes)
                    .setMessage(emptyMessageRes)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        final int[] selectedItemCount = {applicationItems.size()};
        SearchableMultiChoiceDialogBuilder<ApplicationItem> dialogBuilder =
                new SearchableMultiChoiceDialogBuilder<>(mActivity, applicationItems, applicationLabels)
                .addSelections(applicationItems)
                .setTitle(R.string.restore_review_apps_title)
                .setOnMultiChoiceClickListener((dialog, which, item, isChecked) -> {
                    selectedItemCount[0] += isChecked ? 1 : -1;
                    ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE)
                            .setEnabled(selectedItemCount[0] > 0);
                })
                .setPositiveButton(R.string.restore, (dialog, which, selectedItems) -> {
                    if (isDetached()) return;
                    BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                            PackageUtils.getUserPackagePairs(selectedItems), BackupRestoreDialogFragment.MODE_RESTORE);
                    fragment.setOnActionBeginListener(mode -> mActivity.progressIndicator.show());
                    fragment.setOnActionCompleteListener((mode, failedPackages) -> mActivity.progressIndicator.hide());
                    if (isDetached()) return;
                    fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
                })
                .setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setEnabled(selectedItemCount[0] > 0));
        dialog.show();
    }
}
