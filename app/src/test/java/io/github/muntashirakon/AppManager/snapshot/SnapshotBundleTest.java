// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
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

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;

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
                + "\"contents\":[\"profiles\",\"op_history\"]"
                + "}";
        SnapshotBundle.ManifestSummary m = SnapshotBundle.ManifestSummary.parse(json);
        assertEquals(1, m.schemaVersion);
        assertEquals("appmanagerng-snapshot", m.format);
        assertEquals(1700000000000L, m.generatedAt);
        assertEquals("io.github.sysadmindoc.AppManagerNG", m.sourcePackage);
        assertEquals("0.4.2", m.sourceVersionName);
        assertEquals(6, m.sourceVersionCode);
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
    // Excluded-prefs invariant
    // -----------------------------------------------------------------------

    @Test
    public void keystorePrefIsNeverExported() {
        assertTrue(
                "keystore must remain on the excluded list - it ties to local Keystore-derived material",
                SnapshotBundle.EXCLUDED_PREF_NAMES.contains("keystore"));
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
    public void mergeSharedPreferencesXmlKeepsLocalKeysAndReplacesImportedKeys() throws Exception {
        byte[] current = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<map>\n"
                + "  <boolean name=\"keep_local\" value=\"true\" />\n"
                + "  <string name=\"replace_me\">old</string>\n"
                + "</map>\n").getBytes(StandardCharsets.UTF_8);
        byte[] incoming = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                + "<map>\n"
                + "  <string name=\"replace_me\">new</string>\n"
                + "  <int name=\"imported\" value=\"7\" />\n"
                + "</map>\n").getBytes(StandardCharsets.UTF_8);

        String merged = new String(SnapshotBundle.mergeSharedPreferencesXml(current, incoming),
                StandardCharsets.UTF_8);

        assertTrue(merged.contains("name=\"keep_local\""));
        assertTrue(merged.contains("name=\"replace_me\""));
        assertTrue(merged.contains(">new<"));
        assertTrue(merged.contains("name=\"imported\""));
        assertFalse(merged.contains(">old<"));
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
