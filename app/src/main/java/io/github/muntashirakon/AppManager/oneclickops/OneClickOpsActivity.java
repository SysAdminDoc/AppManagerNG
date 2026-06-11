// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArraySet;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog;
import io.github.muntashirakon.AppManager.backup.BackupRetentionPolicy;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.fm.FmUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

public class OneClickOpsActivity extends BaseActivity {
    public static final String EXTRA_OP = "op";

    LinearProgressIndicator progressIndicator;
    PowerManager.WakeLock wakeLock;

    private OneClickOpsViewModel mViewModel;
    private ListItemCreator mReviewItemCreator;
    private ListItemCreator mBackupItemCreator;
    private ListItemCreator mMaintenanceItemCreator;
    private LinearLayoutCompat mContainer;
    private LinearLayoutCompat mReviewContainer;
    private LinearLayoutCompat mBackupContainer;
    private LinearLayoutCompat mMaintenanceContainer;
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setBusy(false);
        }
    };

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        int op = getIntent().getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        if (op == BatchOpsManager.OP_CLEAR_CACHE) {
            BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_CLEAR_CACHE, null, null, null);
            launchService(item);
            finishAndRemoveTask();
            return;
        }
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(OneClickOpsViewModel.class);
        mContainer = findViewById(R.id.container);
        initItemContainers();
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        wakeLock = CpuUtils.getPartialWakeLock("1-click_ops");
        setItems();
        // Watch LiveData
        mViewModel.watchTrackerCount().observe(this, this::blockTrackers);
        mViewModel.watchComponentCount().observe(this, listPair ->
                blockComponents(listPair.first, listPair.second));
        mViewModel.watchAppOpsCount().observe(this, listPairPair ->
                setAppOps(listPairPair.first, listPairPair.second.first, listPairPair.second.second));
        mViewModel.getClearDataCandidates().observe(this, this::clearData);
        mViewModel.watchTrimCachesResult().observe(this, isSuccessful -> {
            CpuUtils.releaseWakeLock(wakeLock);
            setBusy(false);
            UIUtils.displayShortToast(isSuccessful ? R.string.done : R.string.failed);
        });
        mViewModel.getAppsInstalledByAmForDexOpt().observe(this, packages -> {
            CpuUtils.releaseWakeLock(wakeLock);
            setBusy(false);
            DexOptDialog dialog = DexOptDialog.getInstance(packages);
            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
        });
        mViewModel.watchLeftoverScanResult().observe(this, this::reviewLeftovers);
        mViewModel.watchLeftoverDeleteResult().observe(this, result -> {
            CpuUtils.releaseWakeLock(wakeLock);
            setBusy(false);
            UIUtils.displayLongToast(appendFailures(getString(R.string.leftover_files_deleted, result.deleted,
                    Formatter.formatShortFileSize(this, result.reclaimedBytes)), result.failed));
        });
        mViewModel.watchApkDuplicates().observe(this, this::reviewApkDuplicates);
        mViewModel.watchApkDuplicateDeleteResult().observe(this, result -> {
            CpuUtils.releaseWakeLock(wakeLock);
            setBusy(false);
            UIUtils.displayLongToast(appendFailures(getString(R.string.duplicate_apks_deleted, result.deleted,
                    Formatter.formatShortFileSize(this, result.reclaimedBytes)), result.failed));
        });
        mViewModel.watchDuplicateBackupPlan().observe(this, this::reviewDuplicateBackups);
        mViewModel.watchDuplicateBackupDeleteResult().observe(this, result -> {
            CpuUtils.releaseWakeLock(wakeLock);
            setBusy(false);
            UIUtils.displayLongToast(appendFailures(getString(R.string.duplicate_backups_pruned_with_reclaim,
                    result.deleted, Formatter.formatShortFileSize(this, result.reclaimedBytes)), result.failed));
        });
    }

    private void initItemContainers() {
        mReviewContainer = findViewById(R.id.one_click_review_container);
        mBackupContainer = findViewById(R.id.one_click_backup_container);
        mMaintenanceContainer = findViewById(R.id.one_click_maintenance_container);
        if (mReviewContainer == null || mBackupContainer == null || mMaintenanceContainer == null) {
            ListItemCreator itemCreator = new ListItemCreator(this, mContainer);
            mReviewContainer = mContainer;
            mBackupContainer = mContainer;
            mMaintenanceContainer = mContainer;
            mReviewItemCreator = itemCreator;
            mBackupItemCreator = itemCreator;
            mMaintenanceItemCreator = itemCreator;
            return;
        }
        mReviewItemCreator = new ListItemCreator(this, mReviewContainer);
        mBackupItemCreator = new ListItemCreator(this, mBackupContainer);
        mMaintenanceItemCreator = new ListItemCreator(this, mMaintenanceContainer);
    }

    private void setItems() {
        addSectionHeader(mReviewContainer, R.string.one_click_ops_section_review_title,
                R.string.one_click_ops_section_review_summary);
        mReviewItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers),
                        getString(R.string.block_unblock_trackers_description), R.drawable.ic_cctv_off)
                .setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.block_unblock_trackers)
                        .setMessage(R.string.apply_to_system_apps_question)
                        .setPositiveButton(R.string.user_apps_only, (dialog, which) -> {
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.blockTrackers(false);
                        })
                        .setNegativeButton(R.string.include_system_apps, (dialog, which) -> {
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.blockTrackers(true);
                        })
                        .show());
        mReviewItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_components_dots),
                        getString(R.string.block_unblock_components_description), R.drawable.ic_block)
                .setOnClickListener(v -> new TextInputDialogBuilder(this, R.string.input_signatures)
                        .setHelperText(R.string.input_signatures_description)
                        .setCheckboxLabel(R.string.apply_to_system_apps)
                        .setTitle(R.string.block_unblock_components_dots)
                        .setPositiveButton(R.string.search, (dialog, which, signatureNames, systemApps) -> {
                            if (signatureNames == null) return;
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            String[] signatures = signatureNames.toString().split("\\s+");
                            mViewModel.blockComponents(systemApps, signatures);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
        mReviewItemCreator.addItemWithTitleSubtitle(getString(R.string.set_mode_for_app_ops_dots),
                        getString(R.string.deny_app_ops_description), R.drawable.ic_tune)
                .setOnClickListener(v -> showAppOpsSelectionDialog());
        addSectionHeader(mBackupContainer, R.string.one_click_ops_section_backup_title,
                R.string.one_click_ops_section_backup_summary);
        mBackupItemCreator.addItemWithTitleSubtitle(getText(R.string.back_up),
                getText(R.string.backup_msg), R.drawable.ic_backup_restore).setOnClickListener(v ->
                new BackupTasksDialogFragment().show(getSupportFragmentManager(),
                        BackupTasksDialogFragment.TAG));
        mBackupItemCreator.addItemWithTitleSubtitle(getText(R.string.restore),
                getText(R.string.restore_msg), R.drawable.ic_restore).setOnClickListener(v ->
                new RestoreTasksDialogFragment().show(getSupportFragmentManager(),
                        RestoreTasksDialogFragment.TAG));
        addSectionHeader(mMaintenanceContainer, R.string.one_click_ops_section_maintenance_title,
                R.string.one_click_ops_section_maintenance_summary);
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps),
                        getString(R.string.clear_data_from_uninstalled_apps_description), R.drawable.ic_clear_data)
                .setOnClickListener(v -> {
                    setBusy(true);
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.clearData();
                });
        // T19-B: detect orphan Android/{data,obb,media} folders (and root
        // /data/data stubs) left behind by uninstalled apps. This complements
        // the package-record-based "clear data" entry above by catching
        // file-system remnants the framework never cleaned up.
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.detect_leftover_files),
                        getString(R.string.detect_leftover_files_description), R.drawable.ic_trash_can)
                .setOnClickListener(v -> {
                    setBusy(true);
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.scanLeftovers();
                });
