// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandleHidden;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Grants READ_LOGS to AppManagerNG only while none of its processes is doing anything.
 * <p>
 * platform.xml gives READ_LOGS the log groups, and Android kills an app whose groups change
 * ("permission grant or revoke changed gids"). {@link SelfPermissions#init()} used to grant it
 * in the middle of a mode switch, so the first switch to a privileged mode closed Settings
 * before the mode was saved, and AppManagerNG silently stayed on no-root. A cached process loses
 * nothing to that kill: Android restores the task from its saved state.
 */
public final class DeferredLogAccess implements Application.ActivityLifecycleCallbacks {
    public static final String TAG = DeferredLogAccess.class.getSimpleName();

    /** Lets a configuration change start the next activity before this counts as idle. */
    @VisibleForTesting
    static final long SETTLE_MILLIS = 2000;
    // RunningAppProcessInfo.IMPORTANCE_CACHED (API 26) has the same value as the API 1
    // IMPORTANCE_BACKGROUND, and Android reports it for any process state at or below HOME.
    @VisibleForTesting
    static final int IMPORTANCE_CACHED = 400;

    private static final AtomicBoolean sOwed = new AtomicBoolean(false);
    @Nullable
    private static volatile DeferredLogAccess sInstance;

    private int mStartedActivities;
    private long mLastStopUptime;
    @VisibleForTesting
    Runnable mGrant = () -> ThreadUtils.getBackgroundThreadExecutor().execute(DeferredLogAccess::grantIfIdle);

    @MainThread
    public static void register(@NonNull Application application) {
        DeferredLogAccess callbacks = new DeferredLogAccess();
        sInstance = callbacks;
        application.registerActivityLifecycleCallbacks(callbacks);
    }

    /**
     * Remembers that READ_LOGS still has to be granted. It's granted the next time every process
     * of AppManagerNG is cached and no mode switch is running.
     */
    public static void owe() {
        sOwed.set(true);
        ThreadUtils.postOnMainThread(() -> {
            DeferredLogAccess instance = sInstance;
            if (instance != null) {
                instance.settleIfNothingStarted();
            }
        });
    }

    @VisibleForTesting
    static boolean isOwed() {
        return sOwed.get();
    }

    @VisibleForTesting
    static void setOwed(boolean owed) {
        sOwed.set(owed);
    }

    @VisibleForTesting
    static boolean shouldGrantNow(boolean owed, int importance, boolean modeApplying) {
        return owed && !modeApplying && importance >= IMPORTANCE_CACHED;
    }

    /**
     * The importance of the busiest process of this uid in the list, or {@link Integer#MAX_VALUE}
     * for none. Every process of the app shares its uid, so the kill takes the audio player with
     * it. Android 5.0 lists other apps' processes too, and those don't count.
     */
    @VisibleForTesting
    static int busiestImportance(@Nullable List<ActivityManager.RunningAppProcessInfo> processes, int uid) {
        int busiest = Integer.MAX_VALUE;
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.uid == uid) {
                    busiest = Math.min(busiest, process.importance);
                }
            }
        }
        return busiest;
    }

    @WorkerThread
    static int currentImportance() {
        ActivityManager.RunningAppProcessInfo self = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(self);
        int importance = self.importance;
        ActivityManager am = (ActivityManager) ContextUtils.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            importance = Math.min(importance, busiestImportance(am.getRunningAppProcesses(), Process.myUid()));
        }
        return importance;
    }

    @WorkerThread
    static void grantIfIdle() {
        grantIfIdle(DeferredLogAccess::currentImportance);
    }

    /**
     * @param importance read last, right before the grant, so a launch has the least time to land
     *                   between the check and the kill
     * @return {@code true} if READ_LOGS was granted
     */
    @VisibleForTesting
    @WorkerThread
    static boolean grantIfIdle(@NonNull IntSupplier importance) {
        if (SelfPermissions.checkSelfPermission(Manifest.permission.READ_LOGS)
                || !SelfPermissions.canModifyPermissions()) {
            // Held already, or the privileged mode is gone. The next mode init owes it again.
            sOwed.set(false);
            return false;
        }
        if (!shouldGrantNow(sOwed.get(), importance.getAsInt(), Ops.isModeApplyInProgress())) {
            return false;
        }
        if (!sOwed.compareAndSet(true, false)) {
            return false;
        }
        try {
            PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, Manifest.permission.READ_LOGS,
                    UserHandleHidden.myUserId());
            return true;
        } catch (RemoteException | RuntimeException e) {
            // The permission service's own failures come back over binder as runtime exceptions
            // (Parcel.readException), and this background grant must not crash the app.
            Log.w(TAG, "Could not grant READ_LOGS.", e);
            return false;
        }
    }

    @MainThread
    private void settleIfNothingStarted() {
        if (mStartedActivities == 0 && sOwed.get()) {
            ThreadUtils.postOnMainThreadDelayed(this::settled, SETTLE_MILLIS);
        }
    }

    @VisibleForTesting
    @MainThread
    void settled() {
        // Every stop posts its own timer, so an earlier one can fire soon after a later stop.
        // Only the timer of the last stop may grant.
        if (mStartedActivities == 0 && sOwed.get()
                && SystemClock.uptimeMillis() - mLastStopUptime >= SETTLE_MILLIS) {
            mGrant.run();
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        ++mStartedActivities;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (mStartedActivities > 0) {
            --mStartedActivities;
        }
        mLastStopUptime = SystemClock.uptimeMillis();
        settleIfNothingStarted();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
