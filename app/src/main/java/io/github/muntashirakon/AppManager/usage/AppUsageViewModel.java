// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class AppUsageViewModel extends AndroidViewModel {
    private final MutableLiveData<List<PackageUsageInfo>> mPackageUsageInfoListLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageUsageInfo> mPackageUsageInfoLiveData = new MutableLiveData<>();
    private final Object mUsageLock = new Object();
    private final List<PackageUsageInfo> mPackageUsageInfoList = new ArrayList<>();
    private final List<PackageUsageInfo.Entry> mPackageUsageEntries = new ArrayList<>();

    private long mTotalScreenTime;
    private boolean mHasMultipleUsers;
    @IntervalType
    private int mCurrentInterval = IntervalType.INTERVAL_DAILY;
    private long mCurrentDate = System.currentTimeMillis();
    private int mSortOrder = SortOrder.SORT_BY_SCREEN_TIME;

    public AppUsageViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<PackageUsageInfo>> getPackageUsageInfoList() {
        return mPackageUsageInfoListLiveData;
    }

    public LiveData<PackageUsageInfo> getPackageUsageInfo() {
        return mPackageUsageInfoLiveData;
    }

    public List<PackageUsageInfo.Entry> getPackageUsageEntries() {
        synchronized (mUsageLock) {
            return new ArrayList<>(mPackageUsageEntries);
        }
    }

    public void setCurrentDate(long currentDate) {
        mCurrentDate = currentDate;
        loadPackageUsageInfoList();
    }

    public long getCurrentDate() {
        return mCurrentDate;
    }

    public void setCurrentInterval(@IntervalType int currentInterval) {
        mCurrentInterval = currentInterval;
        mCurrentDate = System.currentTimeMillis();
        loadPackageUsageInfoList();
    }

    @IntervalType
    public int getCurrentInterval() {
        return mCurrentInterval;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        ThreadUtils.postOnBackgroundThread(this::sortItems);
    }

    public int getSortOrder() {
        return mSortOrder;
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

    public void loadNext() {
        setCurrentDate(UsageUtils.getNextDateFromInterval(mCurrentInterval, mCurrentDate));
    }

    public void loadPrevious() {
        setCurrentDate(UsageUtils.getPreviousDateFromInterval(mCurrentInterval, mCurrentDate));
    }

    public void loadPackageUsageInfo(PackageUsageInfo usageInfo) {
        if (ThreadUtils.isMainThread()) {
            mPackageUsageInfoLiveData.setValue(usageInfo);
        } else {
            mPackageUsageInfoLiveData.postValue(usageInfo);
        }
    }

    @AnyThread
    public void loadPackageUsageInfoList() {
        ThreadUtils.postOnBackgroundThread(() -> {
            int[] userIds = Users.getUsersIds();
            AppUsageStatsManager usageStatsManager = AppUsageStatsManager.getInstance();
            TimeInterval interval = UsageUtils.getTimeInterval(mCurrentInterval, mCurrentDate);
            List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
            for (int userId : userIds) {
                ExUtils.exceptionAsIgnored(() -> packageUsageInfoList.addAll(usageStatsManager
                        .getUsageStats(interval, userId)));
            }
            long totalScreenTime = 0;
            Set<Integer> users = new HashSet<>(3);
            List<PackageUsageInfo.Entry> packageUsageEntries = new ArrayList<>();
            for (PackageUsageInfo appItem : packageUsageInfoList) {
                if (appItem.entries != null) {
                    packageUsageEntries.addAll(appItem.entries);
                }
                totalScreenTime += appItem.screenTime;
                users.add(appItem.userId);
            }
            replaceUsageData(packageUsageInfoList, packageUsageEntries, totalScreenTime, users.size() > 1);
            sortItems();
        });
    }

    private void sortItems() {
        Collator collator = Collator.getInstance();
        List<PackageUsageInfo> snapshot;
        synchronized (mUsageLock) {
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
            snapshot = new ArrayList<>(mPackageUsageInfoList);
        }
        mPackageUsageInfoListLiveData.postValue(snapshot);
    }

    private void replaceUsageData(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                  @NonNull List<PackageUsageInfo.Entry> packageUsageEntries,
                                  long totalScreenTime, boolean hasMultipleUsers) {
        synchronized (mUsageLock) {
            mPackageUsageInfoList.clear();
            mPackageUsageInfoList.addAll(packageUsageInfoList);
            mPackageUsageEntries.clear();
            mPackageUsageEntries.addAll(packageUsageEntries);
            mTotalScreenTime = totalScreenTime;
            mHasMultipleUsers = hasMultipleUsers;
        }
    }

    @VisibleForTesting
    void replaceUsageDataForTesting(@NonNull List<PackageUsageInfo> packageUsageInfoList,
                                    @NonNull List<PackageUsageInfo.Entry> packageUsageEntries) {
        replaceUsageData(packageUsageInfoList, packageUsageEntries, 0, false);
    }
}
