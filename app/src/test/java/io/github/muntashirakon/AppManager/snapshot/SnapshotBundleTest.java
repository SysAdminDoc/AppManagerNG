// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.tags.AppNoteStore;
import io.github.muntashirakon.AppManager.utils.AppPref;

@RunWith(RobolectricTestRunner.class)
public class SnapshotBundleTest {
    // -----------------------------------------------------------------------
    // ManifestSummary parsing
    // -----------------------------------------------------------------------

    @Test
    public void manifestParsesValidJson() throws Exception {
        String json = "{"
                + "\"schema_version\":1,"
                + "\"format\":\"appmanagerng-snapshot\","
                + "\"generated_at\":1700000000000,"
                + "\"source_package\":\"io.github.sysadmindoc.AppManagerNG\","
                + "\"source_version_name\":\"0.4.2\","
                + "\"source_version_code\":6,"
                + "\"contents\":[\"profiles\",\"op_history\"],"
                + "\"counts\":{\"prefs_files\":5,\"profiles\":3,\"rules\":2,\"op_history\":42}"
                + "}";
        SnapshotBundle.ManifestSummary m = SnapshotBundle.ManifestSummary.parse(json);
        assertEquals(1, m.schemaVersion);
        assertEquals("appmanagerng-snapshot", m.format);
        assertEquals(1700000000000L, m.generatedAt);
        assertEquals("io.github.sysadmindoc.AppManagerNG", m.sourcePackage);
        assertEquals("0.4.2", m.sourceVersionName);
        assertEquals(6, m.sourceVersionCode);
        assertEquals(Arrays.asList("profiles", "op_history"), m.contents);
        assertEquals(5, m.prefsCount);
        assertEquals(3, m.profilesCount);
        assertEquals(2, m.rulesCount);
        assertEquals(42, m.opHistoryCount);
        assertFalse("prefs not in contents → hasPrefs false", m.hasPrefs());
        assertTrue(m.hasProfiles());
        assertTrue(m.hasOpHistory());
    }

    @Test
    public void manifestPreviewReadFromBundle() throws Exception {
        String manifestJson = "{"
                + "\"schema_version\":2,"
                + "\"format\":\"appmanagerng-snapshot\","
                + "\"generated_at\":1700000000000,"
                + "\"source_version_name\":\"0.5.0\","
                + "\"source_version_code\":7,"
                + "\"contents\":[\"prefs\",\"profiles\",\"rules\",\"tags\",\"op_history\"],"
                + "\"counts\":{\"prefs_files\":10,\"profiles\":2,\"rules\":4,\"op_history\":100}"
                + "}";
        byte[] bundle = SnapshotBundle.writeMinimalBundleForTest(
                manifestJson, "{\"entries\":[]}", Collections.emptyMap());
        SnapshotBundle.ManifestSummary preview = SnapshotBundle.readManifestOnly(
                new ByteArrayInputStream(bundle));
        assertEquals(2, preview.schemaVersion);
        assertEquals("0.5.0", preview.sourceVersionName);
        assertEquals(10, preview.prefsCount);
        assertEquals(2, preview.profilesCount);
        assertEquals(4, preview.rulesCount);
        assertEquals(100, preview.opHistoryCount);
        assertTrue(preview.hasPrefs());
        assertTrue(preview.hasProfiles());
        assertTrue(preview.hasRules());
        assertTrue(preview.hasTags());
        assertTrue(preview.hasOpHistory());
    }

    @Test
    public void manifestParseRejectsMissingFields() {
        try {
            SnapshotBundle.ManifestSummary.parse("{}");
            fail("Expected SnapshotImportException for empty manifest");
        } catch (SnapshotImportException expected) {
            // ok
        }
    }

    @Test
    public void manifestParseRejectsMalformedJson() {
        try {
            SnapshotBundle.ManifestSummary.parse("not json {");
            fail("Expected SnapshotImportException for malformed JSON");
        } catch (SnapshotImportException expected) {
            assertTrue(expected.getMessage().startsWith("Manifest is not valid JSON"));
        }
    }

