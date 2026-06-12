// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class MainViewModelLoadStatusTest {
    @Before
    public void setUp() {
        resetMainFilters();
    }

    @After
    public void tearDown() {
        resetMainFilters();
    }

    @Test
    public void failedReloadKeepsLastGoodListAndPublishesRecoverableState() throws Exception {
        TestMainViewModel viewModel = newViewModel();
        List<MainViewModel.AppListLoadStatus> statuses = new ArrayList<>();
        List<List<ApplicationItem>> observedLists = new ArrayList<>();
        Observer<MainViewModel.AppListLoadStatus> statusObserver = statuses::add;
        Observer<List<ApplicationItem>> listObserver = items -> observedLists.add(new ArrayList<>(items));
        viewModel.setNextItems(Collections.singletonList(app("com.example.first")));

        try {
            viewModel.getApplicationListLoadStatus().observeForever(statusObserver);
            viewModel.getApplicationItems().observeForever(listObserver);
            waitForCurrentTask(viewModel);

            assertEquals(MainViewModel.AppListLoadState.LOADED, last(statuses).state);
            assertEquals("com.example.first", last(observedLists).get(0).packageName);

            viewModel.failNextLoad(new IllegalStateException("package scan exploded"));
            viewModel.loadApplicationItems();
            waitForCurrentTask(viewModel);

            MainViewModel.AppListLoadStatus failedStatus = last(statuses);
            assertEquals(MainViewModel.AppListLoadState.FAILED, failedStatus.state);
            assertTrue(failedStatus.hasStaleItems());
            assertEquals(1, failedStatus.staleItemCount);
            assertEquals("IllegalStateException", failedStatus.errorClass);
            assertEquals("package scan exploded", failedStatus.errorMessage);
            assertFalse(observedLists.isEmpty());
            assertEquals("com.example.first", last(observedLists).get(0).packageName);
        } finally {
            viewModel.getApplicationListLoadStatus().removeObserver(statusObserver);
            viewModel.getApplicationItems().removeObserver(listObserver);
            viewModel.close();
        }
    }

    private static TestMainViewModel newViewModel() {
        Application application = ApplicationProvider.getApplicationContext();
        return new TestMainViewModel(application);
    }

    private static ApplicationItem app(@NonNull String packageName) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.label = packageName;
        item.isInstalled = true;
        item.userIds = new int[]{0};
        return item;
    }

    private static void waitForCurrentTask(@NonNull TestMainViewModel viewModel) throws Exception {
        Future<?> future = viewModel.getActiveTaskForTesting();
        if (future != null) {
            future.get(5, TimeUnit.SECONDS);
        }
        ShadowLooper.idleMainLooper();
    }

    @NonNull
    private static <T> T last(@NonNull List<T> items) {
        return items.get(items.size() - 1);
    }

    private static void resetMainFilters() {
        Prefs.MainPage.setFilters(MainListOptions.FILTER_NO_FILTER);
        Prefs.MainPage.setFilteredProfileName(null);
        Prefs.MainPage.setFilteredProfileInverse(false);
        Prefs.MainPage.setFilteredUsers(null);
        Prefs.MainPage.setInstallDateStartMillis(0L);
        Prefs.MainPage.setInstallDateEndMillis(0L);
    }

    private static final class TestMainViewModel extends MainViewModel {
        @NonNull
        private List<ApplicationItem> nextItems = Collections.emptyList();
        private RuntimeException nextFailure;

        private TestMainViewModel(@NonNull Application application) {
            super(application);
        }

        private void setNextItems(@NonNull List<ApplicationItem> nextItems) {
            this.nextItems = nextItems;
            nextFailure = null;
        }

        private void failNextLoad(@NonNull RuntimeException failure) {
            nextFailure = failure;
        }

        @NonNull
        @Override
        protected List<ApplicationItem> loadInstalledOrBackedUpApplications() {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            return new ArrayList<>(nextItems);
        }

        @Override
        protected void publishDynamicShortcuts(@NonNull List<ApplicationItem> updatedApplicationItems) {
            // No-op for the injected package-enumeration contract.
        }

        private void close() {
            onCleared();
        }
    }
}
