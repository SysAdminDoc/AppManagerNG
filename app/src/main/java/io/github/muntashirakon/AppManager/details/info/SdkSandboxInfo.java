// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.parser.ManifestParser;
import io.github.muntashirakon.AppManager.apk.parser.ManifestSdkLibrary;

/**
 * Target-scoped SDK Runtime metadata exposed by the target APK manifest.
 *
 * <p>This deliberately does not use {@code SdkSandboxManager#getSandboxedSdks}
 * for inspected apps. That public API is scoped to the caller package, so using
 * it here would misreport AppManagerNG's sandbox state as the target app's
 * state.</p>
 */
public final class SdkSandboxInfo {
    public static final int MIN_SDK_INT = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    @VisibleForTesting
    static final long VERSION_UNDEFINED = -1;

    public final int sdkInt;
    @NonNull
    public final List<SdkLibrary> declaredSdkLibraries;

    private SdkSandboxInfo(int sdkInt, @NonNull List<SdkLibrary> declaredSdkLibraries) {
        this.sdkInt = sdkInt;
        this.declaredSdkLibraries = Collections.unmodifiableList(declaredSdkLibraries);
    }

    @NonNull
    public static SdkSandboxInfo from(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT < MIN_SDK_INT) {
            return unsupported(Build.VERSION.SDK_INT);
        }
        if (info.publicSourceDir == null || info.publicSourceDir.isEmpty()) {
            return fromRaw(Build.VERSION.SDK_INT, Collections.emptyList());
        }
        try {
            return fromManifest(Build.VERSION.SDK_INT, new ManifestParser(
                    ApkUtils.getManifestFromApk(new File(info.publicSourceDir))).parseUsesSdkLibraries());
        } catch (Throwable ignore) {
            return fromRaw(Build.VERSION.SDK_INT, Collections.emptyList());
        }
    }

    @NonNull
    static SdkSandboxInfo unsupported(int sdkInt) {
        return new SdkSandboxInfo(sdkInt, Collections.emptyList());
    }

    public boolean isSupported() {
        return sdkInt >= MIN_SDK_INT;
    }

    public boolean hasDeclaredSdkLibraries() {
        return !declaredSdkLibraries.isEmpty();
    }

    @NonNull
    @VisibleForTesting
    static SdkSandboxInfo fromManifest(int sdkInt, @Nullable List<ManifestSdkLibrary> manifestSdkLibraries) {
        if (manifestSdkLibraries == null || manifestSdkLibraries.isEmpty()) {
            return fromRaw(sdkInt, Collections.emptyList());
        }
        List<SdkLibrary> sdkLibraries = new ArrayList<>();
        for (ManifestSdkLibrary libraryInfo : manifestSdkLibraries) {
            if (libraryInfo != null) {
                sdkLibraries.add(SdkLibrary.from(libraryInfo));
            }
        }
        return fromRaw(sdkInt, sdkLibraries);
    }

    @NonNull
    @VisibleForTesting
    static SdkSandboxInfo fromRaw(int sdkInt, @Nullable List<SdkLibrary> sdkLibraries) {
        if (sdkInt < MIN_SDK_INT || sdkLibraries == null || sdkLibraries.isEmpty()) {
            return new SdkSandboxInfo(sdkInt, Collections.emptyList());
        }
        return new SdkSandboxInfo(sdkInt, new ArrayList<>(sdkLibraries));
    }

    public static final class SdkLibrary {
        @NonNull
        public final String name;
        public final long versionMajor;
        @Nullable
        public final String certDigest;

        private SdkLibrary(@NonNull String name, long versionMajor, @Nullable String certDigest) {
            this.name = name;
            this.versionMajor = versionMajor;
            this.certDigest = certDigest;
        }

        @NonNull
        private static SdkLibrary from(@NonNull ManifestSdkLibrary info) {
            return fromRaw(info.name, info.versionMajor, info.certDigest);
        }

        @NonNull
        @VisibleForTesting
        static SdkLibrary fromRaw(@NonNull String name, long versionMajor, @Nullable String certDigest) {
            return new SdkLibrary(name, versionMajor, certDigest);
        }

        @NonNull
        public String toDisplayString() {
            StringBuilder builder = new StringBuilder(name);
            if (versionMajor != VERSION_UNDEFINED) {
                builder.append(" v").append(versionMajor);
            }
            return builder.toString();
        }
    }
}
