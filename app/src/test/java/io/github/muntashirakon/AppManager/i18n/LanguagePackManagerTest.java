// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class LanguagePackManagerTest {
    private static final String CANCEL = "\"cancel\": {\"original\": \"Cancel\", \"translation\": \"Abbrechen\"}";
    private static final String SHOWING = "\"main_status_showing_apps\": {\"translation\":"
            + " {\"one\": \"%1$d/%2$d App\", \"other\": \"%1$d/%2$d Apps\"}}";
    private static final String UNKNOWN = "\"nope_not_a_key\": {\"translation\": \"Nein\"}";

    private Context mContext;
    private File mPacks;
    private File mTables;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPacks = new File(mContext.getFilesDir(), "language_packs");
        mTables = new File(mContext.getNoBackupFilesDir(), "language_packs");
        deleteAll(mPacks);
        deleteAll(mTables);
    }

    @Test
    public void importedFilesAreListedWithTheirCounts() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        LanguagePackManager.ImportResult result = manager.importPack(pack("de", CANCEL, SHOWING, UNKNOWN));
        assertEquals("de", result.code);
        assertEquals("Deutsch", result.name);
        assertFalse(result.replaced);
        assertFalse(result.inUse);

        List<LanguagePackManager.Installed> installed = manager.list();
        assertEquals(1, installed.size());
        LanguagePackManager.Installed de = installed.get(0);
        assertEquals("de", de.code);
        assertEquals(2, de.translated);
        assertEquals(0, de.outdated);
        assertEquals(0, de.needsFix);
        assertTrue(de.total > 100);
        assertFalse(de.inUse);

        assertTrue(manager.importPack(pack("de", CANCEL)).replaced);
        assertEquals(1, manager.list().get(0).translated);
    }

    @Test
    public void codesFollowTheAppsLanguageList() throws Exception {
        assertEquals("zh", LanguagePackManager.canonicalCode("zh-CN"));
        assertEquals("pt-BR", LanguagePackManager.canonicalCode("pt-BR"));
        LanguagePackManager manager = new LanguagePackManager(mContext);
        assertEquals("zh", manager.importPack(pack("zh_cn", CANCEL)).code);
        assertTrue(new File(mPacks, "zh.json").isFile());
    }

    @Test
    public void filesWithNothingUsableAreRefused() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        LanguagePackException none = assertThrows(LanguagePackException.class,
                () -> manager.importPack(pack("de", UNKNOWN)));
        assertEquals(LanguagePackException.Reason.NOTHING_USABLE, none.reason);
        LanguagePackException broken = assertThrows(LanguagePackException.class, () -> manager.importPack(
                pack("de", "\"cancel\": {\"translation\": \"Abbrechen %s\"}")));
        assertEquals(LanguagePackException.Reason.NOTHING_USABLE, broken.reason);
        LanguagePackException english = assertThrows(LanguagePackException.class,
                () -> manager.importPack(pack("en", CANCEL)));
        assertEquals(LanguagePackException.Reason.ENGLISH, english.reason);
        assertTrue(manager.list().isEmpty());
    }

    @Test
    public void tablesAreBuiltOnceUntilTheFileChanges() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", CANCEL, SHOWING));
        File table = manager.prepare("de");
        byte[] built = Files.readAllBytes(table.toPath());
        assertEquals(0x02, built[0]);
        assertEquals(0x00, built[1]);
        assertTrue(new String(built, StandardCharsets.UTF_16LE).contains("Abbrechen"));

        // A cached table is used as is
        byte[] marker = {9, 9, 9};
        try (FileOutputStream out = new FileOutputStream(table)) {
            out.write(marker);
        }
        assertArrayEquals(marker, Files.readAllBytes(manager.prepare("de").toPath()));

        // A changed language file is compiled again
        manager.importPack(pack("de", CANCEL, SHOWING, UNKNOWN));
        byte[] rebuilt = Files.readAllBytes(manager.prepare("de").toPath());
        assertEquals(0x02, rebuilt[0]);
    }

    @Test
    public void removalCanBeUndoneUntilItsFinished() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", CANCEL));
        manager.prepare("de");
        LanguagePackManager.Removal removal = manager.remove("de", "Deutsch");
        assertFalse(removal.wasInUse);
        assertTrue(manager.list().isEmpty());
        assertFalse(new File(mTables, "de.arsc").exists());

        manager.undoRemove(removal);
        assertEquals(1, manager.list().size());

        removal = manager.remove("de", "Deutsch");
        manager.finishRemove(removal);
        assertFalse(new File(mPacks, "de.removed").exists());
        LanguagePackManager.Removal gone = removal;
        assertThrows(IOException.class, () -> manager.undoRemove(gone));
    }

    @Test
    public void leftoversAreClearedOnStart() throws Exception {
        assertTrue(mPacks.mkdirs());
        File removed = new File(mPacks, "de.removed");
        File temp = new File(mPacks, "fr.json.tmp");
        File kept = new File(mPacks, "it.json");
        for (File f : new File[]{removed, temp, kept}) {
            assertTrue(f.createNewFile());
        }
        new LanguagePackManager(mContext);
        assertFalse(removed.exists());
        assertFalse(temp.exists());
        assertTrue(kept.exists());
    }

    @Test
    public void templateHasEveryStringToTranslate() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        JSONObject scratch = new JSONObject(manager.template(null)).getJSONObject("translations");
        assertEquals("Cancel", scratch.getJSONObject("cancel").getString("original"));
        assertEquals("", scratch.getJSONObject("cancel").getString("translation"));
        assertTrue(scratch.length() > 100);

        JSONObject ru = new JSONObject(manager.template("ru"));
        assertEquals("ru", ru.getString("language_code"));
        JSONObject ruTranslations = ru.getJSONObject("translations");
        assertFalse(ruTranslations.getJSONObject("cancel").getString("translation").isEmpty());
        JSONObject forms = ruTranslations.getJSONObject("main_status_showing_apps").getJSONObject("translation");
        assertTrue(forms.has("few"));
        assertTrue(forms.has("many"));
        assertTrue(forms.has("other"));
    }

    @Test
    public void exportKeepsTheTranslationsAndMarksTheRest() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", CANCEL, SHOWING, UNKNOWN));
        String json = manager.forEditing("de");
        JSONObject translations = new JSONObject(json).getJSONObject("translations");
        assertEquals("Abbrechen", translations.getJSONObject("cancel").getString("translation"));
        assertFalse(translations.getJSONObject("cancel").has("status"));
        assertEquals(LanguagePackWriter.STATUS_UNUSED, translations.getJSONObject("nope_not_a_key").getString("status"));
        int missing = 0;
        for (String key : keySet(translations)) {
            if (LanguagePackWriter.STATUS_MISSING.equals(translations.getJSONObject(key).optString("status"))) {
                ++missing;
            }
        }
        assertTrue(missing > 100);

        LanguagePack again = LanguagePackReader.read(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, again.entries.size());
        assertThrows(IllegalArgumentException.class, () -> manager.forEditing("../de"));
    }

    private static byte[] pack(String code, String... entries) {
        return ("{\"schema_version\": 1, \"language_code\": \"" + code + "\", \"language_name\": \"Deutsch\","
                + " \"translations\": {" + String.join(",", entries) + "}}").getBytes(StandardCharsets.UTF_8);
    }

    private static void deleteAll(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static Set<String> keySet(JSONObject o) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }
}
