// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES_APK;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class PackageInstallerViewModel extends AndroidViewModel {
    private final PackageManager mPm;
    private PackageInfo mNewPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private ApkSource mApkSource;
    // The screen holds one reference to this file. It changes on a worker thread and is closed on the
    // main thread, so it is swapped and closed under mApkFileLock and never closed twice.
    private final Object mApkFileLock = new Object();
    private volatile ApkFile mApkFile;
    @GuardedBy("mApkFileLock")
    private boolean mCleared;
    // Each queue item's load gets the next number. A cancelled load can still be running when the next one
    // starts, so only the load holding the current number may replace the file or publish what it read.
    @GuardedBy("mApkFileLock")
    private int mLoadGeneration;
    private String mPackageName;
    private String mAppLabel;
    private Drawable mAppIcon;
    private boolean mIsSignatureDifferent = false;
    private int mTrackerCount;
    @Nullable
    private Future<?> mPackageInfoResult;
    private final LoadLiveData mPackageInfoLiveData = new LoadLiveData();
    private final MutableLiveData<Boolean> mPackageUninstalledLiveData = new MutableLiveData<>();
    // An event: it must not show again when the screen is recreated
    private final SingleLiveEvent<String> mSignerPinRefusalLiveData = new SingleLiveEvent<>();
    private final Set<String> mSelectedSplits = new HashSet<>();
    @Nullable
    private Set<String> mBaseNativeAbis;

    public PackageInstallerViewModel(@NonNull Application application) {
        super(application);
        mPm = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        ApkFile apkFile;
        synchronized (mApkFileLock) {
            mCleared = true;
            apkFile = mApkFile;
            mApkFile = null;
        }
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        IoUtils.closeQuietly(apkFile);
        super.onCleared();
    }

    public LiveData<PackageInfo> packageInfoLiveData() {
        return mPackageInfoLiveData;
    }

    public LiveData<Boolean> packageUninstalledLiveData() {
        return mPackageUninstalledLiveData;
    }

    @AnyThread
    public void getPackageInfo(ApkQueueItem apkQueueItem) {
        // The new number comes first: the interrupt can make the previous load fail at once, and a load that
        // still held the current number would report that failure for this item
        int generation = startLoad();
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        mSelectedSplits.clear();
        mPackageInfoResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                // Three possibilities: 1. Install-existing, 2. ApkFile, 3. Uri
                LoadedPackage loaded;
                if (apkQueueItem.isInstallExisting()) {
                    if (apkQueueItem.getPackageName() == null) {
                        throw new IllegalArgumentException("Package name not set for install-existing.");
                    }
                    loaded = loadExistingPackage(apkQueueItem.getPackageName(), generation);
                } else if (apkQueueItem.getApkSource() != null) {
                    loaded = loadPackage(apkQueueItem.getApkSource(), generation);
                } else {
                    throw new IllegalArgumentException("Invalid queue item.");
                }
                if (loaded != null) {
                    publish(generation, loaded, apkQueueItem);
                }
            } catch (Throwable th) { // ApkFileException extends Throwable, not Exception
                Log.e("PIVM", "Couldn't fetch package info", th);
                publishFailure(generation);
            }
        });
    }

    /**
     * Tells the screen the load failed, unless a newer load started or the screen is gone: those fail quietly.
     *
     * @return {@code false} when the load was stale and nothing was published
     */
    @VisibleForTesting
    boolean publishFailure(int generation) {
        synchronized (mApkFileLock) {
            if (!isCurrentLoad(generation)) {
                return false;
            }
            mPackageInfoLiveData.post(generation, null);
            return true;
        }
    }

    /**
     * Starts a new load, so every earlier one can no longer replace the file or publish.
     *
     * @return the new load's number
     */
    @VisibleForTesting
    int startLoad() {
        synchronized (mApkFileLock) {
            return ++mLoadGeneration;
        }
    }

    @GuardedBy("mApkFileLock")
    private boolean isCurrentLoad(int generation) {
        return !mCleared && generation == mLoadGeneration;
    }

    /**
     * Hands what a load read to the screen, unless a newer load started or the screen is gone.
     *
     * @return {@code false} when the load was stale and nothing was published
     */
    @VisibleForTesting
    boolean publish(int generation, @NonNull LoadedPackage loaded, @Nullable ApkQueueItem apkQueueItem) {
        synchronized (mApkFileLock) {
            if (!isCurrentLoad(generation)) {
                return false;
            }
            mApkSource = loaded.apkSource;
            mNewPackageInfo = loaded.newPackageInfo;
            mInstalledPackageInfo = loaded.installedPackageInfo;
            mPackageName = loaded.packageName;
            mAppLabel = loaded.appLabel;
            mAppIcon = loaded.appIcon;
            mTrackerCount = loaded.trackerCount;
            mBaseNativeAbis = loaded.baseNativeAbis;
            mIsSignatureDifferent = loaded.signatureDifferent;
            if (apkQueueItem != null) {
                apkQueueItem.setApkSource(loaded.apkSource);
                apkQueueItem.setPackageName(loaded.packageName);
                apkQueueItem.setAppLabel(loaded.appLabel);
            }
            // Posted under the lock, so a newer load can't post first and then be overwritten
            mPackageInfoLiveData.post(generation, loaded.newPackageInfo);
            return true;
        }
    }

    /**
     * Package info for the screen. A posted value reaches the main thread later, so one posted just before Next
     * was tapped would arrive once the next item's load had started and show, or batch install, the previous
     * package as the next one. Only the value posted last, by a load that is still current, gets through.
     */
    private final class LoadLiveData extends MutableLiveData<PackageInfo> {
        @GuardedBy("mApkFileLock")
        private int mPostedGeneration;
        @GuardedBy("mApkFileLock")
        @Nullable
        private PackageInfo mPosted;

        @GuardedBy("mApkFileLock")
        void post(int generation, @Nullable PackageInfo packageInfo) {
            mPostedGeneration = generation;
            mPosted = packageInfo;
            postValue(packageInfo);
        }

        @Override
        public void setValue(@Nullable PackageInfo value) {
            synchronized (mApkFileLock) {
                // Coalesced posts hand over the newest value. An older one only arrives here when a newer
                // post came in while it was being handed over, and that newer one follows right after.
                if (value != mPosted || !isCurrentLoad(mPostedGeneration)) {
                    return;
                }
            }
            super.setValue(value);
        }
    }

    @NonNull
    public LiveData<String> signerPinRefusalLiveData() {
        return mSignerPinRefusalLiveData;
    }

    /**
     * Uninstalls the app so the selected APKs can replace it. When a signer pin would refuse them,
     * the app and its data stay and the refusal is posted instead.
     */
    public void uninstallPackage(boolean signApkFiles) {
        ApkFile apkFile = mApkFile;
        List<String> selectedSplits = getSelectedSplitsForInstallation();
        ThreadUtils.postOnBackgroundThread(() -> {
            String refusal = uninstallUnlessSignerPinRefuses(getApplication(), apkFile, selectedSplits, signApkFiles,
                    () -> {
                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                        installer.setAppLabel(mAppLabel);
                        mPackageUninstalledLiveData.postValue(
                                installer.uninstall(mPackageName, UserHandleHidden.USER_ALL, false));
                    });
            if (refusal != null) {
                mSignerPinRefusalLiveData.postValue(refusal);
            }
        });
    }

    /**
     * @return the signer pin refusal, in which case {@code uninstall} didn't run, or {@code null}
     * after it ran
     */
    @WorkerThread
    @Nullable
    @VisibleForTesting
    static String uninstallUnlessSignerPinRefuses(@NonNull Context context, @NonNull ApkFile apkFile,
                                                  @NonNull List<String> selectedSplits, boolean signApkFiles,
                                                  @NonNull Runnable uninstall) {
        String refusal = PackageInstallerCompat.signerPinRefusal(context, apkFile, selectedSplits, signApkFiles);
        if (refusal == null) {
            uninstall.run();
        }
        return refusal;
    }

    public PackageInfo getNewPackageInfo() {
        return mNewPackageInfo;
    }

    @Nullable
    public PackageInfo getInstalledPackageInfo() {
        return mInstalledPackageInfo;
    }

    public String getAppLabel() {
        return mAppLabel;
    }

    public Drawable getAppIcon() {
        return mAppIcon;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public ApkFile getApkFile() {
        return mApkFile;
    }

    public ApkSource getApkSource() {
        return mApkSource;
    }

    public int getTrackerCount() {
        return mTrackerCount;
    }

    public boolean isSignatureDifferent() {
        return mIsSignatureDifferent;
    }

    public Set<String> getSelectedSplits() {
        return mSelectedSplits;
    }

    /**
     * The ABIs the base APK and the selected ABI splits carry native code for, or {@code null} when
     * the base APK couldn't be read.
     */
    @Nullable
    public Set<String> getNativeAbis(@NonNull Collection<String> selectedEntryIds) {
        if (mBaseNativeAbis == null || mApkFile == null) {
            return null;
        }
        Set<String> abis = new TreeSet<>(mBaseNativeAbis);
        for (ApkFile.Entry entry : mApkFile.getEntries()) {
            if (entry.type == ApkFile.APK_SPLIT_ABI && selectedEntryIds.contains(entry.id)) {
                abis.add(entry.getAbi());
            }
        }
        return abis;
    }

    @WorkerThread
    @Nullable
    private static Set<String> loadBaseNativeAbis(@NonNull ApkFile apkFile) {
        try {
            return readNativeAbis(apkFile.getBaseEntry().getFile(false));
        } catch (IOException | RuntimeException e) {
            // Only the native-code preflight is lost, the system installer still checks
            Log.w("PIVM", "Couldn't list the base APK's native libraries", e);
            return null;
        }
    }

    /**
     * The ABIs an APK carries native libraries for, read from its {@code lib/<abi>/} entries.
     */
    @VisibleForTesting
    @WorkerThread
    @NonNull
    static Set<String> readNativeAbis(@NonNull File apk) throws IOException {
        try (ZipFile zipFile = new ZipFile(apk)) {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            return InstallDependencyChecker.nativeAbisOf(names);
        }
    }

    public void selectDefaultSplitsForInstallation() {
        if (!mApkFile.isSplit() || !mSelectedSplits.isEmpty()) {
            return;
        }
        HashMap<String, HashSet<Integer>> seenTypes = new HashMap<>();
        for (ApkFile.Entry entry : mApkFile.getEntries()) {
            if (entry.isRequired() && entry.supported()) {
                selectSplit(entry, seenTypes);
            }
        }
        for (ApkFile.Entry entry : mApkFile.getEntries()) {
            if (mSelectedSplits.contains(entry.id) || !entry.supported()) {
                continue;
            }
            HashSet<Integer> types = seenTypes.get(entry.getFeature());
            if (types == null) {
                continue;
            }
            switch (entry.type) {
                case ApkFile.APK_SPLIT_ABI:
                case ApkFile.APK_SPLIT_DENSITY:
                case ApkFile.APK_SPLIT_LOCALE:
                    if (!types.contains(entry.type)) {
                        selectSplit(entry, seenTypes);
                    }
                    break;
            }
        }
    }

    @NonNull
    public ArrayList<String> getSelectedSplitsForInstallation() {
        if (mApkFile.isSplit()) {
            selectDefaultSplitsForInstallation();
            if (mSelectedSplits.isEmpty()) {
                throw new IllegalArgumentException("No splits selected.");
            }
            return new ArrayList<>(mSelectedSplits);
        }
        return new ArrayList<>(Collections.singletonList(mApkFile.getBaseEntry().id));
    }

    /**
     * Each queue item resolves its own file, and the one before it is closed here. A queued install
     * that still needs it took its own reference (ApkFile#acquire), so this close never pulls a file
     * out from under the installer. Resolving the same file again took another reference too, so the
     * previous one is closed even when it is the same instance.
     *
     * @return {@code false} once the screen is gone, in which case {@code apkFile} is closed instead
     */
    @VisibleForTesting
    boolean replaceApkFile(@NonNull ApkFile apkFile) {
        int generation;
        synchronized (mApkFileLock) {
            generation = mLoadGeneration;
        }
        return replaceApkFile(apkFile, generation);
    }

    /**
     * {@link #replaceApkFile(ApkFile)} for one load. A load that a newer one replaced resolves its file too late
     * to show it, so the file is closed and the newer load's file stays.
     *
     * @return {@code false} once the screen is gone or a newer load started, in which case {@code apkFile} is
     * closed instead
     */
    @VisibleForTesting
    boolean replaceApkFile(@NonNull ApkFile apkFile, int generation) {
        ApkFile toClose;
        boolean kept;
        synchronized (mApkFileLock) {
            kept = isCurrentLoad(generation);
            if (kept) {
                toClose = mApkFile;
                mApkFile = apkFile;
            } else {
                toClose = apkFile;
            }
        }
        if (toClose != null) {
            toClose.close();
        }
        return kept;
    }

    private void selectSplit(@NonNull ApkFile.Entry entry, @NonNull HashMap<String, HashSet<Integer>> seenTypes) {
        mSelectedSplits.add(entry.id);
        HashSet<Integer> types = seenTypes.get(entry.getFeature());
        if (types == null) {
            types = new HashSet<>();
            seenTypes.put(entry.getFeature(), types);
        }
        types.add(entry.type);
    }

    /**
     * Reads a package file for one load. Everything goes into locals, so a load that a newer one replaced
     * can't leave its package name, label or installed version behind on the screen.
     *
     * @return {@code null} when the load was replaced, cancelled or its screen is gone
     */
    @WorkerThread
    @Nullable
    private LoadedPackage loadPackage(@NonNull ApkSource apkSource, int generation)
            throws PackageManager.NameNotFoundException, IOException, ApkFile.ApkFileException {
        ApkFile apkFile = apkSource.resolve();
        if (!replaceApkFile(apkFile, generation)) {
            return null;
        }
        PackageInfo newPackageInfo = loadNewPackageInfo(apkFile);
        if (ThreadUtils.isInterrupted()) {
            return null;
        }
        PackageInfo installedPackageInfo;
        try {
            installedPackageInfo = loadInstalledPackageInfo(newPackageInfo.packageName);
            if (ThreadUtils.isInterrupted()) {
                return null;
            }
        } catch (PackageManager.NameNotFoundException ignore) {
            installedPackageInfo = null;
        }
        return describe(apkSource, apkFile, newPackageInfo, installedPackageInfo, newPackageInfo.packageName);
    }

    @WorkerThread
    @Nullable
    private LoadedPackage loadExistingPackage(@NonNull String packageName, int generation)
            throws PackageManager.NameNotFoundException, IOException, ApkFile.ApkFileException {
        PackageInfo installedPackageInfo = loadInstalledPackageInfo(packageName);
        ApkSource apkSource = ApkSource.getApkSource(installedPackageInfo.applicationInfo);
        ApkFile apkFile = apkSource.resolve();
        if (!replaceApkFile(apkFile, generation)) {
            return null;
        }
        PackageInfo newPackageInfo = loadNewPackageInfo(apkFile);
        // Installed under this name, which a renamed package's APK (original-package) doesn't declare
        return describe(apkSource, apkFile, newPackageInfo, installedPackageInfo, packageName);
    }

    @WorkerThread
    @Nullable
    private LoadedPackage describe(@NonNull ApkSource apkSource, @NonNull ApkFile apkFile,
                                   @NonNull PackageInfo newPackageInfo, @Nullable PackageInfo installedPackageInfo,
                                   @NonNull String packageName) {
        String appLabel = mPm.getApplicationLabel(newPackageInfo.applicationInfo).toString();
        Drawable appIcon = mPm.getApplicationIcon(newPackageInfo.applicationInfo);
        int trackerCount = ComponentUtils.getTrackerComponentsCountForPackage(newPackageInfo);
        if (ThreadUtils.isInterrupted()) {
            return null;
        }
        Set<String> baseNativeAbis = loadBaseNativeAbis(apkFile);
        boolean signatureDifferent = installedPackageInfo != null
                && PackageUtils.isSignatureDifferent(newPackageInfo, installedPackageInfo);
        return new LoadedPackage(apkSource, newPackageInfo, installedPackageInfo, packageName, appLabel, appIcon, trackerCount, baseNativeAbis, signatureDifferent);
    }

    /**
     * What one load read, kept apart from the screen's state until {@link #publish} accepts it.
     */
    @VisibleForTesting
    static final class LoadedPackage {
        @NonNull
        final ApkSource apkSource;
        @NonNull
        final PackageInfo newPackageInfo;
        @Nullable
        final PackageInfo installedPackageInfo;
        @NonNull
        final String packageName;
        @NonNull
        final String appLabel;
        @Nullable
        final Drawable appIcon;
        final int trackerCount;
        @Nullable
        final Set<String> baseNativeAbis;
        final boolean signatureDifferent;

        LoadedPackage(@NonNull ApkSource apkSource, @NonNull PackageInfo newPackageInfo,
                      @Nullable PackageInfo installedPackageInfo, @NonNull String packageName,
                      @NonNull String appLabel, @Nullable Drawable appIcon, int trackerCount,
                      @Nullable Set<String> baseNativeAbis, boolean signatureDifferent) {
            this.apkSource = apkSource;
            this.newPackageInfo = newPackageInfo;
            this.installedPackageInfo = installedPackageInfo;
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.appIcon = appIcon;
            this.trackerCount = trackerCount;
            this.baseNativeAbis = baseNativeAbis;
            this.signatureDifferent = signatureDifferent;
        }
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadNewPackageInfo(@NonNull ApkFile apkFile) throws PackageManager.NameNotFoundException, IOException {
        String apkPath = apkFile.getBaseEntry().getFile(false).getAbsolutePath();
        int flags = PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES_APK
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES;
        PackageInfo packageInfo = mPm.getPackageArchiveInfo(apkPath, flags);
        if (packageInfo == null) {
            // Previous method could return null if the APK isn't signed. So, try without it.
            packageInfo = mPm.getPackageArchiveInfo(apkPath, flags & ~GET_SIGNING_CERTIFICATES_APK);
        }
        if (packageInfo == null) {
            throw new PackageManager.NameNotFoundException("Package cannot be parsed.");
        }
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        return packageInfo;
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadInstalledPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES | MATCH_UNINSTALLED_PACKAGES
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }
}
