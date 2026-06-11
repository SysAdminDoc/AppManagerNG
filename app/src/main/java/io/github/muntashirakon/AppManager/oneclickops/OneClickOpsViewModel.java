// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.Manifest;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupRetentionPolicy;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Paths;

public class OneClickOpsViewModel extends AndroidViewModel {
    public static final String TAG = OneClickOpsViewModel.class.getSimpleName();

    private final PackageManager mPm;
    private final MutableLiveData<List<ItemCount>> mTrackerCount = new MutableLiveData<>();
    private final MutableLiveData<Pair<List<ItemCount>, String[]>> mComponentCount = new MutableLiveData<>();
    private final MutableLiveData<Pair<List<AppOpCount>, Pair<int[], Integer>>> mAppOpsCount = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mClearDataCandidates = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mTrimCachesResult = new MutableLiveData<>();
    private final MutableLiveData<String[]> mAppsInstalledByAmForDexOpt = new MutableLiveData<>();
    private final MutableLiveData<List<LeftoverEntry>> mLeftoverScanResult = new MutableLiveData<>();
    private final MutableLiveData<CleanupResult> mLeftoverDeleteResult = new MutableLiveData<>();
    private final MutableLiveData<List<ApkDuplicateSelector.DuplicateGroup>> mApkDuplicates = new MutableLiveData<>();
    private final MutableLiveData<CleanupResult> mApkDuplicateDeleteResult = new MutableLiveData<>();
    private final MutableLiveData<DuplicateBackupPlan> mDuplicateBackupPlan = new MutableLiveData<>();
    private final MutableLiveData<CleanupResult> mDuplicateBackupDeleteResult = new MutableLiveData<>();

    @Nullable
    private Future<?> mFutureResult;

    public OneClickOpsViewModel(@NonNull Application application) {
        super(application);
        mPm = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        cancelCurrentTask();
        super.onCleared();
    }

    public LiveData<List<ItemCount>> watchTrackerCount() {
        return mTrackerCount;
    }

    public LiveData<Pair<List<ItemCount>, String[]>> watchComponentCount() {
        return mComponentCount;
    }

    public LiveData<Pair<List<AppOpCount>, Pair<int[], Integer>>> watchAppOpsCount() {
        return mAppOpsCount;
    }

    public LiveData<List<String>> getClearDataCandidates() {
        return mClearDataCandidates;
    }

    public LiveData<Boolean> watchTrimCachesResult() {
        return mTrimCachesResult;
    }

    public MutableLiveData<String[]> getAppsInstalledByAmForDexOpt() {
        return mAppsInstalledByAmForDexOpt;
    }

    public LiveData<List<LeftoverEntry>> watchLeftoverScanResult() {
        return mLeftoverScanResult;
    }

    public LiveData<CleanupResult> watchLeftoverDeleteResult() {
        return mLeftoverDeleteResult;
    }

    public LiveData<List<ApkDuplicateSelector.DuplicateGroup>> watchApkDuplicates() {
        return mApkDuplicates;
    }

    public LiveData<CleanupResult> watchApkDuplicateDeleteResult() {
        return mApkDuplicateDeleteResult;
    }

    public LiveData<DuplicateBackupPlan> watchDuplicateBackupPlan() {
        return mDuplicateBackupPlan;
    }

    public LiveData<CleanupResult> watchDuplicateBackupDeleteResult() {
        return mDuplicateBackupDeleteResult;
    }

    /**
     * Scan external storage for {@code .apk} files, fingerprint each by
     * (package, versionCode, signing-cert SHA-256) via
     * {@link PackageManager#getPackageArchiveInfo}, and surface every
     * redundant copy through {@link ApkDuplicateSelector} (T19-C).
     */
    @AnyThread
    public void scanApkDuplicates(@NonNull ApkDuplicateSelector.KeepStrategy strategy) {
        postCancellableTask(() -> {
            List<File> apkFiles = ApkDuplicateScanRoots.scanDefaultRoots(null);
            List<ApkDuplicateSelector.Candidate> candidates = new ArrayList<>(apkFiles.size());
            for (File apk : apkFiles) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                ApkDuplicateSelector.Candidate candidate = ApkDuplicateOperations.buildCandidate(
                        mPm, getApplication().getCacheDir(), apk);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            mApkDuplicates.postValue(ApkDuplicateSelector.selectDuplicates(candidates, strategy));
        });
    }

