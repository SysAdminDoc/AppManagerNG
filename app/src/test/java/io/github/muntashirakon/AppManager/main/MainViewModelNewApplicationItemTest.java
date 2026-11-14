// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.test.shadows.ShadowPackageManagerCompat;

@RunWith(RobolectricTestRunner.class)
public class MainViewModelNewApplicationItemTest {
    private static final int ME = UserHandleHidden.myUserId();
    private static final int WORK = ME + 10;
    private static final String PACKAGE = "com.example.gone";

    @Test
    public void aPackageInstalledForNoUserTakesThisUsersLabelWhicheverRowComesFirst() {
        // Android keeps an archived app's launcher name for the user it was archived for only, so
        // only this user's row has it
        App mine = app(ME, false);
        mine.packageLabel = "Gone";
        mine.isOnlyDataInstalled = true;
        App work = app(WORK, false);

        ApplicationItem item = newItem(work, mine, app(WORK + 1, false));
        assertEquals("Gone", item.label);
        // Everything else is still the first row's, as before
        assertFalse(item.isOnlyDataInstalled);
        assertEquals(work.uid, item.uid);
        assertFalse(item.isInstalled);
        assertArrayEquals(new int[]{WORK, ME, WORK + 1}, item.uninstalledUserIds);
        assertEquals("Gone", newItem(mine, app(WORK, false)).label);
    }

    @Test
    public void thisUsersCopyWithoutANameKeepsTheFirstRowsLabel() {
        // Android falls back to the package name when the APK is gone
        App mine = app(ME, false);
        mine.packageLabel = PACKAGE;
        App work = app(WORK, false);
        work.packageLabel = "Gone";

        assertEquals("Gone", newItem(work, mine).label);
    }

    @Test
    public void aPackageInstalledForNoUserHasKeystoreItemsWhenAnyCopyHasThemWhicheverRowComesFirst() {
        for (int withKeystore : new int[]{ME, WORK}) {
            App mine = app(ME, false);
            App work = app(WORK, false);
            (withKeystore == ME ? mine : work).hasKeystore = true;

            assertTrue("mine first, keystore on " + withKeystore, newItem(mine, work).hasKeystore);
            assertTrue("work first, keystore on " + withKeystore, newItem(work, mine).hasKeystore);
        }
        assertFalse(newItem(app(ME, false), app(WORK, false)).hasKeystore);
    }

    @Test
    public void anInstalledRowsKeystoreMarkComesFromItsInstalledCopiesWhicheverRowComesFirst() {
        App installed = app(ME, true);
        App gone = app(WORK, false);
        gone.hasKeystore = true;
        assertFalse(newItem(installed, gone).hasKeystore);
        assertFalse(newItem(gone, installed).hasKeystore);

        installed.hasKeystore = true;
        gone.hasKeystore = false;
        assertTrue(newItem(installed, gone).hasKeystore);
        assertTrue(newItem(gone, installed).hasKeystore);
    }

    @Test
    public void theRowIsLookedUpForArchivedStateBeforeTheListBindsIt() {
        ShadowPackageManagerCompat.setPackageInfoForUser(PACKAGE, ME,
                ApplicationItemTest.uninstalledPackage(PACKAGE, ApplicationItemTest.ARCHIVE_TIME));

        // The list checks this on the main thread, where it makes no call
        assertTrue(newItem(app(ME, false)).isKnownArchived());
    }

    @Before
    @After
    public void forgetPerUserPackages() {
        ShadowPackageManagerCompat.resetPackageInfo();
    }

    @Test
    public void aRowInstalledForAnotherUserIsNotReplacedByThisUsersUninstalledCopy() {
        App mine = app(ME, false);
        mine.packageLabel = "Uninstalled here";
        App work = app(WORK, true);
        work.packageLabel = "Work";

        ApplicationItem item = newItem(work, mine);
        assertTrue(item.isInstalled);
        assertEquals("Work", item.label);
        assertArrayEquals(new int[]{WORK}, item.userIds);
        assertEquals(0, item.uninstalledUserIds.length);
    }

    @Test
    public void aPackageInstalledForAnyUserRecordsNoUninstalledUsersWhicheverRowComesFirst() {
        // As a full load does, so a refreshed row reads the way the next full load shows it
        for (ApplicationItem item : new ApplicationItem[]{newItem(app(WORK, false), app(ME, true)),
                newItem(app(ME, true), app(WORK, false))}) {
            assertTrue(item.isInstalled);
            assertArrayEquals(new int[]{ME}, item.userIds);
            assertEquals(0, item.uninstalledUserIds.length);
            assertFalse(item.hasPerUserPackageState());
        }
    }

    @NonNull
    private static ApplicationItem newItem(@NonNull App... apps) {
        Application application = ApplicationProvider.getApplicationContext();
        MainViewModel viewModel = new MainViewModel(application);
        // A worker thread, as in the app: it reads the backup database, which refuses the main thread
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            ApplicationItem item = worker.submit(() -> viewModel.getNewApplicationItem(PACKAGE, Arrays.asList(apps)))
                    .get(30, TimeUnit.SECONDS);
            assertNotNull(item);
            return item;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new AssertionError(e);
        } finally {
            worker.shutdownNow();
        }
    }

    @NonNull
    private static App app(int userId, boolean installed) {
        App app = new App();
        app.packageName = PACKAGE;
        app.packageLabel = PACKAGE + "@" + userId;
        app.userId = userId;
        app.isInstalled = installed;
        app.isEnabled = true;
        app.uid = UserHandleHidden.getUid(userId, 10_123);
        app.certName = "";
        app.certAlgo = "";
        return app;
    }
}
