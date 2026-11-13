// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Sees each notification as it's posted and hands it to the filter. Android binds it only while the user has given
 * this app notification access, and only the system can bind it.
 */
public class NotificationFilterService extends NotificationListenerService {
    private static final String TAG = "NotificationFilterService";

    @Nullable
    private NotificationFilterEngine mEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        mEngine = new NotificationFilterEngine(this, new ListenerShade(), new AppLookup(this),
                NotificationFilterSettings.getInstance(this), NotificationHistory.getInstance(this)::record,
                System::currentTimeMillis);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        NotificationReposter.ensureChannels(this);
        if (mEngine != null) {
            mEngine.onConnected();
        }
    }

    @Override
    public void onNotificationPosted(@Nullable StatusBarNotification sbn) {
        if (sbn != null && mEngine != null) {
            mEngine.onPosted(sbn);
        }
    }

    @Override
    public void onNotificationRemoved(@Nullable StatusBarNotification sbn) {
        if (sbn != null && mEngine != null) {
            mEngine.onRemoved(sbn);
        }
    }

    private final class ListenerShade implements NotificationFilterEngine.Shade {
        @NonNull
        @Override
        public StatusBarNotification[] active() {
            try {
                StatusBarNotification[] active = getActiveNotifications();
                return active != null ? active : new StatusBarNotification[0];
            } catch (SecurityException e) {
                // Access was taken away while a notification was being handled.
                Log.w(TAG, "Could not list the notifications.", e);
                return new StatusBarNotification[0];
            }
        }

        @Override
        public void cancelSource(@NonNull String key) {
            try {
                cancelNotification(key);
            } catch (SecurityException e) {
                Log.w(TAG, "Could not remove a notification.", e);
            }
        }

        @Override
        public boolean canPost(@NonNull NotificationFilterRule.Action action) {
            return NotificationReposter.canPost(NotificationFilterService.this, action);
        }

        @Override
        public boolean post(@NonNull String tag, @NonNull Notification notification) {
            try {
                NotificationManagerCompat.from(NotificationFilterService.this)
                        .notify(tag, NotificationReposter.NOTIFICATION_ID, notification);
                return true;
            } catch (SecurityException e) {
                // Notifications turned off between the check and the post.
                Log.w(TAG, "Could not post a filtered notification.", e);
                return false;
            }
        }

        @Override
        public void cancelOwn(@NonNull String tag) {
            NotificationManagerCompat.from(NotificationFilterService.this)
                    .cancel(tag, NotificationReposter.NOTIFICATION_ID);
        }
    }

    /** App names and icons, kept for the apps that post most. */
    private static final class AppLookup implements NotificationFilterEngine.Apps {
        @NonNull
        private final PackageManager mPm;
        private final int mIconSize;
        private final LruCache<String, String> mLabels = new LruCache<>(64);
        private final LruCache<String, Bitmap> mIcons = new LruCache<>(16);

        AppLookup(@NonNull Context context) {
            mPm = context.getPackageManager();
            Resources res = context.getResources();
            mIconSize = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        }

        @NonNull
        @Override
        public CharSequence label(@NonNull String packageName) {
            String label = mLabels.get(packageName);
            if (label == null) {
                label = PackageUtils.getPackageLabel(mPm, packageName);
                mLabels.put(packageName, label);
            }
            return label;
        }

        @Nullable
        @Override
        public Bitmap icon(@NonNull String packageName) {
            Bitmap icon = mIcons.get(packageName);
            if (icon != null) {
                return icon;
            }
            try {
                icon = UIUtils.getBitmapFromDrawable(mPm.getApplicationIcon(packageName));
            } catch (PackageManager.NameNotFoundException e) {
                // A work profile app, or one removed since it posted. The copy still names it.
                return null;
            }
            // A full size icon would make every copy a large parcel.
            if (mIconSize > 0 && (icon.getWidth() > mIconSize || icon.getHeight() > mIconSize)) {
                icon = Bitmap.createScaledBitmap(icon, mIconSize, mIconSize, true);
            }
            mIcons.put(packageName, icon);
            return icon;
        }
    }
}
