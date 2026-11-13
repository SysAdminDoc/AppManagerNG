// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.INotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/** Whether the filter can see notifications, and the two ways to let it. */
public final class NotificationListenerAccess {
    private static final String TAG = "NotificationListenerAccess";
    /** {@code Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS}, which Android 5.0 has but doesn't publish. */
    private static final String ACTION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    private NotificationListenerAccess() {
    }

    @NonNull
    public static ComponentName component(@NonNull Context context) {
        return new ComponentName(context, NotificationFilterService.class);
    }

    public static boolean isGranted(@NonNull Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    /** Opens the system page where the user turns notification access on, this app's own page where there is one. */
    public static void openSettings(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component(context).flattenToString());
            try {
                context.startActivity(detail);
                return;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No page for this app's notification access, opening the list.", e);
            }
        }
        context.startActivity(new Intent(ACTION_LISTENER_SETTINGS));
    }

    /** Root and ADB can turn access on without a trip to Settings, from Android 8.1. */
    public static boolean canGrantPrivileged() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
    }

    /** @return Whether access was turned on. */
    @WorkerThread
    public static boolean grantPrivileged(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || !SelfPermissions.checkNotificationListenerAccess()) {
            return false;
        }
        ComponentName listener = component(context);
        int userId = UserHandleHidden.myUserId();
        try {
            INotificationManager manager = INotificationManager.Stub.asInterface(
                    ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.setNotificationListenerAccessGrantedForUser(listener, userId, true, true);
            } else {
                manager.setNotificationListenerAccessGrantedForUser(listener, userId, true);
            }
            return true;
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "Could not turn on notification access.", e);
            return false;
        }
    }
}
