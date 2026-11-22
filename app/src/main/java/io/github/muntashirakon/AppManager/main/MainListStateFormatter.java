// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/**
 * A frozen or suspended app says so in words on its main list row, so the state doesn't rest on a
 * tint or a "°" mark. Read from the loaded item, so binding a row makes no package manager call.
 * The list's cache keeps one bit for an app that's disabled, hidden or suspended, and the suspended
 * flag on top of it, so a disabled or hidden app reads as frozen.
 */
final class MainListStateFormatter {
    private MainListStateFormatter() {
    }

    /**
     * The state the row names, or 0 for an app that runs normally. Suspended comes first: a
     * suspended app is counted as frozen too, and suspended is the more exact word for it.
     */
    @StringRes
    static int stateLabel(@NonNull ApplicationItem item) {
        if (!item.isInstalled) {
            // Not installed, or archived: the row already says that
            return 0;
        }
        // Suspending came with Android 7, and on Android 5 the same bit marked a privileged app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (item.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) {
            return R.string.suspended;
        }
        if (item.isDisabled) {
            return R.string.frozen;
        }
        return 0;
    }

    /**
     * The state as the row shows and a screen reader says it, or an empty string.
     */
    @NonNull
    static String label(@NonNull Context context, @NonNull ApplicationItem item) {
        int label = stateLabel(item);
        return label == 0 ? "" : context.getString(label);
    }
}
