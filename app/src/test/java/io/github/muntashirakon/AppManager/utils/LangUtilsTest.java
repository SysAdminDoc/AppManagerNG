// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LangUtilsTest {
    @Test
    public void canonicalizeLanguageTagMigratesFormerRegionOnlyLocales() {
        assertEquals("es", LangUtils.canonicalizeLanguageTag("es-ES"));
        assertEquals("nb", LangUtils.canonicalizeLanguageTag("nb-NO"));
        assertEquals("ru", LangUtils.canonicalizeLanguageTag("ru-RU"));
        assertEquals("tr", LangUtils.canonicalizeLanguageTag("tr-TR"));
        assertEquals("uk", LangUtils.canonicalizeLanguageTag("uk-UA"));
        assertEquals("zh", LangUtils.canonicalizeLanguageTag("zh-CN"));
        assertEquals("zh-TW", LangUtils.canonicalizeLanguageTag("zh-TW"));
        assertEquals(LangUtils.LANG_AUTO, LangUtils.canonicalizeLanguageTag(LangUtils.LANG_AUTO));
    }
}
