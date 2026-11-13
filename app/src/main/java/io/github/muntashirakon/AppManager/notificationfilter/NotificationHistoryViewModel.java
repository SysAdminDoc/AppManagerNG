// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.TextQuery;

public class NotificationHistoryViewModel extends AndroidViewModel {
    private static final String TAG = "NotificationHistoryVM";

    public static final class State {
        @NonNull
        public final List<NotificationHistoryEntry> visible;
        @NonNull
        public final List<NotificationHistoryFilter.AppChoice> apps;
        @NonNull
        public final Map<String, ApplicationInfo> appInfos;
        @Nullable
        public final String filterPackage;
        @Nullable
        public final String filterAction;
        @NonNull
        public final TextQuery query;
        public final int total;

        State(@NonNull List<NotificationHistoryEntry> visible, @NonNull List<NotificationHistoryFilter.AppChoice> apps,
              @NonNull Map<String, ApplicationInfo> appInfos, @Nullable String filterPackage,
              @Nullable String filterAction, @NonNull TextQuery query, int total) {
            this.visible = visible;
            this.apps = apps;
            this.appInfos = appInfos;
            this.filterPackage = filterPackage;
            this.filterAction = filterAction;
            this.query = query;
            this.total = total;
        }
    }

    /** What a clear took away, newest first, or {@code null} if it failed. Back to {@code null} once handled. */
    public static final class Cleared {
        @Nullable
        public final List<NotificationHistoryEntry> removed;

        Cleared(@Nullable List<NotificationHistoryEntry> removed) {
            this.removed = removed;
        }
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final MutableLiveData<Cleared> mCleared = new MutableLiveData<>();
    // One thread, so a slow read can't land after a newer one.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @NonNull
    private final NotificationHistory mHistory;
    @Nullable
    private volatile String mFilterPackage;
    @Nullable
    private volatile String mFilterAction;
    @NonNull
    private volatile TextQuery mQuery = TextQuery.empty();
    // The last read, so a new search or filter doesn't go back to the database. Worker only.
    @NonNull
    private List<NotificationHistoryEntry> mAll = Collections.emptyList();
    @NonNull
    private List<NotificationHistoryFilter.AppChoice> mApps = Collections.emptyList();
    @NonNull
    private Map<String, ApplicationInfo> mAppInfos = Collections.emptyMap();
    private boolean mLoaded;

    public NotificationHistoryViewModel(@NonNull Application application) {
        super(application);
        mHistory = NotificationHistory.getInstance(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
        super.onCleared();
    }

    @NonNull
    public LiveData<State> getState() {
        return mState;
    }

    @NonNull
    public LiveData<Cleared> getCleared() {
        return mCleared;
    }

    public void consumeCleared() {
        mCleared.setValue(null);
    }

    public void load() {
        mExecutor.execute(this::loadNow);
    }

    public void setFilterPackage(@Nullable String packageName) {
        mFilterPackage = packageName;
        mExecutor.execute(this::filterNow);
    }

    /** @param action An action name, {@link NotificationHistoryEntry#ACTION_NONE}, or {@code null} for all. */
    public void setFilterAction(@Nullable String action) {
        mFilterAction = action;
        mExecutor.execute(this::filterNow);
    }

    @Nullable
    public String getFilterAction() {
        return mFilterAction;
    }

    public void setQuery(@Nullable String query, boolean regex) {
        mQuery = TextQuery.of(query, regex);
        mExecutor.execute(this::filterNow);
    }

    @NonNull
    public TextQuery getQuery() {
        return mQuery;
    }

    public void clear() {
        mHistory.clear(removed -> mCleared.postValue(new Cleared(removed)));
    }

    public void undoClear(@NonNull List<NotificationHistoryEntry> removed) {
        mHistory.restore(removed);
    }

    @WorkerThread
    private void loadNow() {
        List<NotificationHistoryEntry> all;
        try {
            all = mHistory.readAll();
        } catch (SQLException e) {
            Log.w(TAG, "Could not read the notification history.", e);
            all = Collections.emptyList();
        }
        mAll = all;
        mApps = NotificationHistoryFilter.apps(all);
        Map<String, ApplicationInfo> appInfos = new HashMap<>();
        PackageManager pm = getApplication().getPackageManager();
        for (NotificationHistoryFilter.AppChoice app : mApps) {
            ApplicationInfo info = getApplicationInfo(pm, app.packageName);
            if (info != null) {
                appInfos.put(app.packageName, info);
            }
        }
        mAppInfos = appInfos;
        mLoaded = true;
        filterNow();
    }

    @WorkerThread
    private void filterNow() {
        if (!mLoaded) {
            loadNow();
            return;
        }
        String filterPackage = NotificationHistoryFilter.keepIfPresent(mFilterPackage, mApps);
        mFilterPackage = filterPackage;
        String filterAction = mFilterAction;
        TextQuery query = mQuery;
        mState.postValue(new State(NotificationHistoryFilter.filter(mAll, filterPackage, filterAction, query), mApps,
                mAppInfos, filterPackage, filterAction, query, mAll.size()));
    }

    @SuppressLint("WrongConstant")
    @Nullable
    private static ApplicationInfo getApplicationInfo(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            return pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
