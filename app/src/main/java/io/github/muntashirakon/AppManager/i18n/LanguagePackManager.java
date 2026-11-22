// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsSearchIndex;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

/**
 * Language files people import to translate the app themselves. A file is kept as imported, checked
 * against the running app's English, and on Android 11 and later compiled into a small resources
 * table that a {@code ResourcesLoader} lays over the app's own strings for that language. Older
 * Android has no {@code ResourcesLoader}, so there the file's text is shown through
 * {@link LanguagePackCompat}. Using one switches the app to its language, and stopping switches
 * back to whatever was chosen before.
 */
public final class LanguagePackManager {
    public static final String TAG = "LanguagePacks";

    private static final String PREFS = "language_packs";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREVIOUS = "previous_language";
    private static final String DIR = "language_packs";
    private static final String PACK_SUFFIX = ".json";
    /** Debloater descriptions imported on their own, kept next to the language file they belong to. */
    private static final String DEBLOAT_SUFFIX = ".debloat";
    private static final String TABLE_SUFFIX = ".arsc";
    private static final String COMPAT_SUFFIX = ".compat";
    private static final String STAMP_SUFFIX = ".stamp";
    private static final String REMOVED_SUFFIX = ".removed";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String FILE_PREFIX = "AppManagerNG-";
    /** Bumped whenever {@link ArscWriter} output changes, so cached tables are rebuilt. */
    private static final int TABLE_FORMAT = 1;

    public static final class Installed {
        @NonNull
        public final String code;
        @NonNull
        public final String name;
        public final int translated;
        public final int total;
        public final int outdated;
        public final int needsFix;
        public final boolean inUse;

        Installed(@NonNull String code, @NonNull String name, int translated, int total, int outdated,
                  int needsFix, boolean inUse) {
            this.code = code;
            this.name = name;
            this.translated = translated;
            this.total = total;
            this.outdated = outdated;
            this.needsFix = needsFix;
            this.inUse = inUse;
        }
    }

    public static final class ImportResult {
        @NonNull
        public final String code;
        @NonNull
        public final String name;
        public final boolean replaced;
        /** The file replaced the one in use, so the app should load it again. */
        public final boolean inUse;
        /** The language file's name inside the zip it came in, or {@code null} for a plain file. */
        @Nullable
        public final String archiveEntry;
        /** How many .json files that zip had. */
        public final int archiveJsonFiles;
        /** Debloater descriptions the file brought. */
        public final int debloatEntries;

        ImportResult(@NonNull String code, @NonNull String name, boolean replaced, boolean inUse,
                     @Nullable String archiveEntry, int archiveJsonFiles, int debloatEntries) {
            this.code = code;
            this.name = name;
            this.replaced = replaced;
            this.inUse = inUse;
            this.archiveEntry = archiveEntry;
            this.archiveJsonFiles = archiveJsonFiles;
            this.debloatEntries = debloatEntries;
        }
    }

    /** A removed file that can still be put back. */
    public static final class Removal {
        @NonNull
        public final String code;
        @NonNull
        public final String name;
        public final boolean wasInUse;

        Removal(@NonNull String code, @NonNull String name, boolean wasInUse) {
            this.code = code;
            this.name = name;
            this.wasInUse = wasInUse;
        }
    }

    @Nullable
    private static volatile LanguagePackManager sInstance;

