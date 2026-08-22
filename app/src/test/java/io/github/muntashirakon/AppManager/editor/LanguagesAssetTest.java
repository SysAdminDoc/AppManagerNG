// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.eclipse.tm4e.core.registry.IThemeSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;

@RunWith(RobolectricTestRunner.class)
public class LanguagesAssetTest {
    @Test
    public void everySupportedLanguageShipsGrammarAndConfiguration() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        try (InputStream themeStream = context.getAssets().open("editor_themes/light.tmTheme")) {
            IThemeSource themeSource = IThemeSource.fromInputStream(themeStream, "light.tmTheme", null);
            for (String language : Languages.getSupportedLanguages()) {
                assertAssetExists(context, "languages/" + language + "/tmLanguage.json");
                assertAssetExists(context, "languages/" + language + "/language-configuration.json");
                Language loaded = Languages.getLanguage(context, language, themeSource);
                assertTrue(language, loaded instanceof TextMateLanguage);
            }
        }
    }

    private static void assertAssetExists(Context context, String path) throws IOException {
        try (java.io.InputStream inputStream = context.getAssets().open(path)) {
            assertTrue(path, inputStream.available() > 0);
        }
    }
}
