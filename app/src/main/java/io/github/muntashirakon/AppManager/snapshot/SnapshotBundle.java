// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.io.Path;

/**
 * One-button snapshot bundle: ZIP of preferences, profiles, tags, and the
 * operation-history audit log, framed by a {@code manifest.json} that records
 * schema version, source package identity, and bundled content list.
 *
 * <p>Designed for cross-install portability — the bundle uses the JSON
 * representation of op-history rather than raw {@code apps.db} so that a
 * restore on a different applicationId (e.g. upstream App Manager →
 * AppManagerNG) does not need to rewrite the SQLite owner identity.
 *
 * <p>{@code keystore} preferences are deliberately excluded from export. They
 * contain password material tied to the local Android Keystore that would not
 * decrypt anything on another device anyway, and exporting them widens the
 * attack surface against a leaked bundle.
 */
public final class SnapshotBundle {
    public static final String TAG = "SnapshotBundle";

    public static final int SCHEMA_VERSION = 2;
    public static final String FORMAT_ID = "appmanagerng-snapshot";

    @VisibleForTesting
    static final String ENTRY_MANIFEST = "manifest.json";
    @VisibleForTesting
    static final String ENTRY_PREFS_DIR = "prefs/";
    @VisibleForTesting
    static final String ENTRY_PROFILES_DIR = "profiles/";
    @VisibleForTesting
    static final String ENTRY_RULES_DIR = "rules/";
    @VisibleForTesting
    static final String ENTRY_TAGS_DIR = "tags/";
    @VisibleForTesting
    static final String ENTRY_OP_HISTORY = "op_history.json";

