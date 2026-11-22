// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;

/**
 * Keeps this app from uninstalling itself to put another copy in its place. Android ends the app
 * the moment its own uninstall goes through, so the install that should follow never runs and
 * neither copy is left (#45). Updates Android takes in place, signed by the same key, still work.
 */
public final class SelfReplaceGuard {
    private SelfReplaceGuard() {
    }

    /**
     * True for this app's own package, as installed right now (debug builds included).
     */
    public static boolean refuses(@NonNull Context context, @Nullable String packageName) {
        return packageName != null && packageName.equals(context.getPackageName());
    }

    /**
     * What to do instead: back up, then uninstall and install the other copy from outside the app.
     */
    @NonNull
    public static String explanation(@NonNull Context context) {
        String name = context.getString(R.string.app_name);
        return context.getString(R.string.installer_cannot_replace_self, name);
    }
}
