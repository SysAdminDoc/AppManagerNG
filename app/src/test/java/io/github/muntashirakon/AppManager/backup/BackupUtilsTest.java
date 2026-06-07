// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupUtilsTest {

    @Test
    public void getWritableDataDirectory() {
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/data/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/0/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/10/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/0/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/10/com.example.package", 0, 10));
        assertEquals("/mnt/expand/volume-1/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/mnt/expand/volume-1/user/0/com.example.package", 0, 10));
        assertEquals("/mnt/expand/volume-1/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/mnt/expand/volume-1/user_de/0/com.example.package", 0, 10));
        assertEquals("/mnt/expand/volume-1/user/0/com.example.package", BackupUtils.getWritableDataDirectory("/mnt/expand/volume-1/user/10/com.example.package", 10, 0));
        // Single user
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
        // Multiple user todo
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
    }

    @Test
    public void isRestorableDataDirectoryAcceptsGeneratedAppScopedRoots() {
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/data/data/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/data/user/0/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/data/user_de/10/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/mnt/expand/volume-1/user/0/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/mnt/expand/volume-1/user_de/10/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/sdcard/Android/data/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/storage/emulated/0/Android/media/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/storage/1234-5678/Android/obb/com.example"));
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", "/data/media/10/Android/data/com.example"));
    }

    @Test
    public void isRestorableDataDirectoryAcceptsOnlyKnownSpecialTokens() {
        assertTrue(BackupUtils.isRestorableDataDirectory("com.example", BackupManager.DATA_BACKUP_SPECIAL_ADB));
        assertTrue(BackupUtils.isRestorableDataDirectory("android", SystemDataBackup.TOKEN_WIFI_MISC));

        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", SystemDataBackup.TOKEN_WIFI_MISC));
        assertFalse(BackupUtils.isRestorableDataDirectory("android", "special:system:unknown"));
    }

    @Test
    public void isRestorableDataDirectoryRejectsArbitraryOrCrossPackageRoots() {
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/data/system"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/data/user/0/com.other"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/data/user/x/com.example"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/mnt/expand/../user/0/com.example"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/mnt/expand/volume-1/extra/user/0/com.example"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/mnt/expand/volume-1/user/0/com.other"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/storage/emulated/0/Download/com.example"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/storage/../Android/data/com.example"));
        assertFalse(BackupUtils.isRestorableDataDirectory("com.example", "/storage/1234-5678/Android/data/com.other"));
    }
}
