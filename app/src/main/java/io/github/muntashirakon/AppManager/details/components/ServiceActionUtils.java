// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.content.ComponentName;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ServiceActionUtils {
    private ServiceActionUtils() {
    }

    @NonNull
    public static Intent buildServiceIntent(@NonNull String packageName, @NonNull String serviceName) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName,
                ReceiverBroadcastUtils.toQualifiedComponentName(packageName, serviceName)));
        return intent;
    }

    public static boolean canUseUnprivilegedRoute(boolean exported, @Nullable String permission,
                                                  boolean hasPermission, int targetUserId,
                                                  int currentUserId) {
        return targetUserId == currentUserId && exported && (permission == null || hasPermission);
    }

    public static boolean needsPrivilegedDispatch(boolean exported, @Nullable String permission,
                                                  boolean hasPermission, int targetUserId,
                                                  int currentUserId) {
        return !canUseUnprivilegedRoute(exported, permission, hasPermission, targetUserId, currentUserId);
    }
}
