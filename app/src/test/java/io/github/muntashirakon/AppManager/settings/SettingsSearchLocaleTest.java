// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.test.CachedAppContext;

/**
 * Settings search kept the text it first read for the life of the process, so after picking a
 * language it showed and matched the old language's titles until the app was restarted. The
 * expected titles below come from the res/values-* translations.
 */
@RunWith(RobolectricTestRunner.class)
public class SettingsSearchLocaleTest {
    private Context mContext;
    private Locale mDefault;

    @Before
    public void setUp() {
        CachedAppContext.forgetWithPrefs();
        mContext = ApplicationProvider.getApplicationContext();
        mDefault = Locale.getDefault();
        SettingsSearchIndex.invalidate();
    }

    @After
    public void tearDown() {
        switchTo(LangUtils.LANG_AUTO);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        Locale.setDefault(mDefault);
        SettingsSearchIndex.invalidate();
        CachedAppContext.forgetWithPrefs();
    }

    @Test
    public void regionVariantsEachSearchTheirOwnText() {
        switchTo("pt");
        assertEquals("Temas", title("app_theme"));

        switchTo("pt-BR");
        assertEquals("Tema do aplicativo", title("app_theme"));
        assertEquals("tema preto puro", title("app_theme_pure_black"));
        assertTrue(keys(search("Tema do aplicativo")).contains("app_theme"));

        // And back again
        switchTo("pt");
        assertEquals("Temas", title("app_theme"));
        assertFalse(keys(search("Tema do aplicativo")).contains("app_theme"));
    }

    @Test
    public void scriptVariantsEachSearchTheirOwnText() {
        switchTo("zh");
        assertEquals("应用主题", title("app_theme"));
        assertTrue(keys(search("纯黑主题")).contains("app_theme_pure_black"));

        switchTo("zh-TW");
        assertEquals("應用程式主題", title("app_theme"));
        assertTrue(keys(search("純黑色主題")).contains("app_theme_pure_black"));
        assertFalse(keys(search("纯黑主题")).contains("app_theme_pure_black"));

        switchTo("zh");
        assertEquals("应用主题", title("app_theme"));
    }

    @Test
    public void quickChangesInARowEndOnTheLastLanguage() {
        switchTo("de");
        assertEquals("Farbschema der Anwendung", title("app_theme"));

        // Several picks with no search in between
        switchTo("zh");
        switchTo("pt-BR");
        switchTo("tr");
        assertEquals("Uygulama teması", title("app_theme"));
        // Every title and summary, not only the ones above, matches a fresh read
        List<String> kept = texts(SettingsSearchIndex.get(mContext));
        SettingsSearchIndex.invalidate();
        assertEquals(texts(SettingsSearchIndex.get(mContext)), kept);

        switchTo("de");
        switchTo(LangUtils.LANG_AUTO);
        switchTo("de");
        assertEquals("Farbschema der Anwendung", title("app_theme"));
        assertTrue(keys(search("Nutze das Internet")).contains("toggle_internet"));
    }

    @Test
    public void theQueryIsLowercasedByTheSearchedLanguagesRules() {
        switchTo("tr");
        // The JVM's default locale is process-wide and needn't be the app's language. Turkish
        // lowercases "İ" to "i", the default rules to "i" plus a combining dot.
        Locale.setDefault(Locale.ENGLISH);
        assertEquals("İnterneti kullan", title("toggle_internet"));
        assertTrue(keys(search("internet")).contains("toggle_internet"));
    }

    @Test
    public void textReadAcrossAConfigurationChangeIsReadAgain() {
        switchTo("de");
        SettingsSearchIndex first = SettingsSearchIndex.get(mContext);
        assertSame(first, SettingsSearchIndex.get(mContext));

        // Android puts the resources back in the system language before the app reapplies its own,
        // so the same language tags can sit over text read in two languages
        ((Application) mContext).onConfigurationChanged(mContext.getResources().getConfiguration());

        SettingsSearchIndex second = SettingsSearchIndex.get(mContext);
        assertNotSame(first, second);
        assertSame(second, SettingsSearchIndex.get(mContext));
        assertEquals("Farbschema der Anwendung", title("app_theme"));
    }

    /** What picking a language does, and then the app's text follows it as the next screen opens. */
    private void switchTo(@NonNull String language) {
        Prefs.Appearance.setLanguage(language);
        AppearanceUtils.applyOnlyLocale(mContext);
    }

    @NonNull
    private String title(@NonNull String key) {
        for (SettingsSearchIndex.Entry entry : SettingsSearchIndex.get(mContext).all()) {
            if (key.equals(entry.targetKey)) {
                return entry.title.toString();
            }
        }
        fail("No search entry for " + key);
        return "";
    }

    @NonNull
    private List<SettingsSearchIndex.Entry> search(@NonNull String query) {
        return SettingsSearchIndex.get(mContext).search(query);
    }

    @NonNull
    private static List<String> keys(@NonNull List<SettingsSearchIndex.Entry> entries) {
        List<String> keys = new ArrayList<>();
        for (SettingsSearchIndex.Entry entry : entries) {
            keys.add(entry.targetKey);
        }
        return keys;
    }

    @NonNull
    private static List<String> texts(@NonNull SettingsSearchIndex index) {
        List<String> texts = new ArrayList<>();
        for (SettingsSearchIndex.Entry entry : index.all()) {
            texts.add(entry.title + " | " + entry.summary + " | " + entry.parentLabel);
        }
        return texts;
    }
}
