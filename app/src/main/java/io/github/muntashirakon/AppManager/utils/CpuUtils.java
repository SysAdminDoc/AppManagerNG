// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

public class CpuUtils {
    /**
     * Fallback timeout for partial wake locks acquired via {@link #acquireWakeLock}.
     * Any long-running batch / backup / installer / profile operation is expected
     * to complete well under this — the timeout exists so a process death between
     * acquire() and release() can't leak the wake lock until reboot.
     */
    public static final long DEFAULT_WAKE_LOCK_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(2);

    static {
        System.loadLibrary("am");
    }

    @Keep
    public static native long getClockTicksPerSecond();

    @Keep
    @Nullable
    public static native String getCpuModel();

    public static PowerManager.WakeLock getPartialWakeLock(String tagPostfix) {
        PowerManager pm = (PowerManager) ContextUtils.getContext().getSystemService(Context.POWER_SERVICE);
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppManager::" + tagPostfix);
    }

    /**
     * Acquire a wake lock with a fallback timeout so an unexpected process death
     * after acquire() but before release() doesn't drain the battery until
     * reboot. Always pair with {@link #releaseWakeLock}; the timeout is a
     * last-resort safety net, not the intended lifetime.
     */
    public static void acquireWakeLock(@Nullable PowerManager.WakeLock wakeLock) {
        acquireWakeLock(wakeLock, DEFAULT_WAKE_LOCK_TIMEOUT_MILLIS);
    }

    public static void acquireWakeLock(@Nullable PowerManager.WakeLock wakeLock, long timeoutMillis) {
        if (wakeLock == null) {
            return;
        }
        wakeLock.acquire(timeoutMillis);
    }

    public static void releaseWakeLock(@Nullable PowerManager.WakeLock wakeLock) {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
