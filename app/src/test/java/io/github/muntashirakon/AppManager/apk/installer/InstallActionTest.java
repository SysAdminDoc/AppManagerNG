// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/**
 * The installer's button says what the install does to the copy already here. A build with the same
 * version code but another version name is a modified build, not a reinstall of the same one (#47).
 */
@RunWith(RobolectricTestRunner.class)
public class InstallActionTest {
    @Test
    public void eachKindOfInstallGetsItsOwnAction() {
        assertEquals(InstallAction.INSTALL, InstallAction.of(false, 0, null, 5, "1.0", false));
        assertEquals(InstallAction.INSTALL, InstallAction.of(false, 9, "2.0", 5, "1.0", true));
        assertEquals(InstallAction.UPDATE, InstallAction.of(true, 4, "0.9", 5, "1.0", false));
        assertEquals(InstallAction.UPDATE, InstallAction.of(true, 4, "1.0", 5, "1.0", false));
        assertEquals(InstallAction.MODIFY, InstallAction.of(true, 5, "1.0", 5, "1.0-patched", false));
        assertEquals(InstallAction.MODIFY, InstallAction.of(true, 5, null, 5, "1.0", false));
        assertEquals(InstallAction.REINSTALL, InstallAction.of(true, 5, "1.0", 5, "1.0", false));
        assertEquals(InstallAction.REINSTALL, InstallAction.of(true, 5, null, 5, "", false));
        assertEquals(InstallAction.DOWNGRADE, InstallAction.of(true, 6, "1.1", 5, "1.0", false));
        assertEquals(InstallAction.DOWNGRADE, InstallAction.of(true, 6, "1.0", 5, "1.0", false));
    }

    @Test
    public void anotherSignerKeepsTheReinstallPath() {
        // Android won't take it over the installed copy either way, so the uninstall offer stays
        assertEquals(InstallAction.REINSTALL, InstallAction.of(true, 5, "1.0", 5, "1.0-patched", true));
        assertEquals(InstallAction.UPDATE, InstallAction.of(true, 4, "1.0", 5, "1.1", true));
        assertEquals(InstallAction.DOWNGRADE, InstallAction.of(true, 6, "1.1", 5, "1.0", true));
    }

    @Test
    public void eachActionHasItsButtonLabel() {
        assertEquals(R.string.install, InstallAction.INSTALL.labelRes);
        assertEquals(R.string.update, InstallAction.UPDATE.labelRes);
        assertEquals(R.string.modify, InstallAction.MODIFY.labelRes);
        assertEquals(R.string.reinstall, InstallAction.REINSTALL.labelRes);
        assertEquals(R.string.downgrade, InstallAction.DOWNGRADE.labelRes);
    }

    @Test
    public void aModifiedBuildShowsTheOldAndTheNewName() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals(context.getString(R.string.version_name_change_with_code, "1.0", "1.0-patched", 5L),
                InstallAction.MODIFY.versionLine(context, "1.0", "1.0-patched", 5));
        for (InstallAction action : new InstallAction[]{InstallAction.INSTALL, InstallAction.UPDATE,
                InstallAction.REINSTALL, InstallAction.DOWNGRADE}) {
            assertEquals(action.name(), context.getString(R.string.version_name_with_code, "1.0-patched", 5L),
                    action.versionLine(context, "1.0", "1.0-patched", 5));
        }
    }
}
