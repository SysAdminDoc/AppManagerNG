// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.csv.CsvWriter;

public class AppUsageViewModel extends AndroidViewModel {
    private static final String TAG = AppUsageViewModel.class.getSimpleName();
    private static final String[] USAGE_CSV_HEADERS = {
            "package_name", "user_id", "app_label", "last_usage_time", "screen_time_ms", "times_opened",
            "mobile_tx_bytes", "mobile_rx_bytes", "wifi_tx_bytes", "wifi_rx_bytes",
            "previous_screen_time_ms", "screen_time_delta_ms", "previous_times_opened", "times_opened_delta",
            "previous_mobile_tx_bytes", "previous_mobile_rx_bytes", "mobile_tx_delta_bytes", "mobile_rx_delta_bytes",
            "previous_wifi_tx_bytes", "previous_wifi_rx_bytes", "wifi_tx_delta_bytes", "wifi_rx_delta_bytes"
    };

    private final MutableLiveData<List<PackageUsageInfo>> mPackageUsageInfoListLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageUsageInfo> mPackageUsageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<UsageLoadStatus> mUsageLoadStatusLiveData = new MutableLiveData<>();
    private final Object mUsageLock = new Object();
    private final List<PackageUsageInfo> mPackageUsageInfoList = new ArrayList<>();
    private final List<PackageUsageInfo.Entry> mPackageUsageEntries = new ArrayList<>();
    private final Map<String, UsageComparison> mUsageComparisons = new HashMap<>();

    private long mTotalScreenTime;
    private long mPreviousTotalScreenTime;
    private boolean mHasMultipleUsers;
    private boolean mCompareWithPrevious;
    @IntervalType
    private int mCurrentInterval = IntervalType.INTERVAL_DAILY;
    private long mCurrentDate = System.currentTimeMillis();
    private int mSortOrder = SortOrder.SORT_BY_SCREEN_TIME;
    private long mLoadGeneration;
    @Nullable
    private Future<?> mLoadFuture;

    public AppUsageViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<PackageUsageInfo>> getPackageUsageInfoList() {
        return mPackageUsageInfoListLiveData;
    }

    public LiveData<PackageUsageInfo> getPackageUsageInfo() {
        return mPackageUsageInfoLiveData;
    }

    public LiveData<UsageLoadStatus> getUsageLoadStatus() {
        return mUsageLoadStatusLiveData;
    }

    public List<PackageUsageInfo.Entry> getPackageUsageEntries() {
        synchronized (mUsageLock) {
            return new ArrayList<>(mPackageUsageEntries);
        }
    }

    public void setCurrentDate(long currentDate) {
        synchronized (mUsageLock) {
            mCurrentDate = currentDate;
        }
        loadPackageUsageInfoList();
    }

    public long getCurrentDate() {
        synchronized (mUsageLock) {
            return mCurrentDate;
        }
    }

    public void setCurrentInterval(@IntervalType int currentInterval) {
        synchronized (mUsageLock) {
            mCurrentInterval = currentInterval;
            mCurrentDate = System.currentTimeMillis();
        }
        loadPackageUsageInfoList();
    }

    @IntervalType
    public int getCurrentInterval() {
        synchronized (mUsageLock) {
            return mCurrentInterval;
        }
    }

    public void setSortOrder(int sortOrder) {
        synchronized (mUsageLock) {
            mSortOrder = sortOrder;
        }
        ThreadUtils.postOnBackgroundThread(this::sortItems);
    }

    public int getSortOrder() {
        synchronized (mUsageLock) {
            return mSortOrder;
        }
    }

    public long getTotalScreenTime() {
        synchronized (mUsageLock) {
            return mTotalScreenTime;
        }
    }

    public boolean hasMultipleUsers() {
        synchronized (mUsageLock) {
            return mHasMultipleUsers;
        }
    }

    public boolean isCompareWithPrevious() {
        synchronized (mUsageLock) {
            return mCompareWithPrevious;
        }
    }

