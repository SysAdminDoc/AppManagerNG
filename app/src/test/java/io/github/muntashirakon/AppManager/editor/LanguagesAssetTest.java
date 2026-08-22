// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class LanguagesAssetTest {
    @Test
    public void everySupportedLanguageShipsGrammarAndConfiguration() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        for (String language : Languages.getSupportedLanguages()) {
            assertAssetExists(context, "languages/" + language + "/tmLanguage.json");
            assertAssetExists(context, "languages/" + language + "/language-configuration.json");
        }
    }

    private static void assertAssetExists(Context context, String path) throws IOException {
        try (java.io.InputStream inputStream = context.getAssets().open(path)) {
            assertTrue(path, inputStream.available() > 0);
        }
    }
}
