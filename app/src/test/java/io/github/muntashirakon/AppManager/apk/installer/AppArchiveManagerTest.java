// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

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
}
