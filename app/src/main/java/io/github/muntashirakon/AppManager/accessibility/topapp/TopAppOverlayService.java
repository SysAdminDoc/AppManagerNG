// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandleHidden;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Keeps the top-app bubble on screen until the user turns it off. The accessibility service is the better source,
 * since only it can see an overlay. Without it, the last resumed activity comes from usage stats instead.
 */
public class TopAppOverlayService extends Service implements AccessibilityMultiplexer.TopAppListener {
    private static final String TAG = TopAppOverlayService.class.getSimpleName();
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.TOP_APP_OVERLAY";
    @VisibleForTesting
    static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".action.STOP_TOP_APP_OVERLAY";
    private static final long TICK_INTERVAL_MILLIS = 1_000;
    /** How far back the first usage query looks, so that the bubble names something right away. */
    private static final long FIRST_LOOKBACK_MILLIS = 60_000;
    /** The notification has room for three actions, and one of them is Stop. */
    private static final int MAX_RECENT_ACTIONS = 2;
    /** Well away from other pending intents for App Details, which differ only in their extras. */
    private static final int REQUEST_CODE_BASE = 26_000;
    private static final int REQUEST_CODE_STOP = REQUEST_CODE_BASE - 1;

    private static volatile boolean sRunning;

    public static boolean isRunning() {
        return sRunning;
    }