    @NonNull
    public static LanguagePackManager getInstance(@NonNull Context context) {
        LanguagePackManager instance = sInstance;
        if (instance == null) {
            synchronized (LanguagePackManager.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new LanguagePackManager(context.getApplicationContext());
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable LanguagePackManager manager) {
        sInstance = manager;
    }

    /**
     * Android 11 and later lay a language file over the app's strings with a
     * {@code ResourcesLoader}. Older Android shows it through {@link LanguagePackCompat}.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    public static boolean hasResourcesLoader() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * The language file the user chose to use, or {@code null}. Cheap enough for every locale lookup.
     */
    @Nullable
    public static String getActiveCode(@NonNull Context context) {
        String code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null);
        return code != null && code.equals(LanguagePackReader.normalizeCode(code)) ? code : null;
    }

    /**
     * Lays the language file in use over the app's strings. Called from {@code Application#onCreate}
     * before the app's locale is applied. A table cached for this app build loads in a few
     * milliseconds. After an app update the file is compiled again first.
     * <p>
     * This blocks on purpose: the first activity inflates right after {@code onCreate}, and a table
     * loaded later would show the first screen in the wrong language.
     */
    @SuppressLint("WrongThread")
    @MainThread
    public static void installOnStartup(@NonNull Application app) {
        if (getActiveCode(app) == null) {
            return;
        }
        LanguagePackManager manager = getInstance(app);
        String code = manager.inUseCode();
        if (code == null) {
            return;
        }
        long start = SystemClock.elapsedRealtime();
        try {
            manager.load(manager.prepare(code));
            Log.i(TAG, "Loaded the " + code + " language file in " + (SystemClock.elapsedRealtime() - start) + " ms");
        } catch (Exception e) {
            Log.e(TAG, "Couldn't load the " + code + " language file", e);
        }
    }

    /** Makes sure an activity's resources include the language file in use. */
    @MainThread
    public static void attachToActivity(@NonNull Activity activity) {
        LanguagePackManager manager = sInstance;
        if (manager != null && manager.mLoader != null && hasResourcesLoader()) {
            Api30.attach(activity.getResources(), manager.mLoader);
        }
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final File mDir;
    @NonNull
    private final File mTableDir;
    @NonNull
    private final SharedPreferences mPrefs;
    private final Object mTableLock = new Object();
    /** What {@link #prepare} last read for compatibility mode, until {@link #load} takes it. */
    @Nullable
    private volatile Prepared mPrepared;
    private final Object mDebloatLock = new Object();
    @Nullable
    private AndroidSourceStrings mSource;
    /** The {@code ResourcesLoader}, kept as an Object so older Android never loads the class. */
    @Nullable
    private Object mLoader;
    @Nullable
    private String mDebloatStamp;
    @Nullable
    private DebloatTranslations mDebloat;

    @VisibleForTesting
    LanguagePackManager(@NonNull Context context) {
        mContext = context;
        mDir = new File(context.getFilesDir(), DIR);
        mTableDir = new File(context.getNoBackupFilesDir(), DIR);
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Left over when the app stopped before a removal was final or a write finished.
        deleteLeftovers(mDir);
        deleteLeftovers(mTableDir);
    }

    private static void deleteLeftovers(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(REMOVED_SUFFIX) || name.endsWith(TEMP_SUFFIX)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @NonNull
    synchronized AndroidSourceStrings source() throws IOException {
        if (mSource == null) {
            mSource = new AndroidSourceStrings(mContext);
        }
        return mSource;
    }

    /** The language file the app is showing right now, if any. */
    @Nullable
    public String inUseCode() {
        String active = getActiveCode(mContext);
        return active != null && active.equals(Prefs.Appearance.getLanguage(mContext)) ? active : null;
    }

    @WorkerThread
    @NonNull
    public List<Installed> list() throws IOException {
        List<Installed> out = new ArrayList<>();
        File[] files = mDir.listFiles();
        if (files == null) {
            return out;
        }
        String inUse = inUseCode();
        for (File f : files) {
            String fileName = f.getName();
            if (!fileName.endsWith(PACK_SUFFIX)) {
                continue;
            }
            String code = fileName.substring(0, fileName.length() - PACK_SUFFIX.length());
            if (!code.equals(LanguagePackReader.normalizeCode(code))) {
                // Debloater descriptions (de.debloat.json) and anything else that isn't a language
                continue;
            }
            try {
                LanguagePack pack = readStored(code);
                LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source());
                out.add(new Installed(code, pack.name, report.usable, report.sourceCount, report.outdated,
                        report.needsFix, code.equals(inUse)));
            } catch (LanguagePackException e) {
                Log.w(TAG, "Skipping " + fileName, e);
            }
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Imports a language file, or a zip with one in it. */
    @WorkerThread
    @NonNull
    public ImportResult importPack(@NonNull Uri uri) throws IOException, LanguagePackException {
        byte[] data;
        try (InputStream in = mContext.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Can't open " + uri);
            }
            data = readCapped(in);
        }
        return importPack(data);
    }

    @WorkerThread
    @NonNull
    ImportResult importPack(@NonNull byte[] data) throws IOException, LanguagePackException {
        String archiveEntry = null;
        int archiveJsonFiles = 0;
        if (LanguageFileArchive.isZip(data)) {
            LanguageFileArchive.Picked picked = LanguageFileArchive.pick(data);
            data = picked.data;
            archiveEntry = picked.entryName;
            archiveJsonFiles = picked.jsonFiles;
        }
        LanguagePack pack = LanguagePackReader.read(data);
        String code = canonicalCode(pack.code);
        pack = pack.withCode(code);
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source());
        File target = packFile(code);
        if (report.usable == 0) {
            if (pack.entries.isEmpty() && !pack.debloat.isEmpty() && target.isFile()) {
                // Debloater descriptions on their own, for a language that's already here
                writeAtomically(debloatFile(code), data);
                forgetDebloat();
                return new ImportResult(code, pack.name, true, code.equals(inUseCode()), archiveEntry,
                        archiveJsonFiles, pack.debloat.size());
            }
            throw new LanguagePackException(LanguagePackException.Reason.NOTHING_USABLE, "No usable entries");
        }
        boolean replaced = target.exists();
        writeAtomically(target, data);
        if (!pack.debloat.isEmpty()) {
            // The new file has descriptions of its own, which replace any imported before
            //noinspection ResultOfMethodCallIgnored
            debloatFile(code).delete();
        }
        forgetDebloat();
        return new ImportResult(code, pack.name, replaced, code.equals(inUseCode()), archiveEntry, archiveJsonFiles,
                pack.debloat.size());
    }

    /**
     * Gets {@code code}'s text ready for this app build unless it already is: a resources table on
     * Android 11 and later, the text by resource ID before that.
     */
    @WorkerThread
    @NonNull
    public File prepare(@NonNull String code) throws IOException, LanguagePackException {
        synchronized (mTableLock) {
            boolean loader = hasResourcesLoader();
            File packFile = packFile(code);
            String suffix = loader ? TABLE_SUFFIX : COMPAT_SUFFIX;
            File table = new File(mTableDir, code + suffix);
            File stampFile = new File(mTableDir, code + suffix + STAMP_SUFFIX);
            String stamp = stamp(packFile, loader ? TABLE_FORMAT : CompatPack.FORMAT);
            if (table.isFile() && stampFile.isFile() && stamp.equals(readText(stampFile))) {
                if (!loader) {
                    keepForUse(table, readTable(table));
                }
                return table;
            }
            LanguagePack pack = readStored(code);
            byte[] prepared = loader
                    ? LanguagePackCompiler.compile(mContext.getResources(), source(), pack)
                    : CompatPack.build(source(), pack).toBytes();
            writeAtomically(table, prepared);
            writeAtomically(stampFile, stamp.getBytes(StandardCharsets.UTF_8));
            if (!loader) {
                keepForUse(table, prepared);
            }
            return table;
        }
    }

    /**
     * Holds the text {@link #prepare} read for compatibility mode, so {@link #use} on the main
     * thread doesn't read the file again.
     */
    private void keepForUse(@NonNull File table, @NonNull byte[] data) throws IOException {
        mPrepared = new Prepared(table, CompatPack.fromBytes(data));
    }

    @NonNull
    private static byte[] readTable(@NonNull File table) throws IOException {
        try (InputStream in = new FileInputStream(table)) {
            return readAll(in);
        }
    }

    private static final class Prepared {
        @NonNull
        final File table;
        @NonNull
        final CompatPack pack;

        Prepared(@NonNull File table, @NonNull CompatPack pack) {
            this.table = table;
            this.pack = pack;
        }
    }

    /**
     * Shows the app in {@code code}'s language using what {@link #prepare} made, and remembers
     * the language that was chosen before so {@link #stop} can go back to it.
     */
    @MainThread
    public void use(@NonNull String code, @NonNull File prepared) throws IOException {
        load(prepared);
        SharedPreferences.Editor editor = mPrefs.edit().putString(KEY_ACTIVE, code);
        if (inUseCode() == null) {
            editor.putString(KEY_PREVIOUS, Prefs.Appearance.getLanguage(mContext));
        }
        editor.apply();
        applyLanguage(code);
    }

    /** Stops using the language file and goes back to the language chosen before it. */
    @MainThread
    public void stop() {
        boolean inUse = inUseCode() != null;
        String previous = mPrefs.getString(KEY_PREVIOUS, LangUtils.LANG_AUTO);
        mPrefs.edit().remove(KEY_ACTIVE).remove(KEY_PREVIOUS).apply();
        if (mLoader != null && hasResourcesLoader()) {
            Api30.clear(mLoader);
        }
        LanguagePackCompat.setActive(null);
        SettingsSearchIndex.invalidate();
        if (inUse) {
            applyLanguage(previous == null ? LangUtils.LANG_AUTO : previous);
        } else {
            // Changing a loader rebuilds the app's resources without the language override.
            AppearanceUtils.applyOnlyLocale(mContext);
        }
    }

    /** Removes a file, keeping it aside until {@link #finishRemove} or {@link #undoRemove}. */
    @MainThread
    @NonNull
    public Removal remove(@NonNull String code, @NonNull String name) throws IOException {
        boolean wasInUse = code.equals(inUseCode());
        if (wasInUse || code.equals(getActiveCode(mContext))) {
            stop();
        }
        File removed = new File(mDir, code + REMOVED_SUFFIX);
        //noinspection ResultOfMethodCallIgnored
        removed.delete();
        if (!packFile(code).renameTo(removed)) {
            throw new IOException("Couldn't remove " + code);
        }
        File debloat = debloatFile(code);
        if (debloat.isFile()) {
            File debloatRemoved = new File(mDir, code + DEBLOAT_SUFFIX + REMOVED_SUFFIX);
            //noinspection ResultOfMethodCallIgnored
            debloatRemoved.delete();
            if (!debloat.renameTo(debloatRemoved)) {
                Log.w(TAG, "Couldn't set aside the Debloater descriptions of " + code);
            }
        }
        forgetDebloat();
        deleteTable(code);
        return new Removal(code, name, wasInUse);
    }

    @WorkerThread
    public void undoRemove(@NonNull Removal removal) throws IOException {
        File removed = new File(mDir, removal.code + REMOVED_SUFFIX);
        if (!removed.renameTo(packFile(removal.code))) {
            throw new IOException("Couldn't restore " + removal.code);
        }
        File debloatRemoved = new File(mDir, removal.code + DEBLOAT_SUFFIX + REMOVED_SUFFIX);
        if (debloatRemoved.isFile() && !debloatRemoved.renameTo(debloatFile(removal.code))) {
            Log.w(TAG, "Couldn't restore the Debloater descriptions of " + removal.code);
        }
        forgetDebloat();
    }

    @WorkerThread
    public void finishRemove(@NonNull Removal removal) {
        //noinspection ResultOfMethodCallIgnored
        new File(mDir, removal.code + REMOVED_SUFFIX).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(mDir, removal.code + DEBLOAT_SUFFIX + REMOVED_SUFFIX).delete();
    }

    /**
     * Writes a template with every English string. With {@code builtInTag} it's filled in with
     * that built-in translation, ready to be corrected or finished.
     */
    @WorkerThread
    public void exportTemplate(@NonNull Uri uri, @Nullable String builtInTag) throws IOException {
        writeToUri(uri, template(builtInTag).getBytes(StandardCharsets.UTF_8));
    }

    @WorkerThread
    @NonNull
    String template(@Nullable String builtInTag) throws IOException {
        AndroidSourceStrings source = source();
        String code = "";
        String name = "";
        Map<String, TextValue> prefill = Collections.emptyMap();
        Collection<String> keywords = java.util.Arrays.asList(SourceString.QUANTITIES);
        if (builtInTag != null) {
            Locale locale = Locale.forLanguageTag(builtInTag);
            code = canonicalCode(builtInTag);
            name = displayName(locale);
            prefill = source.builtInTranslation(mContext, locale);
            keywords = AndroidSourceStrings.pluralKeywords(locale);
        }
        return LanguagePackWriter.template(code, name, source, prefill, keywords, madeWith());
    }

    /**
     * Writes a file with only the Debloater's descriptions, to translate and import after the
     * language file they belong to.
     */
    @WorkerThread
    public void exportDebloatTemplate(@NonNull Uri uri, @Nullable String builtInTag) throws IOException {
        writeToUri(uri, debloatTemplate(builtInTag).getBytes(StandardCharsets.UTF_8));
    }

    @WorkerThread
    @NonNull
    String debloatTemplate(@Nullable String builtInTag) {
        String code = "";
        String name = "";
        if (builtInTag != null) {
            code = canonicalCode(builtInTag);
            name = displayName(Locale.forLanguageTag(builtInTag));
        }
        DebloatTranslations translations = code.isEmpty() ? null : debloatTranslations(code);
        return LanguagePackWriter.debloatTemplate(code, name, madeWith(), debloatRows(translations, true));
    }

    /**
     * Writes an installed file back out with every string, marking what still needs work, as a
     * .json or as a zip with the .json in it. With {@code withDebloat} it also lists every
     * Debloater description to translate.
     */
    @WorkerThread
    public void exportPack(@NonNull String code, @NonNull Uri uri, boolean zip, boolean withDebloat)
            throws IOException, LanguagePackException {
        writeToUri(uri, exportBytes(code, zip, withDebloat));
    }

    @WorkerThread
    @NonNull
    byte[] exportBytes(@NonNull String code, boolean zip, boolean withDebloat) throws IOException, LanguagePackException {
        byte[] json = forEditing(code, withDebloat).getBytes(StandardCharsets.UTF_8);
        return zip ? LanguageFileArchive.zip(jsonFileName(code), json) : json;
    }

    @WorkerThread
    @NonNull
    String forEditing(@NonNull String code) throws IOException, LanguagePackException {
        return forEditing(code, false);
    }

    @WorkerThread
    @NonNull
    private String forEditing(@NonNull String code, boolean withDebloat) throws IOException, LanguagePackException {
        LanguagePack pack = readStored(code);
        AndroidSourceStrings source = source();
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source);
        // What was translated always goes along, so nothing is lost on the way back in
        List<LanguagePackWriter.DebloatRow> debloat = debloatRows(debloatTranslations(code), withDebloat);
        return LanguagePackWriter.forEditing(pack, report, source,
                AndroidSourceStrings.pluralKeywords(Locale.forLanguageTag(code)), madeWith(), debloat);
    }

    /** The file name an export of {@code code} gets. */
    @NonNull
    public static String jsonFileName(@NonNull String code) {
        return FILE_PREFIX + code + PACK_SUFFIX;
    }

    /** The zip holding {@link #jsonFileName}: the same name with .zip added. */
    @NonNull
    public static String zipFileName(@NonNull String code) {
        return jsonFileName(code) + LanguageFileArchive.ZIP_SUFFIX;
    }

    /**
     * The Debloater descriptions of the language file in use, or {@code null} when there's none
     * or it has no descriptions.
     */
    @WorkerThread
    @Nullable
    public DebloatTranslations debloatTranslations() {
        String code = inUseCode();
        return code == null ? null : debloatTranslations(code);
    }

    /** {@code code}'s Debloater descriptions, from its own file and any imported on their own. */
    @WorkerThread
    @Nullable
    DebloatTranslations debloatTranslations(@NonNull String code) {
        File pack = packFile(code);
        if (!pack.isFile()) {
            return null;
        }
        File addOn = debloatFile(code);
        String stamp = code + ':' + pack.length() + ':' + pack.lastModified() + ':' + addOn.length() + ':'
                + addOn.lastModified();
        synchronized (mDebloatLock) {
            if (stamp.equals(mDebloatStamp)) {
                return mDebloat;
            }
            Map<String, LanguagePack.DebloatEntry> entries = new HashMap<>();
            try {
                entries.putAll(readStored(code).debloat);
            } catch (IOException | LanguagePackException e) {
                Log.w(TAG, "Couldn't read the Debloater descriptions of " + code, e);
            }
            if (addOn.isFile()) {
                try (InputStream in = new FileInputStream(addOn)) {
                    entries.putAll(LanguagePackReader.read(readCapped(in)).debloat);
                } catch (IOException | LanguagePackException e) {
                    Log.w(TAG, "Couldn't read the Debloater descriptions of " + code, e);
                }
            }
            mDebloat = entries.isEmpty() ? null : new DebloatTranslations(entries);
            mDebloatStamp = stamp;
            return mDebloat;
        }
    }

    /**
     * The Debloater entries a file lists: those with a translation, or with {@code everyEntry}
     * all of them, in the debloat list's order. Packages the list no longer has are left out.
     */
    @WorkerThread
    @NonNull
    private static List<LanguagePackWriter.DebloatRow> debloatRows(@Nullable DebloatTranslations translations,
                                                                   boolean everyEntry) {
        Map<String, LanguagePack.DebloatEntry> translated = translations != null ? translations.entries()
                : Collections.emptyMap();
        if (!everyEntry && translated.isEmpty()) {
            return Collections.emptyList();
        }
        List<LanguagePackWriter.DebloatRow> rows = new ArrayList<>();
        for (DebloatObject definition : StaticDataset.getDebloatObjects()) {
            String description = definition.getEnglishDescription();
            if (definition.packageName == null || description == null) {
                continue;
            }
            LanguagePack.DebloatEntry entry = translated.get(definition.packageName);
            if (entry == null && !everyEntry) {
                continue;
            }
            String warning = definition.getEnglishWarning();
            String descriptionTranslation = entry != null ? entry.description : null;
            String warningTranslation = entry != null && warning != null ? entry.warning : null;
            rows.add(new LanguagePackWriter.DebloatRow(definition.packageName, description, descriptionTranslation,
                    previous(descriptionTranslation, entry != null ? entry.originalDescription : null, description),
                    warning, warningTranslation,
                    previous(warningTranslation, entry != null ? entry.originalWarning : null, warning)));
        }
        return rows;
    }

    /** The English a translation was made from, when that isn't the English the app has now. */
    @Nullable
    private static String previous(@Nullable String translation, @Nullable String original, @Nullable String english) {
        return translation != null && original != null && !original.equals(english) ? original : null;
    }

    private void forgetDebloat() {
        synchronized (mDebloatLock) {
            mDebloatStamp = null;
            mDebloat = null;
        }
    }

    @NonNull
    public static String displayName(@NonNull Locale locale) {
        String name = locale.getDisplayName(locale);
        return name.isEmpty() ? locale.toLanguageTag()
                : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    @NonNull
    private static String madeWith() {
        return "AppManagerNG " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }

    /** The code the app's own language list uses for the same language, so both stay in step. */
    @NonNull
    static String canonicalCode(@NonNull String code) {
        String normalized = LanguagePackReader.normalizeCode(LangUtils.canonicalizeLanguageTag(code));
        return normalized != null ? normalized : code;
    }

    @MainThread
    private void applyLanguage(@NonNull String language) {
        Prefs.Appearance.setLanguage(language);
        AppearanceUtils.applyOnlyLocale(mContext);
        AppearanceUtils.applyConfigurationChangesToActivities();
    }

    /** Lays what {@link #prepare} made over the app's strings. */
    @MainThread
    private void load(@NonNull File prepared) throws IOException {
        if (hasResourcesLoader()) {
            loadTable(prepared);
        } else {
            Prepared kept = mPrepared;
            mPrepared = null;
            LanguagePackCompat.setActive(kept != null && kept.table.equals(prepared) ? kept.pack
                    : CompatPack.fromBytes(readTable(prepared)));
        }
        // The file's text may be in the language already shown, which Settings search can't tell
        SettingsSearchIndex.invalidate();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @MainThread
    private void loadTable(@NonNull File table) throws IOException {
        if (mLoader == null) {
            mLoader = Api30.newLoader();
            Api30.setTable(mLoader, table);
            Api30.attach(mContext.getResources(), mLoader);
        } else {
            Api30.setTable(mLoader, table);
        }
    }

    @NonNull
    LanguagePack readStored(@NonNull String code) throws IOException, LanguagePackException {
        byte[] data;
        try (InputStream in = new FileInputStream(packFile(code))) {
            data = readCapped(in);
        }
        return LanguagePackReader.read(data).withCode(code);
    }

    @NonNull
    private File packFile(@NonNull String code) {
        if (!code.equals(LanguagePackReader.normalizeCode(code))) {
            throw new IllegalArgumentException("Bad language code");
        }
        return new File(mDir, code + PACK_SUFFIX);
    }

    @NonNull
    private File debloatFile(@NonNull String code) {
        if (!code.equals(LanguagePackReader.normalizeCode(code))) {
            throw new IllegalArgumentException("Bad language code");
        }
        return new File(mDir, code + DEBLOAT_SUFFIX + PACK_SUFFIX);
    }

    private void deleteTable(@NonNull String code) {
        synchronized (mTableLock) {
            for (String suffix : new String[]{TABLE_SUFFIX, COMPAT_SUFFIX}) {
                //noinspection ResultOfMethodCallIgnored
                new File(mTableDir, code + suffix).delete();
                //noinspection ResultOfMethodCallIgnored
                new File(mTableDir, code + suffix + STAMP_SUFFIX).delete();
            }
            // Written by builds before compatibility mode
            //noinspection ResultOfMethodCallIgnored
            new File(mTableDir, code + STAMP_SUFFIX).delete();
        }
    }

    @NonNull
    private String stamp(@NonNull File packFile, int format) {
        File apk = new File(mContext.getApplicationInfo().sourceDir);
        return format + ":" + BuildConfig.VERSION_CODE + ":" + apk.lastModified() + ":"
                + packFile.length() + ":" + packFile.lastModified();
    }

    private void writeToUri(@NonNull Uri uri, @NonNull byte[] data) throws IOException {
        try (OutputStream os = mContext.getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("Can't open " + uri);
            }
            os.write(data);
        }
    }

    @NonNull
    private static byte[] readCapped(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > LanguagePackReader.MAX_FILE_BYTES) {
                // Let the reader report it the same way as any other oversized file.
                return new byte[LanguagePackReader.MAX_FILE_BYTES + 1];
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Reads a file this class wrote itself, which a language file's size cap doesn't apply to. */
    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @NonNull
    private static String readText(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return new String(readCapped(in), StandardCharsets.UTF_8);
        }
    }

    private static void writeAtomically(@NonNull File target, @NonNull byte[] data) throws IOException {
        File dir = target.getParentFile();
        if (dir != null && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Can't create " + dir);
        }
        File temp = new File(dir, target.getName() + TEMP_SUFFIX);
        try (FileOutputStream os = new FileOutputStream(temp)) {
            os.write(data);
            os.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Can't write " + target);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class Api30 {
        @NonNull
        static Object newLoader() {
            return new ResourcesLoader();
        }

        static void setTable(@NonNull Object loader, @NonNull File table) throws IOException {
            ResourcesProvider provider;
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(table, ParcelFileDescriptor.MODE_READ_ONLY)) {
                provider = ResourcesProvider.loadFromTable(pfd, null);
            }
            // The old provider isn't closed: closing frees its native table at once, and a
            // Resources object that hasn't been rebuilt yet could still read from it. It goes
            // with the garbage collector instead.
            ((ResourcesLoader) loader).setProviders(Collections.singletonList(provider));
        }

        static void clear(@NonNull Object loader) {
            ((ResourcesLoader) loader).clearProviders();
        }

        static void attach(@NonNull Resources res, @NonNull Object loader) {
            // A no-op when the loader is already there, so the resources aren't rebuilt.
            res.addLoaders((ResourcesLoader) loader);
        }
    }
}
