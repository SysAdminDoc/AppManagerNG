// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.options.FilterOptions;

@RunWith(RobolectricTestRunner.class)
public class FinderViewModelTest {
    @Test
    public void applyFilterItemDetachesPresetFromActiveChain() {
        FinderViewModel viewModel = new FinderViewModel(ApplicationProvider.getApplicationContext());
        FilterItem saved = new FilterItem();
        saved.addFilterOption(FilterOptions.create("app_label"));

        viewModel.applyFilterItem(saved);
        saved.removeFilterOptionAt(0);

        assertEquals(1, viewModel.getFilterItem().getSize());
        assertEquals("app_label_1", viewModel.getFilterItem().getExpr());
    }
}
