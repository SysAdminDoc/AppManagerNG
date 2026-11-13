// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.SQLException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Takes toasts from the accessibility service and writes them down one at a time, in the order they came, off the
 * thread that delivers accessibility events. The screen reads, clears and undoes through here as well, on the same
 * worker, so a clear always lands after every toast that arrived before it.
 */
public final class ToastHistory {
    private static final String TAG = "ToastHistory";
    /** Whichever limit is hit first applies. */
    public static final int MAX_ENTRIES = 2_000;
    public static final long MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(30);
    private static final int MAX_LABEL_LENGTH = 200;

    /** Names the app that showed a toast. Called on the worker, as it asks the package manager. */
    @VisibleForTesting
    interface LabelResolver {
        @NonNull
        String labelOf(@NonNull String packageName);
    }

    @Nullable
    private static volatile ToastHistory sInstance;

    @NonNull
    public static ToastHistory getInstance(@NonNull Context context) {
        ToastHistory instance = sInstance;
        if (instance == null) {
            synchronized (ToastHistory.class) {
                instance = sInstance;
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>());
                    worker.allowCoreThreadTimeOut(true);
                    ToastHistory created = new ToastHistory(ToastHistoryDb.open(appContext).dao(), worker,
                            packageName -> resolveLabel(appContext.getPackageManager(), packageName),
                            System::currentTimeMillis);
                    created.setAfterChange(() -> ToastShadeNotification.update(appContext, created));
                    instance = created;
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    static void setInstanceForTesting(@Nullable ToastHistory instance) {
        sInstance = instance;
    }

    @NonNull
    private final ToastHistoryDao mDao;
    @NonNull
    private final Executor mWorker;
    @NonNull
    private final LabelResolver mLabels;
    @NonNull
    private final LongSupplier mClock;
    private final MutableLiveData<Long> mChanges = new MutableLiveData<>();
    /** Runs on the worker after every change, to keep the notification in the shade current. */
    @Nullable
    private volatile Runnable mAfterChange;
    /** Touched on the worker only. Tells the user once per run of failures instead of once per toast. */
    private boolean mSaveFailing;

    @VisibleForTesting
    ToastHistory(@NonNull ToastHistoryDao dao, @NonNull Executor worker, @NonNull LabelResolver labels,
                 @NonNull LongSupplier clock) {
        mDao = dao;
        mWorker = worker;
        mLabels = labels;
        mClock = clock;
    }

    @VisibleForTesting
    void setAfterChange(@Nullable Runnable afterChange) {
        mAfterChange = afterChange;
    }

    /** Brings the notification in the shade in line with the switches, after one of them changed. */
    @AnyThread
    public void refreshShade() {
        mWorker.execute(this::afterChange);
    }

    /** Changes each time the saved toasts do. */
    @NonNull
    public LiveData<Long> getChanges() {
        return mChanges;
    }

    @AnyThread
    public void record(long timestampMillis, @NonNull String packageName, @NonNull String text) {
        mWorker.execute(() -> {
            try {
                String label = mLabels.labelOf(packageName);
                mDao.insertAndPrune(new ToastEntry(timestampMillis, packageName, cutLabel(label),
                        withoutAppName(text, label)), cutoffMillis(), MAX_ENTRIES);
                mSaveFailing = false;
                announceChange();
                afterChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not save a toast.", e);
                if (!mSaveFailing) {
                    mSaveFailing = true;
                    // This app's own toasts are never recorded, so this can't feed itself.
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.toast_history_save_failed));
                }
            }
        });
    }

    /**
     * Removes every toast, including any still waiting to be saved when this was called.
     *
     * @param onDone Gets what was removed, newest first, or {@code null} if it failed. Called on the worker.
     */
    @AnyThread
    public void clear(@NonNull Consumer<List<ToastEntry>> onDone) {
        mWorker.execute(() -> {
            List<ToastEntry> removed;
            try {
                removed = mDao.takeAll();
                announceChange();
                afterChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not clear the toast history.", e);
                removed = null;
            }
            onDone.accept(removed);
        });
    }

    /** Puts back what {@link #clear(Consumer)} removed. The limits still apply, and newer toasts are kept. */
    @AnyThread
    public void restore(@NonNull List<ToastEntry> entries) {
        mWorker.execute(() -> {
            try {
                mDao.restoreAndPrune(entries, cutoffMillis(), MAX_ENTRIES);
                announceChange();
                afterChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not restore the toast history.", e);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.toast_history_save_failed));
            }
        });
    }

    /** @return Every saved toast, newest first. */
    @WorkerThread
    @NonNull
    public List<ToastEntry> readAll() {
        return mDao.getAll();
    }

    /** @return Up to that many of the newest toasts, newest first. */
    @WorkerThread
    @NonNull
    public List<ToastEntry> readNewest(int limit) {
        return mDao.getNewest(limit);
    }

    /** @return One app's toasts, newest first. */
    @WorkerThread
    @NonNull
    public List<ToastEntry> readByPackage(@NonNull String packageName) {
        return mDao.getByPackage(packageName);
    }

    private long cutoffMillis() {
        return mClock.getAsLong() - MAX_AGE_MILLIS;
    }

    private void announceChange() {
        mChanges.postValue(System.nanoTime());
    }

    private void afterChange() {
        Runnable afterChange = mAfterChange;
        if (afterChange != null) {
            afterChange.run();
        }
    }

    @NonNull
    private static String cutLabel(@NonNull String label) {
        return label.length() > MAX_LABEL_LENGTH ? label.substring(0, MAX_LABEL_LENGTH) : label;
    }

    /**
     * From Android 12 SystemUI draws the app's icon and name beside a toast's message, and the name arrives as the
     * event's last line. The entry already names the app, so the line only gets in the way.
     */
    @NonNull
    private static String withoutAppName(@NonNull String text, @NonNull String label) {
        String suffix = "\n" + label;
        if (!label.isEmpty() && text.length() > suffix.length() && text.endsWith(suffix)) {
            return text.substring(0, text.length() - suffix.length()).trim();
        }
        return text;
    }

    @NonNull
    private static String resolveLabel(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            return PackageUtils.getPackageLabel(pm, packageName);
        } catch (RuntimeException e) {
            // The package name still says which app it was, and the toast shouldn't be lost over a label.
            Log.w(TAG, "Could not look up the name of %s.", e, packageName);
            return packageName;
        }
    }
}