    public void setCompareWithPrevious(boolean compareWithPrevious) {
        synchronized (mUsageLock) {
            if (mCompareWithPrevious == compareWithPrevious) {
                return;
            }
            mCompareWithPrevious = compareWithPrevious;
        }
        loadPackageUsageInfoList();
    }

    public long getPreviousTotalScreenTime() {
        synchronized (mUsageLock) {
            return mPreviousTotalScreenTime;
        }
    }

    public UsageComparison getUsageComparison(@NonNull PackageUsageInfo usageInfo) {
        synchronized (mUsageLock) {
            return mUsageComparisons.get(getUsageKey(usageInfo));
        }
    }

    public void loadNext() {
        int interval;
        long date;
        synchronized (mUsageLock) {
            interval = mCurrentInterval;
            date = mCurrentDate;
        }
        setCurrentDate(UsageUtils.getNextDateFromInterval(interval, date));
    }

    public void loadPrevious() {
        int interval;
        long date;
        synchronized (mUsageLock) {
            interval = mCurrentInterval;
            date = mCurrentDate;
        }
        setCurrentDate(UsageUtils.getPreviousDateFromInterval(interval, date));
    }

    public void loadPackageUsageInfo(@NonNull PackageUsageInfo usageInfo) {
        if (ThreadUtils.isMainThread()) {
            mPackageUsageInfoLiveData.setValue(usageInfo);
        } else {
            mPackageUsageInfoLiveData.postValue(usageInfo);
        }
    }

    @AnyThread
    public void loadPackageUsageInfoList() {
        UsageRequest request;
        synchronized (mUsageLock) {
            if (mLoadFuture != null) {
                mLoadFuture.cancel(true);
            }
            request = new UsageRequest(++mLoadGeneration, mCurrentInterval, mCurrentDate,
                    mCompareWithPrevious, getUsageUserIds());
        }
        dispatchUsageLoadStatus(request.generation, UsageLoadStatus.loading(request));
        synchronized (mUsageLock) {
            if (request.generation != mLoadGeneration) {
                return;
            }
            mLoadFuture = submitUsageLoad(() -> loadUsageRequest(request));
        }
    }

    public void cancelUsageLoad() {
        synchronized (mUsageLock) {
            ++mLoadGeneration;
            if (mLoadFuture != null) {
                mLoadFuture.cancel(true);
                mLoadFuture = null;
            }
        }
    }

    @NonNull
    protected int[] getUsageUserIds() {
        return Users.getUsersIds();
    }

    @NonNull
    protected List<PackageUsageInfo> queryUsageStats(@NonNull TimeInterval interval, int userId)
            throws Exception {
        return AppUsageStatsManager.getInstance().getUsageStats(interval, userId);
    }

    @NonNull
    protected Future<?> submitUsageLoad(@NonNull Runnable runnable) {
        return ThreadUtils.postOnBackgroundThread(runnable);
    }

