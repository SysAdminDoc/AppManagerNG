// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.View;

import androidx.appcompat.widget.PopupMenu;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

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

    @Test
    public void relatedWordsMatchByMeaningAndByText() {
        assertTrue(AdvancedSearchView.matches("internet", "Wi-Fi is off", AdvancedSearchView.SEARCH_TYPE_RELATED));
        assertTrue(AdvancedSearchView.matches("lost", "Connection lost", AdvancedSearchView.SEARCH_TYPE_RELATED));
        assertFalse(AdvancedSearchView.matches("internet", "Battery low", AdvancedSearchView.SEARCH_TYPE_RELATED));
        assertFalse(AdvancedSearchView.matches("internet", "Wi-Fi is off", AdvancedSearchView.SEARCH_TYPE_CONTAINS));
        List<String> found = AdvancedSearchView.matches("internet", Arrays.asList("Wi-Fi is off", "Battery low"),
                (AdvancedSearchView.ChoiceGenerator<String>) s -> s, AdvancedSearchView.SEARCH_TYPE_RELATED);
        assertEquals(Collections.singletonList("Wi-Fi is off"), found);
    }

    @Test
    public void relatedWordsIsOfferedOnlyWhereAScreenAsksForIt() {
        Context context = ApplicationProvider.getApplicationContext();
        PopupMenu popupMenu = new PopupMenu(context, new View(context));
        popupMenu.inflate(R.menu.view_advanced_search_type_selections);
        Menu menu = popupMenu.getMenu();

        // What every other search offers.
        AdvancedSearchView.showEnabledTypes(menu, AdvancedSearchView.SEARCH_TYPE_CONTAINS
                | AdvancedSearchView.SEARCH_TYPE_PREFIX | AdvancedSearchView.SEARCH_TYPE_SUFFIX
                | AdvancedSearchView.SEARCH_TYPE_REGEX);
        assertFalse(menu.findItem(R.id.action_search_type_related).isVisible());
        assertTrue(menu.findItem(R.id.action_search_type_regex).isVisible());

        AdvancedSearchView.showEnabledTypes(menu, AdvancedSearchView.SEARCH_TYPE_CONTAINS
                | AdvancedSearchView.SEARCH_TYPE_REGEX | AdvancedSearchView.SEARCH_TYPE_RELATED);
        assertTrue(menu.findItem(R.id.action_search_type_related).isVisible());
        assertTrue(menu.findItem(R.id.action_search_type_regex).isVisible());
        assertTrue(menu.findItem(R.id.action_search_type_contains).isVisible());
        assertFalse(menu.findItem(R.id.action_search_type_prefix).isVisible());
        assertEquals(AdvancedSearchView.SEARCH_TYPE_RELATED,
                AdvancedSearchView.typeOf(R.id.action_search_type_related));
        assertEquals(AdvancedSearchView.SEARCH_TYPE_REGEX, AdvancedSearchView.typeOf(R.id.action_search_type_regex));
    }

    /** The button that picks Contains, Regex or Related words is a bare icon, so TalkBack needs a name for it. */
    @Test
    public void theSearchTypeButtonHasAName() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        AdvancedSearchView searchView = new AdvancedSearchView(context);

        View button = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);

        assertEquals(context.getString(R.string.search_type), String.valueOf(button.getContentDescription()));
    }
}
