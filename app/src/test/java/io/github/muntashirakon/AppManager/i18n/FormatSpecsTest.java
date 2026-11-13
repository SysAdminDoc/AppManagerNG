// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class FormatSpecsTest {
    @Test
    public void ordinaryPlaceholdersCountUp() {
        assertEquals(specs("1:s", "2:d"), FormatSpecs.parse("%s has %d apps"));
    }

    @Test
    public void explicitIndexesKeepTheirPosition() {
        assertEquals(specs("1:s", "2:d"), FormatSpecs.parse("%2$d apps in %1$s"));
    }

    @Test
    public void conversionsGroupByArgumentKind() {
        assertEquals(specs("1:d", "2:d", "3:f", "4:c", "5:s", "6:s"),
                FormatSpecs.parse("%x %o %.2f %c %b %h"));
        assertEquals(specs("1:t"), FormatSpecs.parse("%tY"));
    }

    @Test
    public void percentAndNewlineTakeNoArgument() {
        assertEquals(new TreeSet<>(), FormatSpecs.parse("100%% done%n"));
        assertEquals(specs("1:d"), FormatSpecs.parse("%d%% done"));
    }

    @Test
    public void relativeIndexReusesThePreviousArgument() {
        assertEquals(specs("1:d"), FormatSpecs.parse("%d and %<d"));
        assertNull(FormatSpecs.parse("%<d first"));
    }

    @Test
    public void flagsWidthAndPrecisionAreAccepted() {
        assertEquals(specs("1:d", "2:f"), FormatSpecs.parse("%-5d %+08.3f"));
    }

    @Test
    public void spaceIsAFlagLikeTheFormatterSays() {
        // "% d" is a valid placeholder to java.util.Formatter, so "100% done" really takes an argument
        assertEquals(specs("1:d"), FormatSpecs.parse("100% done"));
    }

    @Test
    public void invalidPercentSequencesReportNull() {
        assertNull(FormatSpecs.parse("Half done (50%)"));
        assertNull(FormatSpecs.parse("ends with %"));
        assertNull(FormatSpecs.parse("%0$s"));
        assertNull(FormatSpecs.parse("%1234$s"));
    }

    @Test
    public void textWithoutPercentHasNoSpecs() {
        assertTrue(FormatSpecs.parse("Cancel").isEmpty());
    }

    private static Set<String> specs(String... items) {
        return new TreeSet<>(Arrays.asList(items));
    }
}