    @Test
    public void manifestParseAcceptsNullOptionalFields() throws Exception {
        String json = "{\"schema_version\":1,\"format\":\"appmanagerng-snapshot\"}";
        SnapshotBundle.ManifestSummary m = SnapshotBundle.ManifestSummary.parse(json);
        assertNull(m.sourcePackage);
        assertNull(m.sourceVersionName);
        assertEquals(0, m.sourceVersionCode);
        assertTrue(m.contents.isEmpty());
        assertEquals(0, m.prefsCount);
        assertEquals(0, m.profilesCount);
    }

    // -----------------------------------------------------------------------
    // Leaf-name sanitisation (path-traversal defence)
    // -----------------------------------------------------------------------

    @Test
    public void leafNameRejectsTraversalAndSeparators() {
        assertFalse(SnapshotBundle.isSafeLeaf(""));
        assertFalse(SnapshotBundle.isSafeLeaf("."));
        assertFalse(SnapshotBundle.isSafeLeaf(".."));
        assertFalse(SnapshotBundle.isSafeLeaf("a/b"));
        assertFalse(SnapshotBundle.isSafeLeaf("a\\b"));
        assertFalse(SnapshotBundle.isSafeLeaf("../escape.xml"));
        // ASCII NUL, controls, and DEL must all be rejected.
        assertFalse(SnapshotBundle.isSafeLeaf("name\u0000.xml"));
        assertFalse(SnapshotBundle.isSafeLeaf("name\u0001.xml"));
        assertFalse(SnapshotBundle.isSafeLeaf("name\u007f.xml"));
        // Newlines / tabs are control characters and must be rejected too.
        assertFalse(SnapshotBundle.isSafeLeaf("a\nb.xml"));
        assertFalse(SnapshotBundle.isSafeLeaf("a\tb.xml"));
    }

    @Test
    public void leafNameAcceptsNormalNames() {
        assertTrue(SnapshotBundle.isSafeLeaf("preferences.xml"));
        assertTrue(SnapshotBundle.isSafeLeaf("server_config.xml"));
        assertTrue(SnapshotBundle.isSafeLeaf("ee6da3a5-1d62-491a-9a9d-1ff97ce8fadc.am.json"));
        assertTrue(SnapshotBundle.isSafeLeaf("name with spaces.xml"));
    }

    // -----------------------------------------------------------------------
    // Op-history JSON round-trip
    // -----------------------------------------------------------------------

    @Test
    public void opHistorySerializerSchemaIsForwardCompatible() throws Exception {
        String json = SnapshotBundle.serializeOpHistory(Collections.emptyList());
        JSONObject root = new JSONObject(json);
        assertEquals(SnapshotBundle.SCHEMA_VERSION, root.getInt("schema_version"));
        assertNotNull(root.optJSONArray("entries"));
        assertEquals(0, root.getJSONArray("entries").length());
    }

    @Test
    public void opHistorySerializerNormalizesSnapshotRows() throws Exception {
        OpHistory futureStatus = opHistoryRow(1L, " future_type ", " future_status ",
                "{\"package_name\":\"com.example.app\"}", "not json");
        OpHistory blankData = opHistoryRow(2L, OpHistoryManager.HISTORY_TYPE_BATCH_OPS,
                OpHistoryManager.STATUS_SUCCESS, " ", null);

        String json = SnapshotBundle.serializeOpHistory(Arrays.asList(futureStatus, blankData));
        JSONArray entries = new JSONObject(json).getJSONArray("entries");

        assertEquals(1, entries.length());
        JSONObject entry = entries.getJSONObject(0);
        assertEquals(OpHistoryManager.HISTORY_TYPE_UNKNOWN, entry.getString("type"));
        assertEquals(OpHistoryManager.STATUS_FAILURE, entry.getString("status"));
        assertEquals("{\"package_name\":\"com.example.app\"}", entry.getString("serialized_data"));
        assertFalse(entry.has("serialized_extra"));
    }