    private void loadUsageRequest(@NonNull UsageRequest request) {
        TimeInterval interval = UsageUtils.getTimeInterval(request.intervalType, request.date);
        QueryBatch current = queryUsers(request, interval, false);
        if (current.successfulUsers == 0) {
            Throwable failure = current.firstFailure != null
                    ? current.firstFailure
                    : new IllegalStateException("No Android users were available for usage query.");
            publishUsageFailure(request, current.failedUsers, failure);
            return;
        }

        Map<String, UsageComparison> usageComparisons = Collections.emptyMap();
        long previousTotalScreenTime = 0;
        int comparisonFailedUsers = 0;
        if (request.compareWithPrevious) {
            long previousDate = UsageUtils.getPreviousDateFromInterval(request.intervalType, request.date);
            QueryBatch previous = queryUsers(request,
                    UsageUtils.getTimeInterval(request.intervalType, previousDate), true);
            comparisonFailedUsers = previous.failedUsers;
            if (previous.successfulUsers > 0) {
                previousTotalScreenTime = getTotalScreenTime(previous.items);
                List<PackageUsageInfo> comparableCurrentItems = new ArrayList<>();
                for (PackageUsageInfo usageInfo : current.items) {
                    if (previous.successfulUserIds.contains(usageInfo.userId)) {
                        comparableCurrentItems.add(usageInfo);
                    }
                }
                usageComparisons = buildUsageComparisons(comparableCurrentItems, previous.items);
            }
        }

        long totalScreenTime = 0;
        Set<Integer> users = new HashSet<>(3);
        List<PackageUsageInfo.Entry> packageUsageEntries = new ArrayList<>();
        for (PackageUsageInfo appItem : current.items) {
            if (appItem.entries != null) {
                packageUsageEntries.addAll(appItem.entries);
            }
            totalScreenTime += appItem.screenTime;
            users.add(appItem.userId);
        }
        publishUsageSuccess(request, current.items, packageUsageEntries, totalScreenTime,
                users.size() > 1, usageComparisons, previousTotalScreenTime,
                current.successfulUsers, current.failedUsers, comparisonFailedUsers);
    }

    @NonNull
    private QueryBatch queryUsers(@NonNull UsageRequest request, @NonNull TimeInterval interval,
                                  boolean comparisonQuery) {
        QueryBatch result = new QueryBatch();
        for (int userId : request.userIds) {
            if (Thread.currentThread().isInterrupted() || !isLatestRequest(request.generation)) {
                break;
            }
            try {
                result.items.addAll(queryUsageStats(interval, userId));
                ++result.successfulUsers;
                result.successfulUserIds.add(userId);
            } catch (Exception e) {
                ++result.failedUsers;
                if (result.firstFailure == null) {
                    result.firstFailure = e;
                }
                Log.w(TAG, (comparisonQuery ? "Previous" : "Current")
                        + " usage query failed for user " + userId + '.', e);
            }
        }
        return result;
    }

    private boolean isLatestRequest(long generation) {
        synchronized (mUsageLock) {
            return generation == mLoadGeneration;
        }
    }

    private void publishUsageSuccess(@NonNull UsageRequest request,
                                     @NonNull List<PackageUsageInfo> packageUsageInfoList,
                                     @NonNull List<PackageUsageInfo.Entry> packageUsageEntries,
                                     long totalScreenTime, boolean hasMultipleUsers,
                                     @NonNull Map<String, UsageComparison> usageComparisons,
                                     long previousTotalScreenTime, int successfulUsers,
                                     int failedUsers, int comparisonFailedUsers) {
        Runnable publish = () -> {
            synchronized (mUsageLock) {
                if (request.generation != mLoadGeneration) {
                    return;
                }
                replaceUsageDataLocked(packageUsageInfoList, packageUsageEntries, totalScreenTime,
                        hasMultipleUsers, usageComparisons, previousTotalScreenTime);
                List<PackageUsageInfo> snapshot = sortItemsLocked();
                mLoadFuture = null;
                mPackageUsageInfoListLiveData.setValue(snapshot);
                mUsageLoadStatusLiveData.setValue(UsageLoadStatus.loaded(request, snapshot.size(),
                        successfulUsers, failedUsers, comparisonFailedUsers));
            }
        };
        dispatchOnMainThread(publish);
    }

    private void publishUsageFailure(@NonNull UsageRequest request, int failedUsers,
                                     @NonNull Throwable failure) {
        Runnable publish = () -> {
            synchronized (mUsageLock) {
                if (request.generation != mLoadGeneration) {
                    return;
                }
                replaceUsageDataLocked(Collections.emptyList(), Collections.emptyList(), 0,
                        false, Collections.emptyMap(), 0);
                mLoadFuture = null;
                mPackageUsageInfoListLiveData.setValue(Collections.emptyList());
                mUsageLoadStatusLiveData.setValue(
                        UsageLoadStatus.failed(request, failedUsers, failure));
            }
        };
        dispatchOnMainThread(publish);
    }

