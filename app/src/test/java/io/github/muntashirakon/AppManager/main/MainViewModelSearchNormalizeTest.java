// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

/**
 * Contract for {@link MainViewModel#normalizeSearchQuery}, which backs the search-debounce
 * coalescing guard (INIT-1): a repeated/debounced callback whose normalized query and type are
 * unchanged must not trigger a redundant filter pass.
 */
public class MainViewModelSearchNormalizeTest {

    @Test
    public void nonRegexQueriesAreLowerCased() {
        assertEquals("github", MainViewModel.normalizeSearchQuery("GitHub", AdvancedSearchView.SEARCH_TYPE_CONTAINS));
        assertEquals("github", MainViewModel.normalizeSearchQuery("GITHUB", AdvancedSearchView.SEARCH_TYPE_PREFIX));
        assertEquals("github", MainViewModel.normalizeSearchQuery("github", AdvancedSearchView.SEARCH_TYPE_SUFFIX));
    }

    @Test
    public void regexQueriesArePreservedVerbatim() {
        assertEquals("Git.*Hub", MainViewModel.normalizeSearchQuery("Git.*Hub", AdvancedSearchView.SEARCH_TYPE_REGEX));
    }

    @Test
    public void differentCasingNormalizesEqualForNonRegex_soFilterIsCoalesced() {
        String a = MainViewModel.normalizeSearchQuery("MapS", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        String b = MainViewModel.normalizeSearchQuery("maps", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        assertEquals(a, b);
    }

    @Test
    public void differentCasingStaysDistinctForRegex_soFilterStillRuns() {
        String a = MainViewModel.normalizeSearchQuery("MapS", AdvancedSearchView.SEARCH_TYPE_REGEX);
        String b = MainViewModel.normalizeSearchQuery("maps", AdvancedSearchView.SEARCH_TYPE_REGEX);
        assertNotEquals(a, b);
    }

    @Test
    public void emptyQueryNormalizesToEmpty() {
        assertEquals("", MainViewModel.normalizeSearchQuery("", AdvancedSearchView.SEARCH_TYPE_CONTAINS));
    }
}
