// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

public class CodeEditorDexCaveatTest {
    @Test
    public void javaDecompileRequiresAndroidO() {
        assertFalse(CodeEditorViewModel.isJavaDecompileSupported(Build.VERSION_CODES.N_MR1));
        assertTrue(CodeEditorViewModel.isJavaDecompileSupported(Build.VERSION_CODES.O));
    }

    @Test
    public void unsupportedCaveatRequiresJavaGenerationAndPreODevice() {
        assertTrue(CodeEditorViewModel.shouldShowJavaDecompileUnsupportedCaveat(true, Build.VERSION_CODES.N_MR1));
        assertFalse(CodeEditorViewModel.shouldShowJavaDecompileUnsupportedCaveat(false, Build.VERSION_CODES.N_MR1));
        assertFalse(CodeEditorViewModel.shouldShowJavaDecompileUnsupportedCaveat(true, Build.VERSION_CODES.O));
    }
}
