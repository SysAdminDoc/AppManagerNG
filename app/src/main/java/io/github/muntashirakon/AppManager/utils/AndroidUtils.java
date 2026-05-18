// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.VisibleForTesting;

/**
 * Android platform-version helpers.
 *
 * <p>Android 16 added minor SDK releases through {@code SDK_INT_FULL}. Keep new
 * platform-version gates here when they need to distinguish 16.0 from 16.1+
 * rather than spreading full-version encoding details through call sites.
 */
public final class AndroidUtils {
    private static final int SDK_INT_FULL_MULTIPLIER = 100_000;

    private AndroidUtils() {
    }

    public static boolean sdkAtLeast(int major, int minor) {
        return sdkAtLeast(getSdkIntFull(), major, minor);
    }

    public static int getSdkMajor() {
        return getMajorSdkVersion(getSdkIntFull());
    }

    public static int getSdkMinor() {
        return getMinorSdkVersion(getSdkIntFull());
    }

    @SuppressLint("NewApi")
    public static int getSdkIntFull() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            return Build.VERSION.SDK_INT_FULL;
        }
        return makeSdkIntFull(Build.VERSION.SDK_INT, 0);
    }

    @VisibleForTesting
    static boolean sdkAtLeast(int currentSdkIntFull, int major, int minor) {
        return currentSdkIntFull >= makeSdkIntFull(major, minor);
    }

    @VisibleForTesting
    static int makeSdkIntFull(int major, int minor) {
        if (major < 1) {
            throw new IllegalArgumentException("major must be positive");
        }
        if (minor < 0 || minor >= SDK_INT_FULL_MULTIPLIER) {
            throw new IllegalArgumentException("minor must be between 0 and "
                    + (SDK_INT_FULL_MULTIPLIER - 1));
        }
        return major * SDK_INT_FULL_MULTIPLIER + minor;
    }

    @VisibleForTesting
    static int getMajorSdkVersion(int sdkIntFull) {
        return sdkIntFull / SDK_INT_FULL_MULTIPLIER;
    }

    @VisibleForTesting
    static int getMinorSdkVersion(int sdkIntFull) {
        return sdkIntFull % SDK_INT_FULL_MULTIPLIER;
    }
}