    /**
     * Delete the chosen duplicate APK files through the privileged
     * {@link Paths} layer, logging each removal and posting a (count, bytes)
     * summary. The keeper of each group is never passed in here.
     */
    @AnyThread
    public void deleteApkDuplicates(@NonNull List<ApkDuplicateSelector.Candidate> dropFiles) {
        postCancellableTask(() -> {
            Pair<Integer, Long> result = ApkDuplicateOperations.deleteCandidates(dropFiles);
            int deleted = result.first != null ? result.first : 0;
            long reclaimed = result.second != null ? result.second : 0L;
            mApkDuplicateDeleteResult.postValue(new CleanupResult(deleted,
                    Math.max(0, dropFiles.size() - deleted), reclaimed));
        });
    }

    /**
     * A single leftover folder paired with its precomputed on-disk size so the
     * review dialog never walks the file system on the main thread (T19-B).
     */
    /**
     * Summary of a cleanup deletion run. Unlike a bare (count, bytes) pair this also
     * carries the number of items that could not be deleted so the UI can tell the user
     * about partial failures instead of reporting unqualified success.
     */
    public static class CleanupResult {
        public final int deleted;
        public final int failed;
        public final long reclaimedBytes;

        public CleanupResult(int deleted, int failed, long reclaimedBytes) {
            this.deleted = deleted;
            this.failed = failed;
            this.reclaimedBytes = reclaimedBytes;
        }
    }

    public static class LeftoverEntry {
        @NonNull
        public final LeftoverScanner.Leftover leftover;
        public final long size;

        public LeftoverEntry(@NonNull LeftoverScanner.Leftover leftover, long size) {
            this.leftover = leftover;
            this.size = size;
        }
    }

    public static class DuplicateBackupEntry {
        @NonNull
        public final Backup backup;
        public final long size;

        public DuplicateBackupEntry(@NonNull Backup backup, long size) {
            this.backup = backup;
            this.size = size;
        }
    }

    public static class DuplicateBackupPlan {
        @NonNull
        public final BackupRetentionPolicy.DuplicateKeepStrategy strategy;
        @NonNull
        public final List<DuplicateBackupEntry> entries;
        public final long reclaimableBytes;

        public DuplicateBackupPlan(@NonNull BackupRetentionPolicy.DuplicateKeepStrategy strategy,
                                   @NonNull List<DuplicateBackupEntry> entries,
                                   long reclaimableBytes) {
            this.strategy = strategy;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.reclaimableBytes = reclaimableBytes;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    @AnyThread
    public void scanDuplicateBackups(@NonNull BackupRetentionPolicy.DuplicateKeepStrategy strategy) {
        postCancellableTask(() -> {
            BackupRetentionPolicy.BackupSizeResolver sizeResolver = BackupRetentionPolicy.backupItemSizeResolver();
            try {
                List<Backup> all = AppsDb.getInstance().backupDao().getAll();
                List<Backup> duplicates = BackupRetentionPolicy.selectVersionDuplicates(all, strategy, sizeResolver);
                List<DuplicateBackupEntry> entries = new ArrayList<>(duplicates.size());
                long reclaimable = 0L;
                for (Backup backup : duplicates) {
                    if (ThreadUtils.isInterrupted()) {
                        return;
                    }
                    long size = sizeResolver.sizeOnDisk(backup);
                    entries.add(new DuplicateBackupEntry(backup, size));
                    if (size > 0L) {
                        reclaimable += size;
                    }
                }
                mDuplicateBackupPlan.postValue(new DuplicateBackupPlan(strategy, entries, reclaimable));
            } catch (Throwable th) {
                io.github.muntashirakon.AppManager.logs.Log.w(TAG, "Duplicate backup scan failed", th);
                // Post null to signal failure — distinct from a successful empty scan, which
                // posts a non-null empty plan. The observer surfaces an error for null.
                mDuplicateBackupPlan.postValue(null);
            }
        });
    }

    @AnyThread
    public void deleteDuplicateBackups(@NonNull DuplicateBackupPlan plan) {
        postCancellableTask(() -> {
            int deleted = 0;
            int failed = 0;
            long reclaimed = 0L;
            for (DuplicateBackupEntry entry : plan.entries) {
                if (ThreadUtils.isInterrupted()) {
                    break;
                }
                try {
                    BackupItems.BackupItem item = entry.backup.getItem();
                    if (item != null && item.delete()) {
                        ++deleted;
                        if (entry.size > 0L) {
                            reclaimed += entry.size;
                        }
                        io.github.muntashirakon.AppManager.logs.Log.i(TAG, "Deleted duplicate backup "
                                + entry.backup.relativeDir + " (" + entry.size + " bytes)");
                    } else {
                        ++failed;
                        io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                                "Failed to delete duplicate backup: " + entry.backup.relativeDir);
                    }
                } catch (Throwable th) {
                    ++failed;
                    io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                            "Error deleting duplicate backup: " + entry.backup.relativeDir, th);
                }
            }
            recordDuplicateBackupCleanupHistory(plan, deleted, reclaimed);
            mDuplicateBackupDeleteResult.postValue(new CleanupResult(deleted, failed, reclaimed));
        });
    }

