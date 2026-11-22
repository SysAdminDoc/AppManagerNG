// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.main.ApplicationItem;

/**
 * What App Info's media capture scan found, kept per package for the version it scanned. The main list's "Blocks
 * screen or audio capture" filter reads only this, so loading the list never scans an APK, and an app nobody has
 * opened in App Info yet isn't listed.
 */
public final class MediaCaptureCache {
    private static final String PREF_NAME = "media_capture_cache";

    private MediaCaptureCache() {
    }

    public static void put(@NonNull Context context, @NonNull String packageName, long versionCode,
                           long lastUpdateTime, int flags) {
        prefs(context).edit().putString(packageName, versionCode + ":" + lastUpdateTime + ":" + flags).apply();
    }

    /**
     * The flags found for exactly this version of the app, or {@code null} when it wasn't scanned or has changed
     * since.
     */
    @Nullable
    public static Integer get(@NonNull Context context, @NonNull String packageName, long versionCode,
                              long lastUpdateTime) {
        String value = prefs(context).getString(packageName, null);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            if (Long.parseLong(parts[0]) != versionCode || Long.parseLong(parts[1]) != lastUpdateTime) {
                return null;
            }
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether a kept scan of this version says the app keeps its screen or its audio from being captured. Hiding
     * the Recents preview alone doesn't count, since screenshots of the app itself still work.
     */
    public static boolean blocksCapture(@NonNull Context context, @NonNull ApplicationItem item) {
        long lastUpdateTime = item.lastUpdateTime != null ? item.lastUpdateTime : 0L;
        Integer flags = get(context, item.packageName, item.versionCode, lastUpdateTime);
        return flags != null && (MediaCaptureScanner.blocksScreenCapture(flags)
                || (flags & MediaCaptureScanner.FLAG_AUDIO_CAPTURE_BLOCKED) != 0);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