    @Test
    public void opHistoryImportNormalizesScalarsAndPreservesIdempotency() throws Exception {
        JSONObject root = new JSONObject()
                .put("schema_version", SnapshotBundle.SCHEMA_VERSION)
                .put("entries", new JSONArray()
                        .put(new JSONObject()
                                .put("type", " future_type ")
                                .put("status", " future_status ")
                                .put("exec_time", 1_700_000_000_000L)
                                .put("serialized_data", "{\"package_name\":\"com.example.app\"}")
                                .put("serialized_extra", "not json"))
                        .put(new JSONObject()
                                .put("type", OpHistoryManager.HISTORY_TYPE_BATCH_OPS)
                                .put("status", OpHistoryManager.STATUS_SUCCESS)
                                .put("exec_time", 1_700_000_000_001L)
                                .put("serialized_data", " ")));

        runOnBackground(() -> {
            AppsDb db = AppsDb.getInstance();
            db.opHistoryDao().deleteAll();
            try {
                assertEquals(1, SnapshotBundle.importOpHistory(root.toString()));
                assertEquals(0, SnapshotBundle.importOpHistory(root.toString()));
                List<OpHistory> rows = db.opHistoryDao().getAll();
                assertEquals(1, rows.size());
                OpHistory row = rows.get(0);
                assertEquals(OpHistoryManager.HISTORY_TYPE_UNKNOWN, row.type);
                assertEquals(OpHistoryManager.STATUS_FAILURE, row.status);
                assertEquals("{\"package_name\":\"com.example.app\"}", row.serializedData);
                assertNull(row.serializedExtra);
            } finally {
                db.opHistoryDao().deleteAll();
            }
            return null;
        });
    }

    // -----------------------------------------------------------------------
    // Portable DB sections (schema 3): log filters, FM favorites, freeze methods
    // -----------------------------------------------------------------------

    @Test
    public void portableDbSectionsRoundTripThroughBundle() throws Exception {
        runOnBackground(() -> {
            AppsDb db = AppsDb.getInstance();
            clearPortableTables(db);

            db.logFilterDao().insert("errors-only");
            FmFavorite fav = new FmFavorite();
            fav.name = "Downloads";
            fav.uri = "content://com.example.docs/tree/Downloads";
            fav.options = 1;
            fav.order = 5;
            fav.type = 2;
            db.fmFavoriteDao().insert(fav);
            // FREEZE_SUSPEND (2): a defined freeze method. Import validation rejects out-of-range
            // values, so use a real one.
            db.freezeTypeDao().insert(new FreezeType("com.example.app", 2));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SnapshotBundle.ExportResult exported = SnapshotBundle.writeTo(
                    ApplicationProvider.getApplicationContext(), out);
            assertEquals(1, exported.logFiltersCount);
            assertEquals(1, exported.fmFavoritesCount);
            assertEquals(1, exported.freezeTypesCount);

            clearPortableTables(db);

            SnapshotBundle.ImportOptions opts = onlyPortableSections();
            SnapshotBundle.ImportResult imported = SnapshotBundle.readFrom(
                    ApplicationProvider.getApplicationContext(),
                    new ByteArrayInputStream(out.toByteArray()), opts);
            assertEquals(1, imported.logFiltersRestored);
            assertEquals(1, imported.fmFavoritesRestored);
            assertEquals(1, imported.freezeTypesRestored);

            assertEquals("errors-only", db.logFilterDao().getAll().get(0).name);
            FmFavorite restoredFav = db.fmFavoriteDao().getAll().get(0);
            assertEquals("content://com.example.docs/tree/Downloads", restoredFav.uri);
            assertEquals(2, restoredFav.type);
            assertEquals(5L, restoredFav.order);
            FreezeType restoredFreeze = db.freezeTypeDao().get("com.example.app");
            assertNotNull(restoredFreeze);
            assertEquals(2, restoredFreeze.type);

            clearPortableTables(db);
            return null;
        });
    }

