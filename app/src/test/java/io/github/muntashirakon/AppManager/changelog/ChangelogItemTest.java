// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChangelogItemTest {
    @Test
    public void toHtmlMarkupConvertsSupportedInlineMarkdown() {
        assertEquals("<b>Bold</b> <i>Italic</i> <tt>Mono</tt> <strike>Gone</strike> "
                        + "<a href=\"https://example.com/release\">Link</a>",
                ChangelogItem.toHtmlMarkup("**Bold** __Italic__ `Mono` ~~Gone~~ "
                        + "[Link](https://example.com/release)"));
    }

    @Test
    public void toHtmlMarkupPreservesBracketHtmlAliases() {
        assertEquals("<b>Existing</b> <a href=\"https://example.com\">site</a>",
                ChangelogItem.toHtmlMarkup("[b]Existing[/b] [a href=\"https://example.com\"]site[/a]"));
    }

    @Test
    public void toHtmlMarkupQuotesReplacementTextSafely() {
        assertEquals("<a href=\"https://example.com/$1\">Price $5</a>",
                ChangelogItem.toHtmlMarkup("[Price $5](https://example.com/$1)"));
    }
}
