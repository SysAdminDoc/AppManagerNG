// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

@RunWith(RobolectricTestRunner.class)
public class DebloaterViewModelTest {
    private static final int UNSAFE = DebloaterListOptions.FILTER_REMOVAL_UNSAFE;
    private static final int AOSP = DebloaterListOptions.FILTER_LIST_AOSP;
    private static final int OEM = DebloaterListOptions.FILTER_LIST_OEM;
    private static final int GOOGLE = DebloaterListOptions.FILTER_LIST_GOOGLE;

    @Test
    public void selectByPackageNamesIgnoresMalformedEntries() {
        DebloaterViewModel viewModel = new DebloaterViewModel(app(),
                new FakeSource(debloatObject("com.example.keep", "aosp"),
                        debloatObject("com.example.match", "aosp")),
                Runnable::run);
        viewModel.loadPackages();

        DebloatPresetIO.DebloatPresetEntry malformed = new DebloatPresetIO.DebloatPresetEntry();
        DebloatPresetIO.DebloatPresetEntry match = new DebloatPresetIO.DebloatPresetEntry();
        match.packageName = "com.example.match";
        DebloatPresetIO.DebloatPresetEntry missing = new DebloatPresetIO.DebloatPresetEntry();
        missing.packageName = "com.example.missing";

        int matched = viewModel.selectByPackageNames(Arrays.asList(malformed, match, missing));

        assertEquals(1, matched);
        assertEquals(1, viewModel.getSelectedItemCount());
        assertTrue(viewModel.getSelectedPackages().containsKey("com.example.match"));
    }

