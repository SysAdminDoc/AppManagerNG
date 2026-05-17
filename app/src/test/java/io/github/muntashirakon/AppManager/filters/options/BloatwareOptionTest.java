// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Pattern;

public class BloatwareOptionTest {
    private static final String DESCRIPTION = "Support for NFC tags interactions.\n"
            + "NFC Tags are used in buses to validate a transport card with your phone.";

    @Test
    public void descriptionContainsIsCaseInsensitive() {
        assertTrue(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_contains", "transport card", null));
        assertTrue(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_contains", "nfc tags", null));
        assertFalse(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_contains", "fax", null));
    }

    @Test
    public void descriptionPrefixSuffixAreCaseInsensitive() {
        assertTrue(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_starts_with", "support for nfc", null));
        assertTrue(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_ends_with", "your phone.", null));
    }

    @Test
    public void descriptionRegexUsesUserPattern() {
        assertTrue(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_regex", null, Pattern.compile("(?s).*transport card.*")));
        assertFalse(BloatwareOption.matchesDescription(DESCRIPTION,
                "description_regex", null, Pattern.compile(".*transport card.*")));
    }
}