    /**
     * SharedPreferences names that must NEVER be exported. {@code keystore} holds
     * the local keystore password derived from Android Keystore; transferring it
     * across devices does not decrypt anything and only widens the leak surface.
     */
    @VisibleForTesting
    static final Set<String> EXCLUDED_PREF_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Collections.singletonList("keystore")));

    /**
     * Hard limit on bundled entry size to bound memory during import. 64 MB is
     * far beyond any realistic preference / profile / history payload and
     * defends against ZIP-bomb style payloads that would otherwise exhaust the
     * importer.
     */
    @VisibleForTesting
    static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;

    private SnapshotBundle() {
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    @WorkerThread
    @NonNull
    public static ExportResult writeTo(@NonNull Context context, @NonNull OutputStream rawOut) throws IOException {
        Context appContext = context.getApplicationContext();
        int prefsCount = 0;
        int profilesCount = 0;
        int rulesCount = 0;
        int opHistoryCount = 0;
        List<String> contents = new ArrayList<>();
        List<String> excluded = new ArrayList<>();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
            // Preferences
            List<File> prefFiles = listSharedPrefFiles(appContext);
            for (File prefFile : prefFiles) {
                String name = stripXmlSuffix(prefFile.getName());
                if (EXCLUDED_PREF_NAMES.contains(name)) {
                    excluded.add("prefs/" + name);
                    continue;
                }
                if (!prefFile.isFile()) continue;
                writeFileEntry(zos, ENTRY_PREFS_DIR + prefFile.getName(), prefFile);
                ++prefsCount;
            }
            if (prefsCount > 0) {
                contents.add("prefs");
            }

            // Profiles
            File profilesDir = ProfileManager.getProfilesDir().getFile();
            if (profilesDir != null && profilesDir.isDirectory()) {
                File[] profileFiles = profilesDir.listFiles();
                if (profileFiles != null) {
                    for (File p : profileFiles) {
                        if (!p.isFile()) continue;
                        writeFileEntry(zos, ENTRY_PROFILES_DIR + p.getName(), p);
                        ++profilesCount;
                    }
                }
            }
            if (profilesCount > 0) {
                contents.add("profiles");
            }

            // Rule TSVs: component, freeze, AppOps, permission, and net-policy rules.
            File rulesDir = RulesStorageManager.getConfDir(appContext).getFile();
            if (rulesDir != null && rulesDir.isDirectory()) {
                File[] ruleFiles = rulesDir.listFiles((dir, name) -> name.endsWith(".tsv"));
                if (ruleFiles != null) {
                    for (File ruleFile : ruleFiles) {
                        if (!ruleFile.isFile()) continue;
                        writeFileEntry(zos, ENTRY_RULES_DIR + ruleFile.getName(), ruleFile);
                        ++rulesCount;
                    }
                }
            }
            if (rulesCount > 0) {
                contents.add("rules");
            }

            // Tags (forward-compat: include if/when the Multi-Tag feature lands)
            File tagsDir = new File(appContext.getFilesDir(), "tags");
            if (tagsDir.isDirectory()) {
                File[] tagFiles = tagsDir.listFiles();
                if (tagFiles != null) {
                    for (File t : tagFiles) {
                        if (!t.isFile()) continue;
                        writeFileEntry(zos, ENTRY_TAGS_DIR + t.getName(), t);
                    }
                    if (tagFiles.length > 0) {
                        contents.add("tags");
                    }
                }
            }

            // Op history (audit log) — JSON-serialized so it survives applicationId rename
            String opHistoryJson;
            try {
                List<OpHistory> rows = AppsDb.getInstance().opHistoryDao().getAll();
                opHistoryJson = serializeOpHistory(rows);
                opHistoryCount = rows.size();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to serialize op history; bundling empty history.", t);
                opHistoryJson = serializeOpHistory(Collections.emptyList());
                opHistoryCount = 0;
            }
            writeStringEntry(zos, ENTRY_OP_HISTORY, opHistoryJson);
            contents.add("op_history");

            // Manifest last so we can record final counts
            String manifest = buildManifestJson(appContext, contents, prefsCount,
                    profilesCount, rulesCount, opHistoryCount, excluded);
            writeStringEntry(zos, ENTRY_MANIFEST, manifest);
        }
        return new ExportResult(prefsCount, profilesCount, rulesCount, opHistoryCount);
    }

    // -----------------------------------------------------------------------
    // Import
    // -----------------------------------------------------------------------

    @WorkerThread
    @NonNull
    public static ImportResult readFrom(@NonNull Context context, @NonNull InputStream rawIn,
                                        @NonNull ImportOptions options)
            throws IOException, SnapshotImportException {
        ManifestSummary manifest = null;
        byte[] opHistoryBytes = null;
        List<PendingFile> pendingPrefs = new ArrayList<>();
        List<PendingFile> pendingProfiles = new ArrayList<>();
        List<PendingFile> pendingRules = new ArrayList<>();
        List<PendingFile> pendingTags = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Hard rejections.
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                        || name.contains("\\")) {
                    throw new SnapshotImportException(
                            "Refusing zip entry with suspicious name: " + name);
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                byte[] bytes = readEntryBounded(zis, MAX_ENTRY_BYTES, name);
                if (ENTRY_MANIFEST.equals(name)) {
                    manifest = ManifestSummary.parse(new String(bytes, StandardCharsets.UTF_8));
                } else if (name.startsWith(ENTRY_PREFS_DIR) && name.endsWith(".xml")) {
                    String leaf = name.substring(ENTRY_PREFS_DIR.length());
                    String prefName = stripXmlSuffix(leaf);
                    if (!isSafeLeaf(leaf) || EXCLUDED_PREF_NAMES.contains(prefName)) {
                        continue;
                    }
                    pendingPrefs.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_PROFILES_DIR)) {
                    String leaf = name.substring(ENTRY_PROFILES_DIR.length());
                    if (!isSafeLeaf(leaf) || !leaf.endsWith(ProfileManager.PROFILE_EXT)) {
                        continue;
                    }
                    pendingProfiles.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_RULES_DIR)) {
                    String leaf = name.substring(ENTRY_RULES_DIR.length());
                    if (!isSafeLeaf(leaf) || !leaf.endsWith(".tsv")) {
                        continue;
                    }
                    pendingRules.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_TAGS_DIR)) {
                    String leaf = name.substring(ENTRY_TAGS_DIR.length());
                    if (!isSafeLeaf(leaf)) {
                        continue;
                    }
                    pendingTags.add(new PendingFile(leaf, bytes));
                } else if (ENTRY_OP_HISTORY.equals(name)) {
                    opHistoryBytes = bytes;
                }
                zis.closeEntry();
            }
        }
        if (manifest == null) {
            throw new SnapshotImportException("Bundle is missing " + ENTRY_MANIFEST
                    + "; refusing to import as AppManagerNG snapshot.");
        }
        if (!FORMAT_ID.equals(manifest.format)) {
            throw new SnapshotImportException("Unexpected bundle format: " + manifest.format);
        }
        if (manifest.schemaVersion > SCHEMA_VERSION) {
            throw new SnapshotImportException(
                    "Bundle was written by a newer AppManagerNG (schema "
                            + manifest.schemaVersion + " > " + SCHEMA_VERSION + ").");
        }
        Context appContext = context.getApplicationContext();

        int prefsRestored = 0;
        int profilesRestored = 0;
        int rulesRestored = 0;
        int tagsRestored = 0;
        int opHistoryRestored = 0;

        if (options.restorePrefs) {
            for (PendingFile pf : pendingPrefs) {
                if (restorePrefFile(appContext, pf, options.mergePrefs)) {
                    ++prefsRestored;
                }
            }
        }
        if (options.restoreProfiles) {
            File profilesDir = ProfileManager.getProfilesDir().getFile();
            if (profilesDir != null) {
                //noinspection ResultOfMethodCallIgnored
                profilesDir.mkdirs();
                for (PendingFile pf : pendingProfiles) {
                    if (writeBytesTo(new File(profilesDir, pf.leaf), pf.bytes)) {
                        ++profilesRestored;
                    }
                }
            }
        }
        if (options.restoreRules) {
            File rulesDir = RulesStorageManager.getConfDir(appContext).getFile();
            if (rulesDir != null) {
                //noinspection ResultOfMethodCallIgnored
                rulesDir.mkdirs();
                for (PendingFile pf : pendingRules) {
                    if (restoreRuleFile(new File(rulesDir, pf.leaf), pf, options.mergeRules)) {
                        ++rulesRestored;
                    }
                }
            }
        }
        if (options.restoreTags) {
            File tagsDir = new File(appContext.getFilesDir(), "tags");
            //noinspection ResultOfMethodCallIgnored
            tagsDir.mkdirs();
            for (PendingFile pf : pendingTags) {
                if (writeBytesTo(new File(tagsDir, pf.leaf), pf.bytes)) {
                    ++tagsRestored;
                }
            }
        }
        if (options.restoreOpHistory && opHistoryBytes != null) {
            opHistoryRestored = importOpHistory(new String(opHistoryBytes, StandardCharsets.UTF_8));
        }

        return new ImportResult(manifest, prefsRestored, profilesRestored, tagsRestored,
                rulesRestored, opHistoryRestored);
    }

    // -----------------------------------------------------------------------
    // Manifest
    // -----------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    static String buildManifestJson(@NonNull Context appContext,
                                    @NonNull List<String> contents,
                                    int prefsCount,
                                    int profilesCount,
                                    int rulesCount,
                                    int opHistoryCount,
                                    @NonNull List<String> excluded) {
        long now = System.currentTimeMillis();
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("schema_version", SCHEMA_VERSION);
            manifest.put("format", FORMAT_ID);
            manifest.put("generated_at", now);
            manifest.put("generated_at_label", formatIsoUtc(now));
            manifest.put("source_package", appContext.getPackageName());
            manifest.put("source_version_code", BuildConfig.VERSION_CODE);
            manifest.put("source_version_name", BuildConfig.VERSION_NAME);
            manifest.put("device_label", Build.MANUFACTURER + " " + Build.MODEL);
            manifest.put("android_sdk_int", Build.VERSION.SDK_INT);
            JSONArray contentsArr = new JSONArray();
            for (String c : contents) contentsArr.put(c);
            manifest.put("contents", contentsArr);
            JSONObject counts = new JSONObject();
            counts.put("prefs_files", prefsCount);
            counts.put("profiles", profilesCount);
            counts.put("rules", rulesCount);
            counts.put("op_history", opHistoryCount);
            manifest.put("counts", counts);
            JSONArray excludedArr = new JSONArray();
            for (String e : excluded) excludedArr.put(e);
            manifest.put("excluded", excludedArr);
            return manifest.toString(2);
        } catch (JSONException e) {
            // JSONObject#put with primitive/String/JSONArray/JSONObject doesn't throw in practice;
            // returning a minimal manifest is safer than aborting the whole export here.
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"format\":\"" + FORMAT_ID + "\"}";
        }
    }

    // -----------------------------------------------------------------------
    // Op-history serialisation
    // -----------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    static String serializeOpHistory(@NonNull List<OpHistory> rows) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            root.put("generated_at", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (OpHistory row : rows) {
                if (row == null) continue;
                String data = normalizeSerializedData(row.serializedData);
                if (data == null) continue;
                String extra = normalizeSerializedExtra(row.serializedExtra);
                JSONObject obj = new JSONObject();
                obj.put("type", OpHistoryManager.normalizeHistoryType(row.type));
                obj.put("exec_time", row.execTime);
                obj.put("status", OpHistoryManager.normalizeStatus(row.status));
                obj.put("serialized_data", data);
                if (extra != null) {
                    obj.put("serialized_extra", extra);
                }
                arr.put(obj);
            }
            root.put("entries", arr);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}";
        }
    }

    /**
     * Append op-history entries from a JSON bundle. Existing entries are not
     * touched — the operation-history surface is append-only by design (see
     * iter-45 audit-log row).
     *
     * @return number of entries appended
     */
    @WorkerThread
    @VisibleForTesting
    static int importOpHistory(@NonNull String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray entries = root.optJSONArray("entries");
            if (entries == null) return 0;
            int restored = 0;
            // Make re-import idempotent. OpHistory.id is autoGenerate and is not
            // exported, so every imported row inserts with id==0 -> SQLite assigns
            // a fresh rowid and the REPLACE conflict strategy never matches. Without
            // a content-based guard, re-importing the same bundle duplicates the
            // entire op-history table each time. Snapshot existing rows' content
            // keys first, and skip any incoming row already present (or duplicated
            // within the bundle).
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (OpHistory existingRow : AppsDb.getInstance().opHistoryDao().getAll()) {
                seen.add(opHistoryKey(OpHistoryManager.normalizeHistoryType(existingRow.type),
                        OpHistoryManager.normalizeStatus(existingRow.status),
                        existingRow.execTime,
                        normalizeSerializedData(existingRow.serializedData),
                        normalizeSerializedExtra(existingRow.serializedExtra)));
            }
            for (int i = 0; i < entries.length(); ++i) {
                JSONObject obj = entries.optJSONObject(i);
                if (obj == null) continue;
                String type = OpHistoryManager.normalizeHistoryType(getNonBlankString(obj, "type"));
                String status = OpHistoryManager.normalizeStatus(getNonBlankString(obj, "status"));
                long execTime = obj.optLong("exec_time", 0);
                String data = normalizeSerializedData(getNonBlankString(obj, "serialized_data"));
                String extra = obj.has("serialized_extra")
                        && !obj.isNull("serialized_extra")
                        ? normalizeSerializedExtra(getNonBlankString(obj, "serialized_extra"))
                        : null;
                if (data == null) {
                    continue;
                }
                if (!seen.add(opHistoryKey(type, status, execTime, data, extra))) {
                    continue; // already present (or a duplicate within this bundle)
                }
                OpHistory row = new OpHistory();
                row.type = type;
                row.status = status;
                row.execTime = execTime;
                row.serializedData = data;
                row.serializedExtra = extra;
                try {
                    AppsDb.getInstance().opHistoryDao().insert(row);
                    ++restored;
                } catch (Throwable t) {
                    Log.w(TAG, "Skipping un-insertable op-history row " + i, t);
                }
            }
            return restored;
        } catch (JSONException e) {
            Log.w(TAG, "Could not parse op-history JSON during snapshot import.", e);
            return 0;
        }
    }

    @Nullable
    private static String normalizeSerializedData(@Nullable String data) {
        return isJsonObjectString(data) ? data : null;
    }

    @Nullable
    private static String normalizeSerializedExtra(@Nullable String extra) {
        return isJsonObjectString(extra) ? extra : null;
    }

    private static boolean isJsonObjectString(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            new JSONObject(value);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Nullable
    private static String getNonBlankString(@NonNull JSONObject obj, @NonNull String key) {
        Object value = obj.opt(key);
        if (!(value instanceof String)) {
            return null;
        }
        String stringValue = (String) value;
        return stringValue.trim().isEmpty() ? null : stringValue;
    }

    /** Content identity for an op-history row (id is autoGenerate and not part of identity). */
    @NonNull
    private static String opHistoryKey(@Nullable String type, @Nullable String status, long execTime,
                                       @Nullable String data, @Nullable String extra) {
        return type + '\u0000' + status + '\u0000' + execTime + '\u0000'
                + data + '\u0000' + (extra == null ? "" : extra);
    }

    // -----------------------------------------------------------------------
    // I/O helpers
    // -----------------------------------------------------------------------

    @NonNull
    private static List<File> listSharedPrefFiles(@NonNull Context appContext) {
        File prefsDir = sharedPrefsDir(appContext);
        if (prefsDir == null || !prefsDir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = prefsDir.listFiles((dir, name) -> name.endsWith(".xml"));
        if (files == null) return Collections.emptyList();
        return Arrays.asList(files);
    }

    private static boolean restorePrefFile(@NonNull Context appContext,
                                           @NonNull PendingFile pf,
                                           boolean merge) {
        if (!pf.leaf.endsWith(".xml")) return false;
        String prefName = stripXmlSuffix(pf.leaf);
        if (EXCLUDED_PREF_NAMES.contains(prefName)) return false;
        // Apply the imported entries THROUGH the live SharedPreferences instance
        // rather than overwriting the backing XML file. Android caches one
        // SharedPreferencesImpl per file (and AppPref holds a long-lived reference
        // to the "preferences" store), so an out-of-band file overwrite was never
        // picked up by the running process AND was silently clobbered by the next
        // settings write that flushed the stale in-memory map back to disk. Going
        // through the editor keeps the in-process cache and the on-disk file
        // coherent, so the import actually takes effect with no restart required.
        try {
            Map<String, Object> entries = parsePrefEntries(pf.bytes);
            SharedPreferences sp = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            if (!merge) {
                // Replace semantics: drop existing keys, then load the imported set.
                editor.clear();
            }
            for (Map.Entry<String, Object> e : entries.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) editor.putBoolean(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) editor.putInt(e.getKey(), (Integer) v);
                else if (v instanceof Long) editor.putLong(e.getKey(), (Long) v);
                else if (v instanceof Float) editor.putFloat(e.getKey(), (Float) v);
                else if (v instanceof String) editor.putString(e.getKey(), (String) v);
                else if (v instanceof Set) {
                    //noinspection unchecked
                    editor.putStringSet(e.getKey(), (Set<String>) v);
                }
            }
            // commit() (synchronous) so the restore is durable before we report
            // success; this runs on the snapshot-import background thread.
            return editor.commit();
        } catch (Exception e) {
            Log.w(TAG, "Could not apply imported preferences from " + pf.leaf + " during snapshot import.", e);
            return false;
        }
    }

    /**
     * Parse a standard Android SharedPreferences {@code <map>} XML document into a
     * typed key/value map. Unknown element types are skipped; a malformed numeric
     * value surfaces as an exception the caller treats as an import failure for
     * that file (rather than silently applying a partial/garbage value).
     */
    @NonNull
    private static Map<String, Object> parsePrefEntries(@NonNull byte[] bytes)
            throws IOException, ParserConfigurationException, SAXException, SnapshotImportException {
        Map<String, Object> out = new LinkedHashMap<>();
        Document doc = parseXmlMap(bytes);
        Element map = doc.getDocumentElement();
        if (map == null) return out;
        NodeList children = map.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;
            String name = el.getAttribute("name");
            if (name.isEmpty()) continue;
            switch (el.getTagName()) {
                case "boolean":
                    out.put(name, Boolean.parseBoolean(el.getAttribute("value")));
                    break;
                case "int":
                    out.put(name, Integer.parseInt(el.getAttribute("value")));
                    break;
                case "long":
                    out.put(name, Long.parseLong(el.getAttribute("value")));
                    break;
                case "float":
                    out.put(name, Float.parseFloat(el.getAttribute("value")));
                    break;
                case "string":
                    out.put(name, el.getTextContent());
                    break;
                case "set":
                    Set<String> set = new LinkedHashSet<>();
                    NodeList items = el.getChildNodes();
                    for (int j = 0; j < items.getLength(); ++j) {
                        Node item = items.item(j);
                        if (item instanceof Element && "string".equals(((Element) item).getTagName())) {
                            set.add(item.getTextContent());
                        }
                    }
                    out.put(name, set);
                    break;
                default:
                    // Unknown SharedPreferences element type; skip.
            }
        }
        return out;
    }

    private static boolean restoreRuleFile(@NonNull File target,
                                           @NonNull PendingFile pf,
                                           boolean merge) {
        byte[] bytesToWrite = pf.bytes;
        if (merge && target.isFile()) {
            try {
                bytesToWrite = mergeRuleBytes(readFileBytes(target), pf.bytes);
            } catch (IOException e) {
                Log.w(TAG, "Could not merge rules from " + pf.leaf + " during snapshot import.", e);
                return false;
            }
        }
        return writeBytesTo(target, bytesToWrite);
    }

    @NonNull
    private static byte[] readFileBytes(@NonNull File file) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
        }
        return buf.toByteArray();
    }

    private static boolean writeBytesTo(@NonNull File target, @NonNull byte[] bytes) {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(bytes);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Could not write " + target + " during snapshot import.", e);
            return false;
        }
    }

    private static void writeFileEntry(@NonNull ZipOutputStream zos,
                                       @NonNull String entryName,
                                       @NonNull File file) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                zos.write(buf, 0, n);
            }
        }
        zos.closeEntry();
    }

    private static void writeStringEntry(@NonNull ZipOutputStream zos,
                                         @NonNull String entryName,
                                         @NonNull String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(System.currentTimeMillis());
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    @NonNull
    private static byte[] readEntryBounded(@NonNull InputStream in, long maxBytes,
                                           @NonNull String entryName) throws IOException, SnapshotImportException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new SnapshotImportException(
                        "Bundle entry " + entryName + " exceeded the safe size limit (" + maxBytes + " bytes).");
            }
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    @VisibleForTesting
    static boolean isSafeLeaf(@NonNull String leaf) {
        if (leaf.isEmpty() || leaf.contains("/") || leaf.contains("\\")
                || "..".equals(leaf) || ".".equals(leaf)) {
            return false;
        }
        // Reject NUL, controls, and the Windows-reserved characters even though the
        // import target is Android — defence-in-depth in case the bundle is staged
        // through other OSes by the user.
        for (int i = 0; i < leaf.length(); ++i) {
            char c = leaf.charAt(i);
            if (c < 0x20 || c == 0x7f) return false;
        }
        return true;
    }

    /**
     * Resolve {@code shared_prefs/} without {@link Context#getDataDir()} (API 24+).
     * On API 21–23 we derive it from {@code filesDir}'s parent, which has always
     * been the same {@code /data/data/<pkg>/} directory.
     */
    @Nullable
    private static File sharedPrefsDir(@NonNull Context appContext) {
        File filesDir = appContext.getFilesDir();
        if (filesDir == null) return null;
        File dataDir = filesDir.getParentFile();
        if (dataDir == null) return null;
        return new File(dataDir, "shared_prefs");
    }

    @NonNull
    @VisibleForTesting
    static byte[] mergeSharedPreferencesXml(@NonNull byte[] currentBytes,
                                            @NonNull byte[] incomingBytes)
            throws SnapshotImportException {
        if (currentBytes.length == 0) {
            return incomingBytes;
        }
        try {
            Document current = parseXmlMap(currentBytes);
            Document incoming = parseXmlMap(incomingBytes);
            Element currentMap = current.getDocumentElement();
            Element incomingMap = incoming.getDocumentElement();
            NodeList importedEntries = incomingMap.getChildNodes();
            for (int i = 0; i < importedEntries.getLength(); ++i) {
                Node importedNode = importedEntries.item(i);
                if (!(importedNode instanceof Element)) {
                    continue;
                }
                Element importedElement = (Element) importedNode;
                String name = importedElement.getAttribute("name");
                if (name == null || name.isEmpty()) {
                    continue;
                }
                removePreferenceNode(currentMap, name);
                currentMap.appendChild(current.importNode(importedElement, true));
            }
            return toXmlBytes(current);
        } catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
            throw new SnapshotImportException("Could not merge SharedPreferences XML: " + e.getMessage());
        }
    }

    @NonNull
    @VisibleForTesting
    static byte[] mergeRuleBytes(@NonNull byte[] currentBytes, @NonNull byte[] incomingBytes) {
        LinkedHashSet<String> rows = new LinkedHashSet<>();
        collectRuleRows(rows, currentBytes);
        collectRuleRows(rows, incomingBytes);
        StringBuilder merged = new StringBuilder();
        for (String row : rows) {
            merged.append(row).append('\n');
        }
        return merged.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void collectRuleRows(@NonNull LinkedHashSet<String> rows, @NonNull byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] splitRows = text.split("\\r?\\n");
        for (String row : splitRows) {
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
    }

    @NonNull
    private static Document parseXmlMap(@NonNull byte[] bytes)
            throws IOException, ParserConfigurationException, SAXException, SnapshotImportException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(bytes));
        Element root = doc.getDocumentElement();
        if (root == null || !"map".equals(root.getTagName())) {
            throw new SnapshotImportException("SharedPreferences XML root is not <map>.");
        }
        return doc;
    }

    private static void removePreferenceNode(@NonNull Element map, @NonNull String name) {
        NodeList children = map.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; --i) {
            Node child = children.item(i);
            if (child instanceof Element && name.equals(((Element) child).getAttribute("name"))) {
                map.removeChild(child);
            }
        }
    }

    @NonNull
    private static byte[] toXmlBytes(@NonNull Document doc) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    @NonNull
    private static String stripXmlSuffix(@NonNull String fileName) {
        if (fileName.endsWith(".xml")) {
            return fileName.substring(0, fileName.length() - ".xml".length());
        }
        return fileName;
    }

    @NonNull
    private static String formatIsoUtc(long epochMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(epochMillis));
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    public static final class ExportOptions {
        // Reserved for future toggles; the v1 schema always bundles every section
        // that is non-empty on disk. Keeping the class so the public API stays stable.
        public ExportOptions() {
        }
    }

    public static final class ImportOptions {
        public boolean restorePrefs = true;
        public boolean restoreProfiles = true;
        public boolean restoreRules = true;
        public boolean restoreTags = true;
        public boolean restoreOpHistory = true;
        public boolean mergePrefs = true;
        public boolean mergeRules = true;
    }

    public static final class ExportResult {
        public final int prefsCount;
        public final int profilesCount;
        public final int rulesCount;
        public final int opHistoryCount;

        ExportResult(int prefsCount, int profilesCount, int rulesCount, int opHistoryCount) {
            this.prefsCount = prefsCount;
            this.profilesCount = profilesCount;
            this.rulesCount = rulesCount;
            this.opHistoryCount = opHistoryCount;
        }
    }

    public static final class ImportResult {
        @NonNull
        public final ManifestSummary manifest;
        public final int prefsRestored;
        public final int profilesRestored;
        public final int tagsRestored;
        public final int rulesRestored;
        public final int opHistoryRestored;

        ImportResult(@NonNull ManifestSummary manifest, int prefsRestored, int profilesRestored,
                     int tagsRestored, int rulesRestored, int opHistoryRestored) {
            this.manifest = manifest;
            this.prefsRestored = prefsRestored;
            this.profilesRestored = profilesRestored;
            this.tagsRestored = tagsRestored;
            this.rulesRestored = rulesRestored;
            this.opHistoryRestored = opHistoryRestored;
        }
    }

    public static final class ManifestSummary {
        public final int schemaVersion;
        @NonNull
        public final String format;
        public final long generatedAt;
        @Nullable
        public final String sourcePackage;
        @Nullable
        public final String sourceVersionName;
        public final int sourceVersionCode;

        ManifestSummary(int schemaVersion, @NonNull String format, long generatedAt,
                        @Nullable String sourcePackage, @Nullable String sourceVersionName,
                        int sourceVersionCode) {
            this.schemaVersion = schemaVersion;
            this.format = format;
            this.generatedAt = generatedAt;
            this.sourcePackage = sourcePackage;
            this.sourceVersionName = sourceVersionName;
            this.sourceVersionCode = sourceVersionCode;
        }

        @NonNull
        public static ManifestSummary parse(@NonNull String json) throws SnapshotImportException {
            try {
                JSONObject obj = new JSONObject(json);
                int schema = obj.optInt("schema_version", -1);
                String format = obj.optString("format", "");
                long ts = obj.optLong("generated_at", 0);
                String pkg = obj.has("source_package") ? obj.optString("source_package", null) : null;
                String ver = obj.has("source_version_name") ? obj.optString("source_version_name", null) : null;
                int code = obj.optInt("source_version_code", 0);
                if (schema < 0 || format.isEmpty()) {
                    throw new SnapshotImportException("Manifest missing schema_version / format.");
                }
                return new ManifestSummary(schema, format, ts, pkg, ver, code);
            } catch (JSONException e) {
                throw new SnapshotImportException("Manifest is not valid JSON: " + e.getMessage());
            }
        }
    }

    private static final class PendingFile {
        @NonNull
        final String leaf;
        @NonNull
        final byte[] bytes;

        PendingFile(@NonNull String leaf, @NonNull byte[] bytes) {
            this.leaf = leaf;
            this.bytes = bytes;
        }
    }

    // -----------------------------------------------------------------------
    // Tiny pure-JVM helpers used by tests (and harmless in production)
    // -----------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    static byte[] writeMinimalBundleForTest(@NonNull String manifestJson,
                                            @NonNull String opHistoryJson,
                                            @NonNull java.util.Map<String, byte[]> profiles)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeStringEntry(zos, ENTRY_MANIFEST, manifestJson);
            writeStringEntry(zos, ENTRY_OP_HISTORY, opHistoryJson);
            for (java.util.Map.Entry<String, byte[]> e : profiles.entrySet()) {
                ZipEntry entry = new ZipEntry(ENTRY_PROFILES_DIR + e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @VisibleForTesting
    @NonNull
    static InputStream asInputStream(@NonNull byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}