    /**
     * Scan external storage (and, when privileged, the root {@code /data/data}
     * stubs) for orphan per-package directories left behind by uninstalled
     * apps. Sizes are computed on the worker thread so the UI can render them
     * without re-walking.
     */
    @AnyThread
    public void scanLeftovers() {
        postCancellableTask(() -> {
            HashSet<String> installed = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(0)) {
                if (applicationInfo != null && applicationInfo.packageName != null) {
                    installed.add(applicationInfo.packageName);
                }
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            List<LeftoverScanner.Leftover> leftovers = new ArrayList<>(
                    LeftoverScanner.scan(Environment.getExternalStorageDirectory(), installed));
            if (!ThreadUtils.isInterrupted() && SelfPermissions.isSystemOrRootOrShell()) {
                // The data layer's File-based listing only succeeds when this
                // process itself can read /data/data; on a remote-privileged
                // setup it returns empty, which is the safe fallback.
                leftovers.addAll(LeftoverScanner.scanInternalDataStubs(
                        new java.io.File("/data/data"), installed));
            }
            List<LeftoverEntry> entries = new ArrayList<>(leftovers.size());
            for (LeftoverScanner.Leftover leftover : leftovers) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                entries.add(new LeftoverEntry(leftover, LeftoverScanner.sizeOnDisk(leftover.path)));
            }
            mLeftoverScanResult.postValue(entries);
        });
    }

    /**
     * Delete the selected leftover folders through the privileged
     * {@link io.github.muntashirakon.io.Path} layer (recursive), recording
     * each deletion in the app log for an audit trail. Posts a (count, bytes)
     * summary when done.
     */
    @AnyThread
    public void deleteLeftovers(@NonNull List<LeftoverEntry> entries) {
        postCancellableTask(() -> {
            int deleted = 0;
            int failed = 0;
            long reclaimed = 0L;
            for (LeftoverEntry entry : entries) {
                if (ThreadUtils.isInterrupted()) {
                    break;
                }
                try {
                    if (Paths.get(entry.leftover.path).delete()) {
                        ++deleted;
                        reclaimed += entry.size;
                        io.github.muntashirakon.AppManager.logs.Log.i(TAG, "Deleted leftover "
                                + entry.leftover.kindLabel() + " folder for "
                                + entry.leftover.packageName + " (" + entry.size + " bytes): "
                                + entry.leftover.path);
                    } else {
                        ++failed;
                        io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                                "Failed to delete leftover folder: " + entry.leftover.path);
                    }
                } catch (Throwable th) {
                    ++failed;
                    io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                            "Error deleting leftover folder: " + entry.leftover.path, th);
                }
            }
            recordLeftoverCleanupHistory(entries, deleted, reclaimed);
            mLeftoverDeleteResult.postValue(new CleanupResult(deleted, failed, reclaimed));
        });
    }

    private void recordLeftoverCleanupHistory(@NonNull List<LeftoverEntry> entries, int deleted, long reclaimed) {
        Application application = getApplication();
        String operationLabel = application.getString(R.string.detect_leftover_files);
        LeftoverCleanupHistoryItem historyItem = new LeftoverCleanupHistoryItem(
                operationLabel, entries, deleted, reclaimed);
        int failed = Math.max(0, entries.size() - deleted);
        OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_CLEANUP,
                historyItem,
                failed == 0,
                OperationJournalMetadata.forOneClickCleanup(application, operationLabel,
                        entries.size(), failed, historyItem.getTargetPreview()));
    }

    private void recordDuplicateBackupCleanupHistory(@NonNull DuplicateBackupPlan plan, int deleted, long reclaimed) {
        Application application = getApplication();
        String operationLabel = application.getString(R.string.delete_duplicate_backups);
        DuplicateBackupCleanupHistoryItem historyItem = new DuplicateBackupCleanupHistoryItem(
                operationLabel, application.getText(R.string.base_backup), plan.strategy, plan.entries,
                deleted, reclaimed);
        int failed = Math.max(0, plan.entries.size() - deleted);
        OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_CLEANUP,
                historyItem,
                failed == 0,
                OperationJournalMetadata.forOneClickCleanup(application, operationLabel,
                        plan.entries.size(), failed, historyItem.getTargetPreview()));
    }

    @AnyThread
    public void blockTrackers(boolean systemApps) {
        postCancellableTask(() -> {
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
            boolean canChangeComponentState = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
            if (!canChangeComponentState) {
                // Since there's no permission, it can only change its own component states
                try {
                    PackageInfo packageInfo = getApplication().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, flags);
                    if (systemApps || !ApplicationInfoCompat.isSystemApp(packageInfo.applicationInfo)) {
                        ItemCount trackerCount = getTrackerCountForApp(packageInfo);
                        if (trackerCount.count > 0) {
                            mTrackerCount.postValue(Collections.singletonList(trackerCount));
                            return;
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                mTrackerCount.postValue(Collections.emptyList());
                return;
            }
            boolean crossUserPermission = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS)
                    || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL);
            boolean isShell = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Users.getSelfOrRemoteUid() == Ops.SHELL_UID;
            List<ItemCount> trackerCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            for (PackageInfo packageInfo : PackageUtils.getAllPackages(flags, !crossUserPermission)) {
                if (packageNames.contains(packageInfo.packageName)) {
                    continue;
                }
                packageNames.add(packageInfo.packageName);
                if (ThreadUtils.isInterrupted()) return;
                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                if (isShell && !ApplicationInfoCompat.isTestOnly(applicationInfo)) {
                    continue;
                }
                if (!systemApps && ApplicationInfoCompat.isSystemApp(applicationInfo)) {
                    continue;
                }
                ItemCount trackerCount = getTrackerCountForApp(packageInfo);
                if (trackerCount.count > 0) {
                    trackerCounts.add(trackerCount);
                }
            }
            mTrackerCount.postValue(trackerCounts);
        });
    }

    @AnyThread
    public void blockComponents(boolean systemApps, @NonNull String[] signatures) {
        if (signatures.length == 0) return;
        postCancellableTask(() -> {
            boolean canChangeComponentState = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
            if (!canChangeComponentState) {
                // Since there's no permission, it can only change its own component states
                ApplicationInfo applicationInfo = getApplication().getApplicationInfo();
                if (systemApps || !ApplicationInfoCompat.isSystemApp(applicationInfo)) {
                    ItemCount componentCount = new ItemCount();
                    componentCount.packageName = applicationInfo.packageName;
                    componentCount.packageLabel = applicationInfo.loadLabel(mPm).toString();
                    componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName,
                            UserHandleHidden.myUserId(), signatures).size();
                    if (componentCount.count > 0) {
                        mComponentCount.postValue(new Pair<>(Collections.singletonList(componentCount), signatures));
                        return;
                    }
                }
                mComponentCount.postValue(new Pair<>(Collections.emptyList(), signatures));
                return;
            }
            boolean crossUserPermission = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS)
                    || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL);
            boolean isShell = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Users.getSelfOrRemoteUid() == Ops.SHELL_UID;
            List<ItemCount> componentCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, !crossUserPermission)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                packageNames.add(applicationInfo.packageName);
                if (ThreadUtils.isInterrupted()) return;
                if (isShell && !ApplicationInfoCompat.isTestOnly(applicationInfo))
                    continue;
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                ItemCount componentCount = new ItemCount();
                componentCount.packageName = applicationInfo.packageName;
                componentCount.packageLabel = applicationInfo.loadLabel(mPm).toString();
                componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName,
                        UserHandleHidden.myUserId(), signatures).size();
                if (componentCount.count > 0) componentCounts.add(componentCount);
            }
            mComponentCount.postValue(new Pair<>(componentCounts, signatures));
        });
    }

    @AnyThread
    public void setAppOps(int[] appOpList, int mode, boolean systemApps) {
        postCancellableTask(() -> {
            Pair<int[], Integer> appOpsModePair = new Pair<>(appOpList, mode);
            List<AppOpCount> appOpCounts = new ArrayList<>();
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                packageNames.add(applicationInfo.packageName);
                if (ThreadUtils.isInterrupted()) return;
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                AppOpCount appOpCount = new AppOpCount();
                appOpCount.packageName = applicationInfo.packageName;
                appOpCount.packageLabel = applicationInfo.loadLabel(mPm).toString();
                appOpCount.appOps = PackageUtils.getFilteredAppOps(applicationInfo.packageName,
                        UserHandleHidden.myUserId(), appOpList, mode);
                appOpCount.count = appOpCount.appOps.size();
                if (appOpCount.count > 0) appOpCounts.add(appOpCount);
            }
            mAppOpsCount.postValue(new Pair<>(appOpCounts, appOpsModePair));
        });
    }

    public void clearData() {
        postCancellableTask(() -> {
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                if (ApplicationInfoCompat.isOnlyDataInstalled(applicationInfo)) {
                    packageNames.add(applicationInfo.packageName);
                }
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            mClearDataCandidates.postValue(new ArrayList<>(packageNames));
        });
    }

    @AnyThread
    public void trimCaches() {
        postCancellableTask(() -> {
            long size = 1024L * 1024L * 1024L * 1024L;  // 1 TB
            boolean success = true;
            for (String volumeUuid : StorageUtils.getTrimCacheVolumeUuids(
                    StorageUtils.getWritableStorageVolumeUuids(getApplication(), UserHandleHidden.myUserId()))) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                try {
                    PackageManagerCompat.freeStorageAndNotify(volumeUuid, size,
                            StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to trim caches for volume " + (volumeUuid == null ? "internal" : volumeUuid), e);
                    success = false;
                }
            }
            mTrimCachesResult.postValue(success);
        });
    }

    public void listAppsInstalledByAmForDexOpt() {
        postCancellableTask(() -> {
            HashSet<String> packageNames = new HashSet<>();
            for (ApplicationInfo applicationInfo : PackageUtils.getAllApplications(0)) {
                if (packageNames.contains(applicationInfo.packageName)) {
                    continue;
                }
                packageNames.add(applicationInfo.packageName);
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
            }
            mAppsInstalledByAmForDexOpt.postValue(packageNames.toArray(new String[0]));
        });
    }

    @AnyThread
    void cancelCurrentTask() {
        setCurrentTask(null);
    }

    @AnyThread
    private void postCancellableTask(@NonNull Runnable runnable) {
        setCurrentTask(ThreadUtils.postOnBackgroundThread(runnable));
    }

    @AnyThread
    @VisibleForTesting
    void setCurrentTask(@Nullable Future<?> future) {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = future;
    }

    @VisibleForTesting
    boolean hasCurrentTask() {
        return mFutureResult != null;
    }

    @NonNull
    private ItemCount getTrackerCountForApp(@NonNull PackageInfo packageInfo) {
        ItemCount trackerCount = new ItemCount();
        trackerCount.packageName = packageInfo.packageName;
        trackerCount.packageLabel = packageInfo.applicationInfo.loadLabel(mPm).toString();
        trackerCount.count = ComponentUtils.getTrackerComponentsCountForPackage(packageInfo);
        return trackerCount;
    }
}
