// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;

@RunWith(RobolectricTestRunner.class)
public class BatchBackupOptionsTest {
    @Test
    public void backupOpOptionsCarryExclusionGlobs() {
        String[] globs = {"**/.thumbnails/**", "databases/*.db-journal"};
        BatchBackupOptions options = new BatchBackupOptions(
                BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_MULTIPLE,
                new String[]{"nightly"}, null, globs);

        BackupOpOptions opOptions = options.getBackupOpOptions("example.pkg", 10);

        assertEquals("nightly", opOptions.backupName);
        assertArrayEquals(globs, opOptions.exclusionGlobs);
    }

    @Test
    public void parcelRetainsExclusionGlobs() {
        String[] globs = {"**/cache/**"};
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, globs);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BatchBackupOptions restored = BatchBackupOptions.CREATOR.createFromParcel(parcel);

        assertArrayEquals(globs, restored.getBackupOpOptions("example.pkg", 0).exclusionGlobs);
    }

    @Test
    public void jsonRetainsExclusionGlobs() throws Exception {
        String[] globs = {"**/cache/**"};
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, globs);

        BatchBackupOptions restored = new BatchBackupOptions(new JSONObject(options.serializeToJson().toString()));

        assertArrayEquals(globs, restored.getBackupOpOptions("example.pkg", 0).exclusionGlobs);
    }
}
