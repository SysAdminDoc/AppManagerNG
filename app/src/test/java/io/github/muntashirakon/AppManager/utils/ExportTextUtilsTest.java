// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExportTextUtilsTest {
    @Test
    public void escapeCsvFieldDefusesFormulaAfterWhitespaceAndEscapesQuotes() {
        assertEquals("' \t=HYPERLINK(\"\"http://evil/\"\")",
                ExportTextUtils.escapeCsvField(" \t=HYPERLINK(\"http://evil/\")"));
    }

    @Test
    public void defuseCsvFormulaLeavesPlainTextUnchanged() {
        assertEquals("Example App", ExportTextUtils.defuseCsvFormula("Example App"));
    }

    @Test
    public void escapeTsvFieldNormalizesControlTextAndDefusesFormulaAfterWhitespace() {
        assertEquals("'  =cmd payload", ExportTextUtils.escapeTsvField(" \t=cmd\npayload"));
    }

    @Test
    public void toMarkdownTextFlattensAndEscapesControlText() {
        assertEquals("Example \\# Inject &lt;script&gt; \\[link\\]\\(http://evil/\\)",
                ExportTextUtils.toMarkdownText("Example\n# Inject <script> [link](http://evil/)"));
    }

    @Test
    public void toPlainTextReportDefusesEachLineAndNormalizesColumnControls() {
        assertEquals("' =cmd payload\nplain line\n'@WEBSERVICE(\"http://evil/\")",
                ExportTextUtils.toPlainTextReport("\t=cmd\rpayload\nplain\tline\n@WEBSERVICE(\"http://evil/\")"));
    }
}
