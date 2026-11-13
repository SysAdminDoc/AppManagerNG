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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

/**
 * Language files people import to translate the app themselves. A file is kept as imported, checked
 * against the running app's English, and on Android 11 and later compiled into a small resources
 * table that a {@code ResourcesLoader} lays over the app's own strings for that language. Using one
 * switches the app to its language, and stopping switches back to whatever was chosen before.
 */
public final class LanguagePackManager {
    public static final String TAG = "LanguagePacks";

    private static final String PREFS = "language_packs";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREVIOUS = "previous_language";
    private static final String DIR = "language_packs";
    private static final String PACK_SUFFIX = ".json";
    private static final String TABLE_SUFFIX = ".arsc";
    private static final String STAMP_SUFFIX = ".stamp";
    private static final String REMOVED_SUFFIX = ".removed";
    private static final String TEMP_SUFFIX = ".tmp";
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

        ImportResult(@NonNull String code, @NonNull String name, boolean replaced, boolean inUse) {
            this.code = code;
            this.name = name;
            this.replaced = replaced;
            this.inUse = inUse;
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

    /** Using a language file needs {@code ResourcesLoader}, which arrived in Android 11. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    public static boolean canUse() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * The language file the user chose to use, or {@code null}. Cheap enough for every locale lookup.
     */
    @Nullable
    public static String getActiveCode(@NonNull Context context) {
        if (!canUse()) {
            return null;
        }
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
        if (!canUse() || getActiveCode(app) == null) {
            return;
        }
        LanguagePackManager manager = getInstance(app);
        String code = manager.inUseCode();
        if (code == null) {
            return;
        }
        long start = SystemClock.elapsedRealtime();
        try {
            manager.loadTable(manager.prepare(code));
            Log.i(TAG, "Loaded the " + code + " language file in " + (SystemClock.elapsedRealtime() - start) + " ms");
        } catch (Exception e) {
            Log.e(TAG, "Couldn't load the " + code + " language file", e);
        }
    }

    /** Makes sure an activity's resources include the language file in use. */
    @MainThread
    public static void attachToActivity(@NonNull Activity activity) {
        LanguagePackManager manager = sInstance;
        if (manager != null && manager.mLoader != null && canUse()) {
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
    @Nullable
    private AndroidSourceStrings mSource;
    /** The {@code ResourcesLoader}, kept as an Object so older Android never loads the class. */
    @Nullable
    private Object mLoader;

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
        LanguagePack pack = LanguagePackReader.read(data);
        String code = canonicalCode(pack.code);
        pack = pack.withCode(code);
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source());
        if (report.usable == 0) {
            throw new LanguagePackException(LanguagePackException.Reason.NOTHING_USABLE, "No usable entries");
        }
        File target = packFile(code);
        boolean replaced = target.exists();
        writeAtomically(target, data);
        return new ImportResult(code, pack.name, replaced, code.equals(inUseCode()));
    }

    /** Compiles {@code code}'s table for this app build unless it's already cached. */
    @WorkerThread
    @NonNull
    public File prepare(@NonNull String code) throws IOException, LanguagePackException {
        synchronized (mTableLock) {
            File packFile = packFile(code);
            File table = new File(mTableDir, code + TABLE_SUFFIX);
            File stampFile = new File(mTableDir, code + STAMP_SUFFIX);
            String stamp = stamp(packFile);
            if (table.isFile() && stampFile.isFile() && stamp.equals(readText(stampFile))) {
                return table;
            }
            LanguagePack pack = readStored(code);
            byte[] arsc = LanguagePackCompiler.compile(mContext.getResources(), source(), pack);
            writeAtomically(table, arsc);
            writeAtomically(stampFile, stamp.getBytes(StandardCharsets.UTF_8));
            return table;
        }
    }

    /**
     * Shows the app in {@code code}'s language using the table {@link #prepare} built, and
     * remembers the language that was chosen before so {@link #stop} can go back to it.
     */
    @MainThread
    public void use(@NonNull String code, @NonNull File table) throws IOException {
        if (!canUse()) {
            throw new IOException("Needs Android 11");
        }
        loadTable(table);
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
        if (mLoader != null && canUse()) {
            Api30.clear(mLoader);
        }
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
        deleteTable(code);
        return new Removal(code, name, wasInUse);
    }

    @WorkerThread
    public void undoRemove(@NonNull Removal removal) throws IOException {
        File removed = new File(mDir, removal.code + REMOVED_SUFFIX);
        if (!removed.renameTo(packFile(removal.code))) {
            throw new IOException("Couldn't restore " + removal.code);
        }
    }

    @WorkerThread
    public void finishRemove(@NonNull Removal removal) {
        //noinspection ResultOfMethodCallIgnored
        new File(mDir, removal.code + REMOVED_SUFFIX).delete();
    }

    /**
     * Writes a template with every English string. With {@code builtInTag} it's filled in with
     * that built-in translation, ready to be corrected or finished.
     */
    @WorkerThread
    public void exportTemplate(@NonNull Uri uri, @Nullable String builtInTag) throws IOException {
        writeToUri(uri, template(builtInTag));
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

    /** Writes an installed file back out with every string, marking what still needs work. */
    @WorkerThread
    public void exportPack(@NonNull String code, @NonNull Uri uri) throws IOException, LanguagePackException {
        writeToUri(uri, forEditing(code));
    }

    @WorkerThread
    @NonNull
    String forEditing(@NonNull String code) throws IOException, LanguagePackException {
        LanguagePack pack = readStored(code);
        AndroidSourceStrings source = source();
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source);
        return LanguagePackWriter.forEditing(pack, report, source,
                AndroidSourceStrings.pluralKeywords(Locale.forLanguageTag(code)), madeWith());
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

    private void deleteTable(@NonNull String code) {
        synchronized (mTableLock) {
            //noinspection ResultOfMethodCallIgnored
            new File(mTableDir, code + TABLE_SUFFIX).delete();
            //noinspection ResultOfMethodCallIgnored
            new File(mTableDir, code + STAMP_SUFFIX).delete();
        }
    }

    @NonNull
    private String stamp(@NonNull File packFile) {
        File apk = new File(mContext.getApplicationInfo().sourceDir);
        return TABLE_FORMAT + ":" + BuildConfig.VERSION_CODE + ":" + apk.lastModified() + ":"
                + packFile.length() + ":" + packFile.lastModified();
    }

    private void writeToUri(@NonNull Uri uri, @NonNull String text) throws IOException {
        try (OutputStream os = mContext.getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("Can't open " + uri);
            }
            os.write(text.getBytes(StandardCharsets.UTF_8));
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

    @NonNull
    private static String readText(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return new String(readCapped(in), StandardCharsets.UTF_8);
        }
    }

    private static void writeAtomically(@NonNull File target, @NonNull byte[] data) throws IOException {
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
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
