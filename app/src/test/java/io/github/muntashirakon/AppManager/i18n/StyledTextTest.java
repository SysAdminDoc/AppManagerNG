// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class StyledTextTest {
    private static final Set<String> NO_LINKS = Collections.emptySet();

    @Test
    public void parsesNestedTagsIntoSpans() throws Exception {
        StyledText st = StyledText.parseMarkup("<b>Bold <i>both</i></b> plain", NO_LINKS);
        assertEquals("Bold both plain", st.text);
        assertEquals(Arrays.asList(
                new StyledText.Span(StyledText.BOLD, 0, 9),
                new StyledText.Span(StyledText.ITALIC, 5, 9)), st.spans);
    }

    @Test
    public void decodesEntitiesAndKeepsStrayCharacters() throws Exception {
        StyledText st = StyledText.parseMarkup("a &lt; b &amp; c &unknown; 3 < 4", NO_LINKS);
        assertEquals("a < b & c &unknown; 3 < 4", st.text);
        assertFalse(st.isStyled());
    }

    @Test
    public void markupRoundTripsThroughParse() throws Exception {
        StyledText original = new StyledText("Read <this> & that", Arrays.asList(
                new StyledText.Span(StyledText.UNDERLINE, 0, 4),
                new StyledText.Span(StyledText.MONOSPACE, 5, 11)));
        String markup = original.toMarkup();
        assertEquals("<u>Read</u> <tt>&lt;this&gt;</tt> &amp; that", markup);
        assertEquals(original, StyledText.parseMarkup(markup, NO_LINKS));
    }

    @Test
    public void overlappingSpansStayNestedInMarkup() throws Exception {
        StyledText original = new StyledText("abcdefgh", Arrays.asList(
                new StyledText.Span(StyledText.BOLD, 0, 5),
                new StyledText.Span(StyledText.ITALIC, 2, 8)));
        String markup = original.toMarkup();
        assertEquals("<b>ab<i>cde</i></b><i>fgh</i>", markup);
        StyledText parsed = StyledText.parseMarkup(markup, NO_LINKS);
        assertEquals("abcdefgh", parsed.text);
    }

    @Test
    public void linksMustUseTheOriginalAddresses() throws Exception {
        Set<String> allowed = Collections.singleton("https://example.org/a?x=1&y=2");
        StyledText st = StyledText.parseMarkup("<a href=\"https://example.org/a?x=1&amp;y=2\">here</a>", allowed);
        assertEquals(Collections.singletonList("https://example.org/a?x=1&y=2"), st.links());
        assertEquals("<a href=\"https://example.org/a?x=1&amp;y=2\">here</a>", st.toMarkup());
        assertThrows(StyledText.MarkupException.class,
                () -> StyledText.parseMarkup("<a href=\"https://evil.example\">here</a>", allowed));
    }

    @Test
    public void rejectsUnsupportedAndUnbalancedTags() {
        assertThrows(StyledText.MarkupException.class, () -> StyledText.parseMarkup("<script>x</script>", NO_LINKS));
        assertThrows(StyledText.MarkupException.class, () -> StyledText.parseMarkup("<b>never closed", NO_LINKS));
        assertThrows(StyledText.MarkupException.class, () -> StyledText.parseMarkup("<b>x</i>", NO_LINKS));
        assertThrows(StyledText.MarkupException.class, () -> StyledText.parseMarkup("<b x", NO_LINKS));
        assertThrows(StyledText.MarkupException.class, () -> StyledText.parseMarkup("<b onclick=\"x\">y</b>", NO_LINKS));
    }

    @Test
    public void emptyTagsAddNoSpan() throws Exception {
        assertFalse(StyledText.parseMarkup("<b></b>text", NO_LINKS).isStyled());
    }

    @Test
    public void unknownSpansCantBeWritten() {
        StyledText st = new StyledText("x", Collections.singletonList(new StyledText.Span("font;color=red", 0, 1)));
        assertNull(st.toMarkup());
        assertTrue(st.isStyled());
    }

    @Test
    public void plainTextMarkupIsTheTextItself() {
        assertEquals("1 < 2 & so on", StyledText.plain("1 < 2 & so on").toMarkup());
    }
}
