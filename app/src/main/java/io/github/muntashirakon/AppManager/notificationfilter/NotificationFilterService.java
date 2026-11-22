// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.INotificationManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.i18n.LanguagePackCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Sees each notification as it's posted and hands it to the filter. Android binds it only while the user has given
 * this app notification access, and only the system can bind it.
 */
public class NotificationFilterService extends NotificationListenerService {
    private static final String TAG = "NotificationFilterService";

    @Nullable
    private NotificationFilterEngine mEngine;
    @Nullable
    private NotificationFilterSettings mSettings;
    /** The ranking that came with the notification being handled, while it's handled. */
    @Nullable
    private RankingMap mPostRanking;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mConnected;
    private boolean mStopReceiverRegistered;
    /** A saved rule applies to what's in the shade too, not just to what comes next. */
    private final Runnable mOnRulesChanged = () -> {
        if (mEngine != null) {
            mEngine.sweep();
        }
        scheduleCheck();
    };
    /** Android doesn't report what happens to a snoozed notification, so while one is snoozed it's looked in on. */
    private final Runnable mCheck = () -> {
        if (mEngine != null) {
            mEngine.check();
        }
        scheduleCheck();
    };
    /** A force stop ends the app's snoozed notifications too, and this broadcast is the only sign of it. */
    private final BroadcastReceiver mPackageStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            String packageName = data != null ? data.getSchemeSpecificPart() : null;
            if (packageName != null && mEngine != null) {
                mEngine.onPackageStopped(packageName, intent.getIntExtra(Intent.EXTRA_UID, -1));
                scheduleCheck();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSettings = NotificationFilterSettings.getInstance(this);
        mEngine = new NotificationFilterEngine(this, new ListenerShade(), new AppLookup(this), mSettings,
                NotificationHistory.getInstance(this)::record, System::currentTimeMillis);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        NotificationReposter.ensureChannels(this);
        mConnected = true;
        if (!mStopReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addDataScheme("package");
            // Only the system sends it.
            ContextCompat.registerReceiver(this, mPackageStopped, filter, ContextCompat.RECEIVER_EXPORTED);
            mStopReceiverRegistered = true;
        }
        if (mEngine != null) {
            mEngine.onConnected();
        }
        if (mSettings != null) {
            mSettings.registerRulesListener(mOnRulesChanged);
        }
        scheduleCheck();
    }

    @Override
    public void onListenerDisconnected() {
        stopWatching();
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        stopWatching();
        super.onDestroy();
    }

    private void stopWatching() {
        mConnected = false;
        mHandler.removeCallbacks(mCheck);
        if (mStopReceiverRegistered) {
            unregisterReceiver(mPackageStopped);
            mStopReceiverRegistered = false;
        }
        if (mSettings != null) {
            mSettings.unregisterRulesListener(mOnRulesChanged);
        }
    }

    /** Looks in on what's snoozed a minute from now, for as long as something is. Each event starts it over. */
    private void scheduleCheck() {
        mHandler.removeCallbacks(mCheck);
        if (mConnected && mEngine != null && mEngine.hasSnoozed()) {
            mHandler.postDelayed(mCheck, NotificationFilterEngine.CHECK_EVERY_MILLIS);
        }
    }

    /**
     * Not passed on: the default calls the one without a ranking. The ranking that came with the post is the one that
     * lists it for sure. By the time it's handled the current one can be newer and already without it, and a missing
     * importance would snooze a quiet notification for the long time.
     */
    @Override
    public void onNotificationPosted(@Nullable StatusBarNotification sbn, @Nullable RankingMap rankingMap) {
        if (sbn == null || mEngine == null) {
            return;
        }
        mPostRanking = rankingMap;
        try {
            mEngine.onPosted(sbn);
        } finally {
            mPostRanking = null;
        }
        scheduleCheck();
    }

    /** Before Android 8, which doesn't say why. */
    @Override
    public void onNotificationRemoved(@Nullable StatusBarNotification sbn) {
        if (sbn != null && mEngine != null) {
            mEngine.onRemoved(sbn, 0);
            scheduleCheck();
        }
    }

