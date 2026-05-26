// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.rosemoe.sora.text.LineSeparator;

public class CodeEditorLineSeparatorUtilsTest {
    @Test
    public void convert_toLf_handlesMixedSeparators() {
        assertEquals("one\ntwo\nthree\nfour",
                CodeEditorLineSeparatorUtils.convert("one\r\ntwo\rthree\nfour", LineSeparator.LF));
    }

    @Test
    public void convert_toCrLf_doesNotDoubleConvertExistingCrLf() {
        assertEquals("one\r\ntwo\r\nthree\r\nfour",
                CodeEditorLineSeparatorUtils.convert("one\r\ntwo\rthree\nfour", LineSeparator.CRLF));
    }

    @Test
    public void convert_toCr_handlesDanglingCarriageReturn() {
        assertEquals("one\rtwo\r",
                CodeEditorLineSeparatorUtils.convert("one\ntwo\r", LineSeparator.CR));
    }
}