    public static void start(@NonNull Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, TopAppOverlayService.class));
    }

    public static void stop(@NonNull Context context) {
        context.stopService(new Intent(context, TopAppOverlayService.class));
    }

    @VisibleForTesting
    static void setRunningForTesting(boolean running) {
        sRunning = running;
    }

    /**
     * The launcher and the keyboard are what the user sees around an ad, not the ad. Naming them would replace the
     * ad's package the moment it closes.
     */
    @NonNull
    @VisibleForTesting
    static Set<String> resolveIgnoredPackages(@NonNull Context context) {
        Set<String> ignored = new HashSet<>(Arrays.asList(context.getPackageName(), "android",
                "com.android.systemui"));
        ResolveInfo home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null && home.activityInfo != null) {
            ignored.add(home.activityInfo.packageName);
        }
        String keyboard = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (keyboard != null) {
            int slash = keyboard.indexOf('/');
            if (slash > 0) {
                ignored.add(keyboard.substring(0, slash));
            }
        }
        return ignored;
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mUsagePollRunning = new AtomicBoolean();
    private final Runnable mTick = this::onTick;
    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private TopAppTracker mTracker;
    private TopAppBubble mBubble;
    private TopAppHistory mHistory;
    @NonNull
    private TopAppTransition.Layers mLastLayers = TopAppTransition.Layers.EMPTY;
    private int mNotificationId;
    private int mNotificationGeneration;
    private boolean mForeground;
    private boolean mBubbleShown;
    private boolean mDestroyed;
    private boolean mSourceAvailable = true;
    /** Only touched by the one usage poll that can be running. */
    private long mLastUsageEventMillis;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        mTracker = new TopAppTracker(resolveIgnoredPackages(this));
        mHistory = TopAppHistory.getInstance(this);
        // Here and not in onStartCommand: a stop can't then land between a foreground start and startForeground.
        mNotificationId = NotificationUtils.nextNotificationId(null);
        // Refused, onStartCommand says the bubble couldn't start and stops
        goForeground();
        mBubble = new TopAppBubble(this, this::openAppDetails);
        updateTile();
    }

    private void goForeground() {
        try {
            NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, getString(R.string.top_app_overlay_title),
                    NotificationManagerCompat.IMPORTANCE_LOW);
            mForeground = ForegroundService.start(this, mNotificationId, buildNotification(Collections.emptyMap()),
                    ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not go foreground.", e);
        }
    }

    /** Quick Settings tiles start at API 24, and below that even loading the tile's class would throw. */
    private void updateTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TopAppOverlayTileService.requestTileStateUpdate(this);
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (!mForeground) {
            // A start from startForegroundService() that reaches a refused service still has to reach
            // startForeground(), even one that only stops it, or Android ends the app. A refused call counts too.
            goForeground();
        }
        // Stopping with stopSelfResult(): a start already on its way keeps the service up to handle it, where
        // stopSelf() would stop it before that start reached startForeground()
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (mBubbleShown) {
            return START_NOT_STICKY;
        }
        if (!mForeground || !mBubble.show()) {
            UIUtils.displayLongToast(R.string.top_app_overlay_failed);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        mBubbleShown = true;
        mMultiplexer.setTopAppListener(this);
        mHandler.post(mTick);
        // A bubble that's gone with the process stays gone, so nothing asks for a restart.
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mBubble != null) {
            mBubble.onConfigurationChanged();
        }
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mMultiplexer.getTopAppListener() == this) {
            mMultiplexer.setTopAppListener(null);
        }
        if (mBubble != null) {
            mBubble.dismiss();
        }
        updateTile();
        super.onDestroy();
    }

    @Override
    public void onWindowStateChanged(@NonNull String packageName) {
        if (!mDestroyed && mTracker.onTopApp(packageName)) {
            onTopAppChanged();
        }
    }

    @Override
    public void onWindowsChanged(@NonNull List<TopAppTracker.Window> windows) {
        mHandler.post(() -> {
            if (!mDestroyed && mTracker.onWindows(windows)) {
                onTopAppChanged();
            }
        });
    }

    @VisibleForTesting
    @NonNull
    TopAppTracker getTracker() {
        return mTracker;
    }

    private void onTopAppChanged() {
        String underlying = mTracker.getUnderlying();
        String overlay = mTracker.getOverlay();
        recordHistory(new TopAppTransition.Layers(underlying, overlay, mTracker.isOverlayOnScreen()));
        if (underlying == null && overlay == null) {
            // Only overlays that were already up when the bubble started, named over the home screen.
            underlying = mTracker.getCurrent();
        }
        mBubble.setLayers(underlying, overlay, mTracker.isOverlayOnScreen(), mTracker.getRecents());
        refreshNotification();
    }

    /**
     * Kept in step even while recording is off, so turning it on mid-session doesn't log the apps already up as
     * if they had just appeared.
     */
    private void recordHistory(@NonNull TopAppTransition.Layers layers) {
        TopAppTransition.Layers before = mLastLayers;
        mLastLayers = layers;
        if (Prefs.Privacy.isTopAppHistoryEnabled()) {
            mHistory.record(TopAppTransition.between(before, layers, System.currentTimeMillis()));
        }
    }

    private void openAppDetails(@NonNull String packageName) {
        try {
            startActivity(detailsIntent(packageName));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open App Details.", e);
            UIUtils.displayLongToast(R.string.tracker_window_app_details_unavailable);
        }
    }

    @NonNull
    private Intent detailsIntent(@NonNull String packageName) {
        return AppDetailsActivity.getIntent(this, packageName, UserHandleHidden.myUserId(), true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void onTick() {
        if (mDestroyed) {
            return;
        }
        // Android hides an overlay when the permission goes away, without removing it or saying anything.
        if (!TopAppOverlayController.canDrawOverlays(this)) {
            UIUtils.displayLongToast(R.string.top_app_overlay_needs_overlay);
            stopSelf();
            return;
        }
        if (mMultiplexer.isServiceConnected()) {
            setSourceAvailable(true);
        } else if (isInteractive()) {
            pollUsageStats();
        }
        mHandler.postDelayed(mTick, TICK_INTERVAL_MILLIS);
    }

    private boolean isInteractive() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    private void pollUsageStats() {
        if (!mUsagePollRunning.compareAndSet(false, true)) {
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean available = false;
            List<String> resumed = Collections.emptyList();
            try {
                available = TopAppOverlayController.hasUsageAccess();
                if (available) {
                    resumed = readResumedPackages();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not read usage stats.", e);
                available = false;
            } finally {
                mUsagePollRunning.set(false);
            }
            boolean finalAvailable = available;
            List<String> finalResumed = resumed;
            mHandler.post(() -> onUsagePolled(finalAvailable, finalResumed));
        });
    }

    /** Everything resumed since the last poll, oldest first, so an ad that came and went between polls is kept. */
    @WorkerThread
    @NonNull
    @SuppressWarnings("MissingPermission")
    private List<String> readResumedPackages() {
        long now = System.currentTimeMillis();
        long begin = mLastUsageEventMillis > 0 ? mLastUsageEventMillis + 1 : now - FIRST_LOOKBACK_MILLIS;
        UsageEvents events = UsageStatsManagerCompat.queryEvents(begin, now, UserHandleHidden.myUserId());
        if (events == null) {
            return Collections.emptyList();
        }
        List<String> resumed = new ArrayList<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumed.add(event.getPackageName());
                mLastUsageEventMillis = event.getTimeStamp();
            }
        }
        return resumed;
    }

    @VisibleForTesting
    void onUsagePolled(boolean available, @NonNull List<String> resumed) {
        if (mDestroyed) {
            return;
        }
        setSourceAvailable(available);
        boolean changed = false;
        for (String packageName : resumed) {
            changed |= mTracker.onTopApp(packageName);
        }
        if (changed) {
            onTopAppChanged();
        }
    }

    private void setSourceAvailable(boolean available) {
        if (mSourceAvailable == available) {
            return;
        }
        mSourceAvailable = available;
        if (!available) {
            UIUtils.displayLongToast(R.string.top_app_overlay_no_source);
        }
        refreshNotification();
    }

    private void refreshNotification() {
        int generation = ++mNotificationGeneration;
        List<String> recents = mTracker.getRecents();
        PackageManager pm = getPackageManager();
        ThreadUtils.postOnBackgroundThread(() -> {
            Map<String, CharSequence> labels = new LinkedHashMap<>();
            for (String packageName : recents) {
                labels.put(packageName, PackageUtils.getPackageLabel(pm, packageName, UserHandleHidden.myUserId()));
            }
            // Posted from the main thread: a late post could otherwise bring the notification back after onDestroy.
            mHandler.post(() -> {
                if (!mDestroyed && generation == mNotificationGeneration && mForeground
                        && SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                    NotificationManagerCompat.from(this).notify(mNotificationId, buildNotification(labels));
                }
            });
        });
    }

    /**
     * @param labels App labels, newest first. Apps with no label yet are named by their package.
     */
    @NonNull
    private Notification buildNotification(@NonNull Map<String, CharSequence> labels) {
        Intent stopIntent = new Intent(this, TopAppOverlayService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntentCompat.getService(this, REQUEST_CODE_STOP, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(getString(R.string.top_app_overlay_title))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .addAction(R.drawable.ic_stop, getString(R.string.top_app_overlay_stop), stop);
        String current = mTracker.getCurrent();
        String overlay = mTracker.getOverlay();
        String underlying = mTracker.getUnderlying();
        if (!mSourceAvailable) {
            builder.setContentText(getString(R.string.top_app_overlay_no_source));
        } else if (current == null) {
            builder.setContentText(getString(R.string.top_app_overlay_waiting));
        } else if (overlay != null && underlying != null && mTracker.isOverlayOnScreen()) {
            builder.setContentText(getString(R.string.top_app_overlay_notification_layers,
                    labelOrPackage(labels, overlay), labelOrPackage(labels, underlying)));
            builder.setContentIntent(detailsPendingIntent(overlay, REQUEST_CODE_BASE));
        } else {
            builder.setContentText(getString(R.string.top_app_overlay_notification_text, current));
            builder.setContentIntent(detailsPendingIntent(current, REQUEST_CODE_BASE));
        }
        List<String> recents = mTracker.getRecents();
        if (recents.size() > 1) {
            List<CharSequence> lines = new ArrayList<>(recents.size());
            int actions = 0;
            for (String packageName : recents) {
                CharSequence label = labels.get(packageName);
                lines.add(label != null ? getString(R.string.top_app_overlay_recent_row, label, packageName)
                        : packageName);
                if (!packageName.equals(current) && actions < MAX_RECENT_ACTIONS) {
                    ++actions;
                    builder.addAction(R.drawable.ic_information_circle, label != null ? label : packageName,
                            detailsPendingIntent(packageName, REQUEST_CODE_BASE + actions));
                }
            }
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(TextUtils.join("\n", lines)));
        }
        return builder.build();
    }

    @NonNull
    private static CharSequence labelOrPackage(@NonNull Map<String, CharSequence> labels, @NonNull String packageName) {
        CharSequence label = labels.get(packageName);
        return label != null ? label : packageName;
    }

    @Nullable
    private PendingIntent detailsPendingIntent(@NonNull String packageName, int requestCode) {
        return PendingIntentCompat.getActivity(this, requestCode, detailsIntent(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT, false);
    }
}
