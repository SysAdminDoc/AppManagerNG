// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * The choices people make on the Local sync screens. Kept apart from the trust store, which holds
 * keys and remembered devices.
 */
public final class LanSyncSettings {
    public static final String PREFS_NAME = "lan_sync_settings";

    public static final int RETENTION_2_HOURS = 0;
    public static final int RETENTION_24_HOURS = 1;
    public static final int RETENTION_7_DAYS = 2;

    public static final int SCREEN_ON_OFF = 0;
    public static final int SCREEN_ON_10_MINUTES = 1;
    public static final int SCREEN_ON_30_MINUTES = 2;
    public static final int SCREEN_ON_UNTIL_DONE = 3;

    private static final String KEY_RETENTION = "received_retention";
    private static final String KEY_KEEP_AFTER_INSTALL = "received_keep_after_install";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";

    @NonNull
    private final SharedPreferences mPrefs;

    public LanSyncSettings(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * How long received APKs and unfinished downloads stay, as one of the RETENTION constants.
     */
    public int getRetention() {
        int retention = mPrefs.getInt(KEY_RETENTION, RETENTION_24_HOURS);
        return retention >= RETENTION_2_HOURS && retention <= RETENTION_7_DAYS ? retention : RETENTION_24_HOURS;
    }

    public void setRetention(int retention) {
        mPrefs.edit().putInt(KEY_RETENTION, retention).apply();
    }

    public long getRetentionMillis() {
        switch (getRetention()) {
            case RETENTION_2_HOURS:
                return TimeUnit.HOURS.toMillis(2);
            case RETENTION_7_DAYS:
                return TimeUnit.DAYS.toMillis(7);
            case RETENTION_24_HOURS:
            default:
                return TimeUnit.HOURS.toMillis(24);
        }
    }

    /**
     * Whether a received APK stays after the app it holds is installed. Off by default.
     */
    public boolean isKeepAfterInstall() {
        return mPrefs.getBoolean(KEY_KEEP_AFTER_INSTALL, false);
    }

    public void setKeepAfterInstall(boolean keep) {
        mPrefs.edit().putBoolean(KEY_KEEP_AFTER_INSTALL, keep).apply();
    }

    /**
     * How long the screen stays on while sharing or a download runs, as one of the SCREEN_ON
     * constants. Until transfers finish by default: the screen going off stops sharing.
     */
    public int getKeepScreenOn() {
        int mode = mPrefs.getInt(KEY_KEEP_SCREEN_ON, SCREEN_ON_UNTIL_DONE);
        return mode >= SCREEN_ON_OFF && mode <= SCREEN_ON_UNTIL_DONE ? mode : SCREEN_ON_UNTIL_DONE;
    }

    public void setKeepScreenOn(int mode) {
        mPrefs.edit().putInt(KEY_KEEP_SCREEN_ON, mode).apply();
    }
}
