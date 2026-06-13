// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.logs.Log;

public final class AdvancedProtectionCompat {
    private static final String TAG = AdvancedProtectionCompat.class.getSimpleName();

    private AdvancedProtectionCompat() {
    }

    public static boolean isAdvancedProtectionEnabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 36) {
            return false;
        }
        try {
            Object manager = context.getSystemService("advanced_protection");
            if (manager instanceof android.security.advancedprotection.AdvancedProtectionManager) {
                return ((android.security.advancedprotection.AdvancedProtectionManager) manager)
                        .isAdvancedProtectionEnabled();
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not query Advanced Protection state.", th);
        }
        return false;
    }
}
