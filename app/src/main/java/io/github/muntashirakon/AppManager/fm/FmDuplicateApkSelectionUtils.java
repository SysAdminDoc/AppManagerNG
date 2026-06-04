// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.io.Path;

final class FmDuplicateApkSelectionUtils {
    private FmDuplicateApkSelectionUtils() {
    }

    static boolean canOfferDuplicateScan(@NonNull List<Path> selectedItems) {
        if (selectedItems.size() < 2) {
            return false;
        }
        for (Path selectedItem : selectedItems) {
            if (!isSupportedLocalApk(selectedItem)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    static List<File> toLocalFiles(@NonNull List<Path> selectedItems) {
        if (!canOfferDuplicateScan(selectedItems)) {
            throw new IllegalArgumentException("Selection does not contain at least two readable local APK files.");
        }
        List<File> files = new ArrayList<>(selectedItems.size());
        for (Path selectedItem : selectedItems) {
            files.add(Objects.requireNonNull(selectedItem.getFile()));
        }
        return files;
    }

    private static boolean isSupportedLocalApk(@NonNull Path selectedItem) {
        return selectedItem.canRead()
                && selectedItem.isFile()
                && selectedItem.length() > 0L
                && selectedItem.getFile() != null
                && FmBatchApkInstallUtils.isSupportedInstallSourceExtension(selectedItem.getExtension());
    }
}
