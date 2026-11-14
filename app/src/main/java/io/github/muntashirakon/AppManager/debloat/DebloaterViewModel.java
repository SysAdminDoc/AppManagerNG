// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.app.Application;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class DebloaterViewModel extends AndroidViewModel {
    /**
     * Reads the full debloat list with the install state of every entry filled in.
     */
    interface Source {
        @WorkerThread
        @NonNull
        List<DebloatObject> load();
    }

    /**
     * The filters and search a load was asked for, taken when it was asked for, so a load that
     * runs later can't pick up a newer setting halfway through.
     */
    private static final class LoadRequest {
        final long generation;
        @DebloaterListOptions.Filter
        final int filterFlags;
        @Nullable
        final String queryString;
        @AdvancedSearchView.SearchType
        final int queryType;

        LoadRequest(long generation, @DebloaterListOptions.Filter int filterFlags,
                    @Nullable String queryString, @AdvancedSearchView.SearchType int queryType) {
            this.generation = generation;
            this.filterFlags = filterFlags;
            this.queryString = queryString;
            this.queryType = queryType;
        }
    }

    private static final String TAG = DebloaterViewModel.class.getSimpleName();

    private final Object mLock = new Object();
    // Guarded by mLock
    @DebloaterListOptions.Filter
    private int mFilterFlags;
    @Nullable
    private String mQueryString = null;
    @AdvancedSearchView.SearchType
    private int mQueryType;
    private long mGeneration;
    // The first load always reads the list.
    private long mReloadsRequested = 1;
    // Only used by the load executor, which runs one load at a time
    private long mReloadsDone;
    // Replaced whole and never changed after, so any thread can read it
    @NonNull
    private volatile List<DebloatObject> mDebloatObjects = Collections.emptyList();

    private final Map<String, int[]> mSelectedPackages = new HashMap<>();
    private final MutableLiveData<List<DebloatObject>> mDebloatObjectListLiveData = new MutableLiveData<>();
    @NonNull
    private final Source mSource;
    @NonNull
    private final Executor mExecutor;

    public DebloaterViewModel(@NonNull Application application) {
        this(application, () -> StaticDataset.getDebloatObjectsWithInstalledInfo(application),
                newLoadExecutor());
    }

    @VisibleForTesting
    DebloaterViewModel(@NonNull Application application, @NonNull Source source, @NonNull Executor executor) {
        super(application);
        mSource = source;
        mExecutor = executor;
        mFilterFlags = AppPref.getInt(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT);
    }

    @VisibleForTesting
    @NonNull
    static Executor newLoadExecutor() {
        // One load at a time, so two reloads never read the whole list side by side. The
        // thread goes away while the screen sits idle. execute() rather than submit(), so a
        // bug in a load surfaces instead of vanishing into a Future nobody reads.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public boolean hasFilterFlag(@DebloaterListOptions.Filter int flag) {
        synchronized (mLock) {
            return (mFilterFlags & flag) != 0;
        }
    }

    public boolean hasActiveFilters() {
        synchronized (mLock) {
            return mFilterFlags != DebloaterListOptions.FILTER_NO_FILTER;
        }
    }

    public boolean hasSearchQuery() {
        synchronized (mLock) {
            return !TextUtils.isEmpty(mQueryString);
        }
    }

    public void addFilterFlag(@DebloaterListOptions.Filter int flag) {
        int filterFlags;
        synchronized (mLock) {
            mFilterFlags |= flag;
            filterFlags = mFilterFlags;
        }
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, filterFlags);
        loadPackages();
    }

    public void removeFilterFlag(@DebloaterListOptions.Filter int flag) {
        int filterFlags;
        synchronized (mLock) {
            mFilterFlags &= ~flag;
            filterFlags = mFilterFlags;
        }
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, filterFlags);
        loadPackages();
    }

    public void clearFilters() {
        synchronized (mLock) {
            mFilterFlags = DebloaterListOptions.FILTER_NO_FILTER;
        }
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, DebloaterListOptions.FILTER_NO_FILTER);
        loadPackages();
    }

    public void setQuery(String queryString, @AdvancedSearchView.SearchType int searchType) {
        synchronized (mLock) {
            mQueryString = queryString;
            mQueryType = searchType;
        }
        loadPackages();
    }

    public LiveData<List<DebloatObject>> getDebloatObjectListLiveData() {
        return mDebloatObjectListLiveData;
    }

    public int getTotalItemCount() {
        return mDebloatObjects.size();
    }

    public int getSelectedItemCount() {
        return mSelectedPackages.size();
    }

    public void select(@NonNull DebloatObject debloatObject) {
        mSelectedPackages.put(debloatObject.packageName, debloatObject.getUsers());
    }

    public void deselect(@NonNull DebloatObject debloatObject) {
        mSelectedPackages.remove(debloatObject.packageName);
    }

    public void deselectAll() {
        mSelectedPackages.clear();
    }

    public boolean isSelected(@NonNull DebloatObject debloatObject) {
        return mSelectedPackages.containsKey(debloatObject.packageName);
    }

    public Map<String, int[]> getSelectedPackages() {
        return mSelectedPackages;
    }

    @NonNull
    public List<DebloatObject> getSelectedDebloatObjects() {
        List<DebloatObject> selectedDebloatObjects = new ArrayList<>();
        for (DebloatObject debloatObject : mDebloatObjects) {
            if (mSelectedPackages.containsKey(debloatObject.packageName)) {
                selectedDebloatObjects.add(debloatObject);
            }
        }
        return selectedDebloatObjects;
    }

    public int countPresetMatches(@NonNull DebloatPreset preset) {
        return getPresetMatches(preset).size();
    }

    public int selectPreset(@NonNull DebloatPreset preset) {
        List<DebloatObject> matches = getPresetMatches(preset);
        mSelectedPackages.clear();
        for (DebloatObject debloatObject : matches) {
            select(debloatObject);
        }
        return matches.size();
    }

    public int selectByPackageNames(@NonNull List<DebloatPresetIO.DebloatPresetEntry> entries) {
        mSelectedPackages.clear();
        int matched = 0;
        java.util.Set<String> targetPackages = new java.util.HashSet<>(entries.size());
        for (DebloatPresetIO.DebloatPresetEntry entry : entries) {
            if (entry.packageName != null) {
                targetPackages.add(entry.packageName);
            }
        }
        for (DebloatObject debloatObject : mDebloatObjects) {
            if (targetPackages.contains(debloatObject.packageName)) {
                select(debloatObject);
                ++matched;
            }
        }
        return matched;
    }

    @NonNull
    private List<DebloatObject> getPresetMatches(@NonNull DebloatPreset preset) {
        List<DebloatObject> matches = new ArrayList<>();
        for (DebloatObject debloatObject : mDebloatObjects) {
            if (preset.matches(debloatObject)) {
                matches.add(debloatObject);
            }
        }
        return matches;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        return getPackagesWithUsers(getSelectedDebloatObjects());
    }

    @NonNull
    public ArrayList<UserPackagePair> getPackagesWithUsers(@NonNull List<DebloatObject> debloatObjects) {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int myUserId = UserHandleHidden.myUserId();
        int[] userIds = Users.getUsersIds();
        for (DebloatObject debloatObject : debloatObjects) {
            String packageName = debloatObject.packageName;
            int[] userHandles = debloatObject.getUsers();
            if (userHandles == null || userHandles.length == 0) {
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, myUserId));
            } else {
                for (int userHandle : userHandles) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    @AnyThread
    public void loadPackages() {
        loadPackages(false);
    }

    /**
     * Loads run one at a time, and only the newest one asked for shows its result, so the list
     * always ends on the latest filters and search however quickly they change.
     *
     * @param reloadInstallInfo when {@code true}, the installed/frozen/user state of every
     *                          debloat entry is recomputed. Pass {@code true} after a batch
     *                          operation (uninstall/freeze) so the list and its filters stop
     *                          acting on stale state; {@code false} for plain filter/sort.
     */
    @AnyThread
    public void loadPackages(boolean reloadInstallInfo) {
        LoadRequest request;
        synchronized (mLock) {
            if (reloadInstallInfo) {
                // Counted rather than passed with the request, so the reload still happens
                // when a newer load replaces this one before it runs.
                ++mReloadsRequested;
            }
            request = new LoadRequest(++mGeneration, mFilterFlags, mQueryString, mQueryType);
        }
        mExecutor.execute(() -> runLoad(request));
    }

    @WorkerThread
    private void runLoad(@NonNull LoadRequest request) {
        if (isSuperseded(request)) {
            // A newer load comes after this one and does the same work with newer settings.
            return;
        }
        List<DebloatObject> debloatObjects = filter(loadDebloatObjects(), request);
        synchronized (mLock) {
            // Checked and posted under the lock that numbers the loads, so a load asked for
            // after this check can only post after this one.
            if (isSuperseded(request)) {
                return;
            }
            mDebloatObjectListLiveData.postValue(debloatObjects);
        }
    }

    private boolean isSuperseded(@NonNull LoadRequest request) {
        synchronized (mLock) {
            return request.generation != mGeneration;
        }
    }

    @WorkerThread
    @NonNull
    private List<DebloatObject> loadDebloatObjects() {
        long reloadsRequested;
        synchronized (mLock) {
            reloadsRequested = mReloadsRequested;
        }
        if (reloadsRequested == mReloadsDone) {
            return mDebloatObjects;
        }
        // getDebloatObjectsWithInstalledInfo() hands back newly filled rows on each call, so
        // re-running it refreshes the cache and never changes rows already on screen.
        List<DebloatObject> debloatObjects;
        try {
            debloatObjects = new ArrayList<>(mSource.load());
            Collections.sort(debloatObjects, (o1, o2) -> CharSequence.compare(o1.getLabelOrPackageName(), o2.getLabelOrPackageName()));
        } catch (RuntimeException e) {
            // Keep the last list that loaded, so the spinner clears and filters and search
            // still work. The next refresh or batch operation asks for a reload again.
            Log.e(TAG, "Couldn't reload the debloat list", e);
            mReloadsDone = reloadsRequested;
            return mDebloatObjects;
        }
        List<DebloatObject> loaded = Collections.unmodifiableList(debloatObjects);
        mDebloatObjects = loaded;
        mReloadsDone = reloadsRequested;
        return loaded;
    }

    @WorkerThread
    @NonNull
    private static List<DebloatObject> filter(@NonNull List<DebloatObject> allDebloatObjects,
                                              @NonNull LoadRequest request) {
        int filterFlags = request.filterFlags;
        List<DebloatObject> debloatObjects = new ArrayList<>();
        if (filterFlags == DebloaterListOptions.FILTER_NO_FILTER) {
            debloatObjects.addAll(allDebloatObjects);
        } else {
            for (DebloatObject debloatObject : allDebloatObjects) {
                // List
                if ((filterFlags & DebloaterListOptions.FILTER_LIST_AOSP) == 0 && "aosp".equals(debloatObject.type)) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_LIST_CARRIER) == 0 && "carrier".equals(debloatObject.type)) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_LIST_GOOGLE) == 0 && "google".equals(debloatObject.type)) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_LIST_MISC) == 0 && "misc".equals(debloatObject.type)) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_LIST_OEM) == 0 && "oem".equals(debloatObject.type)) {
                    continue;
                }
                // Removal
                int removalType = debloatObject.getRemoval();
                if ((filterFlags & DebloaterListOptions.FILTER_REMOVAL_SAFE) == 0 && removalType == DebloatObject.REMOVAL_SAFE) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_REMOVAL_REPLACE) == 0 && removalType == DebloatObject.REMOVAL_REPLACE) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_REMOVAL_CAUTION) == 0 && removalType == DebloatObject.REMOVAL_CAUTION) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_REMOVAL_UNSAFE) == 0 && removalType == DebloatObject.REMOVAL_UNSAFE) {
                    continue;
                }
                // Filter others
                if ((filterFlags & DebloaterListOptions.FILTER_INSTALLED_APPS) != 0 && !debloatObject.isInstalled()) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_UNINSTALLED_APPS) != 0 && debloatObject.isInstalled()) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_USER_APPS) != 0 && !debloatObject.isUserApp()) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_SYSTEM_APPS) != 0 && !debloatObject.isSystemApp()) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_FROZEN_APPS) != 0 && !debloatObject.isFrozen()) {
                    continue;
                }
                if ((filterFlags & DebloaterListOptions.FILTER_UNFROZEN_APPS) != 0 && debloatObject.isFrozen()) {
                    continue;
                }
                debloatObjects.add(debloatObject);
            }
        }
        if (TextUtils.isEmpty(request.queryString)) {
            return debloatObjects;
        }
        // Apply searching
        return AdvancedSearchView.matches(request.queryString, debloatObjects,
                (AdvancedSearchView.ChoicesGenerator<DebloatObject>) item -> {
                    List<String> choices = new ArrayList<>();
                    choices.add(item.packageName);
                    CharSequence label = item.getLabel();
                    if (label != null) {
                        choices.add(label.toString().toLowerCase(Locale.getDefault()));
                    }
                    String warning = item.getWarning();
                    if (warning != null && !warning.isEmpty()) {
                        choices.add(warning.toLowerCase(Locale.getDefault()));
                    }
                    String description = item.getDescription();
                    if (description != null && !description.isEmpty()) {
                        choices.add(description.toLowerCase(Locale.getDefault()));
                    }
                    return choices;
                },
                request.queryType);
    }
}