//        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_app_cache),
//                        getString(R.string.clear_app_cache_description))
//                .setOnClickListener(v -> clearAppCache());
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.trim_caches_in_all_apps),
                        getString(R.string.trim_caches_in_all_apps_description), R.drawable.ic_clear_cache)
                .setOnClickListener(v -> {
                    if (!SelfPermissions.checkSelfPermission(Manifest.permission.CLEAR_APP_CACHE)
                            && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CLEAR_APP_CACHE)) {
                        showInfoDialog(R.string.root_or_adb_required, R.string.trim_caches_permission_required_message);
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.trim_caches_in_all_apps)
                            .setMessage(R.string.trim_caches_confirmation_message)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.trim_caches, (dialog, which) -> {
                                setBusy(true);
                                if (!wakeLock.isHeld()) {
                                    wakeLock.acquire();
                                }
                                mViewModel.trimCaches();
                            })
                            .show();
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.title_perform_runtime_optimization_to_apps),
                            getString(R.string.summary_perform_runtime_optimization_to_apps), R.drawable.ic_run_fast)
                    .setOnClickListener(v -> {
                        if (SelfPermissions.isSystemOrRootOrShell()) {
                            DexOptDialog dialog = DexOptDialog.getInstance(null);
                            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
                            return;
                        }
                        setBusy(true);
                        if (!wakeLock.isHeld()) {
                            wakeLock.acquire();
                        }
                        mViewModel.listAppsInstalledByAmForDexOpt();
                    });
        }
        // 1-click delete old backups (T6, Issue #387 [S33]). Three preset policies
        // backed by the shared BackupRetentionPolicy engine so the same code path
        // is exercised here and from Settings -> Backup -> "Apply retention now".
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.delete_old_backups),
                        getString(R.string.delete_old_backups_description), R.drawable.ic_trash_can)
                .setOnClickListener(v -> {
                    final int[][] policies = {
                            {1, 0},    // keep last 1 per bucket
                            {3, 0},    // keep last 3 per bucket
                            {0, 30},   // older than 30 days
                            {0, 90},   // older than 90 days
                    };
                    CharSequence[] labels = {
                            getString(R.string.delete_old_backups_keep_last_n, 1),
                            getString(R.string.delete_old_backups_keep_last_n, 3),
                            getString(R.string.delete_old_backups_older_than_days, 30),
                            getString(R.string.delete_old_backups_older_than_days, 90),
                    };
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.delete_old_backups)
                            .setItems(labels, (dialog, which) -> {
                                final int[] policy = policies[which];
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.delete_old_backups)
                                        .setMessage(R.string.delete_old_backups_confirm)
                                        .setPositiveButton(R.string.action_continue, (d, w) ->
                                                io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnBackgroundThread(() -> {
                                                    int deleted = io.github.muntashirakon.AppManager.backup
                                                            .BackupRetentionPolicy.pruneWithPolicy(policy[0], policy[1]);
                                                    io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnMainThread(() ->
                                                            UIUtils.displayLongToast(getString(
                                                                    R.string.backup_retention_prune_done, deleted)));
                                                }))
                                        .setNegativeButton(R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        // T19-D: collapse same-version duplicate backups (a package backed up
        // more than once at the same versionCode across backup folders/names)
        // down to a single copy, keeping the largest/newest/oldest per the
        // user's choice. The ViewModel first builds a reclaimable-size plan.
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.delete_duplicate_backups),
                        getString(R.string.delete_duplicate_backups_description), R.drawable.ic_trash_can)
                .setOnClickListener(v -> {
                    CharSequence[] labels = {
                            getString(R.string.delete_duplicate_backups_keep_largest),
                            getString(R.string.delete_duplicate_backups_keep_newest),
                            getString(R.string.delete_duplicate_backups_keep_oldest),
                    };
                    final BackupRetentionPolicy.DuplicateKeepStrategy[] strategies = {
                            BackupRetentionPolicy.DuplicateKeepStrategy.LARGEST_THEN_NEWEST,
                            BackupRetentionPolicy.DuplicateKeepStrategy.NEWEST,
                            BackupRetentionPolicy.DuplicateKeepStrategy.OLDEST,
                    };
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.delete_duplicate_backups)
                            .setItems(labels, (dialog, which) -> {
                                setBusy(true);
                                if (!wakeLock.isHeld()) {
                                    wakeLock.acquire();
                                }
                                mViewModel.scanDuplicateBackups(strategies[which]);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        // T19-C: scan storage for duplicate .apk files (same package + version +
        // signing cert) and reclaim space by keeping the largest copy of each.
        mMaintenanceItemCreator.addItemWithTitleSubtitle(getString(R.string.find_duplicate_apks),
                        getString(R.string.find_duplicate_apks_description), R.drawable.ic_file_copy)
                .setOnClickListener(v -> {
                    setBusy(true);
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.scanApkDuplicates(ApkDuplicateSelector.KeepStrategy.LARGEST);
                });
        setBusy(false);
    }

    private void addSectionHeader(@NonNull LinearLayoutCompat container, @StringRes int titleRes,
                                  @StringRes int summaryRes) {
        View section = LayoutInflater.from(this).inflate(R.layout.view_one_click_ops_section, container, false);
        ((TextView) section.findViewById(R.id.one_click_section_title)).setText(titleRes);
        ((TextView) section.findViewById(R.id.one_click_section_summary)).setText(summaryRes);
        container.addView(section);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
        setBusy(false);
    }

    private void blockTrackers(@Nullable List<ItemCount> trackerCounts) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (trackerCounts == null) {
            showInfoDialog(R.string.package_scan_failed, R.string.package_scan_failed_message);
            return;
        }
        if (trackerCounts.isEmpty()) {
            showInfoDialog(R.string.no_tracker_found, R.string.no_trackers_to_review_message);
            return;
        }
        final ArrayList<String> trackerPackages = new ArrayList<>();
        final List<CharSequence> trackerPackagesWithTrackerCount = new ArrayList<>(trackerCounts.size());
        for (ItemCount tracker : trackerCounts) {
            trackerPackages.add(tracker.packageName);
            trackerPackagesWithTrackerCount.add(new SpannableStringBuilder(tracker.packageLabel)
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_trackers,
                            tracker.count, tracker.count))));
        }
        new SearchableMultiChoiceDialogBuilder<>(this, trackerPackages, trackerPackagesWithTrackerCount)
                .addSelections(trackerPackages)
                .setTitle(R.string.review_tracker_apps_title)
                .setPositiveButton(R.string.block, (dialog, which, selectedPackages) -> {
                    if (!requirePackageSelection(selectedPackages)) return;
                    BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_BLOCK_TRACKERS,
                            selectedPackages, null, null);
                    launchService(item);
                })
                .setNeutralButton(R.string.unblock, (dialog, which, selectedPackages) -> {
                    if (!requirePackageSelection(selectedPackages)) return;
                    BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNBLOCK_TRACKERS,
                            selectedPackages, null, null);
                    launchService(item);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void blockComponents(@Nullable List<ItemCount> componentCounts, @NonNull String[] signatures) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (componentCounts == null) {
            showInfoDialog(R.string.package_scan_failed, R.string.package_scan_failed_message);
            return;
        }
        if (componentCounts.isEmpty()) {
            showInfoDialog(R.string.no_matching_package_found, R.string.no_component_matches_message);
            return;
        }
        SpannableStringBuilder builder;
        final ArrayList<String> selectedPackages = new ArrayList<>();
        List<CharSequence> packageNamesWithComponentCount = new ArrayList<>();
        for (ItemCount component : componentCounts) {
            builder = new SpannableStringBuilder(component.packageLabel)
                    .append("\n").append(getSmallerText(getResources().getQuantityString(R.plurals.no_of_components,
                            component.count, component.count)));
            selectedPackages.add(component.packageName);
            packageNamesWithComponentCount.add(builder);
        }
        BatchComponentOptions options = new BatchComponentOptions(signatures);
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packageNamesWithComponentCount)
                .addSelections(selectedPackages)
                .setTitle(R.string.review_component_apps_title)
                .setPositiveButton(R.string.block, (dialog1, which1, selectedItems) -> {
                    if (!requirePackageSelection(selectedItems)) return;
                    BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_BLOCK_COMPONENTS,
                            selectedItems, null, options);
                    launchService(item);
                })
                .setNeutralButton(R.string.unblock, (dialog1, which1, selectedItems) -> {
                    if (!requirePackageSelection(selectedItems)) return;
                    BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNBLOCK_COMPONENTS,
                            selectedItems, null, options);
                    launchService(item);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAppOpsSelectionDialog() {
        if (!SelfPermissions.canModifyAppOpMode()) {
            showInfoDialog(R.string.root_or_adb_required, R.string.app_ops_permission_required_message);
            return;
        }
        List<Integer> modes = AppOpsManagerCompat.getModeConstants();
        List<Integer> appOps = AppOpsManagerCompat.getAllOps();
        List<CharSequence> modeNames = Arrays.asList(getAppOpModeNames(modes));
        List<CharSequence> appOpNames = Arrays.asList(getAppOpNames(appOps));
        TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(this, R.string.input_app_ops);
        builder.setTitle(R.string.set_mode_for_app_ops_dots)
                .setAuxiliaryInput(R.string.mode, null, null, modeNames, true)
                .setCheckboxLabel(R.string.apply_to_system_apps)
                .setHelperText(R.string.input_app_ops_description)
                .setPositiveButton(R.string.search, (dialog, which, appOpNameList, systemApps) -> {
                    if (appOpNameList == null) return;
                    // Get mode
                    int mode;
                    int[] appOpList;
                    try {
                        String[] appOpsStr = appOpNameList.toString().split("\\s+");
                        if (appOpsStr.length == 0) return;
                        mode = Utils.getIntegerFromString(builder.getAuxiliaryInput(), modeNames, modes);
                        // User can unknowingly insert duplicate values for app ops
                        Set<Integer> appOpSet = new ArraySet<>(appOpsStr.length);
                        for (String appOp : appOpsStr) {
                            appOpSet.add(Utils.getIntegerFromString(appOp, appOpNames, appOps));
                        }
                        appOpList = ArrayUtils.convertToIntArray(appOpSet);
                    } catch (IllegalArgumentException e) {
                        UIUtils.displayShortToast(R.string.failed_to_parse_some_numbers);
                        return;
                    }
                    setBusy(true);
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    mViewModel.setAppOps(appOpList, mode, systemApps);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setAppOps(@Nullable List<AppOpCount> appOpCounts, @NonNull int[] appOpList, int mode) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (appOpCounts == null) {
            showInfoDialog(R.string.package_scan_failed, R.string.package_scan_failed_message);
            return;
        }
        if (appOpCounts.isEmpty()) {
            showInfoDialog(R.string.no_matching_package_found, R.string.no_app_ops_matches_message);
            return;
        }
        SpannableStringBuilder builder1;
        final ArrayList<String> selectedPackages = new ArrayList<>();
        List<CharSequence> packagesWithAppOpCount = new ArrayList<>();
        for (AppOpCount appOp : appOpCounts) {
            builder1 = new SpannableStringBuilder(appOp.packageLabel)
                    .append("\n").append(getSmallerText("(" + appOp.count + ") " + TextUtilsCompat.joinSpannable(", ",
                            appOpToNames(appOp.appOps))));
            selectedPackages.add(appOp.packageName);
            packagesWithAppOpCount.add(builder1);
        }
        BatchAppOpsOptions options = new BatchAppOpsOptions(appOpList, mode);
        new SearchableMultiChoiceDialogBuilder<>(this, selectedPackages, packagesWithAppOpCount)
                .addSelections(selectedPackages)
                .setTitle(R.string.review_app_ops_apps_title)
                .setPositiveButton(R.string.apply, (dialog1, which1, selectedItems) -> {
                    if (!requirePackageSelection(selectedItems)) return;
                    BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_SET_APP_OPS,
                            selectedItems, null, options);
                    launchService(item);
                })
                .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> setBusy(false))
                .show();
    }

    private void reviewLeftovers(@Nullable List<OneClickOpsViewModel.LeftoverEntry> entries) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (entries == null || entries.isEmpty()) {
            showInfoDialog(R.string.detect_leftover_files, R.string.no_leftover_files_found_message);
            return;
        }
        final ArrayList<Integer> indices = new ArrayList<>(entries.size());
        final List<CharSequence> labels = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); ++i) {
            OneClickOpsViewModel.LeftoverEntry entry = entries.get(i);
            indices.add(i);
            labels.add(new SpannableStringBuilder(entry.leftover.packageName)
                    .append("\n").append(getSmallerText(entry.leftover.kindLabel() + " · "
                            + Formatter.formatShortFileSize(this, entry.size))));
        }
        new SearchableMultiChoiceDialogBuilder<>(this, indices, labels)
                .addSelections(indices)
                .setTitle(R.string.leftover_files_found_title)
                .setPositiveButton(R.string.delete, (dialog, which, selectedIndices) -> {
                    if (!requirePackageSelection(selectedIndices)) return;
                    List<OneClickOpsViewModel.LeftoverEntry> selected = new ArrayList<>(selectedIndices.size());
                    for (Integer index : selectedIndices) {
                        selected.add(entries.get(index));
                    }
                    confirmDeleteLeftovers(selected);
                })
                .setNeutralButton(R.string.leftover_files_export_results, (dialog, which, selectedIndices) -> {
                    if (!requirePackageSelection(selectedIndices)) return;
                    List<OneClickOpsViewModel.LeftoverEntry> selected = new ArrayList<>(selectedIndices.size());
                    for (Integer index : selectedIndices) {
                        selected.add(entries.get(index));
                    }
                    shareLeftoverList(selected);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareLeftoverList(@NonNull List<OneClickOpsViewModel.LeftoverEntry> entries) {
        if (entries.isEmpty()) {
            UIUtils.displayLongToast(R.string.no_leftover_files_found_message);
            return;
        }
        Intent shareIntent = buildLeftoverShareIntent(entries, getString(R.string.leftover_files_export_subject));
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.leftover_files_export_results)));
        } catch (Throwable throwable) {
            UIUtils.displayLongToast(R.string.export_failed);
        }
    }

    @VisibleForTesting
    @NonNull
    static Intent buildLeftoverShareIntent(@NonNull List<OneClickOpsViewModel.LeftoverEntry> entries,
                                           @NonNull CharSequence subject) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No leftover entries to export");
        }
        return new Intent(Intent.ACTION_SEND)
                .setType("text/tab-separated-values")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, LeftoverExportFormatter.toTsv(entries));
    }

    private void confirmDeleteLeftovers(@NonNull List<OneClickOpsViewModel.LeftoverEntry> entries) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detect_leftover_files)
                .setMessage(R.string.delete_leftover_files_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        ActionAuthGate.authenticate(this, R.string.authenticate_to_clear_data, () -> {
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.deleteLeftovers(entries);
                        }))
                .show();
    }

    private void reviewApkDuplicates(@Nullable List<ApkDuplicateSelector.DuplicateGroup> groups) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (groups == null || groups.isEmpty()) {
            showInfoDialog(R.string.find_duplicate_apks, R.string.no_duplicate_apks_found_message);
            return;
        }
        final ArrayList<ApkDuplicateSelector.Candidate> drops = new ArrayList<>();
        final ArrayList<Integer> indices = new ArrayList<>();
        final List<CharSequence> labels = new ArrayList<>();
        for (ApkDuplicateSelector.DuplicateGroup group : groups) {
            for (ApkDuplicateSelector.Candidate drop : group.drop) {
                indices.add(drops.size());
                drops.add(drop);
                String dropName = FmUtils.getFileDisplayName(drop.path);
                String keeperName = FmUtils.getFileDisplayName(group.keeper.path);
                labels.add(new SpannableStringBuilder(dropName)
                        .append("\n").append(getSmallerText(drop.packageName + " v" + drop.versionCode
                                + " · " + Formatter.formatShortFileSize(this, drop.sizeBytes)
                                + " · " + getString(R.string.apk_duplicate_keeping, keeperName))));
            }
        }
        new SearchableMultiChoiceDialogBuilder<>(this, indices, labels)
                .addSelections(indices)
                .setTitle(R.string.duplicate_apks_review_title)
                .setPositiveButton(R.string.delete, (dialog, which, selectedIndices) -> {
                    if (!requirePackageSelection(selectedIndices)) return;
                    List<ApkDuplicateSelector.Candidate> selected = new ArrayList<>(selectedIndices.size());
                    for (Integer index : selectedIndices) {
                        selected.add(drops.get(index));
                    }
                    confirmDeleteApkDuplicates(selected);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteApkDuplicates(@NonNull List<ApkDuplicateSelector.Candidate> dropFiles) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.find_duplicate_apks)
                .setMessage(R.string.delete_duplicate_apks_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        ActionAuthGate.authenticate(this, R.string.authenticate_to_clear_data, () -> {
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.deleteApkDuplicates(dropFiles);
                        }))
                .show();
    }

    @NonNull
    private CharSequence appendFailures(@NonNull String successMessage, int failed) {
        if (failed <= 0) {
            return successMessage;
        }
        return successMessage + " " + getResources().getQuantityString(
                R.plurals.cleanup_items_failed_suffix, failed, failed) + ".";
    }

    private void reviewDuplicateBackups(@Nullable OneClickOpsViewModel.DuplicateBackupPlan plan) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (plan == null) {
            // Null signals a failed scan; an empty-but-non-null plan means "nothing found".
            showInfoDialog(R.string.delete_duplicate_backups, R.string.duplicate_backup_scan_failed_message);
            return;
        }
        if (plan.isEmpty()) {
            showInfoDialog(R.string.delete_duplicate_backups, R.string.no_duplicate_backups_found_message);
            return;
        }
        String reclaimable = Formatter.formatShortFileSize(this, plan.reclaimableBytes);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_duplicate_backups)
                .setMessage(getString(R.string.delete_duplicate_backups_confirm_with_reclaim,
                        plan.entries.size(), reclaimable))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        ActionAuthGate.authenticate(this, R.string.authenticate_to_delete_backups, () -> {
                            setBusy(true);
                            if (!wakeLock.isHeld()) {
                                wakeLock.acquire();
                            }
                            mViewModel.deleteDuplicateBackups(plan);
                        }))
                .show();
    }

    private void clearData(@NonNull List<String> candidatePackages) {
        CpuUtils.releaseWakeLock(wakeLock);
        setBusy(false);
        if (candidatePackages.isEmpty()) {
            showInfoDialog(R.string.no_matching_package_found, R.string.no_uninstalled_app_data_message);
            return;
        }
        String[] packages = candidatePackages.toArray(new String[0]);
        new SearchableMultiChoiceDialogBuilder<>(this, packages, packages)
                .addSelections(Arrays.asList(packages))
                .setTitle(R.string.review_orphan_data_title)
                .setPositiveButton(R.string.clear_data, (dialog1, which1, selectedItems) -> {
                    if (!requirePackageSelection(selectedItems)) return;
                    confirmClearData(selectedItems);
                })
                .setNegativeButton(R.string.cancel, (dialog1, which1, selectedItems) -> setBusy(false))
                .show();
    }

    private void confirmClearData(@NonNull List<String> selectedItems) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data)
                .setMessage(getResources().getQuantityString(R.plurals.clear_uninstalled_app_data_confirmation,
                        selectedItems.size(), selectedItems.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_data, (dialog, which) ->
                        ActionAuthGate.authenticate(this, R.string.authenticate_to_clear_data, () -> {
                            BatchQueueItem item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNINSTALL,
                                    new ArrayList<>(selectedItems), null, null);
                            launchService(item);
                        }))
                .show();
    }

    private boolean requirePackageSelection(@NonNull Collection<?> selectedItems) {
        if (!selectedItems.isEmpty()) {
            return true;
        }
        showInfoDialog(R.string.no_packages_selected, R.string.no_packages_selected_message);
        return false;
    }

    private void showInfoDialog(@StringRes int titleRes, @StringRes int messageRes) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_information_circle)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        CpuUtils.releaseWakeLock(wakeLock);
        if (mViewModel != null && shouldCancelPendingWorkOnDestroy(isFinishing(), isChangingConfigurations())) {
            mViewModel.cancelCurrentTask();
            setBusy(false);
        }
        super.onDestroy();
    }

    @VisibleForTesting
    static boolean shouldCancelPendingWorkOnDestroy(boolean isFinishing, boolean isChangingConfigurations) {
        return isFinishing && !isChangingConfigurations;
    }

    private void launchService(@NonNull BatchQueueItem queueItem) {
        setBusy(true);
        Intent intent = BatchOpsService.getServiceIntent(this, queueItem);
        ContextCompat.startForegroundService(this, intent);
    }

    private void setBusy(boolean isBusy) {
        if (progressIndicator != null) {
            if (isBusy) {
                progressIndicator.show();
            } else {
                progressIndicator.hide();
            }
        }
        if (mContainer != null) {
            mContainer.animate().alpha(isBusy ? 0.62f : 1f).setDuration(150L).start();
            setViewAndChildrenEnabled(mContainer, !isBusy);
        }
    }

    private void setViewAndChildrenEnabled(@NonNull View view, boolean isEnabled) {
        view.setEnabled(isEnabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                setViewAndChildrenEnabled(group.getChildAt(i), isEnabled);
            }
        }
    }

    @NonNull
    private List<String> appOpToNames(@NonNull Collection<Integer> appOps) {
        List<String> appOpNames = new ArrayList<>(appOps.size());
        for (int appOp : appOps) {
            appOpNames.add(AppOpsManagerCompat.opToName(appOp));
        }
        return appOpNames;
    }
}
