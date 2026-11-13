// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What lets an app put an ad in front of whatever the user is doing: drawing over other apps, which since Android
 * 10 also lets it start activities from the background, plus a way to wake itself up with nothing on screen. Each
 * of these is common in ordinary apps, so this marks a possible source to look at, never a verdict.
 */
public final class AdSourceSignals {
    /** Can draw over other apps. */
    public static final int OVERLAY = 1;
    /** Starts itself after the phone restarts. */
    public static final int BOOT = 1 << 1;
    /** Can open full screen from a notification, as alarm and calling apps do. */
    public static final int FULL_SCREEN = 1 << 2;

    /** For {@code overlayOpMode} when the app op couldn't be read. */
    public static final int MODE_UNKNOWN = -1;

    /** Android's own name for the full-screen intent permission, which the compile SDK's Manifest has from API 29. */
    private static final String USE_FULL_SCREEN_INTENT = "android.permission.USE_FULL_SCREEN_INTENT";

    private AdSourceSignals() {
    }

    /**
     * @param requested      The app's requested permissions.
     * @param requestedFlags Their {@link PackageInfo#REQUESTED_PERMISSION_GRANTED} flags, in the same order.
     * @param overlayOpMode  The app's mode for the draw-over-apps app op, or {@link #MODE_UNKNOWN}.
     */
    public static int compute(@Nullable String[] requested, @Nullable int[] requestedFlags, int overlayOpMode) {
        if (requested == null) {
            return 0;
        }
        int signals = 0;
        for (int i = 0; i < requested.length; ++i) {
            String permission = requested[i];
            boolean granted = requestedFlags != null && i < requestedFlags.length
                    && (requestedFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            if (Manifest.permission.SYSTEM_ALERT_WINDOW.equals(permission)) {
                // The user's switch in Settings is the app op. Left at its default, the permission's grant decides.
                if (overlayOpMode == AppOpsManager.MODE_ALLOWED || ((overlayOpMode == AppOpsManager.MODE_DEFAULT
                        || overlayOpMode == MODE_UNKNOWN) && granted)) {
                    signals |= OVERLAY;
                }
            } else if (Manifest.permission.RECEIVE_BOOT_COMPLETED.equals(permission)) {
                signals |= BOOT;
            } else if (USE_FULL_SCREEN_INTENT.equals(permission) && granted) {
                signals |= FULL_SCREEN;
            }
        }
        return signals;
    }

    /** A user app that can draw over other apps and has a way to come up without being opened. */
    public static boolean isPossibleAdSource(int signals, boolean systemApp) {
        return !systemApp && (signals & OVERLAY) != 0 && (signals & (BOOT | FULL_SCREEN)) != 0;
    }

    public static boolean isPossibleAdSource(@NonNull ApplicationItem item) {
        return isPossibleAdSource(item.adSourceSignals != null ? item.adSourceSignals : 0, item.isSystem);
    }
}
