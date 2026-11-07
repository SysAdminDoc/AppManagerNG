// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

/**
 * The apps installed for the current user, as Local sync lists and serves them.
 */
public final class AndroidPackageCatalog implements HostCatalog {
    @NonNull
    private final PackageManager mPm;

    public AndroidPackageCatalog(@NonNull Context context) {
        mPm = context.getApplicationContext().getPackageManager();
    }

    @WorkerThread
    @NonNull
    @Override
    public List<SyncApp> listApps(boolean includeSystem) {
        List<SyncApp> apps = new ArrayList<>();
        for (PackageInfo info : installedPackages()) {
            SyncApp app = toSyncApp(info, includeSystem);
            if (app != null) {
                apps.add(app);
            }
        }
        return apps;
    }

    @WorkerThread
    @Nullable
    @Override
    public Map<String, File> apkFiles(@NonNull String packageName) {
        ApplicationInfo info;
        try {
            info = mPm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        if ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
            return null;
        }
        return filesOf(info);
    }

    /**
     * Every installed app, system apps included, keyed by package name, for comparing against a
     * host's list.
     */
    @WorkerThread
    @NonNull
    public Map<String, SyncApp> indexInstalled() {
        Map<String, SyncApp> index = new HashMap<>();
        for (PackageInfo info : installedPackages()) {
            SyncApp app = toSyncApp(info, true);
            if (app != null) {
                index.put(app.packageName, app);
            }
        }
        return index;
    }

    @WorkerThread
    @Nullable
    public SyncApp findInstalled(@NonNull String packageName) {
        try {
            PackageInfo info = mPm.getPackageInfo(packageName, PackageManagerCompat.GET_SIGNING_CERTIFICATES);
            return toSyncApp(info, true);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private List<PackageInfo> installedPackages() {
        List<PackageInfo> packages = mPm.getInstalledPackages(PackageManagerCompat.GET_SIGNING_CERTIFICATES);
        return packages != null ? packages : Collections.<PackageInfo>emptyList();
    }

    @Nullable
    private SyncApp toSyncApp(@NonNull PackageInfo info, boolean includeSystem) {
        ApplicationInfo appInfo = info.applicationInfo;
        if (appInfo == null || (appInfo.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
            return null;
        }
        boolean system = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        if (system && !includeSystem) {
            return null;
        }
        if (!SyncMessages.isValidPackageName(info.packageName)) {
            return null;
        }
        ApkSignatureGate.SignerDigests signers = ApkSigners.fromSignerInfo(PackageUtils.getSignerInfo(info, false));
        if (signers == null || signers.current.size() > SyncMessages.MAX_SIGNERS
                || signers.lineage.size() > SyncMessages.MAX_SIGNERS) {
            return null;
        }
        Map<String, File> files = filesOf(appInfo);
        if (files == null) {
            return null;
        }
        List<SyncApp.ApkFileInfo> fileInfos = new ArrayList<>(files.size());
        for (Map.Entry<String, File> entry : files.entrySet()) {
            fileInfos.add(new SyncApp.ApkFileInfo(entry.getKey(), entry.getValue().length()));
        }
        String label = String.valueOf(appInfo.loadLabel(mPm));
        String versionName = info.versionName != null ? info.versionName : "";
        return new SyncApp(info.packageName, label, PackageInfoCompat.getLongVersionCode(info), versionName,
                system, signers.current, signers.lineage, fileInfos);
    }

    /**
     * The APK files of an install, base first, keyed by file name. Returns {@code null} when any
     * of them is missing, unreadable or has a name the protocol refuses, so a partial app is
     * never offered.
     */
    @VisibleForTesting
    @Nullable
    static Map<String, File> filesOf(@NonNull ApplicationInfo info) {
        List<String> paths = new ArrayList<>();
        if (info.sourceDir == null) {
            return null;
        }
        paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) {
            Collections.addAll(paths, info.splitSourceDirs);
        }
        if (paths.size() > SyncMessages.MAX_FILES) {
            return null;
        }
        Map<String, File> files = new LinkedHashMap<>();
        for (String path : paths) {
            if (path == null) {
                return null;
            }
            File file = new File(path);
            String name = file.getName();
            if (!SyncMessages.isSafeFileName(name) || files.containsKey(name) || !file.isFile() || !file.canRead()) {
                return null;
            }
            files.put(name, file);
        }
        return files;
    }
}
