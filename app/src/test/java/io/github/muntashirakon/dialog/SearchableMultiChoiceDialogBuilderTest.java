// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.CheckBox;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class SearchableMultiChoiceDialogBuilderTest {
    @Test
    public void itemsLoadedLaterReplaceTheEmptyListAndShowSearchAndSelectAll() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        SearchableMultiChoiceDialogBuilder<String> builder = new SearchableMultiChoiceDialogBuilder<>(
                context, new ArrayList<String>(), new ArrayList<CharSequence>());
        View view = builder.getView();
        RecyclerView list = view.findViewById(android.R.id.list);
        CheckBox selectAll = view.findViewById(android.R.id.checkbox);
        View search = view.findViewById(io.github.muntashirakon.ui.R.id.action_search);
        assertEquals(0, list.getAdapter().getItemCount());
        assertEquals(View.GONE, search.getVisibility());
        assertEquals(View.GONE, selectAll.getVisibility());

        List<String> items = Arrays.asList("a", "b", "c", "d", "e", "f", "g");
        List<CharSequence> names = new ArrayList<>(items);
        builder.setItems(items, names);

        assertEquals(7, list.getAdapter().getItemCount());
        assertEquals(View.VISIBLE, search.getVisibility());
        assertEquals(View.VISIBLE, selectAll.getVisibility());
        // An empty list reads as "all selected"; the loaded list starts with nothing selected
        assertFalse(selectAll.isChecked());
    }

    @Test
    public void selectionsOfTheOldListAreClearedWhenItemsAreReplaced() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        List<String> before = Arrays.asList("a", "b");
        SearchableMultiChoiceDialogBuilder<String> builder = new SearchableMultiChoiceDialogBuilder<>(
                context, before, new ArrayList<CharSequence>(before))
                .addSelections(new int[]{0, 1});
        CheckBox selectAll = builder.getView().findViewById(android.R.id.checkbox);

        List<String> after = Arrays.asList("x", "y", "z");
        builder.setItems(after, new ArrayList<CharSequence>(after));

        assertFalse(selectAll.isChecked());
    }
}
