// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Test
    public void buildUsageComparisonsCalculatesPreviousPeriodDeltas() {
        Context context = ApplicationProvider.getApplicationContext();
        PackageUsageInfo current = createUsageInfo(context, "com.example.app", 0,
                120_000L, 4, new AppUsageStatsManager.DataUsage(30, 40),
                new AppUsageStatsManager.DataUsage(300, 400));
        PackageUsageInfo previous = createUsageInfo(context, "com.example.app", 0,
                60_000L, 1, new AppUsageStatsManager.DataUsage(10, 15),
                new AppUsageStatsManager.DataUsage(100, 150));

        Map<String, AppUsageViewModel.UsageComparison> comparisons =
                AppUsageViewModel.buildUsageComparisons(Collections.singletonList(current),
                        Collections.singletonList(previous));
        AppUsageViewModel.UsageComparison comparison = comparisons.values().iterator().next();

        assertEquals(60_000L, comparison.previousScreenTime);
        assertEquals(60_000L, comparison.screenTimeDelta);
        assertEquals(1, comparison.previousTimesOpened);
        assertEquals(3, comparison.timesOpenedDelta);
        assertEquals(20, comparison.mobileDataDelta.getTx());
        assertEquals(25, comparison.mobileDataDelta.getRx());
        assertEquals(200, comparison.wifiDataDelta.getTx());
        assertEquals(250, comparison.wifiDataDelta.getRx());
    }

    @Test
    public void exportUsageCsvEscapesFieldsAndIncludesComparisonColumns() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppUsageViewModel viewModel = new AppUsageViewModel((Application) context);
        PackageUsageInfo current = createUsageInfo(context, "com.example,quoted", 10,
                120_000L, 3, new AppUsageStatsManager.DataUsage(10, 20),
                new AppUsageStatsManager.DataUsage(30, 40));
        PackageUsageInfo previous = createUsageInfo(context, "com.example,quoted", 10,
                60_000L, 1, new AppUsageStatsManager.DataUsage(4, 10),
                new AppUsageStatsManager.DataUsage(5, 8));
        Map<String, AppUsageViewModel.UsageComparison> comparisons =
                AppUsageViewModel.buildUsageComparisons(Collections.singletonList(current),
                        Collections.singletonList(previous));
        viewModel.replaceUsageDataForTesting(Collections.singletonList(current), Collections.emptyList(),
                120_000L, false);
        viewModel.replaceComparisonDataForTesting(comparisons, 60_000L);

        StringWriter writer = new StringWriter();
        viewModel.exportUsageCsv(writer);
        String csv = writer.toString();

        assertTrue(csv.startsWith("package_name,user_id,app_label"));
        assertTrue(csv.contains("\"com.example,quoted\""));
        assertTrue(csv.contains("60000,60000,1,2,4,10,6,10,5,8,25,32"));
    }

    @Test
    public void rapidIntervalAndDateChangesPublishOnlyLatestImmutableRequest() {
        Context context = ApplicationProvider.getApplicationContext();
        TestAppUsageViewModel viewModel = new TestAppUsageViewModel((Application) context);
        long firstDate = 1_704_067_200_000L;
        long latestDate = 1_705_276_800_000L;
        viewModel.userIds = new int[]{0};
        viewModel.query = (interval, userId) -> Collections.singletonList(createUsageInfo(context,
                interval.getIntervalType() + "-" + interval.getStartTime(), userId,
                interval.getStartTime(), 1, null, null));

        viewModel.setCurrentDate(firstDate);
        viewModel.setCurrentInterval(IntervalType.INTERVAL_WEEKLY);
        viewModel.setCurrentDate(latestDate);
        viewModel.userIds[0] = 99;

        assertEquals(3, viewModel.runner.tasks.size());
        assertTrue(viewModel.runner.tasks.get(0).isCancelled());
        assertTrue(viewModel.runner.tasks.get(1).isCancelled());
        viewModel.runner.run(2);
        viewModel.runner.run(0);
        viewModel.runner.run(1);
        ShadowLooper.idleMainLooper();

        AppUsageViewModel.UsageLoadStatus status = viewModel.getUsageLoadStatus().getValue();
        assertNotNull(status);
        assertEquals(AppUsageViewModel.UsageLoadState.LOADED, status.state);
        assertEquals(IntervalType.INTERVAL_WEEKLY, status.intervalType);
        assertEquals(latestDate, status.date);
        assertArrayEquals(new int[]{0}, status.getUserIds());
        assertEquals(0, viewModel.queriedUserIds.get(0).intValue());
        List<PackageUsageInfo> items = viewModel.getPackageUsageInfoList().getValue();
        assertNotNull(items);
        assertEquals(1, items.size());
        TimeInterval expectedInterval = UsageUtils.getTimeInterval(
                IntervalType.INTERVAL_WEEKLY, latestDate);
        assertEquals(IntervalType.INTERVAL_WEEKLY + "-" + expectedInterval.getStartTime(),
                items.get(0).packageName);
    }

    @Test
    public void partialUserFailurePublishesAvailableUsageAndCountsFailure() {
        Context context = ApplicationProvider.getApplicationContext();
        TestAppUsageViewModel viewModel = new TestAppUsageViewModel((Application) context);
        viewModel.userIds = new int[]{0, 10};
        viewModel.query = (interval, userId) -> {
            if (userId == 10) {
                throw new SecurityException("profile denied");
            }
            return Collections.singletonList(createUsageInfo(context, "com.example.available",
                    userId, 100L, 1, null, null));
        };

        viewModel.loadPackageUsageInfoList();
        viewModel.runner.run(0);
        ShadowLooper.idleMainLooper();

        AppUsageViewModel.UsageLoadStatus status = viewModel.getUsageLoadStatus().getValue();
        assertNotNull(status);
        assertEquals(AppUsageViewModel.UsageLoadState.LOADED, status.state);
        assertEquals(1, status.successfulUserCount);
        assertEquals(1, status.failedUserCount);
        assertEquals(1, viewModel.getPackageUsageInfoList().getValue().size());
    }

    @Test
    public void partialComparisonFailureDoesNotFabricateMissingUserBaseline() {
        Context context = ApplicationProvider.getApplicationContext();
        TestAppUsageViewModel viewModel = new TestAppUsageViewModel((Application) context);
        viewModel.userIds = new int[]{0, 10};
        int[] queryCount = {0};
        viewModel.query = (interval, userId) -> {
            if (queryCount[0]++ == 3) {
                throw new SecurityException("comparison profile denied");
            }
            return Collections.singletonList(createUsageInfo(context, "com.example." + userId,
                    userId, 100L, 1, null, null));
        };

        viewModel.setCompareWithPrevious(true);
        viewModel.runner.run(0);
        ShadowLooper.idleMainLooper();

        AppUsageViewModel.UsageLoadStatus status = viewModel.getUsageLoadStatus().getValue();
        assertNotNull(status);
        assertEquals(AppUsageViewModel.UsageLoadState.LOADED, status.state);
        assertEquals(1, status.comparisonFailedUserCount);
        List<PackageUsageInfo> items = viewModel.getPackageUsageInfoList().getValue();
        assertNotNull(items);
        assertEquals(2, items.size());
        PackageUsageInfo user0 = items.get(0).userId == 0 ? items.get(0) : items.get(1);
        PackageUsageInfo user10 = items.get(0).userId == 10 ? items.get(0) : items.get(1);
        assertNotNull(viewModel.getUsageComparison(user0));
        assertNull(viewModel.getUsageComparison(user10));
    }

    @Test
    public void allUserFailureIsDistinctFromSuccessfulEmptyUsage() {
        Context context = ApplicationProvider.getApplicationContext();
        TestAppUsageViewModel failed = new TestAppUsageViewModel((Application) context);
        failed.userIds = new int[]{0, 10};
        failed.query = (interval, userId) -> {
            throw new SecurityException("usage query denied");
        };

        failed.loadPackageUsageInfoList();
        failed.runner.run(0);
        ShadowLooper.idleMainLooper();

        AppUsageViewModel.UsageLoadStatus failedStatus = failed.getUsageLoadStatus().getValue();
        assertNotNull(failedStatus);
        assertTrue(failedStatus.isFailed());
        assertEquals(2, failedStatus.failedUserCount);
        assertEquals("SecurityException", failedStatus.errorClass);
        assertTrue(failed.getPackageUsageInfoList().getValue().isEmpty());

        TestAppUsageViewModel empty = new TestAppUsageViewModel((Application) context);
        empty.userIds = new int[]{0, 10};
        empty.query = (interval, userId) -> Collections.emptyList();
        empty.loadPackageUsageInfoList();
        empty.runner.run(0);
        ShadowLooper.idleMainLooper();

        AppUsageViewModel.UsageLoadStatus emptyStatus = empty.getUsageLoadStatus().getValue();
        assertNotNull(emptyStatus);
        assertFalse(emptyStatus.isFailed());
        assertEquals(AppUsageViewModel.UsageLoadState.LOADED, emptyStatus.state);
        assertEquals(2, emptyStatus.successfulUserCount);
        assertEquals(0, emptyStatus.failedUserCount);
        assertTrue(empty.getPackageUsageInfoList().getValue().isEmpty());
    }

    private static PackageUsageInfo createUsageInfo(Context context, String packageName, int userId,
                                                   long screenTime, int timesOpened,
                                                   AppUsageStatsManager.DataUsage mobileData,
                                                   AppUsageStatsManager.DataUsage wifiData) {
        PackageUsageInfo usageInfo = new PackageUsageInfo(context, packageName, userId, null);
        usageInfo.lastUsageTime = 1_000L;
        usageInfo.screenTime = screenTime;
        usageInfo.timesOpened = timesOpened;
        usageInfo.mobileData = mobileData;
        usageInfo.wifiData = wifiData;
        return usageInfo;
    }

    private interface UsageQuery {
        List<PackageUsageInfo> query(TimeInterval interval, int userId) throws Exception;
    }

    private static final class TestAppUsageViewModel extends AppUsageViewModel {
        final ManualRunner runner = new ManualRunner();
        final List<Integer> queriedUserIds = new ArrayList<>();
        int[] userIds = new int[]{0};
        UsageQuery query = (interval, userId) -> Collections.emptyList();

        TestAppUsageViewModel(Application application) {
            super(application);
        }

        @Override
        protected int[] getUsageUserIds() {
            return userIds;
        }

        @Override
        protected List<PackageUsageInfo> queryUsageStats(TimeInterval interval, int userId)
                throws Exception {
            queriedUserIds.add(userId);
            return query.query(interval, userId);
        }

        @Override
        protected Future<?> submitUsageLoad(Runnable runnable) {
            return runner.submit(runnable);
        }
    }

    private static final class ManualRunner {
        final List<ManualFuture> tasks = new ArrayList<>();

        Future<?> submit(Runnable runnable) {
            ManualFuture task = new ManualFuture(runnable);
            tasks.add(task);
            return task;
        }

        void run(int index) {
            tasks.get(index).runEvenIfCancelled();
        }
    }

    private static final class ManualFuture implements Future<Object> {
        private final Runnable runnable;
        private boolean cancelled;
        private boolean done;

        ManualFuture(Runnable runnable) {
            this.runnable = runnable;
        }

        void runEvenIfCancelled() {
            runnable.run();
            done = true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            if (!done) {
                throw new IllegalStateException("Task has not run.");
            }
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }
}