    private void dispatchUsageLoadStatus(long generation, @NonNull UsageLoadStatus status) {
        dispatchOnMainThread(() -> {
            synchronized (mUsageLock) {
                if (generation == mLoadGeneration) {
                    mUsageLoadStatusLiveData.setValue(status);
                }
            }
        });
    }

    private static void dispatchOnMainThread(@NonNull Runnable runnable) {
        if (ThreadUtils.isMainThread()) {
            runnable.run();
        } else {
            ThreadUtils.postOnMainThread(runnable);
        }
    }

    @NonNull
    public String getDefaultCsvFileName() {
        String timestamp = DateUtils.formatDateTime(getApplication(), System.currentTimeMillis())
                .replaceAll("[^A-Za-z0-9._-]+", "-");
        return "appmanagerng-usage-" + timestamp + ".csv";
    }

    @NonNull
    public String buildUsageCsv() throws IOException {
        StringWriter writer = new StringWriter();
        exportUsageCsv(writer);
        return writer.toString();
    }

    @VisibleForTesting
    void exportUsageCsv(@NonNull Writer writer) throws IOException {
        List<PackageUsageInfo> packageUsageInfoList;
        Map<String, UsageComparison> usageComparisons;
        synchronized (mUsageLock) {
            packageUsageInfoList = new ArrayList<>(mPackageUsageInfoList);
            usageComparisons = new HashMap<>(mUsageComparisons);
        }
        CsvWriter csvWriter = new CsvWriter(writer);
        csvWriter.addLine(USAGE_CSV_HEADERS);
        for (PackageUsageInfo usageInfo : packageUsageInfoList) {
            UsageComparison comparison = usageComparisons.get(getUsageKey(usageInfo));
            csvWriter.addLine(buildCsvRow(usageInfo, comparison));
        }
    }

    private void sortItems() {
        List<PackageUsageInfo> snapshot;
        synchronized (mUsageLock) {
            snapshot = sortItemsLocked();
        }
        mPackageUsageInfoListLiveData.postValue(snapshot);
    }

    @NonNull
    private List<PackageUsageInfo> sortItemsLocked() {
        Collator collator = Collator.getInstance();
        Collections.sort(mPackageUsageInfoList, ((o1, o2) -> {
            switch (mSortOrder) {
                case SortOrder.SORT_BY_APP_LABEL:
                    return collator.compare(o1.appLabel, o2.appLabel);
                case SortOrder.SORT_BY_LAST_USED:
                    return -Long.compare(o1.lastUsageTime, o2.lastUsageTime);
                case SortOrder.SORT_BY_MOBILE_DATA:
                    if (o1.mobileData == null) return o2.mobileData == null ? 0 : -1;
                    return -o1.mobileData.compareTo(o2.mobileData);
                case SortOrder.SORT_BY_PACKAGE_NAME:
                    return o1.packageName.compareToIgnoreCase(o2.packageName);
                case SortOrder.SORT_BY_SCREEN_TIME:
                    return -Long.compare(o1.screenTime, o2.screenTime);
                case SortOrder.SORT_BY_TIMES_OPENED:
                    return -Integer.compare(o1.timesOpened, o2.timesOpened);
                case SortOrder.SORT_BY_WIFI_DATA:
                    if (o1.wifiData == null) return o2.wifiData == null ? 0 : -1;
                    return -o1.wifiData.compareTo(o2.wifiData);
            }
            return 0;
        }));
        return new ArrayList<>(mPackageUsageInfoList);
    }

