// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CodeEditorDiffTest {
    @Test
    public void computeTreatsEmptyOriginalAsOnlyAddedContent() {
        CodeEditorDiff.Result result = CodeEditorDiff.compute("", "first line");

        assertFalse(result.isTooLarge());
        assertFalse(result.isNoChanges());
        assertEquals(1, result.added);
        assertEquals(0, result.removed);
        assertEquals(1, result.displayLines.size());
        assertEquals(CodeEditorDiff.Kind.ADDED, result.displayLines.get(0).kind);
        assertEquals("first line", result.displayLines.get(0).text);
    }

    @Test
    public void computeHandlesCrLfLfAndCrLineEndings() {
        CodeEditorDiff.Result result = CodeEditorDiff.compute("a\r\nb\rc\n", "a\nB\nc\n");

        assertEquals(1, result.added);
        assertEquals(1, result.removed);
        assertEquals(CodeEditorDiff.Kind.REMOVED, result.displayLines.get(0).kind);
        assertEquals("b", result.displayLines.get(0).text);
        assertEquals(CodeEditorDiff.Kind.ADDED, result.displayLines.get(1).kind);
        assertEquals("B", result.displayLines.get(1).text);
    }

    @Test
    public void computeRejectsFilesBeyondLineLimit() {
        CodeEditorDiff.Result result = CodeEditorDiff.compute("a\nb", "a\nB",
                3, CodeEditorDiff.DEFAULT_MAX_DISPLAY_LINES);

        assertTrue(result.isTooLarge());
        assertEquals(0, result.added);
        assertEquals(0, result.removed);
        assertTrue(result.displayLines.isEmpty());
    }

    @Test
    public void computeCapsDisplayedChangesButKeepsAccurateCounts() {
        CodeEditorDiff.Result result = CodeEditorDiff.compute(
                "a\nb\nc\nd",
                "A\nB\nC\nD",
                CodeEditorDiff.DEFAULT_MAX_TOTAL_LINES,
                3);

        assertEquals(4, result.added);
        assertEquals(4, result.removed);
        assertEquals(3, result.displayLines.size());
        assertEquals(5, result.omitted);
    }

    @Test
    public void computeUsesMyersToKeepFarMoveDiffMinimal() {
        CodeEditorDiff.Result result = CodeEditorDiff.compute(
                "start\none\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\nanchor",
                "start\nanchor\none\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten");

        assertEquals(1, result.added);
        assertEquals(1, result.removed);
        assertEquals(CodeEditorDiff.Kind.ADDED, result.displayLines.get(0).kind);
        assertEquals("anchor", result.displayLines.get(0).text);
        assertEquals(CodeEditorDiff.Kind.REMOVED, result.displayLines.get(1).kind);
        assertEquals("anchor", result.displayLines.get(1).text);
    }
}
