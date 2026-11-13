// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextQueryTest {
    @Test
    public void anEmptyQueryMatchesEverything() {
        assertTrue(TextQuery.of(null, false).matches("anything"));
        assertTrue(TextQuery.of("", true).matches("anything"));
        assertTrue(TextQuery.empty().matches((CharSequence) null));
    }

    @Test
    public void plainTextMatchesAnywhereIgnoringCase() {
        TextQuery query = TextQuery.of("SaVeD", false);

        assertTrue(query.matches("Photo saved to gallery"));
        assertFalse(query.matches("Photo deleted"));
        assertFalse(query.isRegex());
    }

    @Test
    public void plainTextIsNotARegularExpression() {
        TextQuery query = TextQuery.of("a.c", false);

        assertTrue(query.matches("a.c"));
        assertFalse(query.matches("abc"));
    }

    @Test
    public void anyFieldCanMatchAndMissingFieldsAreSkipped() {
        TextQuery query = TextQuery.of("example", false);

        assertTrue(query.matches(null, "Label", "com.example.app"));
        assertFalse(query.matches(null, "Label", "org.other"));
    }

    @Test
    public void theQueryIsKeptAsTyped() {
        assertEquals("SaVeD", TextQuery.of("SaVeD", false).getQuery());
        assertEquals("SaV(ed)?", TextQuery.of("SaV(ed)?", true).getQuery());
    }

    @Test
    public void aRegularExpressionFindsItsMatchIgnoringCase() {
        TextQuery query = TextQuery.of("\\d+ NEW messages?", true);

        assertTrue(query.isValid());
        assertTrue(query.matches("You have 3 new messages"));
        assertTrue(query.matches("1 new message"));
        assertFalse(query.matches("No new messages"));
    }

    @Test
    public void anAnchoredRegularExpressionStaysAnchored() {
        TextQuery query = TextQuery.of("^saved$", true);

        assertTrue(query.matches("Saved"));
        assertFalse(query.matches("Photo saved"));
    }

    @Test
    public void aBrokenRegularExpressionMatchesNothingAndSaysSo() {
        TextQuery query = TextQuery.of("(unclosed", true);

        assertFalse(query.isValid());
        assertFalse(query.matches("(unclosed"));
        assertFalse(query.matches(""));
        assertFalse(TextQuery.isUsableRegex("(unclosed"));
    }

    @Test
    public void nestedRepeatsThatCanBacktrackForeverAreTurnedAway() {
        assertTrue(TextQuery.couldStall("(a+)+"));
        assertTrue(TextQuery.couldStall("(a*)*b"));
        assertTrue(TextQuery.couldStall("((ab)+)*"));
        assertTrue(TextQuery.couldStall("(x+x+)+y"));
        assertTrue(TextQuery.couldStall("(\\w+\\s?)+$"));
        assertTrue(TextQuery.couldStall("(.*a){2,}"));
        assertTrue(TextQuery.couldStall(".*a.*b.*c.*d"));
        assertTrue(TextQuery.couldStall("(a{2,5})+"));
        assertTrue(TextQuery.couldStall("(?:a+)+"));

        TextQuery query = TextQuery.of("(a+)+", true);
        assertFalse(query.isValid());
        // The classic catastrophic input would hang here if the pattern had been compiled and run.
        assertFalse(query.matches("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!"));
        assertFalse(TextQuery.isUsableRegex("(a+)+"));
    }

    @Test
    public void everydayPatternsAreAllowed() {
        assertFalse(TextQuery.couldStall("\\d+ new messages?"));
        assertFalse(TextQuery.couldStall("(foo|bar)+"));
        assertFalse(TextQuery.couldStall("[a-z]+@[a-z]+\\.com"));
        assertFalse(TextQuery.couldStall("(ab)?c"));
        assertFalse(TextQuery.couldStall("a+?b*?"));
        assertFalse(TextQuery.couldStall("[(+*]+"));
        assertFalse(TextQuery.couldStall("\\(a+\\)+"));
        assertFalse(TextQuery.couldStall("(?i)verification code"));
        assertFalse(TextQuery.couldStall("(?:ab)+c"));
        assertTrue(TextQuery.isUsableRegex("(foo|bar)+"));
    }
}
