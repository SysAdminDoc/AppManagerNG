// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * Decides whether the Local sync screen keeps the display on. Sharing stops when the screen goes
 * off, so the screen stays on while sharing is on or a list or download is loading, for as long as
 * {@link LanSyncSettings#getKeepScreenOn()} allows. A time limit counts from the moment the work
 * started and starts again after everything has finished.
 */
public final class ScreenOnPolicy {
    public static final class Decision {
        static final Decision RELEASE = new Decision(false, 0);

        public final boolean keepOn;
        /**
         * The {@code SystemClock.elapsedRealtime()} at which to ask again, or 0 when nothing changes
         * until the work does.
         */
        public final long recheckAt;

        Decision(boolean keepOn, long recheckAt) {
            this.keepOn = keepOn;
            this.recheckAt = recheckAt;
        }
    }

    public static long limitMillis(int mode) {
        switch (mode) {
            case LanSyncSettings.SCREEN_ON_10_MINUTES:
                return TimeUnit.MINUTES.toMillis(10);
            case LanSyncSettings.SCREEN_ON_30_MINUTES:
                return TimeUnit.MINUTES.toMillis(30);
            default:
                return 0;
        }
    }

    private long mActiveSince = -1;

    /**
     * @param mode   one of the {@code LanSyncSettings.SCREEN_ON} constants
     * @param active whether sharing is on or a list or download is loading
     * @param now    {@code SystemClock.elapsedRealtime()}
     */
    @NonNull
    public Decision update(int mode, boolean active, long now) {
        if (!active) {
            mActiveSince = -1;
            return Decision.RELEASE;
        }
        if (mActiveSince < 0) {
            mActiveSince = now;
        }
        if (mode == LanSyncSettings.SCREEN_ON_OFF) {
            return Decision.RELEASE;
        }
        long limit = limitMillis(mode);
        if (limit <= 0) {
            return new Decision(true, 0);
        }
        long until = mActiveSince + limit;
        return now < until ? new Decision(true, until) : Decision.RELEASE;
    }
}
