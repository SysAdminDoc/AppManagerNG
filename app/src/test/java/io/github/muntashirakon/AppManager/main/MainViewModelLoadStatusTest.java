// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
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

    /**
     * The list surface must never be silently blank while the cache is being rebuilt: LOADING has
     * to be published before the work starts, not alongside the results it produces.
     */
    @Test
    public void loadingIsAnnouncedBeforeAnyResultsArrive() throws Exception {
        TestMainViewModel viewModel = newViewModel();
        List<MainViewModel.AppListLoadState> states = new ArrayList<>();
        List<String> events = new ArrayList<>();
        Observer<MainViewModel.AppListLoadStatus> statusObserver = status -> {
            states.add(status.state);
            events.add("status:" + status.state);
        };
        Observer<List<ApplicationItem>> listObserver = items -> events.add("items:" + items.size());
        viewModel.setNextItems(Collections.singletonList(app("com.example.first")));

        try {
            viewModel.getApplicationListLoadStatus().observeForever(statusObserver);
            viewModel.getApplicationItems().observeForever(listObserver);
            waitForCurrentTask(viewModel);

            assertEquals(MainViewModel.AppListLoadState.LOADING, states.get(0));
            assertEquals("status:" + MainViewModel.AppListLoadState.LOADING, events.get(0));
            assertEquals(MainViewModel.AppListLoadState.LOADED, last(states));

            // A refresh re-announces LOADING before it publishes anything new.
            int before = events.size();
            viewModel.setNextItems(Collections.singletonList(app("com.example.second")));
            viewModel.loadApplicationItems();
            waitForCurrentTask(viewModel);
            assertEquals("status:" + MainViewModel.AppListLoadState.LOADING, events.get(before));
        } finally {
            viewModel.getApplicationListLoadStatus().removeObserver(statusObserver);
            viewModel.getApplicationItems().removeObserver(listObserver);
            viewModel.close();
        }
    }

    /** While the list is rebuilding, batch actions have to say why rather than quietly do nothing. */
    @Test
    public void batchActionsAreGatedWithAReasonWhileTheListIsRebuilding() {
        assertTrue(MainViewModel.AppListLoadStatus.loading(0).blocksBatchOperations());
        assertFalse("a stale list is still a usable list to act on",
                MainViewModel.AppListLoadStatus.loading(12).blocksBatchOperations());
        assertFalse(MainViewModel.AppListLoadStatus.loaded(12).blocksBatchOperations());
        assertFalse(MainViewModel.AppListLoadStatus.loaded(0).blocksBatchOperations());
    }

    @Test
    public void privilegedEnumerationShortfallIsPublishedAndClearedByTheNextHealthyScan()
            throws Exception {
        TestMainViewModel viewModel = newViewModel();
        List<MainViewModel.AppListLoadStatus> statuses = new ArrayList<>();
        Observer<MainViewModel.AppListLoadStatus> statusObserver = statuses::add;
        Observer<List<ApplicationItem>> listObserver = items -> {
        };
        viewModel.setNextItems(Arrays.asList(
                app("com.example.one"), app("com.example.two"), app("com.example.three")));
        viewModel.setNextEnumerationWarning(
                new PackageManagerCompat.PackageEnumerationWarning(1, 3));

        try {
            viewModel.getApplicationListLoadStatus().observeForever(statusObserver);
            viewModel.getApplicationItems().observeForever(listObserver);
            waitForCurrentTask(viewModel);

            MainViewModel.AppListLoadStatus warningStatus = last(statuses);
            assertTrue(warningStatus.hasEnumerationWarning());
            assertNotNull(warningStatus.privilegedMode);
            assertEquals(1, warningStatus.privilegedPackageCount);
            assertEquals(3, warningStatus.unprivilegedPackageCount);

            viewModel.setNextEnumerationWarning(null);
            viewModel.loadApplicationItems();
            waitForCurrentTask(viewModel);

            assertFalse(last(statuses).hasEnumerationWarning());
            assertEquals(MainViewModel.AppListLoadState.LOADED, last(statuses).state);
        } finally {
            viewModel.getApplicationListLoadStatus().removeObserver(statusObserver);
            viewModel.getApplicationItems().removeObserver(listObserver);
            viewModel.close();
        }
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
        @Nullable
        private PackageManagerCompat.PackageEnumerationWarning nextEnumerationWarning;
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

        private void setNextEnumerationWarning(
                @Nullable PackageManagerCompat.PackageEnumerationWarning warning) {
            nextEnumerationWarning = warning;
        }

        @NonNull
        @Override
        protected List<ApplicationItem> loadInstalledOrBackedUpApplications(
                long loadGeneration) {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            onPackageEnumerationComplete(loadGeneration, nextEnumerationWarning);
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
