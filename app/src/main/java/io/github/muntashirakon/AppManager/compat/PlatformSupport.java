// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * The newest Android version this build was tested on, and whether the device runs a newer one. A newer
 * version only gets a notice: the app never refuses to run on it.
 */
public final class PlatformSupport {
    /**
     * The newest Android API level this build is compiled and tested against, taken from {@code compileSdk}.
     */
    public static final int TESTED_SDK = BuildConfig.TESTED_SDK;

    private static final String PREFS_NAME = "platform_support";
    private static final String KEY_NOTICE_DISMISSED_FOR = "untested_notice_dismissed_for";
    private static final String RELEASE_CODENAME = "REL";

    private PlatformSupport() {
    }

    /**
     * Whether this device runs an Android version newer than {@link #TESTED_SDK}.
     */
    public static boolean isUntested() {
        return isUntested(Build.VERSION.SDK_INT, Build.VERSION.CODENAME);
    }

    /**
     * The API level this device runs, counting a preview as the level it leads up to.
     */
    public static int getRunningLevel() {
        return getRunningLevel(Build.VERSION.SDK_INT, Build.VERSION.CODENAME);
    }

    /**
     * Whether Privilege Health shows its notice: the device runs an untested version and the notice
     * hasn't been dismissed on that version.
     */
    public static boolean shouldShowUntestedNotice() {
        return shouldShowUntestedNotice(Build.VERSION.SDK_INT, Build.VERSION.CODENAME,
                getPrefs().getInt(KEY_NOTICE_DISMISSED_FOR, 0));
    }

    /**
     * Hides the notice until the device moves to another Android version.
     */
    public static void dismissUntestedNotice() {
        getPrefs().edit().putInt(KEY_NOTICE_DISMISSED_FOR, getRunningLevel()).apply();
    }

    /**
     * The support bundle's line for an untested version, or {@code null} on a tested one.
     */
    @Nullable
    public static String describeUntested(int sdkInt, @Nullable String codename) {
        if (!isUntested(sdkInt, codename)) {
            return null;
        }
        String running = isPreview(codename)
                ? "a preview of API " + getRunningLevel(sdkInt, codename) + " (" + codename + ")"
                : "API " + sdkInt;
        return "this build was tested through API " + TESTED_SDK + " and the device runs " + running;
    }

    /**
     * A preview reports the last released level along with its codename, so a preview of the level after
     * {@link #TESTED_SDK} reads as {@code TESTED_SDK} and counts as untested too.
     */
    @VisibleForTesting
    static boolean isUntested(int sdkInt, @Nullable String codename) {
        return getRunningLevel(sdkInt, codename) > TESTED_SDK;
    }

    @VisibleForTesting
    static int getRunningLevel(int sdkInt, @Nullable String codename) {
        return isPreview(codename) ? sdkInt + 1 : sdkInt;
    }

    @VisibleForTesting
    static boolean shouldShowUntestedNotice(int sdkInt, @Nullable String codename, int dismissedFor) {
        return isUntested(sdkInt, codename) && dismissedFor != getRunningLevel(sdkInt, codename);
    }

    private static boolean isPreview(@Nullable String codename) {
        return codename != null && !codename.isEmpty() && !RELEASE_CODENAME.equals(codename);
    }

    @NonNull
    private static SharedPreferences getPrefs() {
        return ContextUtils.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
