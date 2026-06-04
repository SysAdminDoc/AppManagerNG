// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.util.Pair;

import java.io.File;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Paths;

/**
 * Shared parse/delete operations for APK duplicate cleanup entry points.
 */
public final class ApkDuplicateOperations {
    private static final String TAG = ApkDuplicateOperations.class.getSimpleName();

    private ApkDuplicateOperations() {
    }

    /**
     * Parse a single APK-like file into a duplicate-finder candidate. Plain
     * {@code .apk} files are parsed directly. Split/bundle formats
     * ({@code .apks}/{@code .apkm}/{@code .xapk}) first extract a temporary
     * base APK so {@code getPackageArchiveInfo} and apksig can read the
     * install identity, while the returned candidate still points at the
     * original bundle file for deletion/reclaim accounting.
     */
    @WorkerThread
    @Nullable
    public static ApkDuplicateSelector.Candidate buildCandidate(@NonNull PackageManager pm,
                                                                @NonNull File cacheDir,
                                                                @NonNull File apk) {
        File parseTarget = apk;
        File extractedBase = null;
        try {
            if (!isPlainApk(apk)) {
                extractedBase = ApkBundleBaseExtractor.extractBaseApk(apk, cacheDir);
                if (extractedBase == null) {
                    return null;
                }
                parseTarget = extractedBase;
            }
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo packageInfo = pm.getPackageArchiveInfo(parseTarget.getAbsolutePath(), flags);
            if (packageInfo == null || packageInfo.packageName == null
                    || packageInfo.applicationInfo == null) {
                return null;
            }
            // getSignerInfo(..., isExternal=true) re-derives the cert from the
            // APK path via apksig, so point the archive info at the real file.
            packageInfo.applicationInfo.sourceDir = parseTarget.getAbsolutePath();
            packageInfo.applicationInfo.publicSourceDir = parseTarget.getAbsolutePath();
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
        } finally {
            if (extractedBase != null && !extractedBase.delete()) {
                extractedBase.deleteOnExit();
            }
        }
    }

    /**
     * Delete the chosen duplicate APK files through the privileged
     * {@link Paths} layer. The keeper of each group is never passed in here.
     */
    @WorkerThread
    @NonNull
    public static Pair<Integer, Long> deleteCandidates(
            @NonNull Iterable<ApkDuplicateSelector.Candidate> dropFiles) {
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
        return new Pair<>(deleted, reclaimed);
    }

    @VisibleForTesting
    static boolean isPlainApk(@NonNull File apk) {
        return apk.getName().toLowerCase(Locale.ROOT).endsWith(".apk");
    }
}
