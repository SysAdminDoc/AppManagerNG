// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;
import android.content.Context;

import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupTagPolicyStore;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

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
    public void backupOpOptionsCarryProtectionAndNote() {
        BatchBackupOptions options = new BatchBackupOptions(
                BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_MULTIPLE,
                new String[]{"nightly"}, null, null, true, "  Before upgrade\r\nKeep  ");

        BackupOpOptions opOptions = options.getBackupOpOptions("example.pkg", 10);

        assertTrue(opOptions.protectFromPrune);
        assertEquals("Before upgrade\nKeep", opOptions.backupNote);
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
    public void parcelRetainsProtectionAndNote() {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, null, true, "  Critical state  ");
        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BackupOpOptions restored = BatchBackupOptions.CREATOR.createFromParcel(parcel)
                    .getBackupOpOptions("example.pkg", 0);

            assertTrue(restored.protectFromPrune);
            assertEquals("Critical state", restored.backupNote);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void jsonRetainsExclusionGlobs() throws Exception {
        String[] globs = {"**/cache/**"};
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, globs);

        BatchBackupOptions restored = new BatchBackupOptions(new JSONObject(options.serializeToJson().toString()));

        assertArrayEquals(globs, restored.getBackupOpOptions("example.pkg", 0).exclusionGlobs);
    }

    @Test
    public void jsonRetainsProtectionAndNote() throws Exception {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, null, true, "  Critical state  ");

        BatchBackupOptions restored = new BatchBackupOptions(new JSONObject(options.serializeToJson().toString()));
        BackupOpOptions restoredOpOptions = restored.getBackupOpOptions("example.pkg", 0);

        assertTrue(restoredOpOptions.protectFromPrune);
        assertEquals("Critical state", restoredOpOptions.backupNote);
    }

    @Test
    public void policyAwareModeSurvivesJsonAndParcelAndResolvesPerPackage() throws Exception {
        Context context = ContextUtils.getContext();
        context.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("app_tags", Context.MODE_PRIVATE).edit().clear().commit();
        new AppTagStore(context).addTag("example.pkg", "work");
        new BackupTagPolicyStore(context).add(new BackupTagPolicyStore.Policy("work",
                BackupFlags.BACKUP_APK_FILES, CryptoUtils.MODE_NO_ENCRYPTION, 4, 21, null));
        BatchBackupOptions options = BatchBackupOptions.forTagPolicies(
                BackupFlags.BACKUP_EXT_DATA | BackupFlags.BACKUP_MULTIPLE,
                new String[]{"nightly"}, null, null, false, null);

        BatchBackupOptions json = new BatchBackupOptions(options.serializeToJson());
        BackupOpOptions jsonOp = json.getBackupOpOptions("example.pkg", 0);
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_MULTIPLE,
                jsonOp.flags.getFlags());
        assertEquals(4, jsonOp.retentionMaxCount);
        assertEquals(21, jsonOp.retentionMaxAgeDays);

        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            BackupOpOptions parcelOp = BatchBackupOptions.CREATOR.createFromParcel(parcel)
                    .getBackupOpOptions("example.pkg", 0);
            assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_MULTIPLE,
                    parcelOp.flags.getFlags());
            assertEquals(4, parcelOp.retentionMaxCount);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void deleteScopeDefaultsToBaseOnlyAndSerializes() throws Exception {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null);

        DeleteOpOptions deleteOpOptions = options.getDeleteOpOptions("example.pkg", 0);

        assertNull(deleteOpOptions.relativeDirs);
        assertEquals(DeleteOpOptions.DELETE_SCOPE_BASE_ONLY, deleteOpOptions.deleteScope);
        assertEquals(DeleteOpOptions.DELETE_SCOPE_BASE_ONLY, options.serializeToJson().getInt("delete_scope"));
    }

    @Test
    public void deleteScopeCanTargetAllVersionsThroughJsonAndParcel() throws Exception {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, null, false, null, DeleteOpOptions.DELETE_SCOPE_ALL_VERSIONS);

        BatchBackupOptions jsonRestored = new BatchBackupOptions(new JSONObject(options.serializeToJson().toString()));
        DeleteOpOptions jsonDeleteOp = jsonRestored.getDeleteOpOptions("example.pkg", 0);
        assertNull(jsonDeleteOp.relativeDirs);
        assertEquals(DeleteOpOptions.DELETE_SCOPE_ALL_VERSIONS, jsonDeleteOp.deleteScope);

        Parcel parcel = Parcel.obtain();
        try {
            options.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DeleteOpOptions parcelDeleteOp = BatchBackupOptions.CREATOR.createFromParcel(parcel)
                    .getDeleteOpOptions("example.pkg", 0);
            assertNull(parcelDeleteOp.relativeDirs);
            assertEquals(DeleteOpOptions.DELETE_SCOPE_ALL_VERSIONS, parcelDeleteOp.deleteScope);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void deleteScopeRejectsConflictingTargets() {
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, new String[]{"example.pkg/0"}, null, false, null,
                DeleteOpOptions.DELETE_SCOPE_ALL_VERSIONS));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                null, null, null, false, null, DeleteOpOptions.DELETE_SCOPE_SELECTED));
    }

    @Test
    public void constructorTrimsBackupNamesRelativeDirsAndExclusionGlobs() {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                new String[]{" nightly "}, new String[]{" dnsfilter.android\\0_test "},
                new String[]{" /cache//tmp ", " #comment ", null});

        BackupOpOptions backupOpOptions = options.getBackupOpOptions("example.pkg", 0);
        RestoreOpOptions restoreOpOptions = options.getRestoreOpOptions("example.pkg", 0);
        DeleteOpOptions deleteOpOptions = options.getDeleteOpOptions("example.pkg", 0);

        assertEquals("nightly", backupOpOptions.backupName);
        assertArrayEquals(new String[]{"cache/tmp"}, backupOpOptions.exclusionGlobs);
        assertEquals("dnsfilter.android/0_test", restoreOpOptions.relativeDir);
        assertArrayEquals(new String[]{"dnsfilter.android/0_test"}, deleteOpOptions.relativeDirs);
    }

    @Test
    public void jsonTrimsBackupNamesRelativeDirsAndExclusionGlobs() throws Exception {
        JSONObject jsonObject = jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("backup_names", new JSONArray().put(" nightly "))
                .put("relative_dirs", new JSONArray().put(" dnsfilter.android\\0_test "))
                .put("exclusion_globs", new JSONArray().put(" /cache//tmp ").put(" #comment "));

        BatchBackupOptions options = new BatchBackupOptions(jsonObject);

        BackupOpOptions backupOpOptions = options.getBackupOpOptions("example.pkg", 0);
        RestoreOpOptions restoreOpOptions = options.getRestoreOpOptions("example.pkg", 0);
        DeleteOpOptions deleteOpOptions = options.getDeleteOpOptions("example.pkg", 0);
        assertEquals("nightly", backupOpOptions.backupName);
        assertArrayEquals(new String[]{"cache/tmp"}, backupOpOptions.exclusionGlobs);
        assertEquals("dnsfilter.android/0_test", restoreOpOptions.relativeDir);
        assertArrayEquals(new String[]{"dnsfilter.android/0_test"}, deleteOpOptions.relativeDirs);
    }

    @Test
    public void parcelTrimsBackupNamesRelativeDirsAndExclusionGlobs() {
        Parcel parcel = parcelOptions(BackupFlags.BACKUP_INT_DATA,
                new String[]{" nightly "}, new String[]{" dnsfilter.android\\0_test "},
                new String[]{" /cache//tmp ", " #comment "});
        try {
            BatchBackupOptions options = BatchBackupOptions.CREATOR.createFromParcel(parcel);

            BackupOpOptions backupOpOptions = options.getBackupOpOptions("example.pkg", 0);
            RestoreOpOptions restoreOpOptions = options.getRestoreOpOptions("example.pkg", 0);
            DeleteOpOptions deleteOpOptions = options.getDeleteOpOptions("example.pkg", 0);
            assertEquals("nightly", backupOpOptions.backupName);
            assertArrayEquals(new String[]{"cache/tmp"}, backupOpOptions.exclusionGlobs);
            assertEquals("dnsfilter.android/0_test", restoreOpOptions.relativeDir);
            assertArrayEquals(new String[]{"dnsfilter.android/0_test"}, deleteOpOptions.relativeDirs);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void backupNamesAllowNullButRejectBlankEntries() throws Exception {
        BatchBackupOptions options = new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA,
                new String[]{null, " test "}, null);

        JSONArray backupNames = options.serializeToJson().getJSONArray("backup_names");

        assertTrue(backupNames.isNull(0));
        assertEquals("test", backupNames.getString(1));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, new String[]{"   "}, null));
    }

    @Test
    public void constructorRejectsMalformedRelativeDirs() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{null}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{"   "}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{"../outside"}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{"dnsfilter.android//0"}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{"dnsfilter.android/0/"}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null, new String[]{"dnsfilter.android"}));
        assertThrows(IllegalArgumentException.class,
                () -> new BatchBackupOptions(BackupFlags.BACKUP_INT_DATA, null,
                        new String[]{"C:/backup/dnsfilter.android/0"}));
    }

    @Test
    public void constructorRejectsNegativeFlags() {
        assertThrows(IllegalArgumentException.class, () -> new BatchBackupOptions(-1, null, null));
    }

    @Test
    public void jsonRejectsNegativeFlags() {
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(-1)));
    }

    @Test
    public void jsonRejectsMalformedStringArrays() {
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("backup_names", new JSONArray().put(1))));
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("backup_names", new JSONArray().put("   "))));
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("relative_dirs", new JSONArray().put(JSONObject.NULL))));
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("relative_dirs", new JSONArray().put("../outside"))));
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("exclusion_globs", new JSONArray().put(false))));
        assertThrows(JSONException.class, () -> new BatchBackupOptions(jsonOptions(BackupFlags.BACKUP_INT_DATA)
                .put("exclusion_globs", "cache")));
    }

    @Test
    public void parcelRejectsNegativeFlags() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(-1);
            parcel.writeStringArray(null);
            parcel.writeStringArray(null);
            parcel.writeStringArray(null);
            ParcelCompat.writeBoolean(parcel, false);
            parcel.writeString(null);
            parcel.setDataPosition(0);

            assertThrows(IllegalArgumentException.class, () -> BatchBackupOptions.CREATOR.createFromParcel(parcel));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRejectsMalformedStringArrays() {
        Parcel blankBackupNameParcel = parcelOptions(BackupFlags.BACKUP_INT_DATA,
                new String[]{"   "}, null, null);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> BatchBackupOptions.CREATOR.createFromParcel(blankBackupNameParcel));
        } finally {
            blankBackupNameParcel.recycle();
        }

        Parcel badRelativeDirParcel = parcelOptions(BackupFlags.BACKUP_INT_DATA,
                null, new String[]{"../outside"}, null);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> BatchBackupOptions.CREATOR.createFromParcel(badRelativeDirParcel));
        } finally {
            badRelativeDirParcel.recycle();
        }
    }

    private static JSONObject jsonOptions(int flags) throws JSONException {
        return new JSONObject()
                .put("tag", BatchBackupOptions.TAG)
                .put("flags", flags);
    }

    private static Parcel parcelOptions(int flags, String[] backupNames, String[] relativeDirs,
                                        String[] exclusionGlobs) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(flags);
        parcel.writeStringArray(backupNames);
        parcel.writeStringArray(relativeDirs);
        parcel.writeStringArray(exclusionGlobs);
        ParcelCompat.writeBoolean(parcel, false);
        parcel.writeString(null);
        parcel.setDataPosition(0);
        return parcel;
    }
}
