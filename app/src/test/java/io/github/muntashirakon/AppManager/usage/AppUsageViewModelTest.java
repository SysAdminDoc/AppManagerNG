// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AppUsageViewModelTest {
    @Test
    public void getPackageUsageEntriesReturnsStableSnapshot() {
        AppUsageViewModel viewModel = new AppUsageViewModel(ApplicationProvider.getApplicationContext());
        PackageUsageInfo.Entry entry = new PackageUsageInfo.Entry(10L, 20L);

        viewModel.replaceUsageDataForTesting(Collections.emptyList(), Collections.singletonList(entry));

        List<PackageUsageInfo.Entry> snapshot = viewModel.getPackageUsageEntries();
        viewModel.replaceUsageDataForTesting(Collections.emptyList(), Collections.emptyList());

        assertEquals(1, snapshot.size());
        assertEquals(entry, snapshot.get(0));
        assertEquals(0, viewModel.getPackageUsageEntries().size());
    }

    @Test
    public void getPackageUsageEntriesReturnsFreshListEachTime() {
        AppUsageViewModel viewModel = new AppUsageViewModel(ApplicationProvider.getApplicationContext());

        viewModel.replaceUsageDataForTesting(Collections.emptyList(),
                Collections.singletonList(new PackageUsageInfo.Entry(10L, 20L)));

        assertNotSame(viewModel.getPackageUsageEntries(), viewModel.getPackageUsageEntries());
    }
}
