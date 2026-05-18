// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.baksmali.BaksmaliOptions;

import org.junit.Test;

import io.github.muntashirakon.AppManager.BuildConfig;

public class SmaliDecodeOptionsTest {
    @Test
    public void normalizeCommentLevel_fallsBackToBasic() {
        assertEquals(SmaliDecodeOptions.COMMENT_LEVEL_BASIC,
                SmaliDecodeOptions.normalizeCommentLevel("bad"));
    }

    @Test
    public void applyTo_mapsCommentLevelsToBaksmaliOptions() {
        BaksmaliOptions none = new BaksmaliOptions();
        new SmaliDecodeOptions(SmaliDecodeOptions.COMMENT_LEVEL_NONE, false).applyTo(none);
        assertFalse(none.debugInfo);
        assertFalse(none.codeOffsets);
        assertFalse(none.accessorComments);

        BaksmaliOptions basic = new BaksmaliOptions();
        new SmaliDecodeOptions(SmaliDecodeOptions.COMMENT_LEVEL_BASIC, false).applyTo(basic);
        assertEquals(BuildConfig.DEBUG, basic.debugInfo);
        assertFalse(basic.codeOffsets);
        assertFalse(basic.accessorComments);

        BaksmaliOptions verbose = new BaksmaliOptions();
        new SmaliDecodeOptions(SmaliDecodeOptions.COMMENT_LEVEL_VERBOSE, false).applyTo(verbose);
        assertTrue(verbose.debugInfo);
        assertTrue(verbose.codeOffsets);
        assertTrue(verbose.accessorComments);
    }

    @Test
    public void stripCommonAnnotations_removesConfiguredAnnotationBlocksOnly() {
        String smali = ".class public Lexample/Test;\n"
                + ".annotation runtime Landroidx/annotation/Nullable;\n"
                + ".end annotation\n"
                + ".annotation runtime Ldalvik/annotation/MemberClasses;\n"
                + "    value = {}\n"
                + ".end annotation\n"
                + ".method public run()V\n"
                + "    .annotation build Lorg/jetbrains/annotations/NotNull;\n"
                + "    .end annotation\n"
                + "    return-void\n"
                + ".end method\n";

        String stripped = SmaliDecodeOptions.stripCommonAnnotations(smali);

        assertFalse(stripped.contains("Landroidx/annotation/Nullable;"));
        assertFalse(stripped.contains("Lorg/jetbrains/annotations/NotNull;"));
        assertTrue(stripped.contains("Ldalvik/annotation/MemberClasses;"));
        assertTrue(stripped.contains("return-void"));
        assertFalse(stripped.endsWith("\n\n"));
    }
}
