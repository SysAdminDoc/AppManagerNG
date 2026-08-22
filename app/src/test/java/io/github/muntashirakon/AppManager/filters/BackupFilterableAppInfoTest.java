// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_APK_FILES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.File;

import io.github.muntashirakon.AppManager.db.entity.Backup;

@RunWith(RobolectricTestRunner.class)
public class BackupFilterableAppInfoTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void usesCurrentPackageStateWhenBackupPackageIsInstalled() throws Exception {
        File sourceApk = temporaryFolder.newFile("installed.apk");
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "com.example.installed";
        packageInfo.versionName = "installed-version";
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageInfo.packageName;
        applicationInfo.uid = 12345;
        applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED | ApplicationInfo.FLAG_ALLOW_BACKUP
                | ApplicationInfo.FLAG_HAS_CODE;
        applicationInfo.enabled = true;
        applicationInfo.processName = packageInfo.packageName;
        applicationInfo.publicSourceDir = sourceApk.getAbsolutePath();
        packageInfo.applicationInfo = applicationInfo;
        Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager())
                .installPackage(packageInfo);

        Backup backup = backup(packageInfo.packageName, "backup-version", 17, BACKUP_APK_FILES);
        BackupFilterableAppInfo info = new BackupFilterableAppInfo(backup);

        assertTrue(info.isInstalled());
        assertTrue(info.backupAllowed());
        assertTrue(info.hasCode());
        assertEquals("backup-version", info.getVersionName());
        assertEquals(17, info.getVersionCode());
    }

    @Test
    public void usesBackupMetadataWhenPackageIsNotInstalled() {
        Backup backup = backup("com.example.backup", "backup-version", 17, BACKUP_APK_FILES);
        BackupFilterableAppInfo info = new BackupFilterableAppInfo(backup);

        assertFalse(info.isInstalled());
        assertTrue(info.backupAllowed());
        assertFalse(info.isFrozen());
        assertEquals(0, info.getFreezeFlags());
        assertNull(info.fetchSignerInfo());
    }

    private static Backup backup(String packageName, String versionName, long versionCode, int flags) {
        Backup backup = new Backup();
        backup.packageName = packageName;
        backup.backupName = "fixture";
        backup.label = "Fixture";
        backup.versionName = versionName;
        backup.versionCode = versionCode;
        backup.backupTime = 1234;
        backup.userId = 0;
        backup.flags = flags;
        return backup;
    }
}