    @Test
    public void importLogFiltersSkipsDuplicatesAndIsIdempotent() throws Exception {
        runOnBackground(() -> {
            AppsDb db = AppsDb.getInstance();
            clearPortableTables(db);
            db.logFilterDao().insert("existing");

            String json = new JSONObject()
                    .put("schema_version", SnapshotBundle.SCHEMA_VERSION)
                    .put("entries", new JSONArray()
                            .put(new JSONObject().put("name", "existing"))
                            .put(new JSONObject().put("name", "fresh-a"))
                            .put(new JSONObject().put("name", "fresh-b"))
                            .put(new JSONObject().put("name", "  ")))
                    .toString();

            assertEquals(2, SnapshotBundle.importLogFilters(json)); // existing + blank skipped
            assertEquals(0, SnapshotBundle.importLogFilters(json)); // idempotent
            assertEquals(3, db.logFilterDao().getAll().size());
            clearPortableTables(db);
            return null;
        });
    }

    @Test
    public void importFmFavoritesSkipsInvalidUrisAndDuplicates() throws Exception {
        runOnBackground(() -> {
            AppsDb db = AppsDb.getInstance();
            clearPortableTables(db);

            String json = new JSONObject()
                    .put("entries", new JSONArray()
                            .put(new JSONObject().put("name", "Docs").put("uri", "content://x/Docs"))
                            .put(new JSONObject().put("name", "Bad").put("uri", "not-a-uri-no-scheme"))
                            .put(new JSONObject().put("name", "Blank").put("uri", " "))
                            .put(new JSONObject().put("name", "Docs").put("uri", "content://x/Docs")))
                    .toString();

            assertEquals(1, SnapshotBundle.importFmFavorites(json)); // only the valid, non-dup one
            assertEquals(1, db.fmFavoriteDao().getAll().size());
            assertEquals("content://x/Docs", db.fmFavoriteDao().getAll().get(0).uri);
            clearPortableTables(db);
            return null;
        });
    }

    @Test
    public void importFreezeTypesDoesNotOverwriteExistingDeviceChoices() throws Exception {
        runOnBackground(() -> {
            AppsDb db = AppsDb.getInstance();
            clearPortableTables(db);
            db.freezeTypeDao().insert(new FreezeType("com.keep", 1));

            String json = new JSONObject()
                    .put("entries", new JSONArray()
                            .put(new JSONObject().put("package_name", "com.keep").put("type", 9))
                            .put(new JSONObject().put("package_name", "com.new").put("type", 2)))
                    .toString();

            assertEquals(1, SnapshotBundle.importFreezeTypes(json)); // com.keep preserved, com.new added
            assertEquals(1, db.freezeTypeDao().get("com.keep").type); // not overwritten with 9
            assertEquals(2, db.freezeTypeDao().get("com.new").type);
            clearPortableTables(db);
            return null;
        });
    }

    // -----------------------------------------------------------------------
    // Encrypted bundles (schema 3, authenticated envelope)
    // -----------------------------------------------------------------------

    @Test
    public void encryptedBundleRoundTripsWithPassphrase() throws Exception {
        runOnBackground(() -> {
            Context context = ApplicationProvider.getApplicationContext();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SnapshotBundle.writeEncryptedTo(context, out, "correct passphrase".toCharArray());
            byte[] envelope = out.toByteArray();
            assertTrue(SnapshotCrypto.looksEncrypted(envelope));

            SnapshotBundle.ManifestSummary manifest = SnapshotBundle.readManifestOnly(
                    new ByteArrayInputStream(envelope), "correct passphrase".toCharArray());
            assertEquals(SnapshotBundle.FORMAT_ID, manifest.format);
            assertEquals(SnapshotBundle.SCHEMA_VERSION, manifest.schemaVersion);

            SnapshotBundle.ImportResult result = SnapshotBundle.readFrom(context,
                    new ByteArrayInputStream(envelope), onlyPortableSections(),
                    "correct passphrase".toCharArray());
            assertNotNull(result);
            return null;
        });
    }

    @Test
    public void encryptedBundleWithoutPassphraseReportsPassphraseRequired() throws Exception {
        runOnBackground(() -> {
            Context context = ApplicationProvider.getApplicationContext();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SnapshotBundle.writeEncryptedTo(context, out, "pw".toCharArray());
            byte[] envelope = out.toByteArray();

            assertThrows(SnapshotBundle.PassphraseRequiredException.class,
                    () -> SnapshotBundle.readFrom(context, new ByteArrayInputStream(envelope),
                            new SnapshotBundle.ImportOptions(), null));
            assertThrows(SnapshotImportException.class,
                    () -> SnapshotBundle.readFrom(context, new ByteArrayInputStream(envelope),
                            new SnapshotBundle.ImportOptions(), "wrong".toCharArray()));
            return null;
        });
    }

