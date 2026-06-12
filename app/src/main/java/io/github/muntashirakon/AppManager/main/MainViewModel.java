// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonParseException;

import org.json.JSONException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.apk.list.ListExporter;
import io.github.muntashirakon.AppManager.apk.list.ListImporter;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.shortcut.AppActionShortcutPublisher;
import io.github.muntashirakon.AppManager.tags.AppNoteStore;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.TimeInterval;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class MainViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private static final String TAG = MainViewModel.class.getSimpleName();
    @VisibleForTesting
    static final long APPLICATION_LIST_LOAD_TIMEOUT_MS = 30_000L;

    private final PackageManager mPackageManager;
    private final PackageIntentReceiver mPackageObserver;
    @MainListOptions.SortOrder
    private int mSortBy;
    private boolean mReverseSort;
    @MainListOptions.Filter
    private int mFilterFlags;
    @Nullable
    private String mFilterProfileName;
    private boolean mFilterProfileInverse;
    private long mInstallDateStartMillis;
    private long mInstallDateEndMillis;
    @Nullable
    private int[] mSelectedUsers;
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType;
    private Future<?> mFilterResult;
    private final Map<String, ApplicationItem> mSelectedPackageApplicationItemMap = Collections.synchronizedMap(new LinkedHashMap<>());
    final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = Prefs.MainPage.getSortOrder();
        mReverseSort = Prefs.MainPage.isReverseSort();
        mFilterFlags = Prefs.MainPage.getFilters();
        mFilterProfileName = Prefs.MainPage.getFilteredProfileName();
        mFilterProfileInverse = Prefs.MainPage.isFilteredProfileInverse();
        mInstallDateStartMillis = Prefs.MainPage.getInstallDateStartMillis();
        mInstallDateEndMillis = Prefs.MainPage.getInstallDateEndMillis();
        mSelectedUsers = Prefs.MainPage.getFilteredUsers();
        if ("".equals(mFilterProfileName)) mFilterProfileName = null;
    }

    private final MutableLiveData<Boolean> mOperationStatus = new MutableLiveData<>();
    @NonNull
    private final MutableLiveData<AppListImportStatus> mAppListImportStatus = new SingleLiveEvent<>();
    @NonNull
    private final MutableLiveData<List<ApplicationItem>> mApplicationItemsLiveData = new MutableLiveData<>();
    @NonNull
    private final MutableLiveData<AppListLoadStatus> mApplicationListLoadStatusLiveData = new MutableLiveData<>();
    @NonNull
    private final Object mApplicationListLoadStatusLock = new Object();
    @NonNull
    private AppListLoadStatus mApplicationListLoadStatus = AppListLoadStatus.loaded(0);
    private long mApplicationListLoadGeneration;
    private final List<ApplicationItem> mApplicationItems = new ArrayList<>();

    public int getApplicationItemCount() {
        return mApplicationItems.size();
    }

    public boolean hasActiveFilters() {
        return mFilterFlags != MainListOptions.FILTER_NO_FILTER
                || mFilterProfileName != null
                || hasInstallDateFilter()
                || mSelectedUsers != null;
    }

    public boolean hasConstrainedVisibleList() {
        return hasActiveFilters() || !TextUtils.isEmpty(mSearchQuery);
    }

    public int getActiveFilterCount() {
        int count = Integer.bitCount(mFilterFlags);
        if (mFilterProfileName != null) {
            ++count;
        }
        if (hasInstallDateFilter()) {
            ++count;
        }
        if (mSelectedUsers != null) {
            ++count;
        }
        return count;
    }

    @Override
    public boolean isOptionSelected(int option) {
        if (option == MainListOptions.OPTION_PROFILE_FILTER_INVERSE) {
            return mFilterProfileInverse;
        }
        return false;
    }

    @Override
    public void onOptionSelected(int option, boolean selected) {
        if (option != MainListOptions.OPTION_PROFILE_FILTER_INVERSE || mFilterProfileInverse == selected) {
            return;
        }
        mFilterProfileInverse = selected;
        Prefs.MainPage.setFilteredProfileInverse(selected);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (mApplicationItemsLiveData.getValue() == null) {
            loadApplicationItems();
        }
        return mApplicationItemsLiveData;
    }

    @NonNull
    public LiveData<AppListLoadStatus> getApplicationListLoadStatus() {
        return mApplicationListLoadStatusLiveData;
    }

    public LiveData<Boolean> getOperationStatus() {
        return mOperationStatus;
    }

    @NonNull
    public LiveData<AppListImportStatus> getAppListImportStatus() {
        return mAppListImportStatus;
    }

    @GuardedBy("applicationItems")
    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            item.isSelected = false;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    @GuardedBy("applicationItems")
    public ApplicationItem select(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            // Removal is needed because LinkedHashMap insertion-oriented
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            mSelectedPackageApplicationItemMap.put(item.packageName, item);
            item.isSelected = true;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    public void cancelSelection() {
        synchronized (mApplicationItems) {
            for (ApplicationItem item : getSelectedApplicationItems()) {
                int i = mApplicationItems.indexOf(item);
                if (i != -1) {
                    mApplicationItems.get(i).isSelected = false;
                }
            }
            mSelectedPackageApplicationItemMap.clear();
        }
    }

    @Nullable
    public ApplicationItem getLastSelectedPackage() {
        // Last selected package is the same as the last added package.
        Iterator<ApplicationItem> it = mSelectedPackageApplicationItemMap.values().iterator();
        ApplicationItem lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        return lastItem;
    }

    public Map<String, ApplicationItem> getSelectedPackages() {
        return mSelectedPackageApplicationItemMap;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int myUserId = UserHandleHidden.myUserId();
        int[] userIds = Users.getUsersIds();
        for (String packageName : mSelectedPackageApplicationItemMap.keySet()) {
            int[] userIds1 = Objects.requireNonNull(mSelectedPackageApplicationItemMap.get(packageName)).userIds;
            if (userIds1.length == 0) {
                // Could be a backup only item
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, myUserId));
            } else {
                for (int userHandle : userIds1) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    public Collection<ApplicationItem> getSelectedApplicationItems() {
        return mSelectedPackageApplicationItemMap.values();
    }

    public int selectOnlyApplicationItems(@NonNull Collection<ApplicationItem> applicationItems) {
        synchronized (mApplicationItems) {
            return MainSelectionUtils.selectOnly(mApplicationItems, applicationItems, mSelectedPackageApplicationItemMap);
        }
    }

    public String getSearchQuery() {
        return mSearchQuery;
    }

    public void setSearchQuery(String searchQuery, @AdvancedSearchView.SearchType int searchType) {
        String normalized = normalizeSearchQuery(searchQuery, searchType);
        // Coalesce: a debounced or repeated callback with an unchanged normalized query/type
        // shouldn't kick another full filter pass over the install.
        if (searchType == mSearchType && Objects.equals(normalized, mSearchQuery)) {
            return;
        }
        this.mSearchQuery = normalized;
        this.mSearchType = searchType;
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    /**
     * Normalizes a raw search query the same way {@link #setSearchQuery} stores it: lower-cased
     * for non-regex search types, left verbatim for regex. Extracted as a pure static so the
     * coalescing contract can be unit-tested without a live ViewModel.
     */
    @NonNull
    public static String normalizeSearchQuery(@NonNull String searchQuery,
                                              @AdvancedSearchView.SearchType int searchType) {
        return searchType != AdvancedSearchView.SEARCH_TYPE_REGEX
                ? searchQuery.toLowerCase(Locale.ROOT)
                : searchQuery;
    }

    @Override
    public int getSortBy() {
        return mSortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        if (mReverseSort == reverseSort) {
            return;
        }
        cancelIfRunning();
        mReverseSort = reverseSort;
        Prefs.MainPage.setReverseSort(mReverseSort);
        mFilterResult = executor.submit(() -> {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        });
    }

    @Override
    public boolean isReverseSort() {
        return mReverseSort;
    }

    @Override
    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            cancelIfRunning();
            mSortBy = sortBy;
            Prefs.MainPage.setSortOrder(mSortBy);
            mFilterResult = executor.submit(() -> {
                sortApplicationList(mSortBy, mReverseSort);
                filterItemsByFlags();
            });
        }
    }

    @Override
    public boolean hasFilterFlag(@MainListOptions.Filter int flag) {
        return (mFilterFlags & flag) != 0;
    }

    @Override
    public void addFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Override
    public void removeFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    public void setFilterProfileName(@Nullable String filterProfileName) {
        if (mFilterProfileName == null) {
            if (filterProfileName == null) return;
        } else if (mFilterProfileName.equals(filterProfileName)) return;
        mFilterProfileName = filterProfileName;
        Prefs.MainPage.setFilteredProfileName(filterProfileName);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public String getFilterProfileName() {
        return mFilterProfileName;
    }

    public boolean isFilterProfileInverse() {
        return mFilterProfileInverse;
    }

    public boolean hasInstallDateFilter() {
        return mInstallDateStartMillis > 0 || mInstallDateEndMillis > 0;
    }

    public long getInstallDateStartMillis() {
        return mInstallDateStartMillis;
    }

    public long getInstallDateEndMillis() {
        return mInstallDateEndMillis;
    }

    public void setInstallDateRange(long startMillis, long endMillis) {
        if (startMillis <= 0 && endMillis <= 0) {
            clearInstallDateRange();
            return;
        }
        if (startMillis > 0 && endMillis > 0 && startMillis > endMillis) {
            long tmp = startMillis;
            startMillis = endMillis;
            endMillis = tmp;
        }
        if (mInstallDateStartMillis == startMillis && mInstallDateEndMillis == endMillis) {
            return;
        }
        mInstallDateStartMillis = startMillis;
        mInstallDateEndMillis = endMillis;
        Prefs.MainPage.setInstallDateStartMillis(mInstallDateStartMillis);
        Prefs.MainPage.setInstallDateEndMillis(mInstallDateEndMillis);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    public void clearInstallDateRange() {
        if (!hasInstallDateFilter()) {
            return;
        }
        mInstallDateStartMillis = 0L;
        mInstallDateEndMillis = 0L;
        Prefs.MainPage.setInstallDateStartMillis(0L);
        Prefs.MainPage.setInstallDateEndMillis(0L);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    public void setSelectedUsers(@Nullable int[] selectedUsers) {
        if (selectedUsers == null) {
            if (mSelectedUsers == null) {
                // No change
                return;
            }
        } else if (mSelectedUsers != null) {
            if (mSelectedUsers.length == selectedUsers.length) {
                boolean differs = false;
                for (int user : selectedUsers) {
                    if (!ArrayUtils.contains(mSelectedUsers, user)) {
                        differs = true;
                        break;
                    }
                }
                if (!differs) {
                    // No change detected
                    return;
                }
            }
        }
        mSelectedUsers = selectedUsers != null ? selectedUsers.clone() : null;
        Prefs.MainPage.setFilteredUsers(mSelectedUsers);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    public void clearFilters() {
        if (!hasActiveFilters()) {
            return;
        }
        mFilterFlags = MainListOptions.FILTER_NO_FILTER;
        mFilterProfileName = null;
        mFilterProfileInverse = false;
        mInstallDateStartMillis = 0L;
        mInstallDateEndMillis = 0L;
        mSelectedUsers = null;
        Prefs.MainPage.setFilters(mFilterFlags);
        Prefs.MainPage.setFilteredProfileName(null);
        Prefs.MainPage.setFilteredProfileInverse(false);
        Prefs.MainPage.setInstallDateStartMillis(0L);
        Prefs.MainPage.setInstallDateEndMillis(0L);
        Prefs.MainPage.setFilteredUsers(null);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public int[] getSelectedUsers() {
        return mSelectedUsers != null ? mSelectedUsers.clone() : null;
    }

    @AnyThread
    public void onResume() {
        if ((mFilterFlags & MainListOptions.FILTER_RUNNING_APPS) != 0) {
            // Reload filters to get running apps again
            cancelIfRunning();
            mFilterResult = executor.submit(this::filterItemsByFlags);
        }
    }

    public void saveExportedAppList(@ListExporter.ExportType int exportType,
                                    @NonNull Path path,
                                    boolean visibleList,
                                    boolean includeExtendedMetadata) {
        List<ApplicationItem> exportItems = visibleList
                ? getVisibleApplicationItemsSnapshot()
                : getSelectedApplicationItemsSnapshot();
        executor.submit(() -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(path.openOutputStream(), StandardCharsets.UTF_8))) {
                List<PackageInfo> packageInfoList = getPackageInfoListForExport(exportItems);
                if (packageInfoList.isEmpty()) {
                    mOperationStatus.postValue(false);
                    return;
                }
                ListExporter.export(getApplication(), writer, exportType, packageInfoList, includeExtendedMetadata);
                mOperationStatus.postValue(true);
            } catch (IOException | RemoteException e) {
                e.printStackTrace();
                mOperationStatus.postValue(false);
            }
        });
    }

    public void importAppListSelection(@NonNull Path path) {
        executor.submit(() -> {
            try (InputStreamReader reader = new InputStreamReader(path.openInputStream(), StandardCharsets.UTF_8)) {
                Set<String> packageNames = ListImporter.readPackageNames(reader);
                int selectedCount = selectImportedPackageNames(packageNames);
                mAppListImportStatus.postValue(AppListImportStatus.success(selectedCount));
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
                mAppListImportStatus.postValue(AppListImportStatus.failure());
            }
        });
    }

    @NonNull
    private List<ApplicationItem> getSelectedApplicationItemsSnapshot() {
        synchronized (mSelectedPackageApplicationItemMap) {
            return new ArrayList<>(mSelectedPackageApplicationItemMap.values());
        }
    }

    @NonNull
    private List<ApplicationItem> getVisibleApplicationItemsSnapshot() {
        List<ApplicationItem> applicationItems = mApplicationItemsLiveData.getValue();
        return applicationItems != null ? new ArrayList<>(applicationItems) : Collections.emptyList();
    }

    @NonNull
    @WorkerThread
    private List<PackageInfo> getPackageInfoListForExport(@NonNull List<ApplicationItem> applicationItems)
            throws RemoteException {
        List<PackageInfo> packageInfoList = new ArrayList<>();
        HashSet<String> seenPackages = new HashSet<>();
        for (ApplicationItem item : applicationItems) {
            if (!item.isInstalled || !seenPackages.add(item.packageName)) {
                continue;
            }
            int[] userIds = item.userIds.length > 0 ? item.userIds : new int[]{UserHandleHidden.myUserId()};
            for (int userId : userIds) {
                try {
                    packageInfoList.add(PackageManagerCompat.getPackageInfo(item.packageName,
                            PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId));
                    break;
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
        }
        return packageInfoList;
    }

    @WorkerThread
    private int selectImportedPackageNames(@NonNull Set<String> packageNames) {
        synchronized (mApplicationItems) {
            for (ApplicationItem item : mApplicationItems) {
                item.isSelected = false;
            }
            mSelectedPackageApplicationItemMap.clear();
            for (ApplicationItem item : mApplicationItems) {
                if (!item.isInstalled || !packageNames.contains(item.packageName)) {
                    continue;
                }
                mSelectedPackageApplicationItemMap.put(item.packageName, item);
                item.isSelected = true;
            }
            List<ApplicationItem> visibleItems = mApplicationItemsLiveData.getValue();
            if (visibleItems != null) {
                mApplicationItemsLiveData.postValue(new ArrayList<>(visibleItems));
            }
            return mSelectedPackageApplicationItemMap.size();
        }
    }

    @GuardedBy("applicationItems")
    public void loadApplicationItems() {
        cancelIfRunning();
        long loadGeneration = beginApplicationListLoad();
        mFilterResult = executor.submit(() -> {
            try {
                List<ApplicationItem> updatedApplicationItems = loadInstalledOrBackedUpApplications();
                attachUserTags(updatedApplicationItems);
                attachUserNotes(updatedApplicationItems);
                synchronized (mApplicationItems) {
                    mApplicationItems.clear();
                    mApplicationItems.addAll(updatedApplicationItems);
                    // Re-select apps again. select() structurally modifies the selection
                    // map, so iterate over a snapshot to avoid a ConcurrentModificationException
                    // when two or more apps are selected during a list reload.
                    for (ApplicationItem item : new ArrayList<>(getSelectedApplicationItems())) {
                        select(item);
                    }
                    sortApplicationList(mSortBy, mReverseSort);
                    filterItemsByFlags();
                }
                if (updateApplicationListLoadStatus(loadGeneration,
                        AppListLoadStatus.loaded(updatedApplicationItems.size()))) {
                    publishDynamicShortcuts(updatedApplicationItems);
                }
            } catch (Throwable th) {
                Log.e(TAG, "Could not load application list.", th);
                int staleItemCount;
                synchronized (mApplicationItems) {
                    mApplicationItemsLiveData.postValue(new ArrayList<>(mApplicationItems));
                    staleItemCount = mApplicationItems.size();
                }
                updateApplicationListLoadStatus(loadGeneration, AppListLoadStatus.failed(th, staleItemCount));
            }
        });
    }

    @NonNull
    @WorkerThread
    protected List<ApplicationItem> loadInstalledOrBackedUpApplications() {
        return PackageUtils.getInstalledOrBackedUpApplicationsFromDb(getApplication(), true, true);
    }

    @WorkerThread
    protected void publishDynamicShortcuts(@NonNull List<ApplicationItem> updatedApplicationItems) {
        AppActionShortcutPublisher.publishDynamicShortcuts(getApplication(), updatedApplicationItems);
    }

    private long beginApplicationListLoad() {
        int staleItemCount;
        synchronized (mApplicationItems) {
            staleItemCount = mApplicationItems.size();
        }
        AppListLoadStatus status = AppListLoadStatus.loading(staleItemCount);
        long loadGeneration;
        synchronized (mApplicationListLoadStatusLock) {
            loadGeneration = ++mApplicationListLoadGeneration;
            mApplicationListLoadStatus = status;
        }
        dispatchApplicationListLoadStatus(status);
        final long expectedGeneration = loadGeneration;
        ThreadUtils.postOnMainThreadDelayed(() -> markApplicationListLoadTimedOut(expectedGeneration),
                APPLICATION_LIST_LOAD_TIMEOUT_MS);
        return loadGeneration;
    }

    private void markApplicationListLoadTimedOut(long loadGeneration) {
        synchronized (mApplicationListLoadStatusLock) {
            if (loadGeneration != mApplicationListLoadGeneration
                    || mApplicationListLoadStatus.state != AppListLoadState.LOADING) {
                return;
            }
        }
        int staleItemCount;
        synchronized (mApplicationItems) {
            mApplicationItemsLiveData.setValue(new ArrayList<>(mApplicationItems));
            staleItemCount = mApplicationItems.size();
        }
        TimeoutException timeout = new TimeoutException("Application list load exceeded "
                + (APPLICATION_LIST_LOAD_TIMEOUT_MS / 1000L) + " seconds");
        updateApplicationListLoadStatus(loadGeneration, AppListLoadStatus.failed(timeout, staleItemCount));
    }

    private boolean updateApplicationListLoadStatus(long loadGeneration, @NonNull AppListLoadStatus status) {
        synchronized (mApplicationListLoadStatusLock) {
            if (loadGeneration != mApplicationListLoadGeneration) {
                return false;
            }
            mApplicationListLoadStatus = status;
        }
        dispatchApplicationListLoadStatus(status);
        return true;
    }

    private void dispatchApplicationListLoadStatus(@NonNull AppListLoadStatus status) {
        if (ThreadUtils.isMainThread()) {
            mApplicationListLoadStatusLiveData.setValue(status);
        } else {
            mApplicationListLoadStatusLiveData.postValue(status);
        }
    }

    @WorkerThread
    private void attachUserTags(@NonNull List<ApplicationItem> items) {
        Map<String, Set<String>> tagsByPackage = new AppTagStore(getApplication()).snapshot();
        for (ApplicationItem item : items) {
            item.setUserTags(tagsByPackage.get(item.packageName));
        }
    }

    @WorkerThread
    private void attachUserNotes(@NonNull List<ApplicationItem> items) {
        Map<String, String> notesByPackage = new AppNoteStore(getApplication()).snapshot();
        for (ApplicationItem item : items) {
            item.setUserNote(notesByPackage.get(item.packageName));
        }
    }

    private void cancelIfRunning() {
        if (mFilterResult != null) {
            mFilterResult.cancel(true);
        }
    }

    @WorkerThread
    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems;
        if (mSearchType == AdvancedSearchView.SEARCH_TYPE_REGEX) {
            filteredApplicationItems = AdvancedSearchView.matches(mSearchQuery, applicationItems,
                    (AdvancedSearchView.ChoicesGenerator<ApplicationItem>) item -> new ArrayList<String>() {{
                        add(item.packageName);
                        add(item.label);
                        if (!TextUtils.isEmpty(item.userNote)) {
                            add(item.userNote);
                        }
                    }}, AdvancedSearchView.SEARCH_TYPE_REGEX);
            mApplicationItemsLiveData.postValue(filteredApplicationItems);
            return;
        }
        // Others
        filteredApplicationItems = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            boolean matches = AdvancedSearchView.matches(mSearchQuery,
                    item.packageName.toLowerCase(Locale.ROOT), mSearchType);
            if (!matches && mSearchType == AdvancedSearchView.SEARCH_TYPE_CONTAINS) {
                matches = Utils.containsOrHasInitials(mSearchQuery, item.label);
            } else if (!matches) {
                matches = AdvancedSearchView.matches(mSearchQuery, item.label.toLowerCase(Locale.ROOT), mSearchType);
            }
            if (!matches && !TextUtils.isEmpty(item.userNote)) {
                matches = AdvancedSearchView.matches(mSearchQuery, item.userNote.toLowerCase(Locale.ROOT), mSearchType);
            }
            if (matches) {
                filteredApplicationItems.add(item);
            }
        }
        mApplicationItemsLiveData.postValue(filteredApplicationItems);
    }

    @WorkerThread
    @GuardedBy("applicationItems")
    private void filterItemsByFlags() {
        synchronized (mApplicationItems) {
            List<ApplicationItem> candidateApplicationItems = new ArrayList<>();
            ProfileMembershipFilter profileMembershipFilter = getProfileMembershipFilter();
            FilterItem flagFilterItem = MainListOptions.getFilterItemFromFlags(mFilterFlags);
            int timesUsageInfoUsed = flagFilterItem.getTimesUsageInfoUsed() + profileMembershipFilter.getTimesUsageInfoUsed();
            int timesRunningOptionUsed = flagFilterItem.getTimesRunningOptionUsed() + profileMembershipFilter.getTimesRunningOptionUsed();
            boolean hasProfileFilter = profileMembershipFilter.isFiltering();
            for (ApplicationItem item : mApplicationItems) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                if (isAmongSelectedUsers(item)) {
                    candidateApplicationItems.add(item);
                }
            }
            // Other filters
            if (!hasProfileFilter
                    && mFilterFlags == MainListOptions.FILTER_NO_FILTER
                    && !hasInstallDateFilter()) {
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(candidateApplicationItems);
                } else {
                    mApplicationItemsLiveData.postValue(candidateApplicationItems);
                }
                return;
            }
            List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
            Map<String, PackageUsageInfo> packageUsageInfoList = new HashMap<>();
            if (timesUsageInfoUsed > 0) {
                boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
                if (hasUsageAccess) {
                    TimeInterval interval = UsageUtils.getLastWeek();
                    for (int userId : Users.getUsersIds()) {
                        List<PackageUsageInfo> usageInfoList;
                        usageInfoList = ExUtils.exceptionAsNull(() -> AppUsageStatsManager
                                .getInstance().getUsageStats(interval, userId));
                        if (usageInfoList != null) {
                            for (PackageUsageInfo info : usageInfoList) {
                                if (ThreadUtils.isInterrupted()) return;
                                PackageUsageInfo oldInfo = packageUsageInfoList.get(info.packageName);
                                if (oldInfo != null) {
                                    oldInfo.screenTime += info.screenTime;
                                    oldInfo.lastUsageTime = Math.max(oldInfo.lastUsageTime, info.lastUsageTime);
                                    oldInfo.timesOpened += info.timesOpened;
                                    oldInfo.mobileData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.mobileData, info.mobileData);
                                    oldInfo.wifiData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.wifiData, info.wifiData);
                                    if (info.entries != null) {
                                        if (oldInfo.entries == null) {
                                            oldInfo.entries = new ArrayList<>(info.entries);
                                        } else oldInfo.entries.addAll(info.entries);
                                    }
                                } else packageUsageInfoList.put(info.packageName, info);
                            }
                        }
                    }
                }
            }
            HashSet<String> runningPackages = new HashSet<>();
            if (timesRunningOptionUsed > 0) {
                for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
                    if (info.pkgList != null) {
                        runningPackages.addAll(Arrays.asList(info.pkgList));
                    }
                }
            }
            for (ApplicationItem item : candidateApplicationItems) {
                item.setPackageUsageInfo(packageUsageInfoList.get(item.packageName));
                item.setRunning(runningPackages.contains(item.packageName));
                if (!profileMembershipFilter.matches(item)) {
                    continue;
                }
                if (!matchesInstallDateRange(item)) {
                    continue;
                }
                if (!flagFilterItem.matches(item)) {
                    continue;
                }
                if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SPLITS) != 0 && !item.hasSplits) {
                    continue;
                }
                if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SAF) != 0 && !item.usesSaf) {
                    continue;
                }
                // Refine 'with trackers' to mean 'with UNBLOCKED trackers' so users
                // who've already locked an app down don't see it cluttering the
                // tracker view. The FilterItem-level TrackersOption=ge,1 has already
                // dropped apps with zero trackers; this drops apps where every
                // tracker is blocked (trackerBlockedCount >= trackerCount).
                if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_TRACKERS) != 0
                        && item.trackerCount != null && item.trackerCount > 0
                        && item.trackerBlockedCount != null
                        && item.trackerBlockedCount >= item.trackerCount) {
                    continue;
                }
                // 'With granted dangerous perms' filter — drops apps that don't
                // have any granted dangerous permissions. The Room column is
                // populated by the AppDb refresh pass; uses the cached value
                // directly so no per-render lookup is needed.
                if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_GRANTED_PERMS) != 0
                        && (item.dangerousPermGranted == null
                                || item.dangerousPermGranted == 0)) {
                    continue;
                }
                filteredApplicationItems.add(item);
            }
            if (!TextUtils.isEmpty(mSearchQuery)) {
                filterItemsByQuery(filteredApplicationItems);
            } else {
                mApplicationItemsLiveData.postValue(filteredApplicationItems);
            }
        }
    }

    @NonNull
    private ProfileMembershipFilter getProfileMembershipFilter() {
        if (mFilterProfileName != null) {
            String profileId = ProfileManager.getProfileIdCompat(mFilterProfileName);
            Path profilePath = ProfileManager.findProfilePathById(profileId);
            try {
                BaseProfile profile = BaseProfile.fromPath(profilePath);
                return ProfileMembershipFilter.fromProfile(profile, mFilterProfileInverse);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
        return ProfileMembershipFilter.none();
    }

    private boolean isAmongSelectedUsers(@NonNull ApplicationItem applicationItem) {
        if (mSelectedUsers == null) {
            // All users
            return true;
        }
        for (int userId : mSelectedUsers) {
            if (ArrayUtils.contains(applicationItem.userIds, userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesInstallDateRange(@NonNull ApplicationItem item) {
        if (!hasInstallDateFilter()) {
            return true;
        }
        if (!item.isInstalled) {
            return false;
        }
        long firstInstallTime = item.firstInstallTime;
        if (mInstallDateStartMillis > 0 && firstInstallTime < mInstallDateStartMillis) {
            return false;
        }
        return mInstallDateEndMillis <= 0 || firstInstallTime <= mInstallDateEndMillis;
    }

    @GuardedBy("applicationItems")
    private void sortApplicationList(@MainListOptions.SortOrder int sortBy, boolean reverse) {
        synchronized (mApplicationItems) {
            if (sortBy != MainListOptions.SORT_BY_APP_LABEL) {
                sortApplicationList(MainListOptions.SORT_BY_APP_LABEL, false);
            }
            int mode = reverse ? -1 : 1;
            Collator collator = Collator.getInstance();
            Collections.sort(mApplicationItems, (o1, o2) -> {
                switch (sortBy) {
                    case MainListOptions.SORT_BY_APP_LABEL:
                        return mode * collator.compare(o1.label, o2.label);
                    case MainListOptions.SORT_BY_PACKAGE_NAME:
                        return mode * o1.packageName.compareTo(o2.packageName);
                    case MainListOptions.SORT_BY_DOMAIN:
                        boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        return mode * Boolean.compare(isSystem1, isSystem2);
                    case MainListOptions.SORT_BY_LAST_UPDATE:
                        // Sort in decreasing order
                        return -mode * o1.lastUpdateTime.compareTo(o2.lastUpdateTime);
                    case MainListOptions.SORT_BY_TOTAL_SIZE:
                        // Sort in decreasing order
                        return -mode * o1.totalSize.compareTo(o2.totalSize);
                    case MainListOptions.SORT_BY_DATA_USAGE:
                        // Sort in decreasing order
                        return -mode * o1.dataUsage.compareTo(o2.dataUsage);
                    case MainListOptions.SORT_BY_OPEN_COUNT:
                        // Sort in decreasing order
                        return -mode * Integer.compare(o1.openCount, o2.openCount);
                    case MainListOptions.SORT_BY_INSTALLATION_DATE:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.firstInstallTime, o2.firstInstallTime);
                    case MainListOptions.SORT_BY_SCREEN_TIME:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.screenTime, o2.screenTime);
                    case MainListOptions.SORT_BY_LAST_USAGE_TIME:
                        // Sort in decreasing order
                        return -mode * Long.compare(o1.lastUsageTime, o2.lastUsageTime);
                    case MainListOptions.SORT_BY_TARGET_SDK:
                        // null on top
                        if (o1.targetSdk == null && o2.targetSdk == null) return 0;
                        else if (o1.targetSdk == null) return -mode;
                        else if (o2.targetSdk == null) return +mode;
                        return mode * o1.targetSdk.compareTo(o2.targetSdk);
                    case MainListOptions.SORT_BY_SHARED_ID:
                        return mode * Integer.compare(o1.uid, o2.uid);
                    case MainListOptions.SORT_BY_SHA:
                        // null on top
                        if (o1.sha == null && o2.sha == null) {
                            return 0;
                        } else if (o1.sha == null) {
                            return -mode;
                        } else if (o2.sha == null) {
                            return +mode;
                        } else {  // Both aren't null
                            int i = o1.sha.first.compareToIgnoreCase(o2.sha.first);
                            if (i == 0) {
                                return mode * o1.sha.second.compareToIgnoreCase(o2.sha.second);
                            } else return mode * i;
                        }
                    case MainListOptions.SORT_BY_BLOCKED_COMPONENTS:
                        return -mode * o1.blockedCount.compareTo(o2.blockedCount);
                    case MainListOptions.SORT_BY_FROZEN_APP:
                        return -mode * Boolean.compare(o1.isDisabled, o2.isDisabled);
                    case MainListOptions.SORT_BY_BACKUP:
                        return -mode * Boolean.compare(o1.backup != null, o2.backup != null);
                    case MainListOptions.SORT_BY_LAST_ACTION:
                        return -mode * o1.lastActionTime.compareTo(o2.lastActionTime);
                    case MainListOptions.SORT_BY_TRACKERS:
                        // Sort by *unblocked* trackers so the apps that still need
                        // attention float to the top, not the ones the user has
                        // already dealt with. Falls back to total count when
                        // unblocked counts tie so apps with more tracking SDKs
                        // overall come first within an equal-blocked group.
                        int unblocked1 = Math.max(0, o1.trackerCount
                                - (o1.trackerBlockedCount != null ? o1.trackerBlockedCount : 0));
                        int unblocked2 = Math.max(0, o2.trackerCount
                                - (o2.trackerBlockedCount != null ? o2.trackerBlockedCount : 0));
                        if (unblocked1 != unblocked2) {
                            return -mode * Integer.compare(unblocked1, unblocked2);
                        }
                        return -mode * o1.trackerCount.compareTo(o2.trackerCount);
                    case MainListOptions.SORT_BY_DANGEROUS_PERMS:
                        // Sort by *granted* dangerous permissions so the most
                        // privileged-by-actual-grant apps surface first; falls
                        // back to total declared dangerous perms so equally
                        // granted apps with broader declared surface still rank
                        // above leaner ones. Mirrors the SORT_BY_TRACKERS shape.
                        int granted1 = o1.dangerousPermGranted != null ? o1.dangerousPermGranted : 0;
                        int granted2 = o2.dangerousPermGranted != null ? o2.dangerousPermGranted : 0;
                        if (granted1 != granted2) {
                            return -mode * Integer.compare(granted1, granted2);
                        }
                        int total1 = o1.dangerousPermTotal != null ? o1.dangerousPermTotal : 0;
                        int total2 = o2.dangerousPermTotal != null ? o2.dangerousPermTotal : 0;
                        return -mode * Integer.compare(total1, total2);
                }
                return 0;
            });
        }
    }

    @WorkerThread
    private void updateInfoForUid(int uid, String action) {
        Log.d("updateInfoForUid", "Uid: %d", uid);
        String[] packages;
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) packages = getPackagesForUid(uid);
        else packages = mPackageManager.getPackagesForUid(uid);
        updateInfoForPackages(packages, action);
    }

    @WorkerThread
    private void updateInfoForPackages(@Nullable String[] packages, @NonNull String action) {
        Log.d("updateInfoForPackages", "packages: %s", Arrays.toString(packages));
        if (packages == null || packages.length == 0) return;
        boolean modified = false;
        switch (action) {
            case PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED: {
                AppDb appDb = new AppDb();
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appDb.getAllApplications(packageName));
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            case PackageChangeReceiver.ACTION_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_PACKAGE_ADDED:
                // case BatchOpsService.ACTION_BATCH_OPS_COMPLETED:
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
            case Intent.ACTION_PACKAGE_CHANGED: {
                List<App> appList = new AppDb().updateApplications(getApplication(), packages);
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appList);
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            default:
                return;
        }
        if (modified) {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertOrAddApplicationItem(@Nullable ApplicationItem item) {
        if (item == null) return false;
        synchronized (mApplicationItems) {
            if (insertApplicationItem(item)) {
                return true;
            }
            boolean inserted = mApplicationItems.add(item);
            if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                select(item);
            }
            return inserted;
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertApplicationItem(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            boolean isInserted = false;
            for (int i = 0; i < mApplicationItems.size(); ++i) {
                if (item.equals(mApplicationItems.get(i))) {
                    mApplicationItems.set(i, item);
                    isInserted = true;
                    if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                        select(item);
                    }
                }
            }
            return isInserted;
        }
    }

    private boolean deleteApplicationItem(@NonNull String packageName) {
        synchronized (mApplicationItems) {
            ListIterator<ApplicationItem> it = mApplicationItems.listIterator();
            while (it.hasNext()) {
                ApplicationItem item = it.next();
                if (item.packageName.equals(packageName)) {
                    mSelectedPackageApplicationItemMap.remove(packageName);
                    it.remove();
                    return true;
                }
            }
            return false;
        }
    }

    @WorkerThread
    @Nullable
    private ApplicationItem getNewApplicationItem(@NonNull String packageName, @NonNull List<App> apps) {
        ApplicationItem item = new ApplicationItem();
        int thisUser = UserHandleHidden.myUserId();
        for (App app : apps) {
            if (!packageName.equals(app.packageName)) {
                // Package name didn't match
                continue;
            }
            if (app.isInstalled) {
                boolean newItem = item.packageName == null || !item.isInstalled;
                if (item.packageName == null) {
                    item.packageName = app.packageName;
                }
                item.userIds = ArrayUtils.appendInt(item.userIds, app.userId);
                if (app.isEnabled) {
                    item.enabledUserIds = ArrayUtils.appendInt(item.enabledUserIds, app.userId);
                } else {
                    item.disabledUserIds = ArrayUtils.appendInt(item.disabledUserIds, app.userId);
                }
                item.isInstalled = true;
                item.isOnlyDataInstalled = false;
                item.openCount += app.openCount;
                item.screenTime += app.screenTime;
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime;
                }
                item.hasKeystore |= app.hasKeystore;
                item.usesSaf |= app.usesSaf;
                if (app.ssaid != null) {
                    item.ssaid = app.ssaid;
                }
                item.totalSize += app.codeSize + app.dataSize;
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage;
                if (!newItem && app.userId != thisUser) {
                    // This user has the highest priority
                    continue;
                }
            } else {
                // App not installed but may be installed in other profiles
                item.uninstalledUserIds = ArrayUtils.appendInt(item.uninstalledUserIds, app.userId);
                if (item.packageName != null) {
                    // Item exists, use the previous status
                    continue;
                } else {
                    item.packageName = app.packageName;
                    item.isInstalled = false;
                    item.isOnlyDataInstalled = app.isOnlyDataInstalled;
                    item.hasKeystore |= app.hasKeystore;
                }
            }
            item.flags = app.flags;
            item.uid = app.uid;
            item.debuggable = app.isDebuggable();
            item.isUser = !app.isSystemApp();
            item.isDisabled = !app.isEnabled;
            item.label = app.packageLabel;
            item.targetSdk = app.sdk;
            item.versionName = app.versionName;
            item.versionCode = app.versionCode;
            item.sharedUserId = app.sharedUserId;
            item.sha = new Pair<>(app.certName, app.certAlgo);
            item.firstInstallTime = app.firstInstallTime;
            item.lastUpdateTime = app.lastUpdateTime;
            item.hasActivities = app.hasActivities;
            item.hasSplits = app.hasSplits;
            item.blockedCount = app.rulesCount;
            item.trackerCount = app.trackerCount;
            item.trackerBlockedCount = app.trackerBlockedCount;
            item.dangerousPermTotal = app.dangerousPermTotal;
            item.dangerousPermGranted = app.dangerousPermGranted;
            item.lastActionTime = app.lastActionTime;
            if (item.backup == null) {
                item.backup = BackupUtils.getLatestBackupMetadataFromDbNoLockValidate(packageName);
            }
            item.generateOtherInfo();
        }
        if (item.packageName == null) {
            return null;
        }
        item.setUserTags(new AppTagStore(getApplication()).getTags(item.packageName));
        item.setUserNote(new AppNoteStore(getApplication()).getNote(item.packageName));
        return item;
    }

    @GuardedBy("applicationItems")
    @NonNull
    private String[] getPackagesForUid(int uid) {
        synchronized (mApplicationItems) {
            List<String> packages = new LinkedList<>();
            for (ApplicationItem item : mApplicationItems) {
                if (item.uid == uid) packages.add(item.packageName);
            }
            return packages.toArray(new String[0]);
        }
    }

    @Override
    protected void onCleared() {
        if (mPackageObserver != null) getApplication().unregisterReceiver(mPackageObserver);
        executor.shutdownNow();
        super.onCleared();
    }

    @Nullable
    @VisibleForTesting
    Future<?> getActiveTaskForTesting() {
        return mFilterResult;
    }

    public static final class AppListImportStatus {
        public final boolean success;
        public final int selectedCount;

        private AppListImportStatus(boolean success, int selectedCount) {
            this.success = success;
            this.selectedCount = selectedCount;
        }

        @NonNull
        public static AppListImportStatus success(int selectedCount) {
            return new AppListImportStatus(true, selectedCount);
        }

        @NonNull
        public static AppListImportStatus failure() {
            return new AppListImportStatus(false, 0);
        }
    }

    public enum AppListLoadState {
        LOADING,
        LOADED,
        FAILED
    }

    public static final class AppListLoadStatus {
        @NonNull
        public final AppListLoadState state;
        public final int staleItemCount;
        @Nullable
        public final String errorClass;
        @Nullable
        public final String errorMessage;

        private AppListLoadStatus(@NonNull AppListLoadState state, int staleItemCount,
                                  @Nullable String errorClass, @Nullable String errorMessage) {
            this.state = state;
            this.staleItemCount = Math.max(0, staleItemCount);
            this.errorClass = errorClass;
            this.errorMessage = errorMessage;
        }

        @NonNull
        public static AppListLoadStatus loading(int staleItemCount) {
            return new AppListLoadStatus(AppListLoadState.LOADING, staleItemCount, null, null);
        }

        @NonNull
        public static AppListLoadStatus loaded(int itemCount) {
            return new AppListLoadStatus(AppListLoadState.LOADED, itemCount, null, null);
        }

        @NonNull
        public static AppListLoadStatus failed(@NonNull Throwable throwable, int staleItemCount) {
            String errorClass = throwable.getClass().getSimpleName();
            if (TextUtils.isEmpty(errorClass)) {
                errorClass = throwable.getClass().getName();
            }
            return new AppListLoadStatus(AppListLoadState.FAILED, staleItemCount,
                    errorClass, throwable.getMessage());
        }

        public boolean isFailed() {
            return state == AppListLoadState.FAILED;
        }

        public boolean hasStaleItems() {
            return staleItemCount > 0;
        }

        @NonNull
        public String getErrorSummary() {
            if (TextUtils.isEmpty(errorClass)) {
                return "Unknown error";
            }
            if (TextUtils.isEmpty(errorMessage)) {
                return errorClass;
            }
            return errorClass + ": " + errorMessage;
        }
    }

    public static class PackageIntentReceiver extends PackageChangeReceiver {
        private final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            super(model.getApplication());
            mModel = model;
        }

        @Override
        @WorkerThread
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            mModel.cancelIfRunning();
            if (uid != null) {
                mModel.updateInfoForUid(uid, intent.getAction());
            } else if (packages != null) {
                mModel.updateInfoForPackages(packages, intent.getAction());
            } else {
                mModel.loadApplicationItems();
            }
        }
    }
}