    private void replaceUsageData(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                  @NonNull List<PackageUsageInfo.Entry> packageUsageEntries,
                                  long totalScreenTime, boolean hasMultipleUsers,
                                  @NonNull Map<String, UsageComparison> usageComparisons,
                                  long previousTotalScreenTime) {
        synchronized (mUsageLock) {
            replaceUsageDataLocked(packageUsageInfoList, packageUsageEntries, totalScreenTime,
                    hasMultipleUsers, usageComparisons, previousTotalScreenTime);
        }
    }

    private void replaceUsageDataLocked(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                        @NonNull List<PackageUsageInfo.Entry> packageUsageEntries,
                                        long totalScreenTime, boolean hasMultipleUsers,
                                        @NonNull Map<String, UsageComparison> usageComparisons,
                                        long previousTotalScreenTime) {
        mPackageUsageInfoList.clear();
        mPackageUsageInfoList.addAll(packageUsageInfoList);
        mPackageUsageEntries.clear();
        mPackageUsageEntries.addAll(packageUsageEntries);
        mUsageComparisons.clear();
        mUsageComparisons.putAll(usageComparisons);
        mTotalScreenTime = totalScreenTime;
        mPreviousTotalScreenTime = previousTotalScreenTime;
        mHasMultipleUsers = hasMultipleUsers;
    }

    @VisibleForTesting
    void replaceUsageDataForTesting(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                    @NonNull List<PackageUsageInfo.Entry> packageUsageEntries) {
        replaceUsageDataForTesting(packageUsageInfoList, packageUsageEntries, 0, false);
    }

    @VisibleForTesting
    void replaceUsageDataForTesting(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                    @NonNull List<PackageUsageInfo.Entry> packageUsageEntries,
                                    long totalScreenTime, boolean hasMultipleUsers) {
        replaceUsageData(packageUsageInfoList, packageUsageEntries, totalScreenTime, hasMultipleUsers,
                Collections.emptyMap(), 0);
    }

    @VisibleForTesting
    void replaceComparisonDataForTesting(@NonNull Map<String, UsageComparison> usageComparisons,
                                         long previousTotalScreenTime) {
        synchronized (mUsageLock) {
            mUsageComparisons.clear();
            mUsageComparisons.putAll(usageComparisons);
            mPreviousTotalScreenTime = previousTotalScreenTime;
            mCompareWithPrevious = true;
        }
    }

    @NonNull
    @VisibleForTesting
    static Map<String, UsageComparison> buildUsageComparisons(@NonNull List<PackageUsageInfo> currentUsageInfoList,
                                                             @NonNull List<PackageUsageInfo> previousUsageInfoList) {
        Map<String, PackageUsageInfo> previousUsageByPackage = new HashMap<>();
        for (PackageUsageInfo usageInfo : previousUsageInfoList) {
            previousUsageByPackage.put(getUsageKey(usageInfo), usageInfo);
        }
        Map<String, UsageComparison> usageComparisons = new HashMap<>();
        for (PackageUsageInfo usageInfo : currentUsageInfoList) {
            usageComparisons.put(getUsageKey(usageInfo),
                    new UsageComparison(usageInfo, previousUsageByPackage.get(getUsageKey(usageInfo))));
        }
        return usageComparisons;
    }

    private static long getTotalScreenTime(@NonNull List<PackageUsageInfo> packageUsageInfoList) {
        long totalScreenTime = 0;
        for (PackageUsageInfo usageInfo : packageUsageInfoList) {
            totalScreenTime += usageInfo.screenTime;
        }
        return totalScreenTime;
    }

    @NonNull
    private static String getUsageKey(@NonNull PackageUsageInfo usageInfo) {
        return getUsageKey(usageInfo.packageName, usageInfo.userId);
    }

    @NonNull
    private static String getUsageKey(@NonNull String packageName, int userId) {
        return packageName + "#" + userId;
    }

