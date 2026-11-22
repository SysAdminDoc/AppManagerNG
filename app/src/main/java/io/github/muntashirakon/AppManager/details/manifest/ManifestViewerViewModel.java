// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;

public class ManifestViewerViewModel extends AndroidViewModel {
    public static final String TAG = ManifestViewerViewModel.class.getSimpleName();

    private ApkFile mApkFile;
    @Nullable
    private Future<?> mManifestLoaderResult;

    private final FileCache mFileCache = new FileCache();
    private final MutableLiveData<Uri> mManifestLiveData = new MutableLiveData<>();

    public ManifestViewerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mManifestLoaderResult != null) {
            mManifestLoaderResult.cancel(true);
        }
        IoUtils.closeQuietly(mApkFile);
        IoUtils.closeQuietly(mFileCache);
        super.onCleared();
    }

    public LiveData<Uri> getManifestLiveData() {
        return mManifestLiveData;
    }

    @MainThread
    public void loadApkFile(@Nullable ApkSource apkSource, @Nullable String packageName) {
        if (mManifestLoaderResult != null) {
            // A screen made again asks again. While the manifest is being read, or was read into a
            // file that's still there, it gets that one, and the APK isn't opened a second time.
            if (!mManifestLoaderResult.isDone() || isReadableFile(mManifestLiveData.getValue())) {
                return;
            }
            // The read failed, or the cache was cleared since: a viewer for that file would be empty
            mManifestLiveData.setValue(null);
        }
        mManifestLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            if (mApkFile == null) {
                final PackageManager pm = getApplication().getPackageManager();
                ApkSource realApkSource;
                if (apkSource != null) {
                    realApkSource = apkSource;
                } else {
                    try {
                        ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
                        realApkSource = ApkSource.getApkSource(applicationInfo);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.e(TAG, "Error: ", e);
                        return;
                    }
                }
                try {
                    mApkFile = realApkSource.resolve();
                } catch (ApkFile.ApkFileException e) {
                    Log.e(TAG, "Error: ", e);
                    return;
                }
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            if (mApkFile != null) {
                ByteBuffer byteBuffer = mApkFile.getBaseEntry().manifest;
                // Reset properties
                byteBuffer.position(0);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    File cachedFile = mFileCache.createCachedFile("xml");
                    try (PrintStream ps = new PrintStream(cachedFile)) {
                        AndroidBinXmlDecoder.decode(byteBuffer, ps);
                    }
                    mManifestLiveData.postValue(Uri.fromFile(cachedFile));
                } catch (Exception e) {
                    Log.e(TAG, "Could not parse APK", e);
                }
            }
        });
    }

    private static boolean isReadableFile(@Nullable Uri uri) {
        String path = uri != null ? uri.getPath() : null;
        return path != null && new File(path).canRead();
    }
}
