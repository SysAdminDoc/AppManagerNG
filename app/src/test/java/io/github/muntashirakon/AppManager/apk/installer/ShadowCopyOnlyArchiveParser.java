// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.apk.signing.SignerPinCheck;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Reads an APK only when it's one of the signer pin check's private copies, the way a file staged
 * where only the privileged service can read it has to be copied before this app can parse it. A
 * copy holding exactly the expected APK reads as the expected package. Nothing goes through the
 * host's native parser, which keeps the file open so Windows can't delete the copy afterwards.
 */
@Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
public class ShadowCopyOnlyArchiveParser extends ShadowApplicationPackageManager {
    static final List<String> sParsed = new ArrayList<>();
    private static byte[] sApk;
    private static String sPackage;

    static void expect(@NonNull byte[] apk, @NonNull String packageName) {
        sParsed.clear();
        sApk = apk;
        sPackage = packageName;
    }

    @Implementation
    @Override
    protected PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags) {
        sParsed.add(archiveFilePath);
        File archive = new File(archiveFilePath);
        File copies = new File(ContextUtils.getContext().getCacheDir(), SignerPinCheck.COPY_FOLDER);
        if (!copies.equals(archive.getParentFile())) {
            return null;
        }
        byte[] content;
        try {
            content = Files.readAllBytes(archive.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!Arrays.equals(sApk, content)) {
            return null;
        }
        PackageInfo info = new PackageInfo();
        info.packageName = sPackage;
        return info;
    }
}
