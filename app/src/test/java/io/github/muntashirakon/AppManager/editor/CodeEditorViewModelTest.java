// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CodeEditorViewModelTest {
    @Test
    public void getLanguageFromExtMapsCommonAliasesToBundledGrammars() {
        assertEquals("kotlin", CodeEditorViewModel.getLanguageFromExt("kt"));
        assertEquals("kotlin", CodeEditorViewModel.getLanguageFromExt("kts"));
        assertEquals("xml", CodeEditorViewModel.getLanguageFromExt("html"));
        assertEquals("properties", CodeEditorViewModel.getLanguageFromExt("css"));
        assertEquals("properties", CodeEditorViewModel.getLanguageFromExt("toml"));
        assertEquals("properties", CodeEditorViewModel.getLanguageFromExt("ini"));
        assertEquals("sh", CodeEditorViewModel.getLanguageFromExt("bash"));
    }

    @Test
    public void getLanguageFromExtKeepsUnknownExtensionsAndNull() {
        assertEquals("java", CodeEditorViewModel.getLanguageFromExt("java"));
        assertEquals("unknown", CodeEditorViewModel.getLanguageFromExt("unknown"));
        assertNull(CodeEditorViewModel.getLanguageFromExt(null));
    }
}
