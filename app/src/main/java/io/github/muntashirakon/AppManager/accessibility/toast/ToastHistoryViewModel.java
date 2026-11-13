// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.TextQuery;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ToastHistoryViewModel extends AndroidViewModel {
    private static final String TAG = "ToastHistoryViewModel";

    public static final class State {
        @NonNull
        public final List<ToastEntry> visible;
        @NonNull
        public final List<ToastHistoryFilter.AppChoice> apps;
        /** For app icons. An app that's gone since the toast has no entry and gets the default icon. */
        @NonNull
        public final Map<String, ApplicationInfo> appInfos;
        @Nullable
        public final String filterPackage;
        /** The search that was applied. One that isn't valid hides every row, and the screen says why. */
        @NonNull
        public final TextQuery query;
        public final int total;

        State(@NonNull List<ToastEntry> visible, @NonNull List<ToastHistoryFilter.AppChoice> apps,
              @NonNull Map<String, ApplicationInfo> appInfos, @Nullable String filterPackage,
              @NonNull TextQuery query, int total) {
            this.visible = visible;
            this.apps = apps;
            this.appInfos = appInfos;
            this.filterPackage = filterPackage;
            this.query = query;
            this.total = total;
        }
    }

    /** What a clear took away, so it can be put back. The live value goes back to {@code null} once handled. */
    public static final class Cleared {
        /** Newest first, or {@code null} if the clear failed. */
        @Nullable
        public final List<ToastEntry> removed;

        Cleared(@Nullable List<ToastEntry> removed) {
            this.removed = removed;
        }

        public boolean failed() {
            return removed == null;
        }
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final MutableLiveData<Cleared> mCleared = new MutableLiveData<>();
    // One thread, so a slow read can't land after a newer one and put old rows back on screen.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @NonNull
    private final ToastHistory mHistory;
    @Nullable
    private volatile String mFilterPackage;
    @NonNull
    private volatile TextQuery mQuery = TextQuery.empty();
    // The last read, before the search, so typing re-filters it instead of reading the database again. Worker only.
    @Nullable
    private List<ToastEntry> mUnsearched;
    @Nullable
    private State mLast;

    public ToastHistoryViewModel(@NonNull Application application) {
        super(application);
        mHistory = ToastHistory.getInstance(application);
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

    /** Marks the last clear as handled, so it isn't offered again when the screen is recreated. */
    public void consumeCleared() {
        mCleared.setValue(null);
    }

    public void load() {
        mExecutor.execute(this::loadNow);
    }

    public void setFilterPackage(@Nullable String packageName) {
        mFilterPackage = packageName;
        load();
    }

    /** Narrows the list to toasts whose text, app name or package matches, on top of the app filter. */
    public void setQuery(@Nullable String query, boolean regex) {
        mQuery = TextQuery.of(query, regex);
        mExecutor.execute(this::searchNow);
    }

    @NonNull
    public TextQuery getQuery() {
        return mQuery;
    }

    public void clear() {
        mHistory.clear(removed -> mCleared.postValue(new Cleared(removed)));
    }

    public void undoClear(@NonNull List<ToastEntry> removed) {
        mHistory.restore(removed);
    }

    /** @param entries What the screen is showing. */
    public void copy(@NonNull List<ToastEntry> entries) {
        if (entries.isEmpty()) {
            UIUtils.displayShortToast(R.string.toast_history_nothing_to_copy);
            return;
        }
        mExecutor.execute(() -> {
            @StringRes int result = R.string.copied_to_clipboard;
            try {
                Application app = getApplication();
                ClipboardUtils.copyToClipboard(app, app.getString(R.string.toast_history_title),
                        ToastEntry.toCopyText(entries));
            } catch (RuntimeException e) {
                // A long history can be more than the clipboard's binder call will carry.
                Log.w(TAG, "Could not copy the toasts.", e);
                result = R.string.toast_history_copy_failed;
            }
            int message = result;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(message));
        });
    }

    @WorkerThread
    private void loadNow() {
        List<ToastEntry> all = mHistory.readAll();
        List<ToastHistoryFilter.AppChoice> apps = ToastHistoryFilter.apps(all);
        String filterPackage = ToastHistoryFilter.keepIfPresent(mFilterPackage, apps);
        mFilterPackage = filterPackage;
        Map<String, ApplicationInfo> appInfos = new HashMap<>();
        PackageManager pm = getApplication().getPackageManager();
        for (ToastHistoryFilter.AppChoice app : apps) {
            ApplicationInfo info = getApplicationInfo(pm, app.packageName);
            if (info != null) {
                appInfos.put(app.packageName, info);
            }
        }
        List<ToastEntry> visible = filterPackage != null ? mHistory.readByPackage(filterPackage) : all;
        TextQuery query = mQuery;
        mUnsearched = visible;
        mLast = new State(ToastHistoryFilter.search(visible, query), apps, appInfos, filterPackage, query, all.size());
        mState.postValue(mLast);
    }

    @WorkerThread
    private void searchNow() {
        State last = mLast;
        List<ToastEntry> unsearched = mUnsearched;
        if (last == null || unsearched == null) {
            loadNow();
            return;
        }
        TextQuery query = mQuery;
        mLast = new State(ToastHistoryFilter.search(unsearched, query), last.apps, last.appInfos, last.filterPackage,
                query, last.total);
        mState.postValue(mLast);
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