    @NonNull
    private static String[] buildCsvRow(@NonNull PackageUsageInfo usageInfo,
                                        UsageComparison comparison) {
        AppUsageStatsManager.DataUsage mobileData = usageInfo.mobileData != null
                ? usageInfo.mobileData : AppUsageStatsManager.DataUsage.EMPTY;
        AppUsageStatsManager.DataUsage wifiData = usageInfo.wifiData != null
                ? usageInfo.wifiData : AppUsageStatsManager.DataUsage.EMPTY;
        return new String[]{
                usageInfo.packageName,
                String.valueOf(usageInfo.userId),
                usageInfo.appLabel,
                String.valueOf(usageInfo.lastUsageTime),
                String.valueOf(usageInfo.screenTime),
                String.valueOf(usageInfo.timesOpened),
                String.valueOf(mobileData.getTx()),
                String.valueOf(mobileData.getRx()),
                String.valueOf(wifiData.getTx()),
                String.valueOf(wifiData.getRx()),
                comparison != null ? String.valueOf(comparison.previousScreenTime) : "",
                comparison != null ? String.valueOf(comparison.screenTimeDelta) : "",
                comparison != null ? String.valueOf(comparison.previousTimesOpened) : "",
                comparison != null ? String.valueOf(comparison.timesOpenedDelta) : "",
                comparison != null ? String.valueOf(comparison.previousMobileData.getTx()) : "",
                comparison != null ? String.valueOf(comparison.previousMobileData.getRx()) : "",
                comparison != null ? String.valueOf(comparison.mobileDataDelta.getTx()) : "",
                comparison != null ? String.valueOf(comparison.mobileDataDelta.getRx()) : "",
                comparison != null ? String.valueOf(comparison.previousWifiData.getTx()) : "",
                comparison != null ? String.valueOf(comparison.previousWifiData.getRx()) : "",
                comparison != null ? String.valueOf(comparison.wifiDataDelta.getTx()) : "",
                comparison != null ? String.valueOf(comparison.wifiDataDelta.getRx()) : "",
        };
    }

    private static AppUsageStatsManager.DataUsage dataUsageOrEmpty(PackageUsageInfo usageInfo,
                                                                   boolean wifi) {
        AppUsageStatsManager.DataUsage dataUsage = wifi ? usageInfo.wifiData : usageInfo.mobileData;
        return dataUsage != null ? dataUsage : AppUsageStatsManager.DataUsage.EMPTY;
    }

    @NonNull
    private static AppUsageStatsManager.DataUsage subtractDataUsage(@NonNull AppUsageStatsManager.DataUsage current,
                                                                    @NonNull AppUsageStatsManager.DataUsage previous) {
        return new AppUsageStatsManager.DataUsage(current.getTx() - previous.getTx(),
                current.getRx() - previous.getRx());
    }

    @Override
    protected void onCleared() {
        cancelUsageLoad();
        super.onCleared();
    }

    public enum UsageLoadState {
        LOADING,
        LOADED,
        FAILED
    }

    public static final class UsageLoadStatus {
        @NonNull
        public final UsageLoadState state;
        @IntervalType
        public final int intervalType;
        public final long date;
        public final int itemCount;
        public final int totalUserCount;
        public final int successfulUserCount;
        public final int failedUserCount;
        public final int comparisonFailedUserCount;
        @Nullable
        public final String errorClass;
        @Nullable
        public final String errorMessage;
        @NonNull
        private final int[] userIds;

        private UsageLoadStatus(@NonNull UsageLoadState state, @NonNull UsageRequest request,
                                int itemCount, int successfulUserCount, int failedUserCount,
                                int comparisonFailedUserCount, @Nullable Throwable failure) {
            this.state = state;
            intervalType = request.intervalType;
            date = request.date;
            userIds = Arrays.copyOf(request.userIds, request.userIds.length);
            totalUserCount = userIds.length;
            this.itemCount = Math.max(0, itemCount);
            this.successfulUserCount = Math.max(0, successfulUserCount);
            this.failedUserCount = Math.max(0, failedUserCount);
            this.comparisonFailedUserCount = Math.max(0, comparisonFailedUserCount);
            errorClass = failure != null ? failure.getClass().getSimpleName() : null;
            errorMessage = failure != null ? failure.getMessage() : null;
        }

