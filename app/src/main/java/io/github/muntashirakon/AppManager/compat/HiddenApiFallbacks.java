// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Hidden-API calls that failed to link or resolve on this Android build, where the caller went on with
 * another route or a default. Each one is logged once per process, so an accessor that stops working on a
 * new Android version shows up in logcat and the support bundle instead of quietly returning nothing.
 */
public final class HiddenApiFallbacks {
    public static final String TAG = "HiddenApiFallback";

    private static final Set<String> sMembers = new LinkedHashSet<>();

    private HiddenApiFallbacks() {
    }

    /**
     * Logs at warning level that {@code className#member} failed with {@code failure} and a fallback ran,
     * the first time it happens in this process.
     */
    public static void log(@NonNull String className, @NonNull String member, @NonNull Throwable failure) {
        String key = className + "#" + member;
        synchronized (sMembers) {
            if (!sMembers.add(key)) {
                return;
            }
        }
        Log.w(TAG, "%s failed on API %d (%s), using a fallback", failure, key, Build.VERSION.SDK_INT,
                failure.getClass().getSimpleName());
    }

    /**
     * The members that fell back in this process, in the order they first did.
     */
    @NonNull
    public static List<String> getMembers() {
        synchronized (sMembers) {
            return new ArrayList<>(sMembers);
        }
    }

    @VisibleForTesting
    public static void resetForTesting() {
        synchronized (sMembers) {
            sMembers.clear();
        }
    }
}
