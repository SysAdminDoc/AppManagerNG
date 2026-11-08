// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.AppPref;

// The main screen warns that the backup volume isn't available whenever this check fails. With
// storage access granted, the check looked for the AppManager folder inside the volume, which the
// first backup creates, so a fresh install warned about a volume that was there all along.
@RunWith(RobolectricTestRunner.class)
public class BackupVolumeCheckTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private String mSavedVolume;

    @Before
    public void setUp() {
        mSavedVolume = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR);
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @After
    public void tearDown() {
        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, mSavedVolume);
    }

    @Test
    public void aVolumeWithNoBackupsYetIsAvailable() throws IOException {
        File volume = tmp.newFolder("emulated0");
        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, Uri.fromFile(volume).toString());
        assertTrue("storage access is the case that went wrong", SelfPermissions.checkStoragePermission());

        assertTrue(Prefs.BackupRestore.backupVolumeExists());
        assertFalse("checking shouldn't create anything", new File(volume, "AppManager").exists());
    }

    @Test
    public void aVolumeThatIsGoneIsNotAvailable() {
        File volume = new File(tmp.getRoot(), "unplugged-sd-card");
        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, Uri.fromFile(volume).toString());

        assertFalse(Prefs.BackupRestore.backupVolumeExists());
    }
}
