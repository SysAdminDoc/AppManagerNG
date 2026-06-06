// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptimizer;
import io.github.muntashirakon.AppManager.apk.installer.AppArchiveManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.backup.BackupExtrasCoverage;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.DefaultAppRoleBackupHelper;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.convert.ConvertUtils;
import io.github.muntashirakon.AppManager.backup.convert.Converter;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupImportOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchDexOptOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.debloat.RootlessDebloat;
import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@WorkerThread
public class BatchOpsManager {
    public static final String TAG = "BatchOpsManager";

    @IntDef(value = {
            OP_NONE,
            OP_ADVANCED_FREEZE,
            OP_ARCHIVE,
            OP_BACKUP_APK,
            OP_BACKUP,
            OP_BLOCK_COMPONENTS,
            OP_BLOCK_TRACKERS,
            OP_CLEAR_CACHE,
            OP_CLEAR_DATA,
            OP_DELETE_BACKUP,
            OP_DEXOPT,
            OP_DISABLE_BACKGROUND,
            OP_EXPORT_RULES,
            OP_FORCE_STOP,
            OP_FREEZE,
            OP_GRANT_PERMISSIONS,
            OP_IMPORT_BACKUPS,
            OP_INSTALL_EXISTING,
            OP_NET_POLICY,
            OP_REVOKE_PERMISSIONS,
            OP_RESTORE_BACKUP,
            OP_SET_APP_OPS,
            OP_UNBLOCK_COMPONENTS,
            OP_UNBLOCK_TRACKERS,
            OP_UNINSTALL,
            OP_UNARCHIVE,
            OP_UNFREEZE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OpType {
    }

    public static final int OP_NONE = -1;
    public static final int OP_BACKUP_APK = 0;
    public static final int OP_BACKUP = 1;
    public static final int OP_BLOCK_TRACKERS = 2;
    public static final int OP_CLEAR_DATA = 3;
    public static final int OP_DELETE_BACKUP = 4;
    public static final int OP_FREEZE = 5;
    public static final int OP_DISABLE_BACKGROUND = 6;
    public static final int OP_EXPORT_RULES = 7;
    public static final int OP_FORCE_STOP = 8;
    public static final int OP_RESTORE_BACKUP = 9;
    public static final int OP_UNBLOCK_TRACKERS = 10;
    public static final int OP_UNINSTALL = 11;
    public static final int OP_BLOCK_COMPONENTS = 12;
    public static final int OP_SET_APP_OPS = 13;
    public static final int OP_UNFREEZE = 14;
    public static final int OP_UNBLOCK_COMPONENTS = 15;
    public static final int OP_CLEAR_CACHE = 16;
    public static final int OP_GRANT_PERMISSIONS = 17;
    public static final int OP_REVOKE_PERMISSIONS = 18;
    public static final int OP_IMPORT_BACKUPS = 19;
    public static final int OP_NET_POLICY = 20;
    public static final int OP_DEXOPT = 21;
    public static final int OP_ADVANCED_FREEZE = 22;
    public static final int OP_ARCHIVE = 23;
    public static final int OP_UNARCHIVE = 24;
    public static final int OP_INSTALL_EXISTING = 25;

    private static final String GROUP_ID = BuildConfig.APPLICATION_ID + ".notification_group.BATCH_OPS";

    public static class BatchOpsInfo {
        @NonNull
        public static BatchOpsInfo fromQueue(@NonNull BatchQueueItem queueItem) {
            return new BatchOpsInfo(queueItem.getOp(), queueItem.getPackages(),
                    queueItem.getUsers(), queueItem.getOptions());
        }

        @NonNull
        public static BatchOpsInfo fromUserPackagePair(@OpType int op,
                                                       @NonNull List<UserPackagePair> pairs,
                                                       @Nullable IBatchOpOptions options) {
            Result result = new Result(pairs);
            return new BatchOpsInfo(op, result.getFailedPackages(), result.getAssociatedUsers(),
                    options);
        }

        @NonNull
        public static BatchOpsInfo getInstance(@OpType int op,
                                               @NonNull List<String> packages,
                                               @NonNull List<Integer> users,
                                               @Nullable IBatchOpOptions options) {
            return new BatchOpsInfo(op, packages, users, options);
        }

        @OpType
        public final int op;
        @NonNull
        public final List<String> packages;
        @NonNull
        public final List<Integer> users;
        @Nullable
        public final IBatchOpOptions options;

        private BatchOpsInfo(
                @OpType int op,
                @NonNull List<String> packages,
                @NonNull List<Integer> users,
                @Nullable IBatchOpOptions options) {
            this.op = op;
            this.packages = Collections.unmodifiableList(packages);
            this.users = Collections.unmodifiableList(users);
            this.options = options;

            assert packages.size() == users.size();
        }

        public int size() {
            return packages.size();
        }

        @NonNull
        public UserPackagePair getPair(int index) {
            return new UserPackagePair(packages.get(index), users.get(index));
        }

        public List<UserPackagePair> getPairList() {
            List<UserPackagePair> userPackagePairs = new ArrayList<>(packages.size());
            int size = size();
            for (int i = 0; i < size; ++i) {
                userPackagePairs.add(getPair(i));
            }
            return Collections.unmodifiableList(userPackagePairs);
        }
    }

    @Nullable
    public Logger mLogger;
    public final boolean mCustomLogger;

    @Nullable
    private ProgressHandler mProgressHandler;
    @Nullable
    private TargetProgressListener mTargetProgressListener;

    public interface TargetProgressListener {
        void onTargetFinished(@NonNull UserPackagePair pair, boolean failed);
    }

    public BatchOpsManager() {
        mCustomLogger = false;
        mLogger = ExUtils.exceptionAsNull(BatchOpsLogger::new);
    }

    public BatchOpsManager(@Nullable Logger logger) {
        mLogger = logger;
        mCustomLogger = true;
    }

    public Result performOp(@NonNull BatchOpsInfo info, @Nullable ProgressHandler progressHandler) {
        return performOp(info, progressHandler, null);
    }

    public Result performOp(@NonNull BatchOpsInfo info,
                            @Nullable ProgressHandler progressHandler,
                            @Nullable TargetProgressListener targetProgressListener) {
        mProgressHandler = progressHandler;
        mTargetProgressListener = targetProgressListener;
        return performOp(info);
    }

    @CheckResult
    @NonNull
    private Result performOp(@NonNull BatchOpsInfo info) {
        switch (info.op) {
            case OP_ADVANCED_FREEZE:
                return opFreeze(info);
            case OP_ARCHIVE:
                return opArchiveUnarchive(info, true);
            case OP_BACKUP_APK:
                return opBackupApk(info);
            case OP_BACKUP:
                return opBackupRestore(info, BackupRestoreDialogFragment.MODE_BACKUP);
            case OP_BLOCK_TRACKERS:
                return opBlockTrackers(info);
            case OP_CLEAR_DATA:
                return opClearData(info);
            case OP_DELETE_BACKUP:
                return opBackupRestore(info, BackupRestoreDialogFragment.MODE_DELETE);
            case OP_FREEZE:
                return opFreezeUnfreeze(info, true);
            case OP_DISABLE_BACKGROUND:
                return opDisableBackground(info);
            case OP_UNFREEZE:
                return opFreezeUnfreeze(info, false);
            case OP_EXPORT_RULES:
                break;  // Done in the main activity
            case OP_FORCE_STOP:
                return opForceStop(info);
            case OP_RESTORE_BACKUP:
                return opBackupRestore(info, BackupRestoreDialogFragment.MODE_RESTORE);
            case OP_UNINSTALL:
                return opUninstall(info);
            case OP_UNARCHIVE:
                return opArchiveUnarchive(info, false);
            case OP_UNBLOCK_TRACKERS:
                return opUnblockTrackers(info);
            case OP_BLOCK_COMPONENTS:
                return opBlockComponents(info);
            case OP_SET_APP_OPS:
                return opSetAppOps(info);
            case OP_UNBLOCK_COMPONENTS:
                return opUnblockComponents(info);
            case OP_CLEAR_CACHE:
                return opClearCache(info);
            case OP_GRANT_PERMISSIONS:
                return opGrantOrRevokePermissions(info, true);
            case OP_REVOKE_PERMISSIONS:
                return opGrantOrRevokePermissions(info, false);
            case OP_IMPORT_BACKUPS:
                return opImportBackups(info);
            case OP_INSTALL_EXISTING:
                return opInstallExisting(info);
            case OP_NET_POLICY:
                return opNetPolicy(info);
            case OP_DEXOPT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return opPerformDexOpt(info);
                }
                return new Result(Collections.emptyList(), false);
            case OP_NONE:
                break;
        }
        return new Result(info.getPairList());
    }

    @NonNull
    private Result opArchiveUnarchive(@NonNull BatchOpsInfo info, boolean archive) {
        if (!AppArchiveManager.isSupported(Build.VERSION.SDK_INT)) {
            return new Result(info.getPairList(), false);
        }
        List<UserPackagePair> failedPackages = new ArrayList<>();
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();
        int currentUserId = UserHandleHidden.myUserId();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            UserPackagePair pair = info.getPair(i);
            boolean failed = false;
            try {
                ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(pair.getPackageName(),
                        PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                | PackageManagerCompat.MATCH_DISABLED_COMPONENTS,
                        pair.getUserId());
                boolean isSystemOrUpdatedSystemApp = (applicationInfo.flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                boolean canArchive = AppArchiveManager.canShowArchiveAction(Build.VERSION.SDK_INT, pair.getUserId(),
                        currentUserId, (applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0,
                        ApplicationInfoCompat.isStaticSharedLibrary(applicationInfo), isSystemOrUpdatedSystemApp,
                        pair.getPackageName());
                if (!canArchive) {
                    throw new PackageManager.NameNotFoundException("App archiving is unavailable for " + pair);
                }
                CharSequence appLabel = PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserId());
                AppArchiveManager.request(context, pair.getPackageName(), appLabel,
                        archive ? AppArchiveManager.OP_ARCHIVE : AppArchiveManager.OP_UNARCHIVE);
            } catch (Throwable e) {
                failed = true;
                log("====> op=APP_ARCHIVE, pkg=" + pair + ", archive = " + archive, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    public void conclude() {
        if (!mCustomLogger && mLogger != null) {
            mLogger.close();
        }
    }

    @NonNull
    private Result opBackupApk(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        int max = info.size();
        // Initial progress
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        Context context = ContextUtils.getContext();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            pair = info.getPair(i);
            updateProgress(lastProgress, i + 1);
            // Do operation
            boolean failed = false;
            try {
                ApkUtils.backupApk(context, pair.getPackageName(), pair.getUserId());
            } catch (Exception e) {
                failed = true;
                failedPackages.add(pair);
                log("====> op=BACKUP_APK, pkg=" + pair, e);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opBackupRestore(@NonNull BatchOpsInfo info, @BackupRestoreDialogFragment.ActionMode int mode) {
        switch (mode) {
            case BackupRestoreDialogFragment.MODE_BACKUP:
                return backup(info);
            case BackupRestoreDialogFragment.MODE_RESTORE:
                return restoreBackups(info);
            case BackupRestoreDialogFragment.MODE_DELETE:
                return deleteBackups(info);
        }
        return new Result(info.getPairList());
    }

    @NonNull
    private Result backup(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = Collections.synchronizedList(new ArrayList<>());
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();
        CharSequence operationName = context.getString(R.string.backup_restore);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        AtomicInteger counter = new AtomicInteger(0);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        try {
            BatchBackupOptions options = Objects.requireNonNull((BatchBackupOptions) info.options);
            int max = info.size();
            BackupManager backupManager = new BackupManager();
            for (int i = 0; i < max; ++i) {
                UserPackagePair pair = info.getPair(i);
                executor.submit(() -> {
                    synchronized (counter) {
                        counter.set(counter.get() + 1);
                        updateProgress(lastProgress, counter.get());
                    }
                    CharSequence appLabel = PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserId());
                    CharSequence title = context.getString(R.string.backing_up_app, appLabel);
                    ProgressHandler subProgressHandler = newSubProgress(operationName, title);
                    boolean failed = false;
                    try {
                        backupManager.backup(options.getBackupOpOptions(pair.getPackageName(), pair.getUserId()), subProgressHandler);
                    } catch (BackupException e) {
                        failed = true;
                        log("====> op=BACKUP_RESTORE, mode=BACKUP pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                    recordTargetFinished(pair, failed);
                    if (subProgressHandler != null) {
                        ThreadUtils.postOnMainThread(() -> subProgressHandler.onResult(null));
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=BACKUP_RESTORE, mode=BACKUP", th);
        }
        executor.awaitCompletion();
        return new Result(failedPackages);
    }

    @NonNull
    private Result restoreBackups(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = Collections.synchronizedList(new ArrayList<>());
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();
        CharSequence operationName = context.getString(R.string.backup_restore);
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        AtomicBoolean requiresRestart = new AtomicBoolean();
        List<DefaultAppRoleBackupHelper.RoleRebindRequest> pendingDefaultRoleRebindRequests =
                Collections.synchronizedList(new ArrayList<>());
        List<String> restoreExtraWarnings = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger count = new AtomicInteger(0);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        try {
            BatchBackupOptions options = Objects.requireNonNull((BatchBackupOptions) info.options);
            int max = info.size();
            for (int i = 0; i < max; ++i) {
                UserPackagePair pair = info.getPair(i);
                executor.submit(() -> {
                    synchronized (count) {
                        count.set(count.get() + 1);
                        updateProgress(lastProgress, count.get());
                    }
                    CharSequence appLabel = PackageUtils.getPackageLabel(pm, pair.getPackageName(), pair.getUserId());
                    CharSequence title = context.getString(R.string.restoring_app, appLabel);
                    ProgressHandler subProgressHandler = newSubProgress(operationName, title);
                    boolean failed = false;
                    try {
                        BackupManager backupManager = new BackupManager();
                        backupManager.restore(options.getRestoreOpOptions(pair.getPackageName(), pair.getUserId()), subProgressHandler);
                        requiresRestart.set(requiresRestart.get() | backupManager.requiresRestart());
                        pendingDefaultRoleRebindRequests.addAll(backupManager.getPendingDefaultRoleRebindRequests());
                        restoreExtraWarnings.addAll(backupManager.getRestoreExtraWarnings());
                    } catch (Throwable e) {
                        failed = true;
                        log("====> op=BACKUP_RESTORE, mode=RESTORE pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                    recordTargetFinished(pair, failed);
                    if (subProgressHandler != null) {
                        ThreadUtils.postOnMainThread(() -> subProgressHandler.onResult(null));
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=BACKUP_RESTORE, mode=RESTORE", th);
        }
        executor.awaitCompletion();
        Result result = new Result(failedPackages);
        result.setRequiresRestart(requiresRestart.get());
        result.addPendingDefaultRoleRebindRequests(pendingDefaultRoleRebindRequests);
        result.addWarnings(restoreExtraWarnings);
        return result;
    }

    @NonNull
    private Result deleteBackups(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        try {
            BatchBackupOptions options = Objects.requireNonNull((BatchBackupOptions) info.options);
            int max = info.size();
            BackupManager backupManager = new BackupManager();
            UserPackagePair pair;
            for (int i = 0; i < max; ++i) {
                updateProgress(lastProgress, i + 1);
                pair = info.getPair(i);
                boolean failed = false;
                try {
                    backupManager.deleteBackup(options.getDeleteOpOptions(pair.getPackageName(), pair.getUserId()));
                } catch (BackupException e) {
                    failed = true;
                    log("====> op=BACKUP_RESTORE, mode=DELETE pkg=" + pair, e);
                    failedPackages.add(pair);
                }
                recordTargetFinished(pair, failed);
            }
        } catch (Throwable th) {
            log("====> op=BACKUP_RESTORE, mode=DELETE", th);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opImportBackups(@NonNull BatchOpsInfo info) {
        final List<UserPackagePair> failedPkgList = Collections.synchronizedList(new ArrayList<>());
        MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
        try {
            int userId = UserHandleHidden.myUserId();
            BatchBackupImportOptions options = (BatchBackupImportOptions) Objects.requireNonNull(info.options);
            Uri uri = options.getDirectory();
            Path backupPath = Paths.get(uri);
            if (!backupPath.isDirectory()) {
                log("====> op=IMPORT_BACKUP, Not a directory.");
                return new Result(Collections.emptyList(), false);
            }
            Path[] files = ConvertUtils.getRelevantImportFiles(backupPath, options.getImportType());
            fixProgress(files.length);
            float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
            AtomicInteger i = new AtomicInteger(0);
            for (Path file : files) {
                executor.submit(() -> {
                    synchronized (i) {
                        i.set(i.get() + 1);
                        updateProgress(lastProgress, i.get());
                    }
                    Converter converter = ConvertUtils.getConversionUtil(options.getImportType(), file);
                    try {
                        converter.convert();
                        if (options.isRemoveImportedDirectory()) {
                            // Since the conversion was successful, remove the files for it.
                            converter.cleanup();
                        }
                    } catch (BackupException e) {
                        log("====> op=IMPORT_BACKUP, pkg=" + converter.getPackageName(), e);
                        failedPkgList.add(new UserPackagePair(converter.getPackageName(), userId));
                    }
                });
            }
        } catch (Throwable th) {
            log("====> op=IMPORT_BACKUP", th);
        }
        executor.awaitCompletion();
        return new Result(failedPkgList);
    }

    @NonNull
    private Result opBlockComponents(@NonNull BatchOpsInfo info) {
        BatchComponentOptions options = (BatchComponentOptions) Objects.requireNonNull(info.options);
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                ComponentUtils.blockFilteredComponents(pair, options.getSignatures());
            } catch (Exception e) {
                failed = true;
                log("====> op=BLOCK_COMPONENTS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opBlockTrackers(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity intensity =
                io.github.muntashirakon.AppManager.settings.Prefs.Privacy.getTrackerBlockingIntensity();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                ComponentUtils.blockTrackingComponents(pair, intensity);
            } catch (Exception e) {
                failed = true;
                log("====> op=BLOCK_TRACKERS, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opClearCache(@NonNull BatchOpsInfo info) {
        if (info.size() == 0) {
            // No packages supplied means trim all caches
            return opTrimCaches();
        }
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                PackageManagerCompat.deleteApplicationCacheFilesAsUser(pair);
            } catch (Exception e) {
                failed = true;
                log("====> op=CLEAR_CACHE, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opTrimCaches() {
        long size = 1024L * 1024L * 1024L * 1024L;  // 1 TB
        boolean isSuccessful = true;
        for (String volumeUuid : StorageUtils.getTrimCacheVolumeUuids(
                StorageUtils.getWritableStorageVolumeUuids(ContextUtils.getContext(), UserHandleHidden.myUserId()))) {
            try {
                PackageManagerCompat.freeStorageAndNotify(volumeUuid, size,
                        StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED);
            } catch (Throwable e) {
                log("====> op=TRIM_CACHES volume=" + (volumeUuid == null ? "internal" : volumeUuid), e);
                isSuccessful = false;
            }
        }
        return new Result(Collections.emptyList(), isSuccessful);
    }

    @NonNull
    private Result opClearData(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        boolean hadShizukuPermission = ShizukuBridge.hasPermission();
        boolean reportedShizukuPermissionRevoked = false;
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                PackageManagerCompat.clearApplicationUserData(pair);
                if (!reportedShizukuPermissionRevoked
                        && ShizukuBridge.wasPermissionRevokedAfterClearData(hadShizukuPermission)) {
                    log("====> op=CLEAR_DATA, Shizuku permission was revoked after clearing data for " + pair
                            + ". Open Settings > Mode of operation to re-authorize AppManagerNG.");
                    reportedShizukuPermissionRevoked = true;
                }
            } catch (Exception e) {
                failed = true;
                log("====> op=CLEAR_DATA, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opFreeze(@NonNull BatchOpsInfo info) {
        BatchFreezeOptions options = (BatchFreezeOptions) Objects.requireNonNull(info.options);
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            int type;
            if (options.isPreferCustom()) {
                type = Optional.ofNullable(FreezeUtils.loadFreezeMethod(pair.getPackageName()))
                        .orElse(options.getType());
            } else type = options.getType();
            boolean failed = false;
            try {
                FreezeUtils.freeze(pair.getPackageName(), pair.getUserId(), type);
            } catch (Throwable e) {
                failed = true;
                log("====> op=ADVANCED_FREEZE, pkg=" + pair + ", type = " + type, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opFreezeUnfreeze(@NonNull BatchOpsInfo info, boolean freeze) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                if (freeze) {
                    FreezeUtils.freeze(pair.getPackageName(), pair.getUserId());
                } else {
                    FreezeUtils.unfreeze(pair.getPackageName(), pair.getUserId());
                }
            } catch (Throwable e) {
                failed = true;
                log("====> op=APP_FREEZE, pkg=" + pair + ", freeze = " + freeze, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opDisableBackground(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            int uid = PackageUtils.getAppUid(pair);
            if (uid == -1) {
                failedPackages.add(pair);
                recordTargetFinished(pair, true);
                continue;
            }
            boolean failed = false;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appOpsManager.setMode(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appOpsManager.setMode(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, uid,
                            pair.getPackageName(), AppOpsManager.MODE_IGNORED);
                }
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        cb.setAppOp(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, AppOpsManager.MODE_IGNORED);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cb.setAppOp(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, AppOpsManager.MODE_IGNORED);
                    }
                }
            } catch (Throwable e) {
                failed = true;
                log("====> op=DISABLE_BACKGROUND, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opGrantOrRevokePermissions(@NonNull BatchOpsInfo info, boolean isGrant) {
        BatchPermissionOptions options = (BatchPermissionOptions) Objects.requireNonNull(info.options);
        String[] permissions = options.getPermissions();
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        if (permissions.length == 1 && permissions[0].equals("*")) {
            // Wildcard detected
            for (int i = 0; i < max; ++i) {
                updateProgress(lastProgress, i + 1);
                pair = info.getPair(i);
                boolean failed = false;
                try {
                    permissions = PackageUtils.getPermissionsForPackage(pair.getPackageName(), pair.getUserId());
                    if (permissions == null) {
                        recordTargetFinished(pair, false);
                        continue;
                    }
                    for (String permission : permissions) {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserId());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserId());
                        }
                    }
                } catch (Throwable e) {
                    failed = true;
                    log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                    failedPackages.add(pair);
                }
                recordTargetFinished(pair, failed);
            }
        } else {
            for (int i = 0; i < max; ++i) {
                updateProgress(lastProgress, i + 1);
                pair = info.getPair(i);
                boolean failed = false;
                for (String permission : permissions) {
                    try {
                        if (isGrant) {
                            PermissionCompat.grantPermission(pair.getPackageName(), permission, pair.getUserId());
                        } else {
                            PermissionCompat.revokePermission(pair.getPackageName(), permission, pair.getUserId());
                        }
                    } catch (Throwable e) {
                        failed = true;
                        log("====> op=GRANT_OR_REVOKE_PERMISSIONS, pkg=" + pair, e);
                        failedPackages.add(pair);
                    }
                }
                recordTargetFinished(pair, failed);
            }
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opForceStop(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                PackageManagerCompat.forceStopPackage(pair.getPackageName(), pair.getUserId());
            } catch (Throwable e) {
                failed = true;
                log("====> op=FORCE_STOP, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opNetPolicy(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        BatchNetPolicyOptions options = (BatchNetPolicyOptions) Objects.requireNonNull(info.options);
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                int uid = PackageUtils.getAppUid(pair);
                NetworkPolicyManagerCompat.setUidPolicy(uid, options.getPolicies());
            } catch (Throwable e) {
                failed = true;
                log("====> op=NET_POLICY, pkg=" + pair, e);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opSetAppOps(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        BatchAppOpsOptions options = (BatchAppOpsOptions) Objects.requireNonNull(info.options);
        int[] appOps = options.getAppOps();
        int max = info.size();
        UserPackagePair pair;
        if (appOps.length == 1 && appOps[0] == AppOpsManagerCompat.OP_NONE) {
            // Wildcard detected
            for (int i = 0; i < max; ++i) {
                updateProgress(lastProgress, i + 1);
                pair = info.getPair(i);
                boolean failed = false;
                try {
                    List<Integer> appOpList = new ArrayList<>();
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(pair.getPackageName(),
                            PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, pair.getUserId());
                    List<AppOpsManagerCompat.OpEntry> entries = AppOpsManagerCompat.getConfiguredOpsForPackage(
                            appOpsManager, applicationInfo.packageName, applicationInfo.uid);
                    for (AppOpsManagerCompat.OpEntry entry : entries) {
                        appOpList.add(entry.getOp());
                    }
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsManager, pair,
                            ArrayUtils.convertToIntArray(appOpList), options.getMode());
                } catch (Exception e) {
                    failed = true;
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
                recordTargetFinished(pair, failed);
            }
        } else {
            for (int i = 0; i < max; ++i) {
                updateProgress(lastProgress, i + 1);
                pair = info.getPair(i);
                boolean failed = false;
                try {
                    ExternalComponentsImporter.setModeToFilteredAppOps(appOpsManager, pair, appOps, options.getMode());
                } catch (RemoteException e) {
                    failed = true;
                    log("====> op=SET_APP_OPS, pkg=" + pair, e);
                    failedPkgList.add(pair);
                }
                recordTargetFinished(pair, failed);
            }
        }
        return new Result(failedPkgList);
    }

    @NonNull
    private Result opUnblockComponents(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        BatchComponentOptions options = (BatchComponentOptions) Objects.requireNonNull(info.options);
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                ComponentUtils.unblockFilteredComponents(pair, options.getSignatures());
            } catch (Throwable th) {
                failed = true;
                log("====> op=UNBLOCK_COMPONENTS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opUnblockTrackers(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean failed = false;
            try {
                ComponentUtils.unblockTrackingComponents(pair);
            } catch (Throwable th) {
                failed = true;
                log("====> op=UNBLOCK_TRACKERS, pkg=" + pair, th);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    @NonNull
    private Result opUninstall(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        AccessibilityMultiplexer accessibility = AccessibilityMultiplexer.getInstance();
        boolean canUsePmUninstall = RootlessDebloat.canUsePmUninstall();
        if (!canUsePmUninstall && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            // Try to use accessibility in unprivileged mode
            accessibility.enableUninstall(true);
        }
        int max = info.size();
        UserPackagePair pair;
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            pair = info.getPair(i);
            boolean uninstalled;
            if (canUsePmUninstall) {
                uninstalled = RootlessDebloat.uninstallForUser(pair, false);
            } else {
                PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                uninstalled = installer.uninstall(pair.getPackageName(), pair.getUserId(), false);
            }
            if (!uninstalled) {
                log("====> op=UNINSTALL, pkg=" + pair);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, !uninstalled);
        }
        accessibility.enableUninstall(false);
        return new Result(failedPackages);
    }

    @NonNull
    private Result opInstallExisting(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int max = info.size();
        for (int i = 0; i < max; ++i) {
            updateProgress(lastProgress, i + 1);
            UserPackagePair pair = info.getPair(i);
            boolean installed;
            try {
                PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                installed = installer.installExisting(pair.getPackageName(), pair.getUserId());
            } catch (Throwable th) {
                installed = false;
                log("====> op=INSTALL_EXISTING, pkg=" + pair, th);
            }
            if (!installed) {
                log("====> op=INSTALL_EXISTING, pkg=" + pair);
                failedPackages.add(pair);
            }
            recordTargetFinished(pair, !installed);
        }
        return new Result(failedPackages);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @NonNull
    private Result opPerformDexOpt(@NonNull BatchOpsInfo info) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        IPackageManager pm = PackageManagerCompat.getPackageManager();
        DexOptOptions rawOptions = ((BatchDexOptOptions) Objects.requireNonNull(info.options)).getDexOptOptions();
        DexOptOptions.SanitizationResult sanitizationResult = rawOptions.sanitizeForExecution(SelfPermissions.isSystemOrRoot());
        DexOptOptions options = sanitizationResult.options;
        if (sanitizationResult.hasSkippedRootOnlyOptions()) {
            log("====> op=DEXOPT, skipped-root-only-options="
                    + sanitizationResult.getSkippedRootOnlyOptionsSummary());
        }
        if (info.size() > 0) {
            // Override options.packages with this list
            Set<String> packages = new HashSet<>(info.size());
            packages.addAll(info.packages);
            options.packages = packages.toArray(new String[0]);
        } else if (options.packages == null) {
            // Include all packages
            try {
                options.packages = pm.getAllPackages().toArray(new String[0]);
            } catch (RemoteException e) {
                log("====> op=DEXOPT", e);
                return new Result(failedPackages, false);
            }
        }
        fixProgress(options.packages.length);
        float lastProgress = mProgressHandler != null ? mProgressHandler.getLastProgress() : 0;
        int i = 0;
        for (String packageName : options.packages) {
            updateProgress(lastProgress, ++i);
            UserPackagePair pair = new UserPackagePair(packageName, 0);
            if (packageName.equals(BuildConfig.APPLICATION_ID)) {
                // Ignore App Manager
                recordTargetFinished(pair, false);
                continue;
            }
            boolean failed = false;
            DexOptimizer dexOptimizer = new DexOptimizer(pm, packageName);
            if (options.compilerFiler != null) {
                boolean result = true;
                if (options.clearProfileData) {
                    result &= dexOptimizer.clearApplicationProfileData();
                }
                result &= dexOptimizer.performDexOptMode(options.checkProfiles, options.compilerFiler,
                        options.forceCompilation, options.bootComplete, null);
                if (!result) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=dexopt-mode", dexOptimizer.getLastError());
                    failed = true;
                    failedPackages.add(pair);
                    recordTargetFinished(pair, true);
                    continue;
                }
            }
            if (options.compileLayouts && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                boolean result = true;
                if (options.clearProfileData) {
                    result &= dexOptimizer.clearApplicationProfileData();
                }
                result &= dexOptimizer.compileLayouts();
                if (!result) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=compile-layouts", dexOptimizer.getLastError());
                    failed = true;
                    failedPackages.add(pair);
                    recordTargetFinished(pair, true);
                    continue;
                }
            }
            if (options.forceDexOpt) {
                if (!dexOptimizer.forceDexOpt()) {
                    log("====> op=DEXOPT, pkg=" + packageName + ", failed=force-dexopt", dexOptimizer.getLastError());
                    failed = true;
                    failedPackages.add(pair);
                }
            }
            recordTargetFinished(pair, failed);
        }
        return new Result(failedPackages);
    }

    private void log(@Nullable String message, @Nullable Throwable th) {
        if (mLogger != null) {
            mLogger.println(message, th);
        }
    }

    private void log(@Nullable String message) {
        if (mLogger != null) {
            mLogger.println(message);
        }
    }

    private void recordTargetFinished(@NonNull UserPackagePair pair, boolean failed) {
        if (mTargetProgressListener == null) {
            return;
        }
        try {
            mTargetProgressListener.onTargetFinished(pair, failed);
        } catch (Throwable th) {
            log("====> op=TARGET_PROGRESS, pkg=" + pair, th);
        }
    }

    private void updateProgress(float last, int current) {
        if (mProgressHandler == null) {
            return;
        }
        // Current progress = last progress + current
        mProgressHandler.postUpdate(last + current);
    }

    private void fixProgress(int appendMax) {
        if (mProgressHandler == null) {
            return;
        }
        int max = Math.max(mProgressHandler.getLastMax(), 0) + appendMax;
        float current = mProgressHandler.getLastProgress();
        mProgressHandler.postUpdate(max, current);
    }

    @Nullable
    private ProgressHandler newSubProgress(@Nullable CharSequence operationName, @Nullable CharSequence title) {
        if (mProgressHandler == null) {
            return null;
        }
        Object message = mProgressHandler.getLastMessage();
        if (message == null) {
            return null;
        }
        ProgressHandler p = mProgressHandler.newSubProgressHandler();
        if (p instanceof NotificationProgressHandler) {
            NotificationInfo parentNotificationInfo = (NotificationInfo) message;
            NotificationInfo notificationInfo = new NotificationInfo(parentNotificationInfo)
                    .setOperationName(operationName)
                    .setTitle(title);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationInfo.setGroupId(GROUP_ID);
            }
            ThreadUtils.postOnMainThread(() -> p.onProgressStart(-1, 0, notificationInfo));
        } else {
            p.onProgressStart(-1, 0, title);
        }
        return p;
    }

    public static class Result {
        @NonNull
        private final ArrayList<String> mFailedPackages;
        @NonNull
        private final ArrayList<Integer> mAssociatedUsers;
        private final boolean mIsSuccessful;

        private boolean mRequiresRestart;
        @NonNull
        private final ArrayList<DefaultAppRoleBackupHelper.RoleRebindRequest> mPendingDefaultRoleRebindRequests = new ArrayList<>();
        @NonNull
        private final ArrayList<String> mWarnings = new ArrayList<>();

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs) {
            this(failedUserPackagePairs, failedUserPackagePairs.isEmpty());
        }

        public Result(@NonNull List<UserPackagePair> failedUserPackagePairs, boolean isSuccessful) {
            mFailedPackages = new ArrayList<>();
            mAssociatedUsers = new ArrayList<>();
            for (UserPackagePair userPackagePair : failedUserPackagePairs) {
                mFailedPackages.add(userPackagePair.getPackageName());
                mAssociatedUsers.add(userPackagePair.getUserId());
            }
            mIsSuccessful = isSuccessful;
        }

        public boolean requiresRestart() {
            return mRequiresRestart;
        }

        public void setRequiresRestart(boolean requiresRestart) {
            mRequiresRestart = requiresRestart;
        }

        public boolean hasPendingDefaultRoleRebindRequests() {
            return !mPendingDefaultRoleRebindRequests.isEmpty();
        }

        public void addPendingDefaultRoleRebindRequests(
                @NonNull List<DefaultAppRoleBackupHelper.RoleRebindRequest> requests) {
            mPendingDefaultRoleRebindRequests.addAll(requests);
        }

        @NonNull
        public ArrayList<DefaultAppRoleBackupHelper.RoleRebindRequest> getPendingDefaultRoleRebindRequests() {
            return new ArrayList<>(mPendingDefaultRoleRebindRequests);
        }

        public void addWarnings(@NonNull List<String> warnings) {
            for (String warning : warnings) {
                if (warning == null || warning.isEmpty()) {
                    continue;
                }
                if (mWarnings.size() < BackupExtrasCoverage.MAX_AUDIT_WARNINGS) {
                    mWarnings.add(warning);
                } else if (mWarnings.size() == BackupExtrasCoverage.MAX_AUDIT_WARNINGS) {
                    mWarnings.add("Additional operation warnings omitted.");
                    break;
                }
            }
        }

        @NonNull
        public ArrayList<String> getWarnings() {
            return new ArrayList<>(mWarnings);
        }

        public boolean isSuccessful() {
            return mIsSuccessful;
        }

        @NonNull
        public ArrayList<String> getFailedPackages() {
            return mFailedPackages;
        }

        @NonNull
        @UserIdInt
        public ArrayList<Integer> getAssociatedUsers() {
            return mAssociatedUsers;
        }

        @NonNull
        public List<UserPackagePair> getFailedUserPackagePairs() {
            ArrayList<UserPackagePair> pairs = new ArrayList<>(mFailedPackages.size());
            for (int i = 0; i < mFailedPackages.size(); ++i) {
                pairs.add(new UserPackagePair(mFailedPackages.get(i), mAssociatedUsers.get(i)));
            }
            return pairs;
        }
    }
}