    /** Android 8 and later, with the reason, so a pin comes back only when the user swiped it away. */
    @Override
    public void onNotificationRemoved(@Nullable StatusBarNotification sbn, @Nullable RankingMap rankingMap,
                                      int reason) {
        // Not passed on: the default calls the one above, which would handle it a second time.
        if (sbn != null && mEngine != null) {
            mEngine.onRemoved(sbn, reason);
            scheduleCheck();
        }
    }

    /**
     * @param postRanking    The ranking that came with the post being handled, or {@code null} outside of one.
     * @param currentRanking The newest ranking.
     * @return The notification's importance from the first of them that lists it, or -1 when neither does.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    static int importanceIn(@NonNull String key, @Nullable RankingMap postRanking,
                            @Nullable RankingMap currentRanking) {
        Ranking out = new Ranking();
        if (postRanking != null && postRanking.getRanking(key, out)) {
            return out.getImportance();
        }
        if (currentRanking != null && currentRanking.getRanking(key, out)) {
            return out.getImportance();
        }
        return -1;
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
        public boolean canPost(@NonNull String channelId) {
            return NotificationReposter.canPostOn(NotificationFilterService.this, channelId);
        }

        @Override
        public int sdkInt() {
            return Build.VERSION.SDK_INT;
        }

        @Override
        public boolean isRoot() {
            // The cancel goes through the root server, so it has to be up.
            return Ops.isWorkingUidRoot() && LocalServices.alive();
        }

        @Override
        public boolean canUnsnooze() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // "cmd notification unsnooze" came with Android 11.
                return false;
            }
            if (Ops.isDirectRoot()) {
                return true;
            }
            int uid = Ops.getWorkingUid();
            return LocalServices.alive() && (uid == Ops.ROOT_UID || uid == Ops.SHELL_UID);
        }

        @Override
        public boolean snooze(@NonNull String key, long durationMillis) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return false;
            }
            try {
                snoozeNotification(key, durationMillis);
                return true;
            } catch (SecurityException e) {
                Log.w(TAG, "Could not snooze a notification.", e);
                return false;
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean cancelAsRoot(@NonNull StatusBarNotification sbn) {
            String packageName = sbn.getPackageName();
            String tag = sbn.getTag();
            int id = sbn.getId();
            int userId = sbn.getUserId();
            // A call to the root server, so not on the listener's thread.
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    INotificationManager manager = INotificationManager.Stub.asInterface(
                            ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
                    manager.cancelNotificationWithTag(packageName, tag, id, userId);
                } catch (RemoteException | RuntimeException e) {
                    Log.w(TAG, "Could not remove an ongoing notification as root.", e);
                }
            });
            return true;
        }

        @Override
        public void unsnooze(@NonNull String key) {
            ThreadUtils.postOnBackgroundThread(() -> {
                Runner.Result result = Runner.runCommand(new String[]{"cmd", "notification", "unsnooze", key});
                if (!result.isSuccessful()) {
                    Log.w(TAG, "Could not bring back a snoozed notification: %s", result.getOutput());
                }
            });
        }

        @NonNull
        @Override
        public StatusBarNotification[] snoozed() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return new StatusBarNotification[0];
            }
            try {
                StatusBarNotification[] snoozed = getSnoozedNotifications();
                return snoozed != null ? snoozed : new StatusBarNotification[0];
            } catch (SecurityException e) {
                Log.w(TAG, "Could not list the snoozed notifications.", e);
                return new StatusBarNotification[0];
            }
        }

        @Override
        public int importanceOf(@NonNull String key) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return -1;
            }
            return importanceIn(key, mPostRanking, getCurrentRanking());
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
    static final class AppLookup implements NotificationFilterEngine.Apps {
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

    @Override
    protected void attachBaseContext(Context base) {
        // Notifications and tiles show a language file on Android 10 and older this way
        super.attachBaseContext(LanguagePackCompat.wrap(base));
    }
}
