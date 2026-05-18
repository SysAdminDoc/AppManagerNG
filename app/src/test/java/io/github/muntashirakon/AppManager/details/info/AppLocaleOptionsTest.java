// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class AppLocaleOptionsTest {
    @Test
    public void buildOptions_deduplicatesAndKeepsSystemDefaultFirst() {
        List<AppLocaleOptions.Option> options = AppLocaleOptions.buildOptions(new Locale[]{
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("fr-FR"),
                Locale.ROOT
        }, Locale.US, "System default");

        assertEquals(AppLocaleOptions.SYSTEM_DEFAULT_LANGUAGE_TAG, options.get(0).languageTag);
        assertEquals(3, options.size());
        assertTrue(containsTag(options, "en-US"));
        assertTrue(containsTag(options, "fr-FR"));
    }

    @Test
    public void describeLanguageTags_handlesDefaultAndMultipleTags() {
        assertEquals("System default", AppLocaleOptions.describeLanguageTags("", "System default", Locale.US));

        CharSequence description = AppLocaleOptions.describeLanguageTags("en-US,fr-FR",
                "System default", Locale.US);

        assertTrue(description.toString().contains("en-US"));
        assertTrue(description.toString().contains("fr-FR"));
    }

    private static boolean containsTag(List<AppLocaleOptions.Option> options, String languageTag) {
        for (AppLocaleOptions.Option option : options) {
            if (languageTag.equals(option.languageTag)) {
                return true;
            }
        }
        return false;
    }
}