        @NonNull
        static UsageLoadStatus loading(@NonNull UsageRequest request) {
            return new UsageLoadStatus(UsageLoadState.LOADING, request, 0, 0, 0, 0, null);
        }

        @NonNull
        static UsageLoadStatus loaded(@NonNull UsageRequest request, int itemCount,
                                      int successfulUserCount, int failedUserCount,
                                      int comparisonFailedUserCount) {
            return new UsageLoadStatus(UsageLoadState.LOADED, request, itemCount,
                    successfulUserCount, failedUserCount, comparisonFailedUserCount, null);
        }

        @NonNull
        static UsageLoadStatus failed(@NonNull UsageRequest request, int failedUserCount,
                                      @NonNull Throwable failure) {
            return new UsageLoadStatus(UsageLoadState.FAILED, request, 0, 0,
                    failedUserCount, 0, failure);
        }

        @NonNull
        public int[] getUserIds() {
            return Arrays.copyOf(userIds, userIds.length);
        }

        public boolean isFailed() {
            return state == UsageLoadState.FAILED;
        }
    }

    private static final class UsageRequest {
        final long generation;
        @IntervalType
        final int intervalType;
        final long date;
        final boolean compareWithPrevious;
        @NonNull
        final int[] userIds;

        UsageRequest(long generation, @IntervalType int intervalType, long date,
                     boolean compareWithPrevious, @NonNull int[] userIds) {
            this.generation = generation;
            this.intervalType = intervalType;
            this.date = date;
            this.compareWithPrevious = compareWithPrevious;
            this.userIds = Arrays.copyOf(userIds, userIds.length);
        }
    }

    private static final class QueryBatch {
        @NonNull
        final List<PackageUsageInfo> items = new ArrayList<>();
        @NonNull
        final Set<Integer> successfulUserIds = new HashSet<>();
        int successfulUsers;
        int failedUsers;
        @Nullable
        Throwable firstFailure;
    }

    public static final class UsageComparison {
        public final long previousScreenTime;
        public final long screenTimeDelta;
        public final int previousTimesOpened;
        public final int timesOpenedDelta;
        @NonNull
        public final AppUsageStatsManager.DataUsage previousMobileData;
        @NonNull
        public final AppUsageStatsManager.DataUsage mobileDataDelta;
        @NonNull
        public final AppUsageStatsManager.DataUsage previousWifiData;
        @NonNull
        public final AppUsageStatsManager.DataUsage wifiDataDelta;

        UsageComparison(@NonNull PackageUsageInfo currentUsageInfo, PackageUsageInfo previousUsageInfo) {
            AppUsageStatsManager.DataUsage currentMobileData = dataUsageOrEmpty(currentUsageInfo, false);
            AppUsageStatsManager.DataUsage currentWifiData = dataUsageOrEmpty(currentUsageInfo, true);
            if (previousUsageInfo != null) {
                previousScreenTime = previousUsageInfo.screenTime;
                previousTimesOpened = previousUsageInfo.timesOpened;
                previousMobileData = dataUsageOrEmpty(previousUsageInfo, false);
                previousWifiData = dataUsageOrEmpty(previousUsageInfo, true);
            } else {
                previousScreenTime = 0;
                previousTimesOpened = 0;
                previousMobileData = AppUsageStatsManager.DataUsage.EMPTY;
                previousWifiData = AppUsageStatsManager.DataUsage.EMPTY;
            }
            screenTimeDelta = currentUsageInfo.screenTime - previousScreenTime;
            timesOpenedDelta = currentUsageInfo.timesOpened - previousTimesOpened;
            mobileDataDelta = subtractDataUsage(currentMobileData, previousMobileData);
            wifiDataDelta = subtractDataUsage(currentWifiData, previousWifiData);
        }
    }
}
