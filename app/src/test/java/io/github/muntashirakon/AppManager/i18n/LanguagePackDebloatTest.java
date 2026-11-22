// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.debloat.BloatwareDetailsDialog;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.test.CachedAppContext;

/**
 * #31: the Debloater's descriptions come from the debloat list, not strings.xml, so a language
 * file carries them in an optional {@code debloat} section, shipped with the strings or as an
 * add-on file of its own.
 */
@RunWith(RobolectricTestRunner.class)
public class LanguagePackDebloatTest {
    private static final String CANCEL = "\"cancel\": {\"original\": \"Cancel\", \"translation\": \"Abbrechen\"}";
    private static final String BLOAT = "com.example.bloat";
    private static final String OTHER = "com.example.other";
    private static final String DEBLOAT_SECTION = "\"debloat\": {"
            + "\"" + BLOAT + "\": {"
            + "\"description\": {\"original\": \"Shows ads on the lock screen.\", \"translation\": \"Zeigt Werbung auf dem Sperrbildschirm.\"},"
            + "\"warning\": {\"original\": \"Turning it off stops the lock screen widgets.\", \"translation\": \"Ohne sie gehen die Widgets nicht.\"}},"
            + "\"" + OTHER + "\": {"
            + "\"description\": {\"original\": \"An older demo text.\", \"translation\": \"Eine alte Demo.\"}}"
            + "}";

    /** A JSON escape for the bell character, which a description can't hold. */
    private static final String BELL = "\\" + "u0007";

    private Context mContext;
    private File mPacks;

    @Before
    public void setUp() {
        CachedAppContext.forget();
        mContext = ApplicationProvider.getApplicationContext();
        mPacks = new File(mContext.getFilesDir(), "language_packs");
        LanguageFileArchiveTest.deleteAll(mPacks);
        LanguageFileArchiveTest.deleteAll(new File(mContext.getNoBackupFilesDir(), "language_packs"));
        StaticDataset.setDebloatObjectsForTesting(Arrays.asList(
                definition(BLOAT, "oem", "caution", "Shows ads on the lock screen.",
                        "Turning it off stops the lock screen widgets."),
                definition(OTHER, "aosp", "safe", "An old demo.", null)));
    }

