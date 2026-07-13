// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AdvancedSearchViewTest {
    @Test
    public void regexOverloadDoesNotThrowOnMalformedPattern() {
        // An unbalanced bracket is an invalid regex; the single-string overload used to
        // call String.matches() with no guard and would crash the caller.
        assertFalse(AdvancedSearchView.matches("[", "abc", AdvancedSearchView.SEARCH_TYPE_REGEX));
    }

    @Test
    public void regexOverloadUsesSubstringSemantics() {
        // find() semantics: a partial match anywhere in the text counts, matching the
        // collection overloads (previously this overload required a full match).
        assertTrue(AdvancedSearchView.matches("bc", "abcd", AdvancedSearchView.SEARCH_TYPE_REGEX));
        assertFalse(AdvancedSearchView.matches("zz", "abcd", AdvancedSearchView.SEARCH_TYPE_REGEX));
    }

    @Test
    public void regexOverloadsAgreeAcrossStringAndCollection() {
        String query = "bc";
        String text = "abcd";
        boolean single = AdvancedSearchView.matches(query, text, AdvancedSearchView.SEARCH_TYPE_REGEX);
        List<String> collection = AdvancedSearchView.matches(query, Collections.singletonList(text),
                (AdvancedSearchView.ChoiceGenerator<String>) s -> s, AdvancedSearchView.SEARCH_TYPE_REGEX);
        assertEquals("string and collection regex overloads must agree",
                single, !collection.isEmpty());
    }
}
