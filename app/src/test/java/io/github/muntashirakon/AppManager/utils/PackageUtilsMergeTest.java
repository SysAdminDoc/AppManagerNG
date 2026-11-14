// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.main.ApplicationItem;

@RunWith(RobolectricTestRunner.class)
public class PackageUtilsMergeTest {
    private static final int ME = UserHandleHidden.myUserId();
    private static final int WORK = ME + 10;

    @Test
    public void aPackageInstalledForNoUserKeepsEveryUserItWasFoundFor() {
        ApplicationItem item = merge(app("com.example.gone", ME, false), app("com.example.gone", WORK, false))
                .get("com.example.gone");

        assertFalse(item.isInstalled);
        assertArrayEquals(new int[]{ME, WORK}, item.uninstalledUserIds);
        assertEquals(-1, item.getUserId());
    }

    @Test
    public void aPackageInstalledForAnyUserRecordsNoUninstalledUsers() {
        // The full load doesn't record enabled or disabled users either, so a half list would make
        // the row show a user state summary it can't complete
        Map<String, ApplicationItem> items = merge(
                app("com.example.later", WORK, false), app("com.example.later", ME, true),
                app("com.example.first", ME, true), app("com.example.first", WORK, false));

        for (String packageName : Arrays.asList("com.example.later", "com.example.first")) {
            ApplicationItem item = items.get(packageName);
            assertTrue(packageName, item.isInstalled);
            assertArrayEquals(packageName, new int[]{ME}, item.userIds);
            assertEquals(packageName, 0, item.uninstalledUserIds.length);
            assertFalse(packageName, item.hasPerUserPackageState());
        }
    }

    @Test
    public void aPackageInstalledForNoUserTakesThisUsersLabelWhicheverRowComesFirst() {
        // Android keeps an archived app's launcher name for the user it was archived for only, so
        // only this user's row has it
        App mine = app("com.example.gone", ME, false);
        mine.packageLabel = "Gone";
        mine.isOnlyDataInstalled = true;
        App work = app("com.example.gone", WORK, false);
        Map<String, ApplicationItem> items = merge(work, mine, app("com.example.gone", WORK + 1, false));

        ApplicationItem item = items.get("com.example.gone");
        assertEquals("Gone", item.label);
        // Everything else is still the first row's, as before
        assertFalse(item.isOnlyDataInstalled);
        assertEquals(work.uid, item.uid);
        assertFalse(item.isInstalled);
        assertArrayEquals(new int[]{WORK, ME, WORK + 1}, item.uninstalledUserIds);
        assertEquals("Gone", merge(mine, app("com.example.gone", WORK, false)).get("com.example.gone").label);
    }

    @Test
    public void thisUsersCopyWithoutANameKeepsTheFirstRowsLabel() {
        // Android falls back to the package name when the APK is gone
        App mine = app("com.example.gone", ME, false);
        mine.packageLabel = mine.packageName;
        App work = app("com.example.gone", WORK, false);
        work.packageLabel = "Gone";

        assertEquals("Gone", merge(work, mine).get("com.example.gone").label);
    }

    @Test
    public void aPackageInstalledForNoUserHasKeystoreItemsWhenAnyCopyHasThemWhicheverRowComesFirst() {
        for (int withKeystore : new int[]{ME, WORK}) {
            App mine = app("com.example.gone", ME, false);
            App work = app("com.example.gone", WORK, false);
            (withKeystore == ME ? mine : work).hasKeystore = true;

            assertTrue("mine first, keystore on " + withKeystore, merge(mine, work).get("com.example.gone").hasKeystore);
            assertTrue("work first, keystore on " + withKeystore, merge(work, mine).get("com.example.gone").hasKeystore);
        }
        assertFalse(merge(app("com.example.gone", ME, false), app("com.example.gone", WORK, false))
                .get("com.example.gone").hasKeystore);
    }

    @Test
    public void aBackupStaysOnARowThisUsersCopyFillsInSecond() {
        for (boolean installed : new boolean[]{true, false}) {
            Backup backup = new Backup();
            backup.packageName = "com.example.saved";
            Map<String, Backup> backups = new HashMap<>();
            backups.put(backup.packageName, backup);
            Map<String, ApplicationItem> items = new HashMap<>();
            PackageUtils.mergeApplicationItems(items, Arrays.asList(
                    app("com.example.saved", WORK, installed), app("com.example.saved", ME, installed)), backups);

            assertSame("installed=" + installed, backup, items.get("com.example.saved").backup);
            assertTrue("installed=" + installed, backups.isEmpty());
        }
    }

    @Test
    public void anInstalledRowsKeystoreMarkComesFromItsInstalledCopiesWhicheverRowComesFirst() {
        App installed = app("com.example.both", ME, true);
        App gone = app("com.example.both", WORK, false);
        gone.hasKeystore = true;
        assertFalse(merge(installed, gone).get("com.example.both").hasKeystore);
        assertFalse(merge(gone, installed).get("com.example.both").hasKeystore);

        installed.hasKeystore = true;
        gone.hasKeystore = false;
        assertTrue(merge(installed, gone).get("com.example.both").hasKeystore);
        assertTrue(merge(gone, installed).get("com.example.both").hasKeystore);
    }

    @Test
    public void aBackupStaysOnARowWhoseUsersHaveItInstalledAndNotWhicheverRowComesFirst() {
        for (boolean installedFirst : new boolean[]{true, false}) {
            Backup backup = new Backup();
            backup.packageName = "com.example.saved";
            Map<String, Backup> backups = new HashMap<>();
            backups.put(backup.packageName, backup);
            App installed = app("com.example.saved", ME, true);
            App gone = app("com.example.saved", WORK, false);
            Map<String, ApplicationItem> items = new HashMap<>();
            PackageUtils.mergeApplicationItems(items, installedFirst
                    ? Arrays.asList(installed, gone) : Arrays.asList(gone, installed), backups);

            ApplicationItem item = items.get("com.example.saved");
            assertTrue("installed first=" + installedFirst, item.isInstalled);
            assertSame("installed first=" + installedFirst, backup, item.backup);
            assertTrue("installed first=" + installedFirst, backups.isEmpty());
        }
    }

    @Test
    public void aRowInstalledForAnotherUserIsNotReplacedByThisUsersUninstalledCopy() {
        App mine = app("com.example.work", ME, false);
        mine.packageLabel = "Uninstalled here";
        App work = app("com.example.work", WORK, true);
        work.packageLabel = "Work";

        ApplicationItem item = merge(work, mine).get("com.example.work");
        assertTrue(item.isInstalled);
        assertEquals("Work", item.label);
        assertArrayEquals(new int[]{WORK}, item.userIds);
        assertEquals(0, item.uninstalledUserIds.length);
    }

    @NonNull
    private static Map<String, ApplicationItem> merge(@NonNull App... apps) {
        Map<String, ApplicationItem> items = new HashMap<>();
        PackageUtils.mergeApplicationItems(items, Arrays.asList(apps), new HashMap<>());
        return items;
    }

    @NonNull
    private static App app(@NonNull String packageName, int userId, boolean installed) {
        App app = new App();
        app.packageName = packageName;
        app.packageLabel = packageName + "@" + userId;
        app.userId = userId;
        app.isInstalled = installed;
        app.isEnabled = true;
        app.uid = UserHandleHidden.getUid(userId, 10_123);
        // What App.fromPackageInfo stores for a package with no signer it can read
        app.certName = "";
        app.certAlgo = "";
        return app;
    }
}
