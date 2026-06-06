// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class MainViewModelSelectedUsersTest {
    @Before
    public void setUp() {
        Prefs.MainPage.setFilteredUsers(null);
        Prefs.Misc.setSelectedUsers(null);
    }

    @After
    public void tearDown() {
        Prefs.MainPage.setFilteredUsers(null);
        Prefs.Misc.setSelectedUsers(null);
    }

    @Test
    public void constructorLoadsPersistedMainPageUserFilter() {
        Prefs.MainPage.setFilteredUsers(new int[]{10, 0});
        TestMainViewModel viewModel = newViewModel();

        try {
            assertArrayEquals(new int[]{10, 0}, viewModel.getSelectedUsers());
        } finally {
            viewModel.close();
        }
    }

    @Test
    public void setSelectedUsersPersistsMainPageFilterOnly() {
        Prefs.Misc.setSelectedUsers(new int[]{0});
        TestMainViewModel viewModel = newViewModel();

        try {
            viewModel.setSelectedUsers(new int[]{10});

            assertArrayEquals(new int[]{10}, Prefs.MainPage.getFilteredUsers());
            assertArrayEquals(new int[]{0}, Prefs.Misc.getSelectedUsers());
        } finally {
            viewModel.close();
        }
    }

    @Test
    public void emptySelectionRoundTripsAsActiveMainPageFilter() {
        Prefs.MainPage.setFilteredUsers(new int[0]);

        assertArrayEquals(new int[0], Prefs.MainPage.getFilteredUsers());
    }

    @Test
    public void clearFiltersClearsPersistedMainPageUserFilter() {
        Prefs.MainPage.setFilteredUsers(new int[]{10});
        TestMainViewModel viewModel = newViewModel();

        try {
            viewModel.clearFilters();

            assertNull(Prefs.MainPage.getFilteredUsers());
            assertNull(viewModel.getSelectedUsers());
        } finally {
            viewModel.close();
        }
    }

    private static TestMainViewModel newViewModel() {
        Application application = ApplicationProvider.getApplicationContext();
        return new TestMainViewModel(application);
    }

    private static final class TestMainViewModel extends MainViewModel {
        private TestMainViewModel(Application application) {
            super(application);
        }

        private void close() {
            onCleared();
        }
    }
}
