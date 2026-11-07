// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * What a sharing device offers: its installed apps, and the APK files of one package.
 */
public interface HostCatalog {
    @WorkerThread
    @NonNull
    List<SyncApp> listApps(boolean includeSystem);

    /**
     * The installed APK files of {@code packageName} (base and splits), keyed by file name and
     * looked up fresh on every call. These are the only files the host will ever send for that
     * package. Returns {@code null} when the package is not installed.
     */
    @WorkerThread
    @Nullable
    Map<String, File> apkFiles(@NonNull String packageName);
}
