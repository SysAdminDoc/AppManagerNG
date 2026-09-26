// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Waits for a secure device to lock after its screen goes off, then runs a callback. The owner must
 * {@link #close()} it when it stops: after that, nothing already queued can reach the callback.
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

    private final Context mContext;
    private final Object mLock = new Object();
    // A daemon thread, so a checker that was never closed cannot keep a process alive either.
    private final Timer mTimer = new Timer(TIMER_THREAD_NAME, true);
    @Nullable
    private final Runnable mRunnable;

    @GuardedBy("mLock")
    @Nullable
    private CheckLockTask mCheckLockTask;
    @GuardedBy("mLock")
    private boolean mClosed;

    public ScreenLockChecker(@NonNull Context context, @Nullable Runnable runnable) {
        mContext = context.getApplicationContext();
        mRunnable = runnable;
    }

    public void checkLock() {
        checkLock(-1);
    }

    /**
     * Cancels the pending check and stops the timer thread. Later calls to {@link #checkLock()} and
     * any task that was already running do nothing. Safe to call more than once and from any thread.
     */
    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            if (mCheckLockTask != null) {
                mCheckLockTask.cancel();
                mCheckLockTask = null;
            }
            mTimer.cancel();
            mTimer.purge();
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
        delayIndex = getSafeCheckLockDelay(delayIndex);
        Log.i(TAG, "checkLock: isProtected=%b, isLocked=%b, isInteractive=%b, delay=%d",
                isProtected, isLocked, isInteractive, sCheckLockDelays[delayIndex]);

        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            if (mCheckLockTask != null) {
                Log.i(TAG, "checkLock: cancelling CheckLockTask[%x]", System.identityHashCode(mCheckLockTask));
                mCheckLockTask.cancel();
                mCheckLockTask = null;
            }
            if (isProtected && !isLocked && !isInteractive) {
                mCheckLockTask = new CheckLockTask(delayIndex);
                Log.i(TAG, "checkLock: scheduling CheckLockTask[%x] for %d ms", System.identityHashCode(mCheckLockTask), sCheckLockDelays[delayIndex]);
                mTimer.schedule(mCheckLockTask, sCheckLockDelays[delayIndex]);
                return;
            }
        }
        Log.d(TAG, "checkLock: no need to schedule CheckLockTask");
        // The callback runs outside the lock so close() never waits for it, which is why the
        // closed state is checked once more right before it.
        if (isProtected && isLocked && mRunnable != null && !isClosed()) {
            mRunnable.run();
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

    private class CheckLockTask extends TimerTask {
        final int delayIndex;

        CheckLockTask(final int delayIndex) {
            this.delayIndex = delayIndex;
        }

        @Override
        public void run() {
            Log.i(TAG, "CLT.run [%x]: redirect intent to LockMonitor", System.identityHashCode(this));
            checkLock(getSafeCheckLockDelay(delayIndex + 1));
        }
    }
}
