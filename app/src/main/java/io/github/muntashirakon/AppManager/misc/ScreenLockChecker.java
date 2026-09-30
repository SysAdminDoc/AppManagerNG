// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Waits for a secure device to lock after its screen goes off, then runs a callback. The owner must
 * {@link #close()} it when it stops: once that returns, no callback is running and none can start.
 */
public final class ScreenLockChecker implements Closeable {
    public static final String TAG = ScreenLockChecker.class.getSimpleName();
    /** Name of the timer thread; one exists per open checker. */
    public static final String TIMER_THREAD_NAME = "ScreenLockChecker";

    private static final int SECOND = 1000;
    private static final int MINUTE = 60 * SECOND;
    // This tracks the deltas between the actual options of 5s, 15s, 30s, 1m, 2m, 5m, 10m
    // It also includes an initial offset and some extra times (for safety)
    private static final int[] sCheckLockDelays = new int[]{SECOND, 5 * SECOND, 10 * SECOND, 20 * SECOND, 30 * SECOND,
            MINUTE, 3 * MINUTE, 5 * MINUTE, 10 * MINUTE, 30 * MINUTE};

    /** Runs the delayed checks. The checker uses a daemon {@link Timer}; tests drive a clock by hand. */
    @VisibleForTesting
    interface Scheduler {
        /**
         * Runs {@code task} once {@code delayMillis} have passed.
         *
         * @return an action that cancels the task if it has not started
         */
        @NonNull
        Runnable schedule(@NonNull Runnable task, long delayMillis);

        /** Cancels every task and releases the scheduler's thread. */
        void shutdown();
    }

    private static final class TimerScheduler implements Scheduler {
        // A daemon thread, so a checker that was never closed cannot keep a process alive either.
        private final Timer mTimer = new Timer(TIMER_THREAD_NAME, true);

        @NonNull
        @Override
        public Runnable schedule(@NonNull Runnable task, long delayMillis) {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    task.run();
                }
            };
            mTimer.schedule(timerTask, delayMillis);
            return timerTask::cancel;
        }

        @Override
        public void shutdown() {
            mTimer.cancel();
            mTimer.purge();
        }
    }

    private final Context mContext;
    private final Object mLock = new Object();
    private final Scheduler mScheduler;
    @Nullable
    private final Runnable mRunnable;

    @GuardedBy("mLock")
    @Nullable
    private Runnable mCancelPendingCheck;
    @GuardedBy("mLock")
    private boolean mClosed;
    /** Runs between the first closed check and taking the lock, so a test can land close() there. */
    @VisibleForTesting
    @Nullable
    Runnable mBeforeLockForTest;

    public ScreenLockChecker(@NonNull Context context, @Nullable Runnable runnable) {
        this(context, runnable, new TimerScheduler());
    }

    @VisibleForTesting
    ScreenLockChecker(@NonNull Context context, @Nullable Runnable runnable, @NonNull Scheduler scheduler) {
        mContext = context.getApplicationContext();
        mRunnable = runnable;
        mScheduler = scheduler;
    }

    public void checkLock() {
        checkLock(-1);
    }

    /**
     * Cancels the pending check and stops the timer thread. A callback that is already running is
     * waited for; after that, later calls to {@link #checkLock()} and any check still in flight do
     * nothing. Safe to call more than once and from any thread, the callback's included.
     */
    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            if (mCancelPendingCheck != null) {
                mCancelPendingCheck.run();
                mCancelPendingCheck = null;
            }
            mScheduler.shutdown();
        }
    }

    public boolean isClosed() {
        synchronized (mLock) {
            return mClosed;
        }
    }

    @WorkerThread
    private void checkLock(int delayIndex) {
        if (isClosed()) {
            return;
        }
        KeyguardManager keyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        final boolean isProtected = keyguardManager.isKeyguardSecure();
        final boolean isLocked = keyguardManager.isKeyguardLocked();
        final boolean isInteractive = powerManager.isInteractive();
        final int safeDelayIndex = getSafeCheckLockDelay(delayIndex);
        Log.i(TAG, "checkLock: isProtected=%b, isLocked=%b, isInteractive=%b, delay=%d",
                isProtected, isLocked, isInteractive, sCheckLockDelays[safeDelayIndex]);

        Runnable beforeLock = mBeforeLockForTest;
        if (beforeLock != null) {
            beforeLock.run();
        }
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            if (mCancelPendingCheck != null) {
                Log.i(TAG, "checkLock: cancelling the pending check");
                mCancelPendingCheck.run();
                mCancelPendingCheck = null;
            }
            if (isProtected && !isLocked && !isInteractive) {
                Log.i(TAG, "checkLock: checking again in %d ms", sCheckLockDelays[safeDelayIndex]);
                mCancelPendingCheck = mScheduler.schedule(() -> checkLock(getSafeCheckLockDelay(safeDelayIndex + 1)),
                        sCheckLockDelays[safeDelayIndex]);
                return;
            }
            Log.d(TAG, "checkLock: no need to check again");
            if (isProtected && isLocked && mRunnable != null) {
                // The lock is held for the callback, so close() cannot slip in between the closed
                // check and the call: it waits for a callback that has begun and stops any other.
                mRunnable.run();
            }
        }
    }

    private static int getSafeCheckLockDelay(final int delayIndex) {
        final int safeDelayIndex;
        if (delayIndex >= sCheckLockDelays.length) {
            safeDelayIndex = sCheckLockDelays.length - 1;
        } else safeDelayIndex = Math.max(delayIndex, 0);
        Log.v(TAG, "getSafeCheckLockDelay(%d) returns %d", delayIndex, safeDelayIndex);
        return safeDelayIndex;
    }
}
