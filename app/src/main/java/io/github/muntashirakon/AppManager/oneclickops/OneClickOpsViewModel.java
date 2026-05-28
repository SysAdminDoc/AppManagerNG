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
import android.os.storage.StorageManagerHidden;
import android.os.storage.StorageVolume;
import android.os.storage.StorageVolumeHidden;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Future;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
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
    private final MutableLiveData<Pair<Integer, Long>> mLeftoverDeleteResult = new MutableLiveData<>();
    private final MutableLiveData<List<ApkDuplicateSelector.DuplicateGroup>> mApkDuplicates = new MutableLiveData<>();
    private final MutableLiveData<Pair<Integer, Long>> mApkDuplicateDeleteResult = new MutableLiveData<>();

    @Nullable
    private Future<?> mFutureResult;

    public OneClickOpsViewModel(@NonNull Application application) {
        super(application);
        mPm = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
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

    public LiveData<Pair<Integer, Long>> watchLeftoverDeleteResult() {
        return mLeftoverDeleteResult;
    }

    public LiveData<List<ApkDuplicateSelector.DuplicateGroup>> watchApkDuplicates() {
        return mApkDuplicates;
    }

    public LiveData<Pair<Integer, Long>> watchApkDuplicateDeleteResult() {
        return mApkDuplicateDeleteResult;
    }

    /**
     * Scan external storage for {@code .apk} files, fingerprint each by
     * (package, versionCode, signing-cert SHA-256) via
     * {@link PackageManager#getPackageArchiveInfo}, and surface every
     * redundant copy through {@link ApkDuplicateSelector} (T19-C).
     */
    @AnyThread
    public void scanApkDuplicates(@NonNull ApkDuplicateSelector.KeepStrategy strategy) {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
            List<java.io.File> apkFiles = ApkFileScanner.scan(
                    Environment.getExternalStorageDirectory(), null);
            List<ApkDuplicateSelector.Candidate> candidates = new ArrayList<>(apkFiles.size());
            for (java.io.File apk : apkFiles) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                ApkDuplicateSelector.Candidate candidate = buildApkCandidate(apk);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            mApkDuplicates.postValue(ApkDuplicateSelector.selectDuplicates(candidates, strategy));
        });
    }

    /**
     * Parse a single {@code .apk} into a duplicate-finder candidate. Bundle
     * formats ({@code .apks}/{@code .apkm}/{@code .xapk}) are not parseable by
     * {@code getPackageArchiveInfo} and return {@code null} here; their
     * base-APK extraction stays on the T19-C follow-up.
     */
    @Nullable
    private ApkDuplicateSelector.Candidate buildApkCandidate(@NonNull java.io.File apk) {
        try {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo packageInfo = mPm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (packageInfo == null || packageInfo.packageName == null
                    || packageInfo.applicationInfo == null) {
                return null;
            }
            // getSignerInfo(..., isExternal=true) re-derives the cert from the
            // APK path via apksig, so point the archive info at the real file.
            packageInfo.applicationInfo.sourceDir = apk.getAbsolutePath();
            packageInfo.applicationInfo.publicSourceDir = apk.getAbsolutePath();
            long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
            String cert = null;
            String[] certs = PackageUtils.getSigningCertSha256Checksum(packageInfo, true);
            if (certs.length > 0) {
                cert = certs[0];
            }
            return new ApkDuplicateSelector.Candidate(apk, packageInfo.packageName, versionCode,
                    cert, apk.length());
        } catch (Throwable th) {
            Log.w(TAG, "Failed to parse APK candidate: " + apk, th);
            return null;
        }
    }

    /**
     * Delete the chosen duplicate APK files through the privileged
     * {@link Paths} layer, logging each removal and posting a (count, bytes)
     * summary. The keeper of each group is never passed in here.
     */
    @AnyThread
    public void deleteApkDuplicates(@NonNull List<ApkDuplicateSelector.Candidate> dropFiles) {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
            int deleted = 0;
            long reclaimed = 0L;
            for (ApkDuplicateSelector.Candidate candidate : dropFiles) {
                if (ThreadUtils.isInterrupted()) {
                    break;
                }
                try {
                    if (Paths.get(candidate.path).delete()) {
                        ++deleted;
                        if (candidate.sizeBytes > 0) {
                            reclaimed += candidate.sizeBytes;
                        }
                        io.github.muntashirakon.AppManager.logs.Log.i(TAG, "Deleted duplicate APK "
                                + candidate.packageName + " v" + candidate.versionCode + ": "
                                + candidate.path);
                    } else {
                        io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                                "Failed to delete duplicate APK: " + candidate.path);
                    }
                } catch (Throwable th) {
                    io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                            "Error deleting duplicate APK: " + candidate.path, th);
                }
            }
            mApkDuplicateDeleteResult.postValue(new Pair<>(deleted, reclaimed));
        });
    }

    /**
     * A single leftover folder paired with its precomputed on-disk size so the
     * review dialog never walks the file system on the main thread (T19-B).
     */
    public static class LeftoverEntry {
        @NonNull
        public final LeftoverScanner.Leftover leftover;
        public final long size;

        LeftoverEntry(@NonNull LeftoverScanner.Leftover leftover, long size) {
            this.leftover = leftover;
            this.size = size;
        }
    }

    /**
     * Scan external storage (and, when privileged, the root {@code /data/data}
     * stubs) for orphan per-package directories left behind by uninstalled
     * apps. Sizes are computed on the worker thread so the UI can render them
     * without re-walking.
     */
    @AnyThread
    public void scanLeftovers() {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
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
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
            int deleted = 0;
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
                        io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                                "Failed to delete leftover folder: " + entry.leftover.path);
                    }
                } catch (Throwable th) {
                    io.github.muntashirakon.AppManager.logs.Log.w(TAG,
                            "Error deleting leftover folder: " + entry.leftover.path, th);
                }
            }
            mLeftoverDeleteResult.postValue(new Pair<>(deleted, reclaimed));
        });
    }

    @AnyThread
    public void blockTrackers(boolean systemApps) {
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
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
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
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
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
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
        if (mFutureResult != null) {
            mFutureResult.cancel(true);
        }
        mFutureResult = ThreadUtils.postOnBackgroundThread(() -> {
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
        ThreadUtils.postOnBackgroundThread(() -> {
            long size = 1024L * 1024L * 1024L * 1024L;  // 1 TB
            boolean success = true;
            for (String volumeUuid : getTrimCacheVolumeUuids(getWritableStorageVolumeUuids())) {
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

    @NonNull
    private List<String> getWritableStorageVolumeUuids() {
        List<String> volumeUuids = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return volumeUuids;
        }
        try {
            StorageVolume[] volumes = StorageManagerCompat.getVolumeList(getApplication(),
                    UserHandleHidden.myUserId(), StorageManagerHidden.FLAG_FOR_WRITE);
            for (@NonNull StorageVolume volume : volumes) {
                try {
                    String uuid = Refine.<StorageVolumeHidden>unsafeCast(volume).getUuid();
                    if (uuid != null) {
                        volumeUuids.add(uuid);
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "Failed to read storage volume UUID.", e);
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to enumerate storage volumes for cache trimming.", e);
        }
        return volumeUuids;
    }

    @VisibleForTesting
    @NonNull
    static List<String> getTrimCacheVolumeUuids(@NonNull List<String> storageVolumeUuids) {
        LinkedHashSet<String> orderedVolumeUuids = new LinkedHashSet<>();
        orderedVolumeUuids.add(null);
        for (String uuid : storageVolumeUuids) {
            if (uuid != null && !uuid.isEmpty()) {
                orderedVolumeUuids.add(uuid);
            }
        }
        return new ArrayList<>(orderedVolumeUuids);
    }

    public void listAppsInstalledByAmForDexOpt() {
        ThreadUtils.postOnBackgroundThread(() -> {
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

    @NonNull
    private ItemCount getTrackerCountForApp(@NonNull PackageInfo packageInfo) {
        ItemCount trackerCount = new ItemCount();
        trackerCount.packageName = packageInfo.packageName;
        trackerCount.packageLabel = packageInfo.applicationInfo.loadLabel(mPm).toString();
        trackerCount.count = ComponentUtils.getTrackerComponentsCountForPackage(packageInfo);
        return trackerCount;
    }
}
