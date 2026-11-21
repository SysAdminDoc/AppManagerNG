// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Says which mode AppManagerNG works in, with a toast, only to someone looking at it.
 * <p>
 * The mode checks run from background work too (the permission watcher, scheduled jobs, the
 * services that keep the server up), and each of them used to toast the mode. With nothing of
 * AppManagerNG on screen, that put "Working through Shizuku shell mode" over other apps all day
 * (#56). A toast now needs a started AppManagerNG screen, a mode other than the one already
 * announced in this process, and the switch in Mode of operation left on.
 */
public final class ModeAnnouncer implements Application.ActivityLifecycleCallbacks {
    private static final AtomicInteger sStartedActivities = new AtomicInteger();
    private static final AtomicInteger sLastAnnounced = new AtomicInteger();

    private ModeAnnouncer() {
    }

    @MainThread
    public static void register(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new ModeAnnouncer());
    }

    /**
     * Shows the mode toast if someone can see it and hasn't been told this mode yet.
     */
    @AnyThread
    public static void announce(@StringRes int message, boolean longToast) {
        if (!shouldAnnounce(message)) {
            return;
        }
        ThreadUtils.postOnMainThread(() -> {
            if (longToast) {
                UIUtils.displayLongToast(message);
            } else {
                UIUtils.displayShortToast(message);
            }
        });
    }

    /**
     * Forgets the announced mode, so the next connection says its mode again. Called when the
     * mode setting changes.
     */
    @AnyThread
    public static void forget() {
        sLastAnnounced.set(0);
    }

    @VisibleForTesting
    @AnyThread
    static boolean shouldAnnounce(@StringRes int message) {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_MODE_TOAST_BOOL)) {
            return false;
        }
        if (sStartedActivities.get() <= 0) {
            // Nobody to tell. The mode is still announced the next time a screen asks for it.
            return false;
        }
        return sLastAnnounced.getAndSet(message) != message;
    }

    @VisibleForTesting
    static void reset() {
        sStartedActivities.set(0);
        sLastAnnounced.set(0);
    }

    @VisibleForTesting
    static int startedActivities() {
        return sStartedActivities.get();
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        sStartedActivities.incrementAndGet();
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // Never below zero, so a stop delivered without its start can't hide the next screen
        int count;
        do {
            count = sStartedActivities.get();
        } while (count > 0 && !sStartedActivities.compareAndSet(count, count - 1));
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
