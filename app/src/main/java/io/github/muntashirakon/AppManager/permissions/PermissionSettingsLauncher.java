// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.utils.IntentUtils;

/**
 * Opens the Android Settings page where the user can change a permission that App Manager
 * itself can't change (no root or ADB). The most specific page is tried first and the app's
 * own info page is the last resort, since every Android version has it.
 */
public final class PermissionSettingsLauncher {
    private static final String TAG = PermissionSettingsLauncher.class.getSimpleName();

    private PermissionSettingsLauncher() {
    }

    /**
     * The first permission in {@code permissionNames} that has a dedicated Settings page.
     */
    @Nullable
    static String firstWithSettingsPage(@NonNull Collection<String> permissionNames,
                                        @NonNull Map<String, PermUtils.SettingItem> settingItems) {
        for (String name : permissionNames) {
            if (settingItems.containsKey(name)) return name;
        }
        return null;
    }

    /**
     * Settings pages to try for these permissions of {@code packageName}, best first.
     */
    @NonNull
    public static List<Intent> candidates(@NonNull String packageName,
                                          @NonNull Collection<String> permissionNames) {
        List<Intent> intents = new ArrayList<>(2);
        String permission = firstWithSettingsPage(permissionNames, PermUtils.permissionNameToSettingItem);
        if (permission != null) {
            PermUtils.SettingItem item = PermUtils.permissionNameToSettingItem.get(permission);
            if (item != null) intents.add(item.toIntent(packageName));
        }
        intents.add(IntentUtils.getAppDetailsSettings(packageName));
        return intents;
    }

    /**
     * Start the first candidate that some activity accepts.
     *
     * @return {@code true} if a Settings page opened
     */
    public static boolean launch(@NonNull Context context, @NonNull List<Intent> candidates) {
        for (Intent intent : candidates) {
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            try {
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "Settings page %s is not available.", e, intent.getAction());
            }
        }
        return false;
    }
}