    private static void clearPortableTables(AppsDb db) {
        for (LogFilter f : db.logFilterDao().getAll()) db.logFilterDao().delete(f);
        for (FmFavorite f : db.fmFavoriteDao().getAll()) db.fmFavoriteDao().delete(f.id);
        for (FreezeType f : db.freezeTypeDao().getAll()) db.freezeTypeDao().delete(f.packageName);
    }

    private static SnapshotBundle.ImportOptions onlyPortableSections() {
        SnapshotBundle.ImportOptions opts = new SnapshotBundle.ImportOptions();
        opts.restorePrefs = false;
        opts.restoreProfiles = false;
        opts.restoreRules = false;
        opts.restoreTags = false;
        opts.restoreOpHistory = false;
        return opts;
    }

    // -----------------------------------------------------------------------
    // Excluded-prefs invariant
    // -----------------------------------------------------------------------

    @Test
    public void keystorePrefIsNeverExported() {
        assertTrue(
                "keystore must remain on the excluded list - it ties to local Keystore-derived material",
                SnapshotBundle.EXCLUDED_PREF_NAMES.contains("keystore"));
    }

    @Test
    public void privilegedServerSecretIsNeverExportedOrImported() {
        assertTrue(
                "the local privileged-channel authenticator must stay device-local",
                SnapshotBundle.EXCLUDED_PREF_NAMES.contains("server_secrets"));
    }

    @Test
    public void deviceLocalSecretsAreNeverExportedOrImported() {
        assertTrue(SnapshotBundle.EXCLUDED_PREF_NAMES.contains(AppPref.DEVICE_LOCAL_PREF_NAME));
        assertFalse(SnapshotBundle.ALLOWED_PREF_NAMES.contains(AppPref.DEVICE_LOCAL_PREF_NAME));
    }

    @Test
    public void appNotesPrefsAreIncludedInSnapshots() {
        assertFalse(SnapshotBundle.EXCLUDED_PREF_NAMES.contains(AppNoteStore.PREFS_NAME));
        assertTrue(SnapshotBundle.ALLOWED_PREF_NAMES.contains(AppNoteStore.PREFS_NAME));
    }

    @Test
    public void backupTagPoliciesAreIncludedWithOnlySchemaAndPolicyPayload() {
        assertTrue(SnapshotBundle.ALLOWED_PREF_NAMES.contains("backup_tag_policies"));
        assertTrue(SnapshotBundle.isAllowedPrefEntry("backup_tag_policies", "_schema", 1));
        assertTrue(SnapshotBundle.isAllowedPrefEntry("backup_tag_policies", "policies", "[]"));
        assertTrue(SnapshotBundle.isAllowedPrefEntry("backup_tag_policies", "destinations", "[]"));
        assertFalse(SnapshotBundle.isAllowedPrefEntry("backup_tag_policies", "policies", 1));
        assertFalse(SnapshotBundle.isAllowedPrefEntry("backup_tag_policies", "unexpected", "value"));
    }

    // -----------------------------------------------------------------------
    // Sensitive-key boundary (P0): secrets must never be exported or imported
    // -----------------------------------------------------------------------