    @Test
    public void onlyTheNewestLoadIsShownWhenOlderOnesRunLater() {
        QueueExecutor executor = new QueueExecutor();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), threeLists(), executor);

        viewModel.clearFilters();
        viewModel.addFilterFlag(UNSAFE);
        viewModel.addFilterFlag(AOSP);
        viewModel.removeFilterFlag(AOSP);
        viewModel.addFilterFlag(OEM);
        viewModel.addFilterFlag(GOOGLE);
        viewModel.setQuery("oem", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        executor.runNewestFirst();
        idleMainLooper();

        assertEquals(Collections.singletonList("com.example.oem"), packages(viewModel));
    }

    @Test
    public void aLoadOvertakenWhileItRunsDoesNotReplaceTheNewerList() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        viewModel.clearFilters();
        // While the first load reads the list, the search changes and the newer load runs
        // to the end before the first one gets to post.
        source.onLoad = () -> {
            viewModel.setQuery("google", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            executor.runNewest();
        };

        executor.runOldest();
        idleMainLooper();

        assertEquals(Collections.singletonList("com.example.google"), packages(viewModel));
    }

    @Test
    public void aLoadOvertakenWhileItRunsPostsNothing() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        viewModel.clearFilters();
        source.onLoad = () -> viewModel.setQuery("google", AdvancedSearchView.SEARCH_TYPE_CONTAINS);

        executor.runOldest();
        idleMainLooper();

        assertNull(viewModel.getDebloatObjectListLiveData().getValue());
        executor.runAll();
        idleMainLooper();
        assertEquals(Collections.singletonList("com.example.google"), packages(viewModel));
    }

    @Test
    public void aReloadAskedForByAReplacedLoadStillHappens() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        viewModel.clearFilters();
        executor.runAll();
        assertEquals(1, source.loads.get());

        // A batch op installed something; the reload it asks for is replaced by a search
        // before it runs.
        source.objects.add(debloatObject("com.example.added", "misc"));
        viewModel.loadPackages(true);
        viewModel.setQuery("", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        executor.runAll();
        idleMainLooper();

        assertEquals(2, source.loads.get());
        assertEquals(4, viewModel.getTotalItemCount());
        assertTrue(packages(viewModel).contains("com.example.added"));
    }

    @Test
    public void aReloadAskedForWhileALoadReadsTheListReadsItAgain() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        // A batch op installs something just after the first load has read the list.
        source.afterRead = () -> {
            source.objects.add(debloatObject("com.example.added", "misc"));
            viewModel.loadPackages(true);
        };
        viewModel.clearFilters();

        executor.runAll();
        idleMainLooper();

        assertEquals(2, source.loads.get());
        assertTrue(packages(viewModel).contains("com.example.added"));
    }

    @Test
    public void aPlainFilterChangeDoesNotReadTheListAgain() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        viewModel.clearFilters();
        executor.runAll();
        viewModel.addFilterFlag(UNSAFE);
        executor.runAll();
        viewModel.addFilterFlag(OEM);
        executor.runAll();
        idleMainLooper();

        assertEquals(1, source.loads.get());
        assertEquals(Collections.singletonList("com.example.oem"), packages(viewModel));
    }

    @Test
    public void rapidChangesOnTheRealExecutorEndOnTheLatestStateWithOneLoadAtATime() throws Exception {
        Executor executor = DebloaterViewModel.newLoadExecutor();
        FakeSource source = threeLists();
        source.pauseMillis = 1;
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        Random random = new Random(20261113);
        int[] flags = {UNSAFE, AOSP, OEM, GOOGLE};
        String[] queries = {"", "com", "oem", "google", "aosp"};
        for (int i = 0; i < 200; ++i) {
            switch (random.nextInt(4)) {
                case 0:
                    viewModel.addFilterFlag(flags[random.nextInt(flags.length)]);
                    break;
                case 1:
                    viewModel.removeFilterFlag(flags[random.nextInt(flags.length)]);
                    break;
                case 2:
                    viewModel.setQuery(queries[random.nextInt(queries.length)],
                            AdvancedSearchView.SEARCH_TYPE_CONTAINS);
                    break;
                default:
                    viewModel.loadPackages(true);
                    break;
            }
        }
        viewModel.clearFilters();
        viewModel.addFilterFlag(UNSAFE);
        viewModel.addFilterFlag(AOSP);
        viewModel.setQuery("aosp", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        CountDownLatch drained = new CountDownLatch(1);
        executor.execute(drained::countDown);
        assertTrue(drained.await(30, TimeUnit.SECONDS));
        idleMainLooper();

        assertEquals(Collections.singletonList("com.example.aosp"), packages(viewModel));
        assertEquals(1, source.mostAtOnce.get());
    }

    @Test
    public void loadsFinishingInAnyOrderOnlyShowTheNewestStateReadInOnePass() {
        QueueExecutor executor = new QueueExecutor();
        FakeSource source = threeLists();
        source.freshRows = true;
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, executor);
        // Gets the same calls and runs each one on the spot, so it always shows what the
        // newest state should look like.
        DebloaterViewModel expected = new DebloaterViewModel(app(), threeLists(), Runnable::run);
        List<List<DebloatObject>> shown = new ArrayList<>();
        viewModel.getDebloatObjectListLiveData().observeForever(shown::add);
        Random random = new Random(20261113);
        int[] flags = {UNSAFE, AOSP, OEM, GOOGLE};
        String[] queries = {"", "com", "oem", "google", "aosp"};
        int readsBeforeNewestReload = 0;
        int newestRuns = 0;
        int replacedRuns = 0;
        for (int round = 0; round < 500; ++round) {
            for (int changes = 1 + random.nextInt(4); changes > 0; --changes) {
                int flag = flags[random.nextInt(flags.length)];
                switch (random.nextInt(4)) {
                    case 0:
                        viewModel.addFilterFlag(flag);
                        expected.addFilterFlag(flag);
                        break;
                    case 1:
                        viewModel.removeFilterFlag(flag);
                        expected.removeFilterFlag(flag);
                        break;
                    case 2:
                        String query = queries[random.nextInt(queries.length)];
                        viewModel.setQuery(query, AdvancedSearchView.SEARCH_TYPE_CONTAINS);
                        expected.setQuery(query, AdvancedSearchView.SEARCH_TYPE_CONTAINS);
                        break;
                    default:
                        readsBeforeNewestReload = source.loads.get();
                        viewModel.loadPackages(true);
                        expected.loadPackages(true);
                        break;
                }
            }
            // Any waiting load may finish next, not just the oldest, and some are still
            // waiting when the next round of changes comes in.
            while (executor.size() > 0 && random.nextInt(4) != 0) {
                int shownBefore = shown.size();
                boolean newest = executor.runAt(random.nextInt(executor.size())) == executor.queued();
                idleMainLooper();
                if (!newest) {
                    ++replacedRuns;
                    assertEquals("a replaced load posted", shownBefore, shown.size());
                    continue;
                }
                ++newestRuns;
                assertEquals("the newest load didn't post", shownBefore + 1, shown.size());
                List<DebloatObject> rows = shown.get(shown.size() - 1);
                assertEquals(packages(expected), names(rows));
                Set<Integer> reads = new HashSet<>();
                for (DebloatObject row : rows) {
                    reads.add(source.readOf.get(row));
                }
                if (!rows.isEmpty()) {
                    assertEquals("rows from more than one read", 1, reads.size());
                    assertTrue("rows read before the newest reload was asked for",
                            reads.iterator().next() > readsBeforeNewestReload);
                }
            }
        }
        while (executor.size() > 0) {
            executor.runAt(random.nextInt(executor.size()));
        }
        idleMainLooper();

        // Both kinds of completion happened often enough to mean something.
        assertTrue("newest loads run: " + newestRuns, newestRuns > 100);
        assertTrue("replaced loads run: " + replacedRuns, replacedRuns > 100);
        assertEquals(packages(expected), packages(viewModel));
    }

    @Test
    public void aReloadThatFailsKeepsTheLastListAndFiltersStillWork() {
        FakeSource source = threeLists();
        DebloaterViewModel viewModel = new DebloaterViewModel(app(), source, Runnable::run);
        List<List<DebloatObject>> shown = new ArrayList<>();
        viewModel.getDebloatObjectListLiveData().observeForever(shown::add);
        viewModel.clearFilters();
        idleMainLooper();
        assertEquals(3, packages(viewModel).size());

        source.failNext = new IllegalStateException("database is closed");
        viewModel.loadPackages(true);
        idleMainLooper();
        // Posted again, so the spinner shown for the reload goes away.
        assertEquals(2, shown.size());
        assertEquals(3, packages(viewModel).size());

        viewModel.setQuery("oem", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        idleMainLooper();
        assertEquals(Collections.singletonList("com.example.oem"), packages(viewModel));
        assertEquals("a filter change read the list again", 2, source.loads.get());

        viewModel.loadPackages(true);
        assertEquals("the next reload didn't try again", 3, source.loads.get());
    }

    @NonNull
    private static Application app() {
        return ApplicationProvider.getApplicationContext();
    }

    private static void idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @NonNull
    private static List<String> packages(@NonNull DebloaterViewModel viewModel) {
        List<DebloatObject> shown = viewModel.getDebloatObjectListLiveData().getValue();
        return shown == null ? new ArrayList<>() : names(shown);
    }

    @NonNull
    private static List<String> names(@NonNull List<DebloatObject> rows) {
        List<String> packages = new ArrayList<>();
        for (DebloatObject debloatObject : rows) {
            packages.add(debloatObject.packageName);
        }
        return packages;
    }

    @NonNull
    private static FakeSource threeLists() {
        return new FakeSource(debloatObject("com.example.oem", "oem"),
                debloatObject("com.example.aosp", "aosp"),
                debloatObject("com.example.google", "google"));
    }

    private static DebloatObject debloatObject(String packageName, String type) {
        DebloatObject debloatObject = new DebloatObject();
        debloatObject.packageName = packageName;
        debloatObject.type = type;
        return debloatObject;
    }

    private static final class FakeSource implements DebloaterViewModel.Source {
        final List<DebloatObject> objects;
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();
        // Which read handed out each row, when every read hands out new rows
        final Map<DebloatObject, Integer> readOf = Collections.synchronizedMap(new IdentityHashMap<>());
        Runnable onLoad;
        // Runs once the read has its rows, so what it changes shows up only in a later read
        Runnable afterRead;
        long pauseMillis;
        // New rows on every read, like the real list
        boolean freshRows;
        RuntimeException failNext;

        FakeSource(DebloatObject... objects) {
            this.objects = new ArrayList<>(Arrays.asList(objects));
        }

        @NonNull
        @Override
        public List<DebloatObject> load() {
            int now = inFlight.incrementAndGet();
            mostAtOnce.accumulateAndGet(now, Math::max);
            try {
                int read = loads.incrementAndGet();
                Runnable hook = onLoad;
                onLoad = null;
                if (hook != null) {
                    hook.run();
                }
                RuntimeException failure = failNext;
                failNext = null;
                if (failure != null) {
                    throw failure;
                }
                if (pauseMillis > 0) {
                    try {
                        Thread.sleep(pauseMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                List<DebloatObject> rows = new ArrayList<>(objects.size());
                for (DebloatObject definition : objects) {
                    DebloatObject row = freshRows ? definition.copyDefinition(null) : definition;
                    if (freshRows) {
                        readOf.put(row, read);
                    }
                    rows.add(row);
                }
                Runnable after = afterRead;
                afterRead = null;
                if (after != null) {
                    after.run();
                }
                return rows;
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    /**
     * Holds tasks until the test runs them, in whatever order it picks.
     */
    private static final class QueueExecutor implements Executor {
        private final List<Runnable> mTasks = new ArrayList<>();
        private final List<Integer> mNumbers = new ArrayList<>();
        private int mQueued;

        @Override
        public void execute(@NonNull Runnable command) {
            mTasks.add(command);
            mNumbers.add(++mQueued);
        }

        int size() {
            return mTasks.size();
        }

        /**
         * @return how many tasks had been queued when the newest one was
         */
        int queued() {
            return mQueued;
        }

        /**
         * @return the task's number, counting from 1 in the order it was queued
         */
        int runAt(int index) {
            int number = mNumbers.remove(index);
            mTasks.remove(index).run();
            return number;
        }

        void runOldest() {
            runAt(0);
        }

        void runNewest() {
            runAt(mTasks.size() - 1);
        }

        void runNewestFirst() {
            while (!mTasks.isEmpty()) {
                runNewest();
            }
        }

        void runAll() {
            while (!mTasks.isEmpty()) {
                runOldest();
            }
        }
    }
}
