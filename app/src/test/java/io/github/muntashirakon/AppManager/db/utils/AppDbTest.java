// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.users.Users;

// Off the main thread, as in the app: the database refuses queries from it
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
@Config(shadows = AppDbTest.ThisUserOnly.class)
public class AppDbTest {
    private static final String PACKAGE = "com.example.reinstalled";
    private static final int ME = UserHandleHidden.myUserId();

    @After
    public void forgetPackage() {
        // The database outlives a test
        AppDb appDb = new AppDb();
        for (App app : appDb.getAllApplications(PACKAGE)) {
            appDb.deleteApplication(PACKAGE, app.userId);
        }
        for (Backup backup : appDb.getAllBackups(PACKAGE)) {
            appDb.deleteBackup(backup);
        }
    }

    @Test
    public void anAppInstalledAgainAfterItsBackupWasListedIsReadFromThePackage() throws IOException {
        // Backed up, then uninstalled, so the list read it from its backup
        Backup backup = backup("test", 1_700_000_000_000L, "1.0");
        AppDb appDb = new AppDb();
        appDb.insert(backup);
        appDb.insert(App.fromBackup(backup));
        // Then installed again
        Application application = RuntimeEnvironment.getApplication();
        install(application);

        // What a package change and the list's own refresh both run
        App mine = mine(appDb.updateApplications(application, new String[]{PACKAGE}));

        assertNotNull(mine);
        assertTrue(mine.isInstalled);
    }

    @Test
    public void anAppInstalledAgainIsStoredAsInstalledHoweverManyBackupsItHas() throws IOException {
        AppDb appDb = new AppDb();
        Backup older = backup("older", 1_600_000_000_000L, "0.9");
        Backup latest = backup("latest", 1_700_000_000_000L, "1.0");
        appDb.insert(older);
        appDb.insert(latest);
        appDb.insert(App.fromBackup(latest));
        Application application = RuntimeEnvironment.getApplication();
        install(application);

        List<App> apps = appDb.updateApplications(application, new String[]{PACKAGE});

        // One row for this user, and the one stored is the one returned
        assertEquals(1, apps.size());
        assertTrue(apps.get(0).isInstalled);
        App stored = mine(appDb.getAllApplications(PACKAGE));
        assertNotNull(stored);
        assertTrue(stored.isInstalled);
    }

    @Test
    public void anUninstalledAppWithSeveralBackupsIsListedFromItsLatest() {
        AppDb appDb = new AppDb();
        // The older one comes first by insertion and by name, so the database hands it back first
        appDb.insert(backup("a_older", 1_600_000_000_000L, "0.9"));
        appDb.insert(backup("b_latest", 1_700_000_000_000L, "1.0"));

        List<App> apps = appDb.updateApplications(RuntimeEnvironment.getApplication(), new String[]{PACKAGE});

        assertEquals(1, apps.size());
        assertFalse(apps.get(0).isInstalled);
        assertEquals("1.0", apps.get(0).versionName);
        App stored = mine(appDb.getAllApplications(PACKAGE));
        assertNotNull(stored);
        assertEquals("1.0", stored.versionName);
    }

    @Test
    public void aRemovedUsersBackupsAreListedOnceFromTheLatest() {
        // A user the device no longer has, so the rest of the backups stand for them
        int removed = ME + 10;
        AppDb appDb = new AppDb();
        appDb.insert(backup(removed, "a_older", 1_600_000_000_000L, "0.9"));
        appDb.insert(backup(removed, "b_latest", 1_700_000_000_000L, "1.0"));

        List<App> apps = appDb.updateApplications(RuntimeEnvironment.getApplication(), new String[]{PACKAGE});

        assertEquals(1, apps.size());
        assertEquals(removed, apps.get(0).userId);
        assertEquals("1.0", apps.get(0).versionName);
    }

    @NonNull
    private static Backup backup(@NonNull String name, long time, @NonNull String versionName) {
        return backup(ME, name, time, versionName);
    }

    @NonNull
    private static Backup backup(int userId, @NonNull String name, long time, @NonNull String versionName) {
        Backup backup = new Backup();
        backup.packageName = PACKAGE;
        backup.backupName = userId + "_" + name;
        backup.userId = userId;
        backup.backupTime = time;
        backup.label = "Reinstalled";
        backup.versionName = versionName;
        return backup;
    }

    @Test
    @Config(shadows = AppDbTest.UnreadableSsaids.class)
    public void aPackageChangeIsStillReadWhenTheSsaidsCannotBe() throws IOException {
        Application application = RuntimeEnvironment.getApplication();
        install(application);

        App mine = mine(new AppDb().updateApplications(application, new String[]{PACKAGE}));

        assertNotNull(mine);
        assertTrue(mine.isInstalled);
    }

    /** Flagged, with a process and an APK on disk, which is what an installed package has. */
    private static void install(@NonNull Application application) throws IOException {
        Path apk = Files.createTempFile("appmanagerng-installed", ".apk");
        apk.toFile().deleteOnExit();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE;
        packageInfo.versionName = "1.0";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = PACKAGE;
        packageInfo.applicationInfo.processName = PACKAGE;
        packageInfo.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        packageInfo.applicationInfo.sourceDir = apk.toString();
        packageInfo.applicationInfo.publicSourceDir = apk.toString();
        shadowOf(application.getPackageManager()).installPackage(packageInfo);
    }

    @Nullable
    private static App mine(@NonNull List<App> apps) {
        for (App app : apps) {
            if (app.userId == ME) {
                return app;
            }
        }
        return null;
    }

    /** Robolectric's user service lists no users, and a device always has this one. */
    @Implements(Users.class)
    public static class ThisUserOnly {
        @Implementation
        protected static int[] getUsersIds() {
            return new int[]{ME};
        }
    }

    /** The file can't be read, and the message names it with the encoded colon of a file URI. */
    @Implements(SsaidSettings.class)
    public static class UnreadableSsaids {
        @Implementation
        protected void __constructor__(int userId) throws IOException {
            throw new IOException("file:///data/system/users/" + userId + "/settings_ssaid.xml%3A unreadable");
        }
    }
}
