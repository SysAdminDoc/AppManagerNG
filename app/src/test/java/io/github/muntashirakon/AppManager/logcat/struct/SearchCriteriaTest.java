// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SearchCriteriaTest {
    @Test
    public void messageSearchMatchesLogOutput() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("OtherTag");
        logLine.setLogOutput("payload contains needle");

        assertTrue(new SearchCriteria("needle").matches(logLine));
    }

    @Test
    public void messageSearchRejectsAbsentLogOutput() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("OtherTag");
        logLine.setLogOutput("payload");

        assertFalse(new SearchCriteria("needle").matches(logLine));
    }

    @Test
    public void quotedMultiWordTagFilterMatchesFullValue() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("Foo Bar");
        logLine.setLogOutput("payload");

        // Regression: the closing-quote strip used parts.length (token count) instead of the
        // token's own length, truncating "Foo Bar" to "Foo B" (or crashing on longer queries).
        assertTrue(new SearchCriteria("tag=:\"Foo Bar\"").matches(logLine));
        assertFalse(new SearchCriteria("tag=:\"Foo Baz\"").matches(logLine));
    }

    @Test
    public void regexTagFilterMatchesAsRegexNotLiteral() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("FooBar");
        logLine.setLogOutput("payload");

        // Regression: the regex value was Pattern.quote()'d, so it only matched the literal string.
        assertTrue(new SearchCriteria("tag~:Foo.*").matches(logLine));
        assertFalse(new SearchCriteria("tag~:Baz.*").matches(logLine));
    }

    @Test
    public void invalidRegexTagFilterDegradesToLiteralWithoutCrashing() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("[unclosed");
        logLine.setLogOutput("payload");

        // A syntactically invalid regex must not crash filter parsing; it falls back to a literal.
        assertTrue(new SearchCriteria("tag~:[unclosed").matches(logLine));
    }

    @Test
    public void overflowPidFilterIsEmpty() {
        SearchCriteria searchCriteria = new SearchCriteria("pid:999999999999");

        assertTrue(searchCriteria.isEmpty());
    }

    @Test
    public void overflowUidFilterIsEmpty() {
        SearchCriteria searchCriteria = new SearchCriteria("uid:999999999999");

        assertTrue(searchCriteria.isEmpty());
    }
}
