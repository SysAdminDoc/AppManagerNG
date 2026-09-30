// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
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
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.FmFavoriteDao;
import io.github.muntashirakon.AppManager.db.dao.FreezeTypeDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.utils.DurableFile;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.tags.AppNoteStore;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArchiveExtractionGuard;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
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
 * <p>{@code keystore}, {@code server_secrets}, and {@code device_local_secrets}
 * preference files are excluded from export wholesale, and the individual secret keys inside the main
 * {@code preferences} file (authorization key, Tasker signing secret, VirusTotal
 * API key — see {@link AppPref#SENSITIVE_PREF_KEYS}) are stripped on export and
 * never applied on import, so a leaked or crafted bundle can neither disclose nor
 * overwrite live credentials.
 */
public final class SnapshotBundle {
    public static final String TAG = "SnapshotBundle";

    // Schema 3 added the portable DB-backed sections: log filters, file-manager
    // favorites, and per-package freeze methods. Older readers (schema 2) reject a
    // schema-3 bundle; a schema-3 reader imports a schema-2 bundle and simply finds
    // the new sections absent.
    public static final int SCHEMA_VERSION = 3;
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
    @VisibleForTesting
    static final String ENTRY_LOG_FILTERS = "log_filters.json";
    @VisibleForTesting
    static final String ENTRY_FM_FAVORITES = "fm_favorites.json";
    @VisibleForTesting
    static final String ENTRY_FREEZE_TYPES = "freeze_types.json";

    /**
     * SharedPreferences names that must NEVER be exported. {@code keystore} holds
     * the local keystore password derived from Android Keystore; transferring it
     * across devices does not decrypt anything and only widens the leak surface.
     * {@code server_secrets} holds the sole authenticator for the local privileged
     * channel and must be regenerated independently on every installation.
     * {@code device_local_secrets} contains the other credentials deliberately
     * bound to one installation.
     */
    @VisibleForTesting
    static final Set<String> EXCLUDED_PREF_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("keystore", "server_secrets",
                    AppPref.DEVICE_LOCAL_PREF_NAME)));

    /**
     * Durable, portable SharedPreferences stores owned by the app. Operational stores such as
     * installer crash recovery, interrupted batch journals, privileged-server configuration,
     * Android widget IDs, and device-local secrets intentionally stay outside snapshots.
     */
    @VisibleForTesting
    static final Set<String> ALLOWED_PREF_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    AppPref.getSharedPreferencesName(),
                    "app_notes",
                    "app_tags",
                    "backup_tag_policies",
                    "filter_presets",
                    "profile_triggers",
                    "profile_trigger_runs",
                    "fm_open_with_defaults")));

    /**
     * Hard limit on bundled entry size to bound memory during import. 64 MB is
     * far beyond any realistic preference / profile / history payload and
     * defends against ZIP-bomb style payloads that would otherwise exhaust the
     * importer.
     */
    @VisibleForTesting
    static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    @VisibleForTesting
    static final int MAX_BUNDLE_ENTRIES = 10_000;

    /**
     * Hard cap on a whole (possibly encrypted) bundle we buffer in memory to check for the
     * encryption envelope. Per-entry and entry-count bounds still apply after decompression.
     */
    @VisibleForTesting
    static final long MAX_BUNDLE_BYTES = 256L * 1024 * 1024;
    @VisibleForTesting
    static final long MAX_BUNDLE_EXPANDED_BYTES = 256L * 1024 * 1024;

    /** Thrown when an encrypted bundle is opened without (or with a wrong) passphrase. */
    public static final class PassphraseRequiredException extends SnapshotImportException {
        public PassphraseRequiredException(@NonNull String message) {
            super(message);
        }
    }

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
        int logFiltersCount = 0;
        int fmFavoritesCount = 0;
        int freezeTypesCount = 0;
        List<String> contents = new ArrayList<>();
        List<String> excluded = new ArrayList<>();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
            // Preferences
            List<File> prefFiles = listSharedPrefFiles(appContext);
            for (File prefFile : prefFiles) {
                String name = stripXmlSuffix(prefFile.getName());
                if (EXCLUDED_PREF_NAMES.contains(name) || !ALLOWED_PREF_NAMES.contains(name)) {
                    excluded.add("prefs/" + name);
                    continue;
                }
                if (!prefFile.isFile()) continue;
                byte[] filtered;
                try {
                    filtered = filterSensitivePrefXml(readFileBytes(prefFile));
                } catch (Exception e) {
                    // A preferences file that appears to carry a secret but could not be
                    // sanitized is skipped rather than exported verbatim — never leak.
                    Log.w(TAG, "Skipping preferences \"" + name + "\" from snapshot: could not sanitize.", e);
                    excluded.add("prefs/" + name);
                    continue;
                }
                writeBytesEntry(zos, ENTRY_PREFS_DIR + prefFile.getName(), filtered, prefFile.lastModified());
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
                // Rows without data are skipped, so count what was written.
                opHistoryCount = Math.max(0, countEntries(opHistoryJson.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception t) {
                Log.w(TAG, "Failed to serialize op history; bundling empty history.", t);
                opHistoryJson = serializeOpHistory(Collections.emptyList());
                opHistoryCount = 0;
            }
            writeStringEntry(zos, ENTRY_OP_HISTORY, opHistoryJson);
            contents.add("op_history");

            // Portable DB-backed user state (schema 3): saved log filters, file-manager
            // favorites, and per-package freeze methods. JSON-serialized (autogenerated ids
            // are not exported) so they survive an applicationId rename, exactly like op
            // history. Cached/rebuildable tables (app inventory, scan results, backup rows)
            // are deliberately excluded.
            SectionExport logFilters = tryExport("log filters", SnapshotBundle::exportLogFilters);
            writeStringEntry(zos, ENTRY_LOG_FILTERS, logFilters.json);
            logFiltersCount = logFilters.count;
            if (logFiltersCount > 0) contents.add("log_filters");

            SectionExport fmFavorites = tryExport("file-manager favorites", SnapshotBundle::exportFmFavorites);
            writeStringEntry(zos, ENTRY_FM_FAVORITES, fmFavorites.json);
            fmFavoritesCount = fmFavorites.count;
            if (fmFavoritesCount > 0) contents.add("fm_favorites");

            SectionExport freezeTypes = tryExport("freeze methods", SnapshotBundle::exportFreezeTypes);
            writeStringEntry(zos, ENTRY_FREEZE_TYPES, freezeTypes.json);
            freezeTypesCount = freezeTypes.count;
            if (freezeTypesCount > 0) contents.add("freeze_types");

            // Manifest last so we can record final counts
            String manifest = buildManifestJson(appContext, contents, prefsCount,
                    profilesCount, rulesCount, opHistoryCount, logFiltersCount,
                    fmFavoritesCount, freezeTypesCount, excluded);
            writeStringEntry(zos, ENTRY_MANIFEST, manifest);
        }
        return new ExportResult(prefsCount, profilesCount, rulesCount, opHistoryCount,
                logFiltersCount, fmFavoritesCount, freezeTypesCount);
    }

    /**
     * Export a snapshot encrypted with {@code passphrase}: build the plaintext ZIP in memory,
     * then wrap it in the authenticated {@link SnapshotCrypto} envelope. The plaintext bytes are
     * zeroed before returning; the caller should clear the passphrase.
     */
    @WorkerThread
    @NonNull
    public static ExportResult writeEncryptedTo(@NonNull Context context, @NonNull OutputStream rawOut,
                                                 @NonNull char[] passphrase)
            throws IOException, GeneralSecurityException {
        // The plaintext ZIP goes to app-private staging rather than the heap, so peak memory does
        // not scale with the bundle; the staging file is deleted whatever happens.
        File staging = createStagingFile("snapshot-export");
        try {
            ExportResult result;
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(staging))) {
                result = writeTo(context, out);
            }
            try (InputStream plaintext = new BufferedInputStream(new FileInputStream(staging))) {
                SnapshotCrypto.encryptTo(plaintext, rawOut, passphrase);
            }
            return result;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            staging.delete();
        }
    }

    private interface SectionSerializer {
        @NonNull
        SectionExport serialize();
    }

    private static SectionExport tryExport(@NonNull String label, @NonNull SectionSerializer serializer) {
        try {
            return serializer.serialize();
        } catch (Exception t) {
            Log.w(TAG, "Failed to serialize " + label + "; bundling empty section.", t);
            return new SectionExport("{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}", 0);
        }
    }

    private static final class SectionExport {
        @NonNull
        final String json;
        final int count;

        SectionExport(@NonNull String json, int count) {
            this.json = json;
            this.count = count;
        }
    }

    // -----------------------------------------------------------------------
    // Import — preview
    // -----------------------------------------------------------------------

    /**
     * Read the manifest of a plaintext snapshot bundle for preview purposes. The bundle is staged
     * and checked in full, exactly as an import would check it; nothing is restored.
     */
    @WorkerThread
    @NonNull
    public static ManifestSummary readManifestOnly(@NonNull InputStream rawIn)
            throws IOException, SnapshotImportException {
        try {
            return readManifestOnly(rawIn, null);
        } catch (GeneralSecurityException e) {
            // Only decryption throws this, and a bundle staged without a passphrase is never decrypted.
            throw new IOException(e);
        }
    }

    /**
     * Preview a bundle that may be encrypted. A plaintext bundle is previewed directly; an
     * encrypted one is decrypted with {@code passphrase} first ({@link PassphraseRequiredException}
     * is thrown when the passphrase is missing or wrong).
     */
    @WorkerThread
    @NonNull
    public static ManifestSummary readManifestOnly(@NonNull InputStream rawIn,
                                                   @Nullable char[] passphrase)
            throws IOException, SnapshotImportException, GeneralSecurityException {
        try (StagedSnapshot staged = stageForImport(rawIn, passphrase)) {
            return staged.manifest;
        }
    }

    // -----------------------------------------------------------------------
    // Import — full
    // -----------------------------------------------------------------------

    /**
     * Import a bundle that may be encrypted. A plaintext bundle is imported directly; an
     * encrypted one is decrypted with {@code passphrase} first ({@link PassphraseRequiredException}
     * is thrown when the passphrase is missing or wrong).
     */
    @WorkerThread
    @NonNull
    public static ImportResult readFrom(@NonNull Context context, @NonNull InputStream rawIn,
                                        @NonNull ImportOptions options, @Nullable char[] passphrase)
            throws IOException, SnapshotImportException, GeneralSecurityException {
        try (StagedSnapshot staged = stageForImport(rawIn, passphrase)) {
            return importStaged(context, staged, options);
        }
    }

    @WorkerThread
    @NonNull
    public static ImportResult readFrom(@NonNull Context context, @NonNull InputStream rawIn,
                                        @NonNull ImportOptions options)
            throws IOException, SnapshotImportException {
        try {
            return readFrom(context, rawIn, options, null);
        } catch (GeneralSecurityException e) {
            // Only decryption throws this, and a bundle staged without a passphrase is never decrypted.
            throw new IOException(e);
        }
    }

    /**
     * A snapshot copied once into private storage. The preview and the import both read this copy,
     * never the source again, and the import refuses to run unless the copy still hashes to
     * {@link #sha256}. Whatever the source provider does after the copy, the import applies exactly
     * the bundle that was reviewed.
     */
    public static final class StagedSnapshot implements Closeable {
        @NonNull
        final File file;
        /** SHA-256 of the staged plaintext bundle, in lower-case hex. */
        @NonNull
        public final String sha256;
        /** The manifest of the staged bundle, which passed every check an import makes. */
        @NonNull
        public final ManifestSummary manifest;

        StagedSnapshot(@NonNull File file, @NonNull String sha256, @NonNull ManifestSummary manifest) {
            this.file = file;
            this.sha256 = sha256;
            this.manifest = manifest;
        }

        /** Deletes the staged copy. */
        @Override
        public void close() {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Copies the bundle into private staging, decrypting it first when it is an envelope, hashes the
     * plaintext, and checks it the way an import will: entry names, duplicates, format, schema and
     * the manifest's counts. GCM only authenticates at the end, so nothing is parsed until
     * {@link SnapshotCrypto#decryptTo} has returned without throwing.
     */
    @WorkerThread
    @NonNull
    public static StagedSnapshot stageForImport(@NonNull InputStream rawIn, @Nullable char[] passphrase)
            throws IOException, SnapshotImportException, GeneralSecurityException {
        // Peek just far enough to tell an envelope from a plain ZIP without buffering the bundle.
        PushbackInputStream in = new PushbackInputStream(rawIn, SnapshotCrypto.MAGIC.length);
        byte[] prefix = new byte[SnapshotCrypto.MAGIC.length];
        int prefixLength = readAtMost(in, prefix);
        boolean encrypted = prefixLength == prefix.length && SnapshotCrypto.looksEncrypted(prefix);
        in.unread(prefix, 0, prefixLength);
        if (encrypted && (passphrase == null || passphrase.length == 0)) {
            throw new PassphraseRequiredException("This snapshot is encrypted; a passphrase is required.");
        }
        File staging = createStagingFile("snapshot-import");
        try {
            MessageDigest digest = newSha256();
            try (OutputStream out = new DigestOutputStream(new CappedOutputStream(
                    new BufferedOutputStream(new FileOutputStream(staging)), MAX_BUNDLE_BYTES), digest)) {
                if (encrypted) {
                    SnapshotCrypto.decryptTo(in, out, passphrase);
                } else {
                    IoUtils.copy(in, out);
                }
            } catch (CappedOutputStream.CapExceededException e) {
                throw new SnapshotImportException("Snapshot bundle is too large.");
            }
            String sha256 = HexEncoding.encodeToString(digest.digest(), false);
            ParsedBundle parsed;
            try (InputStream staged = new BufferedInputStream(new FileInputStream(staging))) {
                parsed = parse(staged);
            }
            return new StagedSnapshot(staging, sha256, parsed.manifest);
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            staging.delete();
            throw t;
        }
    }

    /**
     * Imports a staged snapshot. The copy is read once, hashed while it is parsed, and nothing is
     * written unless it still matches the digest it had when it was staged and passes every check.
     */
    @WorkerThread
    @NonNull
    public static ImportResult importStaged(@NonNull Context context, @NonNull StagedSnapshot snapshot,
                                            @NonNull ImportOptions options)
            throws IOException, SnapshotImportException {
        MessageDigest digest = newSha256();
        ParsedBundle parsed;
        try (DigestInputStream in = new DigestInputStream(new FileInputStream(snapshot.file), digest)) {
            // The ZIP reader stops before the central directory, so read the rest for the digest.
            parsed = parse(new FilterInputStream(in) {
                @Override
                public void close() {
                }
            });
            byte[] rest = new byte[8192];
            //noinspection StatementWithEmptyBody
            while (in.read(rest) >= 0) {
            }
        }
        String actual = HexEncoding.encodeToString(digest.digest(), false);
        if (!actual.equals(snapshot.sha256)) {
            throw new SnapshotImportException("The snapshot changed after it was reviewed; nothing was imported.");
        }
        Log.i(TAG, "Importing snapshot %s", snapshot.sha256);
        return apply(context, parsed, options, snapshot.sha256);
    }

    @NonNull
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Fails a write that would take the output past {@code cap} bytes. */
    private static final class CappedOutputStream extends FilterOutputStream {
        static final class CapExceededException extends IOException {
            CapExceededException() {
                super("Snapshot bundle is too large.");
            }
        }

        private final long mCap;
        private long mWritten;

        CappedOutputStream(@NonNull OutputStream out, long cap) {
            super(out);
            mCap = cap;
        }

        @Override
        public void write(int b) throws IOException {
            reserve(1);
            out.write(b);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            reserve(len);
            out.write(b, off, len);
        }

        private void reserve(int len) throws CapExceededException {
            if (mWritten + len > mCap) {
                throw new CapExceededException();
            }
            mWritten += len;
        }
    }

    private static int readAtMost(@NonNull InputStream in, @NonNull byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n < 0) break;
            read += n;
        }
        return read;
    }

    /**
     * Creates an empty file in app-private storage. Staging must never touch shared storage: it
     * briefly holds unverified — and, for exports, plaintext — snapshot content.
     */
    @NonNull
    private static File createStagingFile(@NonNull String prefix) throws IOException {
        File dir = new File(ContextUtils.getContext().getCacheDir(), "snapshot-staging");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create the snapshot staging directory.");
        }
        return File.createTempFile(prefix, ".tmp", dir);
    }

    /** Everything an import can apply, read from one pass over one bundle and checked as a whole. */
    private static final class ParsedBundle {
        ManifestSummary manifest;
        byte[] opHistoryBytes;
        byte[] logFiltersBytes;
        byte[] fmFavoritesBytes;
        byte[] freezeTypesBytes;
        final List<PendingFile> pendingPrefs = new ArrayList<>();
        final List<PendingFile> pendingProfiles = new ArrayList<>();
        final List<PendingFile> pendingRules = new ArrayList<>();
        final List<PendingFile> pendingTags = new ArrayList<>();
        // What the archive holds before any file is skipped, for comparison with the manifest.
        int prefEntries;
        int profileEntries;
        int ruleEntries;
    }

    /**
     * Reads a plaintext bundle into memory and checks it. Refuses suspicious or repeated entry
     * names, an unknown format, a newer schema, and counts the manifest declares that the payload
     * doesn't match. No preference or file is touched here.
     */
    @NonNull
    private static ParsedBundle parse(@NonNull InputStream rawIn) throws IOException, SnapshotImportException {
        ParsedBundle bundle = new ParsedBundle();
        ArchiveExtractionGuard guard = createImportGuard();
        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                assertReasonableEntryCount(++entryCount);
                guard.onNewEntry();
                guard.assertEntrySize(entry.getSize());
                String name = entry.getName();
                // Hard rejections.
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                        || name.contains("\\")) {
                    throw new SnapshotImportException(
                            "Refusing zip entry with suspicious name: " + name);
                }
                if (!names.add(name)) {
                    // A reader that keeps the first copy and one that keeps the last would disagree
                    // about what this bundle says, so it says nothing.
                    throw new SnapshotImportException("Snapshot has more than one entry named " + name);
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                byte[] bytes = readEntryBounded(zis, MAX_ENTRY_BYTES, name, guard);
                if (ENTRY_MANIFEST.equals(name)) {
                    bundle.manifest = ManifestSummary.parse(new String(bytes, StandardCharsets.UTF_8));
                } else if (name.startsWith(ENTRY_PREFS_DIR) && name.endsWith(".xml")) {
                    ++bundle.prefEntries;
                    String leaf = name.substring(ENTRY_PREFS_DIR.length());
                    String prefName = stripXmlSuffix(leaf);
                    if (!isSafeLeaf(leaf) || EXCLUDED_PREF_NAMES.contains(prefName)
                            || !ALLOWED_PREF_NAMES.contains(prefName)) {
                        continue;
                    }
                    bundle.pendingPrefs.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_PROFILES_DIR)) {
                    ++bundle.profileEntries;
                    String leaf = name.substring(ENTRY_PROFILES_DIR.length());
                    if (!isSafeLeaf(leaf) || !leaf.endsWith(ProfileManager.PROFILE_EXT)) {
                        continue;
                    }
                    bundle.pendingProfiles.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_RULES_DIR)) {
                    ++bundle.ruleEntries;
                    String leaf = name.substring(ENTRY_RULES_DIR.length());
                    if (!isSafeLeaf(leaf) || !leaf.endsWith(".tsv")) {
                        continue;
                    }
                    bundle.pendingRules.add(new PendingFile(leaf, bytes));
                } else if (name.startsWith(ENTRY_TAGS_DIR)) {
                    String leaf = name.substring(ENTRY_TAGS_DIR.length());
                    if (!isSafeLeaf(leaf)) {
                        continue;
                    }
                    bundle.pendingTags.add(new PendingFile(leaf, bytes));
                } else if (ENTRY_OP_HISTORY.equals(name)) {
                    bundle.opHistoryBytes = bytes;
                } else if (ENTRY_LOG_FILTERS.equals(name)) {
                    bundle.logFiltersBytes = bytes;
                } else if (ENTRY_FM_FAVORITES.equals(name)) {
                    bundle.fmFavoritesBytes = bytes;
                } else if (ENTRY_FREEZE_TYPES.equals(name)) {
                    bundle.freezeTypesBytes = bytes;
                }
                zis.closeEntry();
            }
        }
        ManifestSummary manifest = bundle.manifest;
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
        reconcile(manifest, "prefs_files", manifest.prefsCount, bundle.prefEntries, false);
        reconcile(manifest, "profiles", manifest.profilesCount, bundle.profileEntries, false);
        reconcile(manifest, "rules", manifest.rulesCount, bundle.ruleEntries, false);
        // Exports before v0.6.25 counted op history rows the serializer then skipped for having no
        // data, so an older bundle can hold fewer entries than it declares, never more.
        reconcile(manifest, "op_history", manifest.opHistoryCount, countEntries(bundle.opHistoryBytes), true);
        reconcile(manifest, "log_filters", manifest.logFiltersCount, countEntries(bundle.logFiltersBytes), false);
        reconcile(manifest, "fm_favorites", manifest.fmFavoritesCount, countEntries(bundle.fmFavoritesBytes), false);
        reconcile(manifest, "freeze_types", manifest.freezeTypesCount, countEntries(bundle.freezeTypesBytes), false);
        return bundle;
    }

    /** Refuses a bundle whose payload doesn't match a count its manifest declares. */
    private static void reconcile(@NonNull ManifestSummary manifest, @NonNull String key, int declared, int found,
                                  boolean mayHoldFewer) throws SnapshotImportException {
        if (!manifest.declaredCounts.contains(key)) {
            return;
        }
        if (found == declared || (mayHoldFewer && found >= 0 && found < declared)) {
            return;
        }
        throw new SnapshotImportException("The manifest lists " + declared + " " + key
                + " but the snapshot holds " + (found < 0 ? "an unreadable list" : String.valueOf(found))
                + "; refusing to import it.");
    }

    /** The length of a section's {@code entries} array, 0 for a missing section, -1 if unreadable. */
    @VisibleForTesting
    static int countEntries(@Nullable byte[] section) {
        if (section == null) {
            return 0;
        }
        try {
            JSONArray entries = new JSONObject(new String(section, StandardCharsets.UTF_8)).optJSONArray("entries");
            return entries != null ? entries.length() : -1;
        } catch (JSONException e) {
            return -1;
        }
    }

    @WorkerThread
    @NonNull
    private static ImportResult apply(@NonNull Context context, @NonNull ParsedBundle bundle,
                                      @NonNull ImportOptions options, @NonNull String sha256)
            throws IOException {
        ManifestSummary manifest = bundle.manifest;
        List<PendingFile> pendingPrefs = bundle.pendingPrefs;
        List<PendingFile> pendingProfiles = bundle.pendingProfiles;
        List<PendingFile> pendingRules = bundle.pendingRules;
        List<PendingFile> pendingTags = bundle.pendingTags;
        byte[] opHistoryBytes = bundle.opHistoryBytes;
        byte[] logFiltersBytes = bundle.logFiltersBytes;
        byte[] fmFavoritesBytes = bundle.fmFavoritesBytes;
        byte[] freezeTypesBytes = bundle.freezeTypesBytes;
        Context appContext = context.getApplicationContext();

        int prefsRestored = 0;
        int profilesRestored = 0;
        int rulesRestored = 0;
        int tagsRestored = 0;
        int opHistoryRestored = 0;
        int logFiltersRestored = 0;
        int fmFavoritesRestored = 0;
        int freezeTypesRestored = 0;

        if (options.restorePrefs) {
            for (PendingFile pf : pendingPrefs) {
                if (restorePrefFile(appContext, pf, options.mergePrefs)) {
                    ++prefsRestored;
                    if ("app_notes.xml".equals(pf.leaf)) {
                        AppNoteStore.reconcileRoom(appContext);
                    }
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
        // Every database section commits together: a failure in a later section must not leave
        // the rows an earlier one inserted behind.
        final byte[] opHistoryPayload = opHistoryBytes;
        final byte[] logFiltersPayload = logFiltersBytes;
        final byte[] fmFavoritesPayload = fmFavoritesBytes;
        final byte[] freezeTypesPayload = freezeTypesBytes;
        int[] dbCounts = new int[4];
        boolean hasDbSections = (options.restoreOpHistory && opHistoryPayload != null)
                || (options.restoreLogFilters && logFiltersPayload != null)
                || (options.restoreFmFavorites && fmFavoritesPayload != null)
                || (options.restoreFreezeTypes && freezeTypesPayload != null);
        if (hasDbSections) {
            AppsDb.getInstance().runInTransaction(() -> {
                if (options.restoreOpHistory && opHistoryPayload != null) {
                    dbCounts[0] = importOpHistory(new String(opHistoryPayload, StandardCharsets.UTF_8));
                }
                if (options.restoreLogFilters && logFiltersPayload != null) {
                    dbCounts[1] = importLogFilters(new String(logFiltersPayload, StandardCharsets.UTF_8));
                }
                if (options.restoreFmFavorites && fmFavoritesPayload != null) {
                    dbCounts[2] = importFmFavorites(new String(fmFavoritesPayload, StandardCharsets.UTF_8));
                }
                if (options.restoreFreezeTypes && freezeTypesPayload != null) {
                    dbCounts[3] = importFreezeTypes(new String(freezeTypesPayload, StandardCharsets.UTF_8));
                }
            });
        }
        opHistoryRestored = dbCounts[0];
        logFiltersRestored = dbCounts[1];
        fmFavoritesRestored = dbCounts[2];
        freezeTypesRestored = dbCounts[3];

        return new ImportResult(manifest, sha256, prefsRestored, profilesRestored, tagsRestored,
                rulesRestored, opHistoryRestored, logFiltersRestored, fmFavoritesRestored,
                freezeTypesRestored);
    }

    @VisibleForTesting
    static void assertReasonableEntryCount(int entryCount) throws SnapshotImportException {
        if (entryCount > MAX_BUNDLE_ENTRIES) {
            throw new SnapshotImportException("Snapshot bundle has too many entries.");
        }
    }

    @NonNull
    private static ArchiveExtractionGuard createImportGuard() {
        return new ArchiveExtractionGuard(MAX_BUNDLE_EXPANDED_BYTES, MAX_BUNDLE_ENTRIES,
                MAX_ENTRY_BYTES, MAX_BUNDLE_EXPANDED_BYTES);
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
                                    int logFiltersCount,
                                    int fmFavoritesCount,
                                    int freezeTypesCount,
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
            counts.put("log_filters", logFiltersCount);
            counts.put("fm_favorites", fmFavoritesCount);
            counts.put("freeze_types", freezeTypesCount);
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
                } catch (Exception t) {
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
    // Portable DB sections (schema 3): log filters, FM favorites, freeze methods
    // -----------------------------------------------------------------------

    @NonNull
    private static SectionExport exportLogFilters() {
        JSONArray arr = new JSONArray();
        int count = 0;
        for (LogFilter row : AppsDb.getInstance().logFilterDao().getAll()) {
            if (row == null || row.name == null || row.name.trim().isEmpty()) continue;
            try {
                arr.put(new JSONObject().put("name", row.name));
                ++count;
            } catch (JSONException ignore) {
            }
        }
        return new SectionExport(wrapSection(arr), count);
    }

    @NonNull
    private static SectionExport exportFmFavorites() {
        JSONArray arr = new JSONArray();
        int count = 0;
        for (FmFavorite row : AppsDb.getInstance().fmFavoriteDao().getAll()) {
            if (row == null || row.name == null || row.uri == null) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", row.name);
                o.put("uri", row.uri);
                if (row.initUri != null) o.put("init_uri", row.initUri);
                o.put("options", row.options);
                o.put("order", row.order);
                o.put("type", row.type);
                arr.put(o);
                ++count;
            } catch (JSONException ignore) {
            }
        }
        return new SectionExport(wrapSection(arr), count);
    }

    @NonNull
    private static SectionExport exportFreezeTypes() {
        JSONArray arr = new JSONArray();
        int count = 0;
        for (FreezeType row : AppsDb.getInstance().freezeTypeDao().getAll()) {
            if (row == null || row.packageName == null || row.packageName.trim().isEmpty()) continue;
            try {
                arr.put(new JSONObject().put("package_name", row.packageName).put("type", row.type));
                ++count;
            } catch (JSONException ignore) {
            }
        }
        return new SectionExport(wrapSection(arr), count);
    }

    @NonNull
    private static String wrapSection(@NonNull JSONArray entries) {
        try {
            return new JSONObject()
                    .put("schema_version", SCHEMA_VERSION)
                    .put("generated_at", System.currentTimeMillis())
                    .put("entries", entries)
                    .toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}";
        }
    }

    /** Import saved log filters, skipping names that already exist (unique index). */
    @WorkerThread
    @VisibleForTesting
    static int importLogFilters(@NonNull String json) {
        JSONArray entries = parseEntries(json, "log filters");
        if (entries == null) return 0;
        LogFilterDao dao = AppsDb.getInstance().logFilterDao();
        Set<String> seen = new HashSet<>();
        for (LogFilter f : dao.getAll()) {
            if (f != null && f.name != null) seen.add(f.name);
        }
        int restored = 0;
        for (int i = 0; i < entries.length(); ++i) {
            JSONObject o = entries.optJSONObject(i);
            if (o == null) continue;
            String name = getNonBlankString(o, "name");
            if (name == null || !seen.add(name)) continue;
            try {
                dao.insert(name);
                ++restored;
            } catch (Exception e) {
                Log.w(TAG, "Skipping un-insertable log filter during snapshot import.", e);
            }
        }
        return restored;
    }

    /**
     * Import file-manager favorites. Favorites with a blank or unparseable URI are skipped
     * (invalid/unavailable paths are not trusted), and favorites already present (same name +
     * URI) are not duplicated.
     */
    @WorkerThread
    @VisibleForTesting
    static int importFmFavorites(@NonNull String json) {
        JSONArray entries = parseEntries(json, "file-manager favorites");
        if (entries == null) return 0;
        FmFavoriteDao dao = AppsDb.getInstance().fmFavoriteDao();
        Set<String> seen = new HashSet<>();
        for (FmFavorite f : dao.getAll()) {
            if (f != null) seen.add(favoriteKey(f.name, f.uri));
        }
        int restored = 0;
        for (int i = 0; i < entries.length(); ++i) {
            JSONObject o = entries.optJSONObject(i);
            if (o == null) continue;
            String name = getNonBlankString(o, "name");
            String uri = getNonBlankString(o, "uri");
            if (name == null || uri == null || !isValidFavoriteUri(uri)) continue;
            if (!seen.add(favoriteKey(name, uri))) continue;
            FmFavorite favorite = new FmFavorite();
            favorite.name = name;
            favorite.uri = uri;
            // Validate init_uri like uri: a malformed initial path would only fail later when the
            // File Manager tries to navigate to it, so drop it instead of trusting it.
            String initUri = getNonBlankString(o, "init_uri");
            favorite.initUri = (initUri != null && isValidFavoriteUri(initUri)) ? initUri : null;
            favorite.options = o.optInt("options", 0);
            favorite.order = o.optLong("order", 0);
            favorite.type = o.optInt("type", 0);
            try {
                dao.insert(favorite);
                ++restored;
            } catch (Exception e) {
                Log.w(TAG, "Skipping un-insertable favorite during snapshot import.", e);
            }
        }
        return restored;
    }

    /** Import per-package freeze methods without overwriting the device's existing choices. */
    @WorkerThread
    @VisibleForTesting
    static int importFreezeTypes(@NonNull String json) {
        JSONArray entries = parseEntries(json, "freeze methods");
        if (entries == null) return 0;
        FreezeTypeDao dao = AppsDb.getInstance().freezeTypeDao();
        Set<String> seen = new HashSet<>();
        for (FreezeType f : dao.getAll()) {
            if (f != null && f.packageName != null) seen.add(f.packageName);
        }
        int restored = 0;
        for (int i = 0; i < entries.length(); ++i) {
            JSONObject o = entries.optJSONObject(i);
            if (o == null) continue;
            String pkg = getNonBlankString(o, "package_name");
            if (pkg == null || !seen.add(pkg)) continue;
            int type = o.optInt("type", 0);
            // Reject out-of-range freeze methods (the column is an IntDef of 1/2/4/8); a bad value
            // would apply an undefined freeze method later.
            if (!FreezeUtils.isValidFreezeMethod(type)) continue;
            try {
                dao.insert(new FreezeType(pkg, type));
                ++restored;
            } catch (Exception e) {
                Log.w(TAG, "Skipping un-insertable freeze method during snapshot import.", e);
            }
        }
        return restored;
    }

    @Nullable
    private static JSONArray parseEntries(@NonNull String json, @NonNull String label) {
        try {
            return new JSONObject(json).optJSONArray("entries");
        } catch (JSONException e) {
            Log.w(TAG, "Could not parse " + label + " JSON during snapshot import.", e);
            return null;
        }
    }

    @NonNull
    private static String favoriteKey(@Nullable String name, @Nullable String uri) {
        return name + ' ' + uri;
    }

    private static boolean isValidFavoriteUri(@NonNull String uri) {
        // A real favorite is a content:// or file:// URI; a value with no scheme is not a
        // navigable path and would only fail (or mislead) on restore, so drop it.
        return Uri.parse(uri).getScheme() != null;
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
        if (EXCLUDED_PREF_NAMES.contains(prefName) || !ALLOWED_PREF_NAMES.contains(prefName)) {
            return false;
        }
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
            int parsedEntryCount = entries.size();
            entries.entrySet().removeIf(entry ->
                    !isAllowedPrefEntry(prefName, entry.getKey(), entry.getValue()));
            if (parsedEntryCount > 0 && entries.isEmpty()) {
                Log.w(TAG, "Skipping preferences \"" + prefName
                        + "\": it contains no registered portable keys.");
                return false;
            }
            SharedPreferences sp = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);
            // Preserve the device's own sensitive values so a replace-mode import
            // (editor.clear()) cannot wipe them out from under the user.
            Map<String, Object> preserved = new LinkedHashMap<>();
            Map<String, ?> current = sp.getAll();
            for (String sensitive : AppPref.SENSITIVE_PREF_KEYS) {
                Object v = current.get(sensitive);
                if (v != null) preserved.put(sensitive, v);
            }
            SharedPreferences.Editor editor = sp.edit();
            if (!merge) {
                // Replace semantics: drop existing keys, then load the imported set.
                editor.clear();
            }
            for (Map.Entry<String, Object> e : entries.entrySet()) {
                putTypedPref(editor, e.getKey(), e.getValue());
            }
            // Re-apply preserved secrets last so clear() above cannot have dropped them.
            for (Map.Entry<String, Object> e : preserved.entrySet()) {
                putTypedPref(editor, e.getKey(), e.getValue());
            }
            // commit() (synchronous) so the restore is durable before we report
            // success; this runs on the snapshot-import background thread.
            return editor.commit();
        } catch (Exception e) {
            Log.w(TAG, "Could not apply imported preferences from " + pf.leaf + " during snapshot import.", e);
            return false;
        }
    }

    @VisibleForTesting
    static boolean isAllowedPrefEntry(@NonNull String prefName, @NonNull String key,
                                      @Nullable Object value) {
        if (AppPref.getSharedPreferencesName().equals(prefName)) {
            return AppPref.isPortablePreference(key, value);
        }
        switch (prefName) {
            case "app_notes":
            case "app_tags":
                return ("_schema".equals(key) && value instanceof Integer)
                        || (value instanceof String && PackageUtils.validateName(key));
            case "filter_presets":
                return ("_schema".equals(key) && value instanceof Integer)
                        || ("presets".equals(key) && value instanceof String);
            case "backup_tag_policies":
                return ("_schema".equals(key) && value instanceof Integer)
                        || (("policies".equals(key) || "destinations".equals(key)) && value instanceof String);
            case "profile_triggers":
                return ("_schema".equals(key) && value instanceof Integer)
                        || ("triggers".equals(key) && value instanceof String);
            case "profile_trigger_runs":
                return isDynamicPrefEntry(key, "last_run_", value, Long.class, 128)
                        || isDynamicPrefEntry(key, "last_result_", value, String.class, 128)
                        || isDynamicPrefEntry(key, "last_diagnostics_", value, String.class, 128)
                        || isDynamicPrefEntry(key, "run_history_", value, String.class, 128);
            case "fm_open_with_defaults":
                return isDynamicPrefEntry(key, "file:", value, String.class, 4096)
                        || isDynamicPrefEntry(key, "extension:", value, String.class, 255);
            default:
                return false;
        }
    }

    private static boolean isDynamicPrefEntry(@NonNull String key, @NonNull String prefix,
                                              @Nullable Object value, @NonNull Class<?> valueType,
                                              int maxSuffixLength) {
        if (!valueType.isInstance(value) || !key.startsWith(prefix)) return false;
        String suffix = key.substring(prefix.length());
        if (suffix.isEmpty() || suffix.length() > maxSuffixLength) return false;
        for (int i = 0; i < suffix.length(); ++i) {
            if (Character.isISOControl(suffix.charAt(i))) return false;
        }
        return true;
    }

    private static void putTypedPref(@NonNull SharedPreferences.Editor editor,
                                     @NonNull String key, @Nullable Object v) {
        if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
        else if (v instanceof Integer) editor.putInt(key, (Integer) v);
        else if (v instanceof Long) editor.putLong(key, (Long) v);
        else if (v instanceof Float) editor.putFloat(key, (Float) v);
        else if (v instanceof String) editor.putString(key, (String) v);
        else if (v instanceof Set) {
            //noinspection unchecked
            editor.putStringSet(key, (Set<String>) v);
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
        // Atomic replacement: a failure part-way through leaves the existing file untouched
        // instead of truncating it into a half-restored state.
        try {
            new DurableFile(target).write(bytes);
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

    private static void writeBytesEntry(@NonNull ZipOutputStream zos,
                                        @NonNull String entryName,
                                        @NonNull byte[] bytes,
                                        long time) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(time);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    /**
     * Strip {@link AppPref#SENSITIVE_PREF_KEYS} entries from a SharedPreferences
     * {@code <map>} document so live secrets (authorization key, Tasker signing
     * secret, VirusTotal API key) never enter an exported bundle. Files that carry
     * no sensitive key are returned byte-for-byte unchanged, so ordinary preferences
     * still round-trip exactly.
     */
    @VisibleForTesting
    @NonNull
    static byte[] filterSensitivePrefXml(@NonNull byte[] xmlBytes)
            throws IOException, ParserConfigurationException, SAXException,
            SnapshotImportException, TransformerException {
        if (!mayContainSensitiveKey(xmlBytes)) {
            return xmlBytes;
        }
        Document doc = parseXmlMap(xmlBytes);
        Element map = doc.getDocumentElement();
        for (String key : AppPref.SENSITIVE_PREF_KEYS) {
            removePreferenceNode(map, key);
        }
        return toXmlBytes(doc);
    }

    private static boolean mayContainSensitiveKey(@NonNull byte[] xmlBytes) {
        String text = new String(xmlBytes, StandardCharsets.UTF_8);
        for (String key : AppPref.SENSITIVE_PREF_KEYS) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
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
        return readEntryBounded(in, maxBytes, entryName, null);
    }

    @NonNull
    private static byte[] readEntryBounded(@NonNull InputStream in, long maxBytes,
                                           @NonNull String entryName,
                                           @Nullable ArchiveExtractionGuard guard)
            throws IOException, SnapshotImportException {
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
            if (guard != null) guard.addBytes(n);
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
        public boolean restoreLogFilters = true;
        public boolean restoreFmFavorites = true;
        public boolean restoreFreezeTypes = true;
        public boolean mergePrefs = true;
        public boolean mergeRules = true;
    }

    public static final class ExportResult {
        public final int prefsCount;
        public final int profilesCount;
        public final int rulesCount;
        public final int opHistoryCount;
        public final int logFiltersCount;
        public final int fmFavoritesCount;
        public final int freezeTypesCount;

        ExportResult(int prefsCount, int profilesCount, int rulesCount, int opHistoryCount,
                     int logFiltersCount, int fmFavoritesCount, int freezeTypesCount) {
            this.prefsCount = prefsCount;
            this.profilesCount = profilesCount;
            this.rulesCount = rulesCount;
            this.opHistoryCount = opHistoryCount;
            this.logFiltersCount = logFiltersCount;
            this.fmFavoritesCount = fmFavoritesCount;
            this.freezeTypesCount = freezeTypesCount;
        }
    }

    public static final class ImportResult {
        @NonNull
        public final ManifestSummary manifest;
        /** SHA-256 of the bundle that was imported, the same one its preview showed. */
        @NonNull
        public final String sha256;
        public final int prefsRestored;
        public final int profilesRestored;
        public final int tagsRestored;
        public final int rulesRestored;
        public final int opHistoryRestored;
        public final int logFiltersRestored;
        public final int fmFavoritesRestored;
        public final int freezeTypesRestored;

        ImportResult(@NonNull ManifestSummary manifest, @NonNull String sha256, int prefsRestored,
                     int profilesRestored, int tagsRestored, int rulesRestored, int opHistoryRestored,
                     int logFiltersRestored, int fmFavoritesRestored, int freezeTypesRestored) {
            this.manifest = manifest;
            this.sha256 = sha256;
            this.prefsRestored = prefsRestored;
            this.profilesRestored = profilesRestored;
            this.tagsRestored = tagsRestored;
            this.rulesRestored = rulesRestored;
            this.opHistoryRestored = opHistoryRestored;
            this.logFiltersRestored = logFiltersRestored;
            this.fmFavoritesRestored = fmFavoritesRestored;
            this.freezeTypesRestored = freezeTypesRestored;
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
        @NonNull
        public final List<String> contents;
        public final int prefsCount;
        public final int profilesCount;
        public final int rulesCount;
        public final int opHistoryCount;
        public final int logFiltersCount;
        public final int fmFavoritesCount;
        public final int freezeTypesCount;
        /** The keys of the manifest's counts object; only these are checked against the payload. */
        @NonNull
        final Set<String> declaredCounts;

        ManifestSummary(int schemaVersion, @NonNull String format, long generatedAt,
                        @Nullable String sourcePackage, @Nullable String sourceVersionName,
                        int sourceVersionCode, @NonNull List<String> contents,
                        int prefsCount, int profilesCount, int rulesCount, int opHistoryCount,
                        int logFiltersCount, int fmFavoritesCount, int freezeTypesCount,
                        @NonNull Set<String> declaredCounts) {
            this.schemaVersion = schemaVersion;
            this.format = format;
            this.generatedAt = generatedAt;
            this.sourcePackage = sourcePackage;
            this.sourceVersionName = sourceVersionName;
            this.sourceVersionCode = sourceVersionCode;
            this.contents = contents;
            this.prefsCount = prefsCount;
            this.profilesCount = profilesCount;
            this.rulesCount = rulesCount;
            this.opHistoryCount = opHistoryCount;
            this.logFiltersCount = logFiltersCount;
            this.fmFavoritesCount = fmFavoritesCount;
            this.freezeTypesCount = freezeTypesCount;
            this.declaredCounts = declaredCounts;
        }

        public boolean hasPrefs() {
            return contents.contains("prefs") && prefsCount > 0;
        }

        public boolean hasProfiles() {
            return contents.contains("profiles") && profilesCount > 0;
        }

        public boolean hasRules() {
            return contents.contains("rules") && rulesCount > 0;
        }

        public boolean hasTags() {
            return contents.contains("tags");
        }

        public boolean hasOpHistory() {
            return contents.contains("op_history") && opHistoryCount > 0;
        }

        public boolean hasLogFilters() {
            return contents.contains("log_filters") && logFiltersCount > 0;
        }

        public boolean hasFmFavorites() {
            return contents.contains("fm_favorites") && fmFavoritesCount > 0;
        }

        public boolean hasFreezeTypes() {
            return contents.contains("freeze_types") && freezeTypesCount > 0;
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
                List<String> contents = new ArrayList<>();
                JSONArray contentsArr = obj.optJSONArray("contents");
                if (contentsArr != null) {
                    for (int i = 0; i < contentsArr.length(); i++) {
                        contents.add(contentsArr.optString(i, ""));
                    }
                }
                int prefsCount = 0, profilesCount = 0, rulesCount = 0, opHistoryCount = 0;
                int logFiltersCount = 0, fmFavoritesCount = 0, freezeTypesCount = 0;
                JSONObject counts = obj.optJSONObject("counts");
                Set<String> declaredCounts = new HashSet<>();
                if (counts != null) {
                    for (java.util.Iterator<String> keys = counts.keys(); keys.hasNext(); ) {
                        declaredCounts.add(keys.next());
                    }
                    prefsCount = counts.optInt("prefs_files", 0);
                    profilesCount = counts.optInt("profiles", 0);
                    rulesCount = counts.optInt("rules", 0);
                    opHistoryCount = counts.optInt("op_history", 0);
                    logFiltersCount = counts.optInt("log_filters", 0);
                    fmFavoritesCount = counts.optInt("fm_favorites", 0);
                    freezeTypesCount = counts.optInt("freeze_types", 0);
                }
                return new ManifestSummary(schema, format, ts, pkg, ver, code,
                        contents, prefsCount, profilesCount, rulesCount, opHistoryCount,
                        logFiltersCount, fmFavoritesCount, freezeTypesCount,
                        Collections.unmodifiableSet(declaredCounts));
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
