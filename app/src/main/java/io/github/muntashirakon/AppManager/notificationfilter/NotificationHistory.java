// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.Context;
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

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Writes down what the filter did, one entry at a time and off the listener's thread. Reads, clears and undoes go
 * through the same worker, so a clear always lands after every entry that came before it.
 */
public final class NotificationHistory {
    private static final String TAG = "NotificationHistory";
    /** Whichever limit is hit first applies, together with the retention the user picked. */
    public static final int MAX_ENTRIES = 5_000;

    /** How long entries are kept, read each time so a new choice applies to the next write. */
    @VisibleForTesting
    interface Retention {
        long millis();
    }

    @Nullable
    private static volatile NotificationHistory sInstance;

    @NonNull
    public static NotificationHistory getInstance(@NonNull Context context) {
        NotificationHistory instance = sInstance;
        if (instance == null) {
            synchronized (NotificationHistory.class) {
                instance = sInstance;
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>());
                    worker.allowCoreThreadTimeOut(true);
                    NotificationFilterSettings settings = NotificationFilterSettings.getInstance(appContext);
                    instance = new NotificationHistory(NotificationHistoryDb.open(appContext).dao(), worker,
                            settings::getRetentionMillis, System::currentTimeMillis);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    static void setInstanceForTesting(@Nullable NotificationHistory instance) {
        sInstance = instance;
    }

    @NonNull
    private final NotificationHistoryDao mDao;
    @NonNull
    private final Executor mWorker;
    @NonNull
    private final Retention mRetention;
    @NonNull
    private final LongSupplier mClock;
    private final MutableLiveData<Long> mChanges = new MutableLiveData<>();

    @VisibleForTesting
    NotificationHistory(@NonNull NotificationHistoryDao dao, @NonNull Executor worker,
                        @NonNull Retention retention, @NonNull LongSupplier clock) {
        mDao = dao;
        mWorker = worker;
        mRetention = retention;
        mClock = clock;
    }

    /** Changes each time the saved entries do. */
    @NonNull
    public LiveData<Long> getChanges() {
        return mChanges;
    }

    @AnyThread
    public void record(@NonNull NotificationHistoryEntry entry) {
        mWorker.execute(() -> {
            try {
                mDao.insertAndPrune(entry, cutoffMillis(), MAX_ENTRIES);
                announceChange();
            } catch (SQLException e) {
                // Filtering goes on without the history, and the listener has no screen to say so on.
                Log.w(TAG, "Could not save a notification.", e);
            }
        });
    }

    /** Drops what's past the retention now, after the user picked a shorter one. */
    @AnyThread
    public void prune() {
        mWorker.execute(() -> {
            try {
                mDao.prune(cutoffMillis(), MAX_ENTRIES);
                announceChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not trim the notification history.", e);
            }
        });
    }

    /**
     * Removes every entry, including any still waiting to be saved when this was called.
     *
     * @param onDone Gets what was removed, newest first, or {@code null} if it failed. Called on the worker.
     */
    @AnyThread
    public void clear(@NonNull Consumer<List<NotificationHistoryEntry>> onDone) {
        mWorker.execute(() -> {
            List<NotificationHistoryEntry> removed;
            try {
                removed = mDao.takeAll();
                announceChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not clear the notification history.", e);
                removed = null;
            }
            onDone.accept(removed);
        });
    }

    /** Puts back what {@link #clear(Consumer)} removed. The limits still apply, and newer entries are kept. */
    @AnyThread
    public void restore(@NonNull List<NotificationHistoryEntry> entries) {
        mWorker.execute(() -> {
            try {
                mDao.restoreAndPrune(entries, cutoffMillis(), MAX_ENTRIES);
                announceChange();
            } catch (SQLException e) {
                Log.w(TAG, "Could not restore the notification history.", e);
            }
        });
    }

    /** @return Every entry still within the retention, newest first. */
    @WorkerThread
    @NonNull
    public List<NotificationHistoryEntry> readAll() {
        // A screen opened after a quiet week shouldn't show what the retention already let go.
        mDao.prune(cutoffMillis(), MAX_ENTRIES);
        return mDao.getAll();
    }

    private long cutoffMillis() {
        return mClock.getAsLong() - mRetention.millis();
    }

    private void announceChange() {
        mChanges.postValue(System.nanoTime());
    }
}