    @Test
    public void exportStripsSensitiveKeysButKeepsOrdinaryOnes() throws Exception {
        StringBuilder xml = new StringBuilder(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
        for (String sensitive : AppPref.SENSITIVE_PREF_KEYS) {
            xml.append("<string name=\"").append(sensitive).append("\">SECRET-")
                    .append(sensitive).append("</string>\n");
        }
        xml.append("<int name=\"app_theme\" value=\"2\" />\n")
                .append("<boolean name=\"app_op_show_default\" value=\"true\" />\n")
                .append("</map>");

        byte[] filtered = SnapshotBundle.filterSensitivePrefXml(
                xml.toString().getBytes(StandardCharsets.UTF_8));
        String out = new String(filtered, StandardCharsets.UTF_8);

        for (String sensitive : AppPref.SENSITIVE_PREF_KEYS) {
            assertFalse("secret key name must be stripped: " + sensitive, out.contains(sensitive));
            assertFalse("secret value must be stripped", out.contains("SECRET-" + sensitive));
        }
        assertTrue("ordinary keys must survive", out.contains("app_theme"));
        assertTrue(out.contains("app_op_show_default"));
    }

    @Test
    public void exportLeavesPrefsWithoutSecretsByteIdentical() throws Exception {
        byte[] in = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
                + "<int name=\"app_theme\" value=\"2\" /></map>").getBytes(StandardCharsets.UTF_8);
        assertArrayEquals("prefs with no secret must pass through unchanged",
                in, SnapshotBundle.filterSensitivePrefXml(in));
    }

    @Test
    public void importNeitherOverwritesNorClearsSensitiveKeys() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String sensitive = AppPref.SENSITIVE_PREF_KEYS.iterator().next();
        SharedPreferences sp = context.getSharedPreferences(
                AppPref.getSharedPreferencesName(), Context.MODE_PRIVATE);
        sp.edit().clear()
                .putString(sensitive, "REAL-DEVICE-SECRET")
                .putInt("app_theme", 1)
                .commit();

        // Crafted bundle: poison the secret and change an ordinary key, in REPLACE mode.
        String prefsXml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
                + "<string name=\"" + sensitive + "\">POISON</string>"
                + "<int name=\"app_theme\" value=\"3\" /></map>";
        byte[] bundle = bundleWithPrefEntry("preferences.xml", prefsXml);

        SnapshotBundle.ImportOptions opts = new SnapshotBundle.ImportOptions();
        opts.restoreProfiles = false;
        opts.restoreRules = false;
        opts.restoreTags = false;
        opts.restoreOpHistory = false;
        opts.mergePrefs = false; // replace mode exercises editor.clear()
        SnapshotBundle.readFrom(context, new ByteArrayInputStream(bundle), opts);

