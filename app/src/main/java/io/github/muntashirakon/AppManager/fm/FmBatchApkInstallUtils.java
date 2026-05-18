// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.io.Path;

final class FmBatchApkInstallUtils {
    private FmBatchApkInstallUtils() {
    }

    static boolean canOfferInstall(@NonNull List<Path> selectedItems) {
        if (selectedItems.isEmpty()) {
            return false;
        }
        for (Path selectedItem : selectedItems) {
            if (!selectedItem.canRead() || !selectedItem.isFile()
                    || !isSupportedInstallSourceExtension(selectedItem.getExtension())) {
                return false;
            }
        }
        return true;
    }

    static boolean isSupportedInstallSourceName(@Nullable String displayName) {
        if (displayName == null) {
            return false;
        }
        int extensionIndex = displayName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex + 1 == displayName.length()) {
            return false;
        }
        return isSupportedInstallSourceExtension(displayName.substring(extensionIndex + 1));
    }

    @NonNull
    static Intent getInstallIntent(@NonNull Context context, @NonNull List<Path> selectedItems) {
        if (!canOfferInstall(selectedItems)) {
            throw new IllegalArgumentException("Selection does not contain only installable APK files.");
        }
        ArrayList<Uri> uris = new ArrayList<>(selectedItems.size());
        for (Path selectedItem : selectedItems) {
            uris.add(FmProvider.getContentUri(selectedItem));
        }
        return PackageInstallerActivity.getBatchInstallInstance(context, uris);
    }

    private static boolean isSupportedInstallSourceExtension(@Nullable String extension) {
        return extension != null && ApkFile.SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}
