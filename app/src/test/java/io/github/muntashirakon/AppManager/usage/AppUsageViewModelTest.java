// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
}
