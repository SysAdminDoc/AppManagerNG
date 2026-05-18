// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class FmVolumeScanWarningTest {
    @Test
    public void isLikelyStorageVolumeRoot_detectsPrimaryExternalRoot() {
        File root = new File("/storage/emulated/0");

        assertTrue(FmVolumeScanWarning.isLikelyStorageVolumeRoot(root, root));
    }

    @Test
    public void isLikelyStorageVolumeRoot_detectsRemovableStorageRoot() {
        assertTrue(FmVolumeScanWarning.isLikelyStorageVolumeRoot(new File("/storage/1234-ABCD"), null));
        assertTrue(FmVolumeScanWarning.isLikelyStorageVolumeRoot(new File("/mnt/media_rw/1234-ABCD"), null));
    }

    @Test
    public void isLikelyStorageVolumeRoot_ignoresNestedFolders() {
        assertFalse(FmVolumeScanWarning.isLikelyStorageVolumeRoot(new File("/storage/emulated/0/Download"),
                new File("/storage/emulated/0")));
    }

    @Test
    public void estimateScanMinutes_hasMinimumAndRoundsUp() {
        assertEquals(1, FmVolumeScanWarning.estimateScanMinutes(1,
                FmVolumeScanWarning.DEFAULT_SCAN_BYTES_PER_SECOND));
        assertEquals(2, FmVolumeScanWarning.estimateScanMinutes(65L * 1024L * 1024L * 60L,
                FmVolumeScanWarning.DEFAULT_SCAN_BYTES_PER_SECOND));
    }
}
