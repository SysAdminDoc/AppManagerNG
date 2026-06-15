// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.os.Build;
import android.security.advancedprotection.AdvancedProtectionManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

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
            return isAdvancedProtectionEnabledApi36(context);
        } catch (Exception th) {
            Log.w(TAG, "Could not query Advanced Protection state.", th);
        }
        return false;
    }

    @RequiresApi(36)
    @RequiresPermission(android.Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE)
    private static boolean isAdvancedProtectionEnabledApi36(@NonNull Context context) {
        Object manager = context.getSystemService(Context.ADVANCED_PROTECTION_SERVICE);
        if (manager instanceof AdvancedProtectionManager) {
            return ((AdvancedProtectionManager) manager).isAdvancedProtectionEnabled();
        }
        return false;
    }
}