    /** An entry as the debloat list has it (Gson can't build one on the JVM: it has a Drawable field). */
    private static DebloatObject definition(String packageName, String type, String removal, String description,
                                            String warning) {
        DebloatObject object = new DebloatObject();
        object.packageName = packageName;
        object.type = type;
        try {
            for (String[] field : new String[][]{{"mRemoval", removal}, {"mDescription", description},
                    {"mWarning", warning}}) {
                Field f = DebloatObject.class.getDeclaredField(field[0]);
                f.setAccessible(true);
                f.set(object, field[1]);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return object;
    }

    @After
    public void tearDown() {
        StaticDataset.clearDebloatObjectsCache();
        LanguagePackManager.setInstanceForTesting(null);
        mContext.getSharedPreferences("language_packs", Context.MODE_PRIVATE).edit().clear().commit();
        AppPref.getNewInstance(mContext).setPref(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, LangUtils.LANG_AUTO);
        CachedAppContext.forget();
    }

    @Test
    public void theReaderKeepsTheDebloatSectionAndSkipsWhatIsBroken() throws Exception {
        String broken = "\"debloat\": {"
                + "\"" + BLOAT + "\": {\"description\": {\"original\": \"Shows ads on the lock screen.\", \"translation\": \"Werbung\"}},"
                + "\"../bad\": {\"description\": {\"translation\": \"Pfad\"}},"
                + "\"com.example.array\": [\"no\"],"
                + "\"com.example.number\": {\"description\": {\"translation\": 5}},"
                + "\"com.example.control\": {\"description\": {\"translation\": \"bell" + BELL + "\"}},"
                + "\"com.example.empty\": {\"description\": {\"original\": \"x\", \"translation\": \"\"}}"
                + "}";
        LanguagePack pack = LanguagePackReader.read(pack("de", broken, CANCEL));
        assertEquals(1, pack.entries.size());
        assertEquals(1, pack.debloat.size());
        LanguagePack.DebloatEntry entry = pack.debloat.get(BLOAT);
        assertNotNull(entry);
        assertEquals("Werbung", entry.description);
        assertEquals("Shows ads on the lock screen.", entry.originalDescription);
        assertNull(entry.warning);

        // Files without the section read as before, and a section that isn't an object is ignored
        assertTrue(LanguagePackReader.read(pack("de", null, CANCEL)).debloat.isEmpty());
        assertTrue(LanguagePackReader.read(pack("de", "\"debloat\": [1, 2]", CANCEL)).debloat.isEmpty());
        // An add-on file has the section and no strings
        LanguagePack addOn = LanguagePackReader.read(pack("de", DEBLOAT_SECTION));
        assertTrue(addOn.entries.isEmpty());
        assertEquals(2, addOn.debloat.size());
    }

    @Test
    public void anAddOnFileJoinsTheInstalledLanguageAndSurvivesItsUpdates() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        // An add-on needs the language it belongs to
        LanguagePackException alone = assertThrows(LanguagePackException.class,
                () -> manager.importPack(pack("de", DEBLOAT_SECTION)));
        assertEquals(LanguagePackException.Reason.NOTHING_USABLE, alone.reason);

        manager.importPack(pack("de", null, CANCEL));
        assertNull(manager.debloatTranslations("de"));
        LanguagePackManager.ImportResult added = manager.importPack(pack("de", DEBLOAT_SECTION));
        assertTrue(added.replaced);
        assertEquals(2, added.debloatEntries);
        // The strings are still there
        assertEquals(1, manager.list().get(0).translated);

        DebloatTranslations translations = manager.debloatTranslations("de");
        assertNotNull(translations);
        assertEquals("Zeigt Werbung auf dem Sperrbildschirm.",
                translations.description(BLOAT, "Shows ads on the lock screen."));
        assertEquals("Ohne sie gehen die Widgets nicht.",
                translations.warning(BLOAT, "Turning it off stops the lock screen widgets."));
        // Translated from older English: the current English is shown instead
        assertNull(translations.description(OTHER, "An old demo."));
        // No English warning, so nothing to translate
        assertNull(translations.warning(BLOAT, null));
        assertNull(translations.description("com.example.unknown", "Anything"));

        // Importing the strings again keeps the descriptions
        manager.importPack(pack("de", null, CANCEL));
        assertNotNull(manager.debloatTranslations("de"));

        // Removing the language takes the add-on with it, and undo brings both back
        LanguagePackManager.Removal removal = manager.remove("de", "Deutsch");
        assertNull(manager.debloatTranslations("de"));
        manager.undoRemove(removal);
        assertNotNull(manager.debloatTranslations("de"));
    }

    @Test
    public void exportsCarryTheDebloaterDescriptionsOnRequest() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", DEBLOAT_SECTION.replace("\"" + OTHER + "\"", "\"com.example.gone\""), CANCEL));

        // A plain export keeps what was translated, so nothing is lost on the way back in
        JSONObject plain = new JSONObject(new String(manager.exportBytes("de", false, false), StandardCharsets.UTF_8));
        JSONObject section = plain.getJSONObject("debloat");
        assertEquals(1, section.length());
        assertEquals("Zeigt Werbung auf dem Sperrbildschirm.",
                section.getJSONObject(BLOAT).getJSONObject("description").getString("translation"));

        // With the descriptions, every entry of the list is there to fill in
        JSONObject full = new JSONObject(new String(manager.exportBytes("de", false, true), StandardCharsets.UTF_8));
        section = full.getJSONObject("debloat");
        assertEquals(2, section.length());
        JSONObject other = section.getJSONObject(OTHER);
        assertEquals("An old demo.", other.getJSONObject("description").getString("original"));
        assertEquals("", other.getJSONObject("description").getString("translation"));
        assertFalse(other.has("warning"));
        assertEquals("Turning it off stops the lock screen widgets.",
                section.getJSONObject(BLOAT).getJSONObject("warning").getString("original"));
        assertEquals("Abbrechen", full.getJSONObject("translations").getJSONObject("cancel").getString("translation"));
        // And it imports back
        assertEquals(1, LanguagePackReader.read(manager.exportBytes("de", false, true)).debloat.size());

        // The add-on template holds only the descriptions
        JSONObject template = new JSONObject(manager.debloatTemplate("ru"));
        assertEquals("ru", template.getString("language_code"));
        assertEquals(0, template.getJSONObject("translations").length());
        assertEquals(2, template.getJSONObject("debloat").length());
        assertEquals("Shows ads on the lock screen.", template.getJSONObject("debloat").getJSONObject(BLOAT)
                .getJSONObject("description").getString("original"));
    }

    @Test
    public void theDebloaterShowsTheTranslatedDescription() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", DEBLOAT_SECTION, CANCEL));
        LanguagePackManager.setInstanceForTesting(manager);
        DebloatObject english = filled(BLOAT);
        assertNotNull(english);
        assertEquals("Shows ads on the lock screen.", english.getDescription());

        // In use: the language file is active and is the app's language
        mContext.getSharedPreferences("language_packs", Context.MODE_PRIVATE).edit().putString("active", "de").commit();
        AppPref.getNewInstance(mContext).setPref(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, "de");
        assertEquals("de", manager.inUseCode());

        DebloatObject translated = filled(BLOAT);
        assertNotNull(translated);
        assertEquals("Zeigt Werbung auf dem Sperrbildschirm.", translated.getDescription());
        assertEquals("Ohne sie gehen die Widgets nicht.", translated.getWarning());
        // Presets and OEM matching keep reading the English
        assertEquals("Shows ads on the lock screen.", translated.getEnglishDescription());
        // A stale translation leaves the English
        DebloatObject other = filled(OTHER);
        assertNotNull(other);
        assertEquals("An old demo.", other.getDescription());

        // The details dialog shows it
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.AppTheme_V2);
        controller.setup();
        try {
            BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(BLOAT);
            dialog.showNow(controller.get().getSupportFragmentManager(), BloatwareDetailsDialog.TAG);
            String shown = waitForDescription(dialog);
            assertNotNull(shown);
            assertTrue(shown, shown.startsWith("Zeigt Werbung auf dem Sperrbildschirm."));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    /** The list fills its rows on a worker thread, since that reads the app database. */
    private DebloatObject filled(String packageName) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            return worker.submit(() -> StaticDataset.getDebloatObjectWithInstalledInfo(mContext, packageName)).get();
        } finally {
            worker.shutdown();
        }
    }

    private static String waitForDescription(BloatwareDetailsDialog dialogFragment) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = dialogFragment.getDialog();
            TextView description = dialog == null ? null : dialog.findViewById(R.id.apk_description);
            if (description != null && description.getText() != null && description.getText().length() > 0) {
                return description.getText().toString();
            }
            Thread.sleep(20);
        }
        return null;
    }

    private static byte[] pack(String code, String debloat, String... entries) {
        return ("{\"schema_version\": 1, \"language_code\": \"" + code + "\", \"language_name\": \"Deutsch\","
                + (debloat != null ? " " + debloat + "," : "")
                + " \"translations\": {" + String.join(",", entries) + "}}").getBytes(StandardCharsets.UTF_8);
    }
}
