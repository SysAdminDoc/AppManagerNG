// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.net.Uri;
import android.os.Parcel;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.convert.ImportType;

@RunWith(RobolectricTestRunner.class)
public class BatchBackupImportOptionsTest {
    @Test
    public void testParcelable() {
        Uri uri = Uri.parse("file:///sdcard/OAndBackup");
        BatchBackupImportOptions options = new BatchBackupImportOptions(ImportType.OAndBackup, uri, false);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchBackupImportOptions options2 = BatchBackupImportOptions.CREATOR.createFromParcel(parcel);
        assertEquals(ImportType.OAndBackup, options2.getImportType());
        assertEquals(uri, options2.getDirectory());
        assertFalse(options2.isRemoveImportedDirectory());
    }

    @Test
    public void constructorRejectsUnsupportedImportTypesAndEmptyDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupImportOptions(
                -1, Uri.parse("file:///sdcard/OAndBackup"), false));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupImportOptions(
                3, Uri.parse("file:///sdcard/OAndBackup"), false));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupImportOptions(
                ImportType.OAndBackup, Uri.parse(""), false));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupImportOptions(
                ImportType.OAndBackup, Uri.parse("   "), false));
    }

    @Test
    public void jsonRestorationAcceptsSupportedImportType() throws Exception {
        BatchBackupImportOptions options = BatchBackupImportOptions.DESERIALIZER.deserialize(jsonOptions(
                ImportType.SwiftBackup, "content://io.github.muntashirakon.AppManager/tree/backups", true));

        assertEquals(ImportType.SwiftBackup, options.getImportType());
        assertEquals(Uri.parse("content://io.github.muntashirakon.AppManager/tree/backups"), options.getDirectory());
        assertTrue(options.isRemoveImportedDirectory());
    }

    @Test
    public void jsonRestorationRejectsUnsupportedImportTypesAndEmptyDirectory() {
        assertThrows(JSONException.class, () -> BatchBackupImportOptions.DESERIALIZER.deserialize(jsonOptions(
                -1, "file:///sdcard/OAndBackup", false)));
        assertThrows(JSONException.class, () -> BatchBackupImportOptions.DESERIALIZER.deserialize(jsonOptions(
                3, "file:///sdcard/OAndBackup", false)));
        assertThrows(JSONException.class, () -> BatchBackupImportOptions.DESERIALIZER.deserialize(jsonOptions(
                ImportType.OAndBackup, "", false)));
        assertThrows(JSONException.class, () -> BatchBackupImportOptions.DESERIALIZER.deserialize(jsonOptions(
                ImportType.OAndBackup, "   ", false)));
    }

    @Test
    public void parcelRestorationRejectsUnsupportedImportType() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(3);
            parcel.writeParcelable(Uri.parse("file:///sdcard/OAndBackup"), 0);
            parcel.writeByte((byte) 0);
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class,
                    () -> BatchBackupImportOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRestorationRejectsEmptyDirectory() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(ImportType.OAndBackup);
            parcel.writeParcelable(Uri.parse(""), 0);
            parcel.writeByte((byte) 0);
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class,
                    () -> BatchBackupImportOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject jsonOptions(int importType, String directory,
                                          boolean removeImportedDirectory) throws JSONException {
        return new JSONObject()
                .put("tag", BatchBackupImportOptions.TAG)
                .put("import_type", importType)
                .put("directory", directory)
                .put("remove_imported_directory", removeImportedDirectory);
    }
}
