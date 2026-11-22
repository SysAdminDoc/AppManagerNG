// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/**
 * What installing an APK does to the copy already on the device, named on the installer's button.
 */
public enum InstallAction {
    INSTALL(R.string.install),
    UPDATE(R.string.update),
    /**
     * The same version code under another version name and the same key: a modified build of the
     * installed version, like a patched one (#47).
     */
    MODIFY(R.string.modify),
    REINSTALL(R.string.reinstall),
    DOWNGRADE(R.string.downgrade);

    @StringRes
    public final int labelRes;

    InstallAction(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /**
     * A missing version name counts as an empty one. Another key keeps {@link #REINSTALL} for an
     * equal version code, since that path offers the uninstall Android needs first.
     */
    @NonNull
    public static InstallAction of(boolean installed, long installedVersionCode, @Nullable String installedVersionName,
                                   long newVersionCode, @Nullable String newVersionName, boolean signatureDiffers) {
        if (!installed) {
            return INSTALL;
        }
        if (installedVersionCode < newVersionCode) {
            return UPDATE;
        }
        if (installedVersionCode > newVersionCode) {
            return DOWNGRADE;
        }
        if (!signatureDiffers && !nonNull(installedVersionName).equals(nonNull(newVersionName))) {
            return MODIFY;
        }
        return REINSTALL;
    }

    /**
     * The version line under the app's name: old and new name for {@link #MODIFY}, since the
     * version code alone doesn't tell the two apart, and the new version otherwise.
     */
    @NonNull
    public String versionLine(@NonNull Context context, @Nullable String installedVersionName,
                              @Nullable String newVersionName, long newVersionCode) {
        if (this == MODIFY) {
            return context.getString(R.string.version_name_change_with_code, nonNull(installedVersionName),
                    nonNull(newVersionName), newVersionCode);
        }
        return context.getString(R.string.version_name_with_code, newVersionName, newVersionCode);
    }

    @NonNull
    private static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }
}
