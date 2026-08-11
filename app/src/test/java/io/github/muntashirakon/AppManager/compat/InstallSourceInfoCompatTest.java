// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The update owner is the package allowed to replace an app without asking the user again. It is
 * carried separately from the installer because the two genuinely differ — an app installed by a
 * file manager can still be owned by a store — and it crosses a process boundary as a Parcel, so a
 * field appended without a matching read would shift every value after it.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallSourceInfoCompatTest {
    private static InstallSourceInfoCompat roundTrip(InstallSourceInfoCompat source) {
        Parcel parcel = Parcel.obtain();
        try {
            source.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return InstallSourceInfoCompat.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void installerOnlyConstructorRecordsNoUpdateOwner() {
        // Below Android 14, and for the legacy installer-name path, nothing is recorded — which
        // must read as "unknown", not as some package owning updates.
        InstallSourceInfoCompat info = new InstallSourceInfoCompat("com.example.store");

        assertEquals("com.example.store", info.getInstallingPackageName());
        assertNull(info.getUpdateOwnerPackageName());
    }

    @Test
    public void theInstallerSurvivesAParcelRoundTrip() {
        InstallSourceInfoCompat restored = roundTrip(new InstallSourceInfoCompat("com.example.store"));

        assertEquals("com.example.store", restored.getInstallingPackageName());
        assertNull(restored.getUpdateOwnerPackageName());
        assertNull(restored.getInitiatingPackageName());
        assertNull(restored.getOriginatingPackageName());
    }

    @Test
    public void theUpdateOwnerLabelIsSeparateFromTheInstallerLabel() {
        InstallSourceInfoCompat info = new InstallSourceInfoCompat("com.example.filemanager");
        info.setInstallingPackageLabel("File Manager");
        info.setUpdateOwnerPackageLabel("App Store");

        assertEquals("File Manager", info.getInstallingPackageLabel());
        assertEquals("App Store", info.getUpdateOwnerPackageLabel());
    }

    @Test
    public void anUnsetUpdateOwnerLabelStaysNull() {
        // The detail row renders only when a label is present, so this is what keeps an empty
        // "Update owner" entry out of the installer dialog.
        assertNull(new InstallSourceInfoCompat("com.example.store").getUpdateOwnerPackageLabel());
    }
}
