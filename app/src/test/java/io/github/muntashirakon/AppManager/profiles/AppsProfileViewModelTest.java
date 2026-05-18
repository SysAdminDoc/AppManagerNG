// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.Backup;

@RunWith(RobolectricTestRunner.class)
public class AppsProfileViewModelTest {
    @Test
    public void mergeSelectablePackagesAddsBackupOnlyPackages() {
        ApplicationInfo installedInfo = new ApplicationInfo();
        installedInfo.packageName = "com.example.installed";
        installedInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        AppsProfileViewModel.SelectablePackageItem installedItem =
                AppsProfileViewModel.SelectablePackageItem.fromApplicationInfo("Installed", installedInfo);

        Backup installedBackup = new Backup();
        installedBackup.packageName = "com.example.installed";
        installedBackup.label = "Installed backup";

        Backup backupOnly = new Backup();
        backupOnly.packageName = "com.example.backuponly";
        backupOnly.label = "Backup Only";

        HashMap<String, Backup> backups = new HashMap<>();
        backups.put(installedBackup.packageName, installedBackup);
        backups.put(backupOnly.packageName, backupOnly);

        List<AppsProfileViewModel.SelectablePackageItem> merged =
                AppsProfileViewModel.mergeSelectablePackages(Collections.singletonList(installedItem), backups,
                        Collections.emptyList());

        assertEquals(2, merged.size());
        assertPackage(merged, "com.example.installed", false);
        assertPackage(merged, "com.example.backuponly", true);
    }

    @Test
    public void mergeSelectablePackagesKeepsMissingSelectedPackageVisible() {
        List<AppsProfileViewModel.SelectablePackageItem> merged =
                AppsProfileViewModel.mergeSelectablePackages(Collections.emptyList(), Collections.emptyMap(),
                        Arrays.asList("com.example.missing"));

        assertEquals(1, merged.size());
        assertEquals("com.example.missing", merged.get(0).packageName);
        assertTrue(merged.get(0).backupOnly);
    }

    private static void assertPackage(List<AppsProfileViewModel.SelectablePackageItem> items,
                                      String packageName, boolean backupOnly) {
        for (AppsProfileViewModel.SelectablePackageItem item : items) {
            if (packageName.equals(item.packageName)) {
                assertEquals(backupOnly, item.backupOnly);
                if (backupOnly) {
                    assertEquals("Backup Only", item.label);
                } else {
                    assertFalse(item.backupOnly);
                }
                return;
            }
        }
        throw new AssertionError("Missing package " + packageName);
    }
}
