// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.toast.ToastEntry;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class TopAppHistoryViewModel extends AndroidViewModel {
    private static final String TAG = "TopAppHistoryViewModel";

    public static final class State {
        /** Newest first. */
        @NonNull
        public final List<TopAppTransition> entries;
        /** Every app named in {@link #entries}. An app that's gone since is named by its package. */
        @NonNull
        public final Map<String, CharSequence> labels;
        /** For app icons. An app that's gone since has no entry and gets the default icon. */
        @NonNull
        public final Map<String, ApplicationInfo> appInfos;

        State(@NonNull List<TopAppTransition> entries, @NonNull Map<String, CharSequence> labels,
              @NonNull Map<String, ApplicationInfo> appInfos) {
            this.entries = entries;
            this.labels = labels;
            this.appInfos = appInfos;
        }
    }

    /** What a clear took away, so it can be put back. The live value goes back to {@code null} once handled. */
    public static final class Cleared {
        /** Newest first, or {@code null} if the clear failed. */
        @Nullable
        public final List<TopAppTransition> removed;

        Cleared(@Nullable List<TopAppTransition> removed) {
            this.removed = removed;
        }
    }

    /** One plain sentence for a row and for copying. */
    @NonNull
    public static String describe(@NonNull Context context, @NonNull TopAppTransition transition,
                                  @NonNull Map<String, CharSequence> labels) {
        CharSequence app = labelOf(labels, transition.packageName);
        CharSequence other = transition.other != null ? labelOf(labels, transition.other) : null;
        switch (transition.kind) {
            case OVERLAY_SHOWN:
                return other != null ? context.getString(R.string.top_app_history_overlay_over, app, other)
                        : context.getString(R.string.top_app_history_overlay, app);
            case OVERLAY_GONE:
                return context.getString(R.string.top_app_history_overlay_gone, app);
            case APP:
            default:
                return other != null ? context.getString(R.string.top_app_history_switched, other, app)
                        : context.getString(R.string.top_app_history_came_up, app);
        }
    }

    /** The packages behind a row, in the order its sentence names them. */
    @NonNull
    public static String packagesOf(@NonNull TopAppTransition transition) {
        if (transition.other == null) {
            return transition.packageName;
        }
        return transition.kind == TopAppTransition.Kind.APP ? transition.other + " → " + transition.packageName
                : transition.packageName + " / " + transition.other;
    }

    @NonNull
    static String toCopyText(@NonNull Context context, @NonNull List<TopAppTransition> entries,
                             @NonNull Map<String, CharSequence> labels) {
        DateFormat timeFormat = ToastEntry.newTimeFormat();
        StringBuilder text = new StringBuilder();
        for (TopAppTransition entry : entries) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(timeFormat.format(new Date(entry.timestampMillis))).append("  ")
                    .append(describe(context, entry, labels)).append("  (").append(packagesOf(entry)).append(')');
        }
        return text.toString();
    }

    @NonNull
    private static CharSequence labelOf(@NonNull Map<String, CharSequence> labels, @NonNull String packageName) {
        CharSequence label = labels.get(packageName);
        return label != null ? label : packageName;
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final MutableLiveData<Cleared> mCleared = new MutableLiveData<>();
    // One thread, so a slow load can't land after a newer one and put old rows back on screen.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @NonNull
    private final TopAppHistory mHistory;

    public TopAppHistoryViewModel(@NonNull Application application) {
        super(application);
        mHistory = TopAppHistory.getInstance(application);
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
        // Labels are looked up here and not on the history's thread, which the bubble is writing on.
        mHistory.read(entries -> mExecutor.execute(() -> resolve(entries)));
    }

    public void clear() {
        mHistory.clear(removed -> mCleared.postValue(new Cleared(removed)));
    }

    public void undoClear(@NonNull List<TopAppTransition> removed) {
        mHistory.restore(removed);
    }

    /** @param state What the screen is showing. */
    public void copy(@Nullable State state) {
        if (state == null || state.entries.isEmpty()) {
            UIUtils.displayShortToast(R.string.top_app_history_nothing_to_copy);
            return;
        }
        copy(state.entries, state.labels);
    }

    public void copy(@NonNull List<TopAppTransition> entries, @NonNull Map<String, CharSequence> labels) {
        mExecutor.execute(() -> {
            @StringRes int result = R.string.copied_to_clipboard;
            try {
                Application app = getApplication();
                ClipboardUtils.copyToClipboard(app, app.getString(R.string.top_app_history_title),
                        toCopyText(app, entries, labels));
            } catch (RuntimeException e) {
                // A long history can be more than the clipboard's binder call will carry.
                Log.w(TAG, "Could not copy the top app history.", e);
                result = R.string.top_app_history_copy_failed;
            }
            int message = result;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(message));
        });
    }

    @WorkerThread
    private void resolve(@NonNull List<TopAppTransition> entries) {
        PackageManager pm = getApplication().getPackageManager();
        Map<String, CharSequence> labels = new HashMap<>();
        Map<String, ApplicationInfo> appInfos = new HashMap<>();
        for (TopAppTransition entry : entries) {
            resolve(pm, entry.packageName, labels, appInfos);
            if (entry.other != null) {
                resolve(pm, entry.other, labels, appInfos);
            }
        }
        mState.postValue(new State(Collections.unmodifiableList(entries), labels, appInfos));
    }

    private static void resolve(@NonNull PackageManager pm, @NonNull String packageName,
                                @NonNull Map<String, CharSequence> labels,
                                @NonNull Map<String, ApplicationInfo> appInfos) {
        if (labels.containsKey(packageName)) {
            return;
        }
        ApplicationInfo info = getApplicationInfo(pm, packageName);
        CharSequence label = null;
        if (info != null) {
            appInfos.put(packageName, info);
            try {
                label = info.loadLabel(pm);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not load the label of %s.", e, packageName);
            }
        }
        labels.put(packageName, label != null ? label.toString() : packageName);
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
