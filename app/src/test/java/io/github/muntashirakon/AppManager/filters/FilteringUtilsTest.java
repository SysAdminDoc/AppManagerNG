// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.Backup;

public class FilteringUtilsTest {
    @Test
    public void selectLatestBackupOnlyEntriesSkipsExistingAndMissingBackups() {
        Backup installed = backup("pkg.installed", 0, "installed", 10);
        Backup older = backup("pkg.backup", 0, "old", 10);
        Backup latest = backup("pkg.backup", 0, "latest", 20);
        Backup missing = backup("pkg.missing", 0, "missing", 30);
        HashSet<String> existingPackageUsers = new HashSet<>(
                Collections.singletonList(FilteringUtils.getPackageUserKey("pkg.installed", 0)));

        List<Backup> result = FilteringUtils.selectLatestBackupOnlyEntries(
                Arrays.asList(installed, older, latest, missing),
                existingPackageUsers,
                new int[]{0},
                backup -> !"missing".equals(backup.backupName));

        assertEquals(1, result.size());
        assertSame(latest, result.get(0));
    }

    @Test
    public void selectLatestBackupOnlyEntriesKeepsUsersSeparate() {
        Backup user0 = backup("pkg.backup", 0, "user-0", 10);
        Backup user10 = backup("pkg.backup", 10, "user-10", 20);

        List<Backup> result = FilteringUtils.selectLatestBackupOnlyEntries(
                Arrays.asList(user0, user10),
                Collections.emptySet(),
                new int[]{0, 10},
                backup -> true);

        assertEquals(2, result.size());
        assertSame(user0, result.get(0));
        assertSame(user10, result.get(1));
    }

    @Test
    public void selectLatestBackupOnlyEntriesHonorsRequestedUsers() {
        Backup user0 = backup("pkg.user0", 0, "user-0", 10);
        Backup user10 = backup("pkg.user10", 10, "user-10", 20);

        List<Backup> result = FilteringUtils.selectLatestBackupOnlyEntries(
                Arrays.asList(user0, user10),
                Collections.emptySet(),
                new int[]{10},
                backup -> true);

        assertEquals(1, result.size());
        assertSame(user10, result.get(0));
    }

    @Test
    public void packageUserKeyIncludesPackageAndUser() {
        assertEquals("pkg.name:10", FilteringUtils.getPackageUserKey("pkg.name", 10));
    }

    private static Backup backup(String packageName, int userId, String backupName, long backupTime) {
        Backup backup = new Backup();
        backup.packageName = packageName;
        backup.userId = userId;
        backup.backupName = backupName;
        backup.label = packageName;
        backup.versionName = "1.0";
        backup.versionCode = backupTime;
        backup.backupTime = backupTime;
        return backup;
    }
}
