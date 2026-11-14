// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.BuildConfig;

@RunWith(RobolectricTestRunner.class)
public class AppArchiveManagerTest {
    @Test
    public void supportStartsAtAndroid15() {
        assertFalse(AppArchiveManager.isSupported(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
        assertTrue(AppArchiveManager.isSupported(Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }

    @Test
    public void archiveActionIsCurrentUserUserAppOnly() {
        assertTrue(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                0, 0, false, false, false, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                0, 0, false, false, false, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                10, 0, false, false, false, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                0, 0, true, false, false, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                0, 0, false, true, false, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                0, 0, false, false, true, "com.example"));
        assertFalse(AppArchiveManager.canShowArchiveAction(Build.VERSION_CODES.VANILLA_ICE_CREAM,
                0, 0, false, false, false, BuildConfig.APPLICATION_ID));
    }

    @Test
    public void archivedStateComesFromArchiveTimestamp() {
        assertFalse(AppArchiveManager.isArchiveTimeArchived(0));
        assertTrue(AppArchiveManager.isArchiveTimeArchived(1));
    }

    @Test
    public void archiveResultUsesPackageInstallerStatus() {
        Intent success = new Intent();
        success.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_STATUS, 0);
        Intent pending = new Intent();
        pending.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_STATUS, -1);
        Intent failure = new Intent();
        failure.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_STATUS, 1);

        assertTrue(AppArchiveManager.isSuccess(success, AppArchiveManager.OP_ARCHIVE));
        assertTrue(AppArchiveManager.isPendingUserAction(pending));
        assertFalse(AppArchiveManager.isSuccess(failure, AppArchiveManager.OP_ARCHIVE));
    }

    @Test
    public void unarchiveResultUsesUnarchiveStatusWhenPresent() {
        Intent success = new Intent();
        success.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS, 0);
        Intent pending = new Intent();
        pending.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS, 1);
        Intent failure = new Intent();
        failure.putExtra(AppArchiveManager.EXTRA_PACKAGE_INSTALLER_UNARCHIVE_STATUS, 100);

        assertTrue(AppArchiveManager.isSuccess(success, AppArchiveManager.OP_UNARCHIVE));
        assertTrue(AppArchiveManager.isPendingUserAction(pending));
        assertFalse(AppArchiveManager.isSuccess(failure, AppArchiveManager.OP_UNARCHIVE));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void theArchivedLabelAndIconComeFromTheFirstLauncherEntryWithALabel() {
        ShadowArchivedPackages.reset();
        Drawable unnamedIcon = new ColorDrawable(Color.RED);
        Drawable namedIcon = new ColorDrawable(Color.BLUE);
        ShadowArchivedPackages.keep(PACKAGE, "", unnamedIcon);
        ShadowArchivedPackages.keep(PACKAGE, "Example", namedIcon);
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();

        assertEquals("Example", String.valueOf(AppArchiveManager.getArchivedLabel(pm, PACKAGE)));
        assertSame(namedIcon, AppArchiveManager.getArchivedIcon(pm, PACKAGE));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void whenTheNamedEntryKeptNoIconTheFirstOtherIconStandsIn() {
        ShadowArchivedPackages.reset();
        Drawable firstIcon = new ColorDrawable(Color.RED);
        ShadowArchivedPackages.keep(PACKAGE, "", null);
        ShadowArchivedPackages.keep(PACKAGE, "Example", null);
        ShadowArchivedPackages.keep(PACKAGE, "", firstIcon);
        ShadowArchivedPackages.keep(PACKAGE, "", new ColorDrawable(Color.BLUE));
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();

        assertEquals("Example", String.valueOf(AppArchiveManager.getArchivedLabel(pm, PACKAGE)));
        assertSame(firstIcon, AppArchiveManager.getArchivedIcon(pm, PACKAGE));

        ShadowArchivedPackages.reset();
        ShadowArchivedPackages.keep(PACKAGE, "Example", null);
        assertNull(AppArchiveManager.getArchivedIcon(pm, PACKAGE));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anEntryWithoutALabelStillGivesItsIconButNoLabel() {
        ShadowArchivedPackages.reset();
        Drawable icon = new ColorDrawable(Color.RED);
        ShadowArchivedPackages.keep(PACKAGE, null, icon);
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();

        assertNull(AppArchiveManager.getArchivedLabel(pm, PACKAGE));
        assertSame(icon, AppArchiveManager.getArchivedIcon(pm, PACKAGE));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void androidKeepingNoLauncherEntryGivesNothingInsteadOfThrowing() {
        ShadowArchivedPackages.reset();
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        // No entries at all
        assertNull(AppArchiveManager.getArchivedLabel(pm, PACKAGE));
        assertNull(AppArchiveManager.getArchivedIcon(pm, PACKAGE));

        ShadowArchivedPackages.keepNone();
        assertNull(AppArchiveManager.getArchivedLabel(pm, PACKAGE));
        assertNull(AppArchiveManager.getArchivedIcon(pm, PACKAGE));
        assertEquals(4, ShadowArchivedPackages.sAsked.size());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.TIRAMISU, shadows = ShadowArchivedPackages.class)
    public void beforeAndroid15NothingIsAsked() {
        ShadowArchivedPackages.reset();
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();

        assertNull(AppArchiveManager.getArchivedLabel(pm, PACKAGE));
        assertNull(AppArchiveManager.getArchivedIcon(pm, PACKAGE));
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
    }

    private static final String PACKAGE = "com.example.archived";
}
