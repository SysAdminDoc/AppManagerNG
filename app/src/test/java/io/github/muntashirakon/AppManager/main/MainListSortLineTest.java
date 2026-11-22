// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.format.Formatter;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.DateUtils;

/**
 * Issue 38: sorted by install date, size or last use, the list never showed the value it was sorted by. The details
 * line now puts that value right after the UID, and a setting keeps the old fixed line.
 */
@RunWith(RobolectricTestRunner.class)
public class MainListSortLineTest {
    private static final String DOT = " " + (char) 0x00B7 + " ";
    private static final long INSTALLED = 1_700_000_000_000L;
    private static final long UPDATED = 1_710_000_000_000L;
    private static final long USED = 1_720_000_000_000L;
    private static final long ACTED = 1_730_000_000_000L;

    private final Context mContext = RuntimeEnvironment.getApplication();

    @After
    public void restorePreferences() {
        Prefs.MainPage.setSortOrder(MainListOptions.SORT_BY_APP_LABEL);
        Prefs.MainPage.setMetadataFollowsSort(true);
    }

    @Test
    public void sortedByInstallDateTheLineShowsTheDateAndTime() {
        assertEquals("UID 10123" + DOT + "Installed " + DateUtils.formatDateTime(mContext, INSTALLED) + DOT
                + "v4.7.3" + DOT + type() + DOT + "SDK 35", line(MainListOptions.SORT_BY_INSTALLATION_DATE));
    }

    @Test
    public void sortedByLastUpdateTheLineShowsTheUpdate() {
        assertEquals("UID 10123" + DOT + "Updated " + DateUtils.formatDateTime(mContext, UPDATED) + DOT
                + "v4.7.3" + DOT + type() + DOT + "SDK 35", line(MainListOptions.SORT_BY_LAST_UPDATE));
    }

    @Test
    public void sortedBySizeTheLineShowsTheSize() {
        assertTrue(line(MainListOptions.SORT_BY_TOTAL_SIZE).startsWith("UID 10123" + DOT + "Size "
                + Formatter.formatShortFileSize(mContext, 52_428_800L) + DOT + "v4.7.3"));
    }

    @Test
    public void sortedByLastUseTheLineShowsWhenItWasUsed() {
        assertTrue(line(MainListOptions.SORT_BY_LAST_USAGE_TIME).startsWith("UID 10123" + DOT + "Last used "
                + DateUtils.formatDateTime(mContext, USED) + DOT));
    }

    @Test
    public void sortedByTargetSdkTheSdkMovesToTheFront() {
        assertEquals("UID 10123" + DOT + "SDK 35" + DOT + "v4.7.3" + DOT + type(),
                line(MainListOptions.SORT_BY_TARGET_SDK));
    }

    @Test
    public void theUsageSortsShowTheirNumbers() {
        assertTrue(line(MainListOptions.SORT_BY_DATA_USAGE).startsWith("UID 10123" + DOT + "Data "
                + Formatter.formatShortFileSize(mContext, 2_048L) + DOT));
        assertTrue(line(MainListOptions.SORT_BY_OPEN_COUNT).startsWith("UID 10123" + DOT + "Opened 3 times" + DOT));
        assertTrue(line(MainListOptions.SORT_BY_SCREEN_TIME).startsWith("UID 10123" + DOT + "Screen time "
                + DateUtils.getFormattedDuration(mContext, 3_900_000L) + DOT));
    }

    @Test
    public void theCountSortsShowTheirCounts() {
        assertTrue(line(MainListOptions.SORT_BY_TRACKERS).startsWith("UID 10123" + DOT + "4 trackers" + DOT));
        assertTrue(line(MainListOptions.SORT_BY_BLOCKED_COMPONENTS).startsWith("UID 10123" + DOT + "2 blocked" + DOT));
        assertTrue(line(MainListOptions.SORT_BY_DANGEROUS_PERMS).startsWith("UID 10123" + DOT
                + "1 of 5 dangerous granted" + DOT));
        assertTrue(line(MainListOptions.SORT_BY_LAST_ACTION).startsWith("UID 10123" + DOT + "Last action "
                + DateUtils.formatDateTime(mContext, ACTED) + DOT));
    }

    @Test
    public void aSortWithoutAValueKeepsTheUsualLine() {
        String usual = "UID 10123" + DOT + "v4.7.3" + DOT + type() + DOT + "SDK 35";
        assertEquals(usual, line(MainListOptions.SORT_BY_APP_LABEL));
        assertEquals(usual, line(MainListOptions.SORT_BY_PACKAGE_NAME));
        assertEquals(usual, line(MainListOptions.SORT_BY_DOMAIN));
    }

    @Test
    public void theSettingKeepsTheFixedLine() {
        Prefs.MainPage.setMetadataFollowsSort(false);

        assertEquals("UID 10123" + DOT + "v4.7.3" + DOT + type() + DOT + "SDK 35",
                line(MainListOptions.SORT_BY_INSTALLATION_DATE));
    }

    @Test
    public void aScreenReaderHearsTheSortedValue() {
        FrameLayout row = bind(MainListOptions.SORT_BY_INSTALLATION_DATE);

        assertTrue(row.getContentDescription().toString().contains(
                "Installed " + DateUtils.formatDateTime(mContext, INSTALLED) + "."));
        assertTrue(row.getContentDescription().toString().endsWith(" UID 10123."));
    }

    @NonNull
    private String type() {
        return mContext.getString(R.string.user);
    }

    @NonNull
    private String line(int sortBy) {
        TextView metadata = bind(sortBy).findViewById(R.id.metadata);
        return metadata.getText().toString();
    }

    @NonNull
    private static FrameLayout bind(int sortBy) {
        Prefs.MainPage.setSortOrder(sortBy);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
        activity.setTheme(R.style.AppTheme_V2);
        activity.viewModel = new MainViewModel(activity.getApplication());
        MainRecyclerAdapter adapter = new MainRecyclerAdapter(activity);
        adapter.setDefaultList(Collections.singletonList(item()));
        MainRecyclerAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(activity), 0);
        adapter.onBindViewHolder(holder, 0);
        return (FrameLayout) holder.itemView;
    }

    @NonNull
    private static ApplicationItem item() {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.app";
        item.label = "Example";
        item.uid = 10123;
        item.userIds = new int[]{0};
        item.versionTag = "v4.7.3";
        item.sdkString = "SDK 35";
        item.targetSdk = 35;
        item.isUser = true;
        item.firstInstallTime = INSTALLED;
        item.lastUpdateTime = UPDATED;
        item.lastUsageTime = USED;
        item.lastActionTime = ACTED;
        item.totalSize = 52_428_800L;
        item.dataUsage = 2_048L;
        item.openCount = 3;
        item.screenTime = 3_900_000L;
        item.trackerCount = 4;
        item.blockedCount = 2;
        item.dangerousPermTotal = 5;
        item.dangerousPermGranted = 1;
        return item;
    }
}
