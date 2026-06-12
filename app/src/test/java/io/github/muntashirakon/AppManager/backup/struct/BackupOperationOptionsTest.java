// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.BackupFlags;

@RunWith(RobolectricTestRunner.class)
public class BackupOperationOptionsTest {
    @Test
    public void backupOptionsNormalizeConstructorJsonAndParcelValues() throws Exception {
        BackupOpOptions options = new BackupOpOptions(" dnsfilter.android ", 0,
                BackupFlags.BACKUP_INT_DATA, " nightly ", true,
                new String[]{" /cache//tmp ", " #comment ", null}, true,
                "  Before upgrade\r\nKeep  ");

        assertEquals("dnsfilter.android", options.packageName);
        assertEquals("nightly", options.backupName);
        assertArrayEquals(new String[]{"cache/tmp"}, options.exclusionGlobs);
        assertTrue(options.protectFromPrune);
        assertEquals("Before upgrade\nKeep", options.backupNote);

        BackupOpOptions jsonRestored = new BackupOpOptions(new JSONObject(options.serializeToJson().toString()));
        assertEquals("dnsfilter.android", jsonRestored.packageName);
        assertEquals("nightly", jsonRestored.backupName);
        assertArrayEquals(new String[]{"cache/tmp"}, jsonRestored.exclusionGlobs);
        assertTrue(jsonRestored.protectFromPrune);
        assertEquals("Before upgrade\nKeep", jsonRestored.backupNote);

        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            BackupOpOptions parcelRestored = BackupOpOptions.CREATOR.createFromParcel(parcel);
            assertEquals("dnsfilter.android", parcelRestored.packageName);
            assertEquals("nightly", parcelRestored.backupName);
            assertArrayEquals(new String[]{"cache/tmp"}, parcelRestored.exclusionGlobs);
            assertTrue(parcelRestored.protectFromPrune);
            assertEquals("Before upgrade\nKeep", parcelRestored.backupNote);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void restoreOptionsNormalizeConstructorJsonAndParcelValues() throws Exception {
        RestoreOpOptions options = new RestoreOpOptions(" dnsfilter.android ", 0,
                " dnsfilter.android\\0_test ", BackupFlags.BACKUP_INT_DATA);

        assertEquals("dnsfilter.android", options.packageName);
        assertEquals("dnsfilter.android/0_test", options.relativeDir);

        RestoreOpOptions jsonRestored = new RestoreOpOptions(new JSONObject(options.serializeToJson().toString()));
        assertEquals("dnsfilter.android", jsonRestored.packageName);
        assertEquals("dnsfilter.android/0_test", jsonRestored.relativeDir);

        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            RestoreOpOptions parcelRestored = RestoreOpOptions.CREATOR.createFromParcel(parcel);
            assertEquals("dnsfilter.android", parcelRestored.packageName);
            assertEquals("dnsfilter.android/0_test", parcelRestored.relativeDir);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void deleteOptionsNormalizeRelativeDirsAndPreserveNullParcelValues() throws Exception {
        DeleteOpOptions options = new DeleteOpOptions(" dnsfilter.android ", 0,
                new String[]{" dnsfilter.android\\0 ", " backups\\01234567-89ab-cdef-0123-456789abcdef "});

        assertEquals("dnsfilter.android", options.packageName);
        assertArrayEquals(new String[]{"dnsfilter.android/0", "backups/01234567-89ab-cdef-0123-456789abcdef"},
                options.relativeDirs);

        DeleteOpOptions jsonRestored = new DeleteOpOptions(new JSONObject(options.serializeToJson().toString()));
        assertArrayEquals(new String[]{"dnsfilter.android/0", "backups/01234567-89ab-cdef-0123-456789abcdef"},
                jsonRestored.relativeDirs);

        Parcel parcel = Parcel.obtain();
        try {
            new DeleteOpOptions("dnsfilter.android", 0, null).writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DeleteOpOptions parcelRestored = DeleteOpOptions.CREATOR.createFromParcel(parcel);
            assertNull(parcelRestored.relativeDirs);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void constructorsRejectMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> new BackupOpOptions(
                "bad package", 0, BackupFlags.BACKUP_INT_DATA, null, true));
        assertThrows(IllegalArgumentException.class, () -> new BackupOpOptions(
                "dnsfilter.android", -1, BackupFlags.BACKUP_INT_DATA, null, true));
        assertThrows(IllegalArgumentException.class, () -> new BackupOpOptions(
                "dnsfilter.android", 0, -1, null, true));
        assertThrows(IllegalArgumentException.class, () -> new BackupOpOptions(
                "dnsfilter.android", 0, BackupFlags.BACKUP_INT_DATA, "   ", true));
        assertThrows(IllegalArgumentException.class, () -> new RestoreOpOptions(
                "dnsfilter.android", 0, "../outside", BackupFlags.BACKUP_INT_DATA));
        assertThrows(IllegalArgumentException.class, () -> new DeleteOpOptions(
                "dnsfilter.android", 0, new String[]{null}));
    }

    @Test
    public void jsonRejectsMalformedValues() {
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "bad package", 0, BackupFlags.BACKUP_INT_DATA, null, true, null)));
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "dnsfilter.android", -1, BackupFlags.BACKUP_INT_DATA, null, true, null)));
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "dnsfilter.android", 0, -1, null, true, null)));
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "dnsfilter.android", 0, BackupFlags.BACKUP_INT_DATA, "   ", true, null)));
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "dnsfilter.android", 0, BackupFlags.BACKUP_INT_DATA, null, true, "cache")));
        assertThrows(JSONException.class, () -> new BackupOpOptions(backupJson(
                "dnsfilter.android", 0, BackupFlags.BACKUP_INT_DATA, null, true,
                new JSONArray().put(false))));
        assertThrows(JSONException.class, () -> new RestoreOpOptions(restoreJson(
                "dnsfilter.android", 0, "../outside", BackupFlags.BACKUP_INT_DATA)));
        assertThrows(JSONException.class, () -> new DeleteOpOptions(deleteJson(
                "dnsfilter.android", 0, new JSONArray().put(JSONObject.NULL))));
    }

    @Test
    public void parcelRejectsMalformedValues() {
        Parcel badPackageParcel = backupParcel("bad package", 0, BackupFlags.BACKUP_INT_DATA,
                null, true, null);
        try {
            assertThrows(IllegalArgumentException.class, () -> BackupOpOptions.CREATOR.createFromParcel(badPackageParcel));
        } finally {
            badPackageParcel.recycle();
        }

        Parcel badRelativeDirParcel = restoreParcel("dnsfilter.android", 0,
                "../outside", BackupFlags.BACKUP_INT_DATA);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> RestoreOpOptions.CREATOR.createFromParcel(badRelativeDirParcel));
        } finally {
            badRelativeDirParcel.recycle();
        }

        Parcel nullRelativeDirParcel = deleteParcel("dnsfilter.android", 0, new String[]{null});
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> DeleteOpOptions.CREATOR.createFromParcel(nullRelativeDirParcel));
        } finally {
            nullRelativeDirParcel.recycle();
        }
    }

    private static JSONObject backupJson(String packageName, int userId, int flags, String backupName,
                                         boolean override, Object exclusionGlobs) throws JSONException {
        JSONObject jsonObject = new JSONObject()
                .put("package_name", packageName)
                .put("user_id", userId)
                .put("flags", flags)
                .put("override", override);
        if (backupName != null) {
            jsonObject.put("backup_name", backupName);
        }
        if (exclusionGlobs != null) {
            jsonObject.put("exclusion_globs", exclusionGlobs);
        }
        return jsonObject;
    }

    private static JSONObject restoreJson(String packageName, int userId, String relativeDir, int flags)
            throws JSONException {
        return new JSONObject()
                .put("package_name", packageName)
                .put("user_id", userId)
                .put("relative_dir", relativeDir)
                .put("flags", flags);
    }

    private static JSONObject deleteJson(String packageName, int userId, JSONArray relativeDirs) throws JSONException {
        return new JSONObject()
                .put("package_name", packageName)
                .put("user_id", userId)
                .put("relative_dirs", relativeDirs);
    }

    private static Parcel backupParcel(String packageName, int userId, int flags, String backupName, boolean override,
                                       String[] exclusionGlobs) {
        Parcel parcel = Parcel.obtain();
        parcel.writeString(packageName);
        parcel.writeInt(userId);
        parcel.writeInt(flags);
        parcel.writeString(backupName);
        ParcelCompat.writeBoolean(parcel, override);
        parcel.writeStringArray(exclusionGlobs);
        ParcelCompat.writeBoolean(parcel, false);
        parcel.writeString(null);
        parcel.setDataPosition(0);
        return parcel;
    }

    private static Parcel restoreParcel(String packageName, int userId, String relativeDir, int flags) {
        Parcel parcel = Parcel.obtain();
        parcel.writeString(packageName);
        parcel.writeInt(userId);
        parcel.writeString(relativeDir);
        parcel.writeInt(flags);
        parcel.setDataPosition(0);
        return parcel;
    }

    private static Parcel deleteParcel(String packageName, int userId, String[] relativeDirs) {
        Parcel parcel = Parcel.obtain();
        parcel.writeString(packageName);
        parcel.writeInt(userId);
        parcel.writeStringArray(relativeDirs);
        parcel.setDataPosition(0);
        return parcel;
    }
}