        assertEquals("import must not overwrite a live secret",
                "REAL-DEVICE-SECRET", sp.getString(sensitive, null));
        assertEquals("ordinary key must still be restored under replace mode",
                3, sp.getInt("app_theme", -1));
    }

    @Test
    public void importRejectsUnknownPreferenceFilesWithoutCreatingThem() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File prefsDir = new File(context.getFilesDir().getParentFile(), "shared_prefs");
        File unknownFile = new File(prefsDir, "crafted_snapshot_store.xml");
        assertFalse(unknownFile.exists());
        byte[] bundle = bundleWithPrefEntry("crafted_snapshot_store.xml",
                "<map><boolean name=\"enabled\" value=\"true\" /></map>");

        SnapshotBundle.ImportResult result = SnapshotBundle.readFrom(context,
                new ByteArrayInputStream(bundle), prefsOnlyOptions(true));

        assertEquals(0, result.prefsRestored);
        assertFalse(unknownFile.exists());
    }

    @Test
    public void importAppliesOnlyRegisteredPreferenceKeysWithDeclaredTypes() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences sp = context.getSharedPreferences(
                AppPref.getSharedPreferencesName(), Context.MODE_PRIVATE);
        sp.edit().remove("crafted_unknown_key").putInt("app_theme", 1).commit();
        byte[] bundle = bundleWithPrefEntry("preferences.xml",
                "<map>"
                        + "<int name=\"app_theme\" value=\"3\" />"
                        + "<boolean name=\"crafted_unknown_key\" value=\"true\" />"
                        + "<string name=\"layout_orientation\">wrong-type</string>"
                        + "</map>");

        SnapshotBundle.ImportResult result = SnapshotBundle.readFrom(context,
                new ByteArrayInputStream(bundle), prefsOnlyOptions(true));

        assertEquals(1, result.prefsRestored);
        assertEquals(3, sp.getInt("app_theme", -1));
        assertFalse(sp.contains("crafted_unknown_key"));
        assertFalse("wrong-typed registered values must be dropped",
                "wrong-type".equals(sp.getAll().get("layout_orientation")));
    }

    @NonNull
    private static SnapshotBundle.ImportOptions prefsOnlyOptions(boolean merge) {
        SnapshotBundle.ImportOptions options = new SnapshotBundle.ImportOptions();
        options.restoreProfiles = false;
        options.restoreRules = false;
        options.restoreTags = false;
        options.restoreOpHistory = false;
        options.restoreLogFilters = false;
        options.restoreFmFavorites = false;
        options.restoreFreezeTypes = false;
        options.mergePrefs = merge;
        return options;
    }

    private static byte[] bundleWithPrefEntry(String leaf, String xml) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String manifest = new JSONObject()
                    .put("schema_version", SnapshotBundle.SCHEMA_VERSION)
                    .put("format", SnapshotBundle.FORMAT_ID)
                    .toString();
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("prefs/" + leaf));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // -----------------------------------------------------------------------
    // Bundle structure (writeMinimalBundleForTest fixture only - does not touch the device)
    // -----------------------------------------------------------------------

    @Test
    public void minimalBundleContainsManifestAndOpHistoryEntries() throws Exception {
        String manifest = new JSONObject()
                .put("schema_version", SnapshotBundle.SCHEMA_VERSION)
                .put("format", SnapshotBundle.FORMAT_ID)
                .put("generated_at", 1700000000000L)
                .put("contents", new JSONArray().put("profiles").put("op_history"))
                .toString();
        String opHistory = "{\"schema_version\":1,\"entries\":[]}";
        Map<String, byte[]> profiles = new HashMap<>();
        profiles.put("a.am.json", "{}".getBytes(StandardCharsets.UTF_8));
        byte[] bundle = SnapshotBundle.writeMinimalBundleForTest(manifest, opHistory, profiles);

        boolean sawManifest = false;
        boolean sawOpHistory = false;
        boolean sawProfile = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) sawManifest = true;
                else if ("op_history.json".equals(entry.getName())) sawOpHistory = true;
                else if ("profiles/a.am.json".equals(entry.getName())) sawProfile = true;
            }
        }
        assertTrue(sawManifest);
        assertTrue(sawOpHistory);
        assertTrue(sawProfile);
    }

    // -----------------------------------------------------------------------
    // Reject "wrong format" bundles
    // -----------------------------------------------------------------------

    @Test
    public void readRejectsBundleWithWrongFormat() throws Exception {
        String manifest = new JSONObject()
                .put("schema_version", 1)
                .put("format", "some-other-tool")
                .toString();
        byte[] bundle = SnapshotBundle.writeMinimalBundleForTest(
                manifest,
                "{\"schema_version\":1,\"entries\":[]}",
                Collections.emptyMap());
        try (InputStream in = new ByteArrayInputStream(bundle)) {
            try {
                SnapshotBundle.readFrom(null, in, new SnapshotBundle.ImportOptions());
                fail("Expected SnapshotImportException for unknown format");
            } catch (SnapshotImportException expected) {
                assertTrue(expected.getMessage().contains("Unexpected bundle format"));
            } catch (NullPointerException npe) {
                // ImportOptions branches that touch Context (prefs / profiles restore) are
                // unreachable on the format-rejection path; if a refactor reaches Context
                // before the format check this assertion will catch the regression.
                fail("Format check must happen before any Context access: " + npe);
            } catch (IOException io) {
                fail("Unexpected IOException: " + io);
            }
        }
    }

    @Test
    public void readRejectsBundleWithFutureSchema() throws Exception {
        String manifest = new JSONObject()
                .put("schema_version", SnapshotBundle.SCHEMA_VERSION + 99)
                .put("format", SnapshotBundle.FORMAT_ID)
                .toString();
        byte[] bundle = SnapshotBundle.writeMinimalBundleForTest(
                manifest,
                "{\"schema_version\":1,\"entries\":[]}",
                Collections.emptyMap());
        try (InputStream in = new ByteArrayInputStream(bundle)) {
            SnapshotBundle.readFrom(null, in, new SnapshotBundle.ImportOptions());
            fail("Expected SnapshotImportException for future schema");
        } catch (SnapshotImportException expected) {
            assertTrue(expected.getMessage().contains("newer AppManagerNG"));
        }
    }

    private static OpHistory opHistoryRow(long id,
                                          String type,
                                          String status,
                                          String serializedData,
                                          String serializedExtra) {
        OpHistory row = new OpHistory();
        row.id = id;
        row.type = type;
        row.execTime = 1_700_000_000_000L + id;
        row.status = status;
        row.serializedData = serializedData;
        row.serializedExtra = serializedExtra;
        return row;
    }

    private static <T> T runOnBackground(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        Thread thread = new Thread(task);
        thread.start();
        return task.get();
    }

    // -----------------------------------------------------------------------
    // Path-traversal rejection in ZIP entry name
    // -----------------------------------------------------------------------

    @Test
    public void readRejectsBundleWithTraversalEntry() throws Exception {
        // Build a bundle by hand whose entry name escapes the import target.
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            String manifest = new JSONObject()
                    .put("schema_version", 1)
                    .put("format", SnapshotBundle.FORMAT_ID)
                    .toString();
            ZipEntry m = new ZipEntry("manifest.json");
            zos.putNextEntry(m);
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            ZipEntry bad = new ZipEntry("../escape.txt");
            zos.putNextEntry(bad);
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
        }
        try (InputStream in = new ByteArrayInputStream(baos.toByteArray())) {
            SnapshotBundle.readFrom(null, in, new SnapshotBundle.ImportOptions());
            fail("Expected SnapshotImportException for traversal entry");
        } catch (SnapshotImportException expected) {
            assertTrue(expected.getMessage().contains("suspicious"));
        }
    }

    @Test
    public void readRejectsActualBundleWithTooManyEntries() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String manifest = new JSONObject()
                    .put("schema_version", 1)
                    .put("format", SnapshotBundle.FORMAT_ID)
                    .toString();
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (int i = 1; i <= SnapshotBundle.MAX_BUNDLE_ENTRIES; ++i) {
                zos.putNextEntry(new ZipEntry("ignored/entry-" + i));
                zos.closeEntry();
            }
        }
        SnapshotImportException expected = assertThrows(SnapshotImportException.class,
                () -> SnapshotBundle.readFrom(null, new ByteArrayInputStream(baos.toByteArray()),
                        new SnapshotBundle.ImportOptions()));
        assertTrue(expected.getMessage().contains("too many entries"));
    }

    @Test
    public void mergePreferenceImportKeepsLocalKeysAndReplacesImportedKeys() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences sp = context.getSharedPreferences(
                AppPref.getSharedPreferencesName(), Context.MODE_PRIVATE);
        sp.edit().clear()
                .putInt("app_theme", 1)
                .putBoolean("app_op_show_default", false)
                .commit();
        byte[] bundle = bundleWithPrefEntry("preferences.xml",
                "<map><boolean name=\"app_op_show_default\" value=\"true\" /></map>");

        SnapshotBundle.ImportResult result = SnapshotBundle.readFrom(context,
                new ByteArrayInputStream(bundle), prefsOnlyOptions(true));

        assertEquals(1, result.prefsRestored);
        assertEquals("merge must retain a local key absent from the snapshot",
                1, sp.getInt("app_theme", -1));
        assertTrue("merge must replace an imported key",
                sp.getBoolean("app_op_show_default", false));
    }

    @Test
    public void mergeRuleBytesPreservesOrderAndDedupesRows() {
        byte[] current = ("com.example\tACTIVITY\t.a\tblocked\n"
                + "com.example\tSERVICE\t.s\tblocked\n").getBytes(StandardCharsets.UTF_8);
        byte[] incoming = ("com.example\tSERVICE\t.s\tblocked\n"
                + "com.other\tRECEIVER\t.r\tblocked\n").getBytes(StandardCharsets.UTF_8);

        String merged = new String(SnapshotBundle.mergeRuleBytes(current, incoming), StandardCharsets.UTF_8);

        assertEquals("com.example\tACTIVITY\t.a\tblocked\n"
                + "com.example\tSERVICE\t.s\tblocked\n"
                + "com.other\tRECEIVER\t.r\tblocked\n", merged);
    }
}
