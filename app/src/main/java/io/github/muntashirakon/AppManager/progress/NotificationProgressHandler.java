// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class NotificationProgressHandler extends QueuedProgressHandler {
    private static final String TAG_PROGRESS = null;
    private static final String TAG_QUEUE = "queue";
    private static final String TAG_ALERT = "alert";

    @NonNull
    private final Context mContext;
    @NonNull
    private final NotificationManagerInfo mProgressNotificationManagerInfo;
    @NonNull
    private final NotificationManagerInfo mCompletionNotificationManagerInfo;
    @Nullable
    private final NotificationManagerInfo mQueueNotificationManagerInfo;
    @NonNull
    private final NotificationManagerCompat mProgressNotificationManager;
    @NonNull
    private final NotificationManagerCompat mCompletionNotificationManager;
    @Nullable
    private final NotificationManagerCompat mQueueNotificationManager;
    private final int mProgressNotificationId;

    @Nullable
    private NotificationInfo mLastProgressNotification = null;
    private volatile int mLastMax = MAX_INDETERMINATE;
    private volatile float mLastProgress = 0;
    // These three are written on the main thread and read by jobs on their own threads
    private volatile boolean mAttachedToService;
    // Android wouldn't put the service in the foreground, so its progress goes out as a normal notification
    private volatile boolean mRefused;
    private volatile boolean mDetached;
    // A sub-progress handler follows its parent's service, which takes its notification down with its own
    @Nullable
    private final NotificationProgressHandler mParent;
    private final Set<NotificationProgressHandler> mChildren = new CopyOnWriteArraySet<>();
    @StringRes
    private int mRefusedMessage = R.string.foreground_service_refused_keep_open;
    // Queue notifications still showing, by the message that posted them (main thread only)
    private final Map<Object, Integer> mQueueNotificationIds = new IdentityHashMap<>();

    public NotificationProgressHandler(@NonNull Context context,
                                       @NonNull NotificationManagerInfo progressNotificationManagerInfo,
                                       @NonNull NotificationManagerInfo completionNotificationManagerInfo,
                                       @Nullable NotificationManagerInfo queueNotificationManagerInfo) {
        this(context, progressNotificationManagerInfo, completionNotificationManagerInfo, queueNotificationManagerInfo,
                null);
    }

    private NotificationProgressHandler(@NonNull Context context,
                                        @NonNull NotificationManagerInfo progressNotificationManagerInfo,
                                        @NonNull NotificationManagerInfo completionNotificationManagerInfo,
                                        @Nullable NotificationManagerInfo queueNotificationManagerInfo,
                                        @Nullable NotificationProgressHandler parent) {
        mParent = parent;
        mContext = context;
        mProgressNotificationManagerInfo = progressNotificationManagerInfo;
        mCompletionNotificationManagerInfo = completionNotificationManagerInfo;
        mQueueNotificationManagerInfo = queueNotificationManagerInfo;
        mProgressNotificationManager = getNotificationManager(context, mProgressNotificationManagerInfo);
        mCompletionNotificationManager = getNotificationManager(context, mCompletionNotificationManagerInfo);
        mQueueNotificationManager = getNotificationManager(context, mQueueNotificationManagerInfo);
        mProgressNotificationId = NotificationUtils.nextNotificationId(TAG_PROGRESS);
    }

    @Override
    public void onQueue(@Nullable Object message) {
        if (mQueueNotificationManager == null || mQueueNotificationManagerInfo == null || message == null) {
            return;
        }
        NotificationInfo info = (NotificationInfo) message;
        Notification notification = info
                .getBuilder(mContext, mQueueNotificationManagerInfo)
                .setLocalOnly(true)
                .build();
        int id = NotificationUtils.nextNotificationId(TAG_QUEUE);
        mQueueNotificationIds.put(info, id);
        notify(mContext, mQueueNotificationManager, TAG_QUEUE, id, notification);
    }

    @Override
    public void onDequeue(@Nullable Object message) {
        Integer id = message != null ? mQueueNotificationIds.remove(message) : null;
        if (id != null && mQueueNotificationManager != null) {
            mQueueNotificationManager.cancel(TAG_QUEUE, id);
        }
    }

    /**
     * What the toast says when Android won't put the service in the foreground. Set it before {@link #onAttach}.
     */
    public void setRefusedMessage(@StringRes int message) {
        mRefusedMessage = message;
    }

    @Override
    public void onAttach(@Nullable Service service, @NonNull Object message) {
        mLastProgressNotification = (NotificationInfo) message;
        if (service != null) {
            Notification notification = mLastProgressNotification
                    .getBuilder(mContext, mProgressNotificationManagerInfo)
                    .setLocalOnly(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(0, 0, false)
                    .build();
            // Refused, the work still runs and its progress goes out as a normal notification, but Android may stop
            // a service that isn't in the foreground once the app leaves the screen
            mAttachedToService = ForegroundService.start(service, mProgressNotificationId, notification,
                    ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            mRefused = !mAttachedToService;
            // The toast asks the user to stay in the app, which means nothing to someone who isn't in it, as when a
            // routine applies a profile. If Android does stop the work, onStoppedUnfinished() says so.
            if (mRefused && Utils.isAppInForeground()) {
                ForegroundService.tellRefused(mRefusedMessage);
            }
        }
    }

    /**
     * The service got another start while it works, as when something is queued. After a refused
     * {@link #onAttach}, every such start has to try the foreground again, or Android ends the app once the start's
     * time runs out. It tries quietly, and if Android agrees this time the progress notification becomes the
     * service's own.
     */
    @MainThread
    public void onStartedAgain(@NonNull Service service) {
        if (!mRefused || mLastProgressNotification == null) {
            return;
        }
        // Built from the last update, text included, so the progress doesn't lose its text until the next one
        Notification notification = progressBuilder(mLastMax, mLastProgress).setOngoing(true).build();
        mAttachedToService = ForegroundService.start(service, mProgressNotificationId, notification,
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        mRefused = !mAttachedToService;
    }

    /**
     * Android stopped the service before its work was done (see
     * {@link ForegroundService#onStoppedUnfinished(boolean)}). Takes down the progress and anything still shown as
     * queued, and says what may not have finished.
     */
    @MainThread
    public void onStoppedUnfinished(@Nullable CharSequence operationName, boolean queueDropped) {
        mDetached = true;
        mProgressNotificationManager.cancel(TAG_PROGRESS, mProgressNotificationId);
        for (NotificationProgressHandler child : mChildren) {
            // Each app's own progress in a batch, which stops updating now that this one is detached
            child.mProgressNotificationManager.cancel(TAG_PROGRESS, child.mProgressNotificationId);
        }
        mChildren.clear();
        if (mQueueNotificationManager != null) {
            for (Integer id : mQueueNotificationIds.values()) {
                mQueueNotificationManager.cancel(TAG_QUEUE, id);
            }
        }
        mQueueNotificationIds.clear();
        Notification notification = new NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(operationName)
                .setTitle(mContext.getString(R.string.foreground_service_stopped_title))
                .setBody(mContext.getString(queueDropped ? R.string.foreground_service_stopped_with_queue
                        : R.string.foreground_service_stopped))
                .setStyle(new NotificationCompat.BigTextStyle())
                .getBuilder(mContext, mCompletionNotificationManagerInfo)
                .build();
        notify(mContext, mCompletionNotificationManager, TAG_ALERT, NotificationUtils.nextNotificationId(TAG_ALERT),
                notification);
    }

    @Override
    public void onProgressStart(int max, float current, @Nullable Object message) {
        onProgressUpdate(max, current, message);
    }

    @Override
    public void onProgressUpdate(int max, float current, @Nullable Object message) {
        if (message != null) {
            mLastProgressNotification = (NotificationInfo) message;
        } else {
            Objects.requireNonNull(mLastProgressNotification);
        }
        mLastMax = max;
        mLastProgress = current;
        if (isDetached()) {
            // The service is gone, and a job it left running mustn't put its progress back up
            return;
        }
        // Ongoing only while it's the service's own notification: a plain one left by a killed process could
        // otherwise never be dismissed
        NotificationCompat.Builder builder = progressBuilder(max, current).setOngoing(!isRefused());
        notify(mContext, mProgressNotificationManager, TAG_PROGRESS, mProgressNotificationId, builder.build());
    }

    @NonNull
    private NotificationCompat.Builder progressBuilder(int max, float current) {
        boolean indeterminate = max == MAX_INDETERMINATE;
        int newMax = Math.max(max, 0);
        int newCurrent = max < 0 ? 0 : (int) current;
        NotificationCompat.Builder builder = Objects.requireNonNull(mLastProgressNotification)
                .getBuilder(mContext, mProgressNotificationManagerInfo)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setProgress(newMax, newCurrent, indeterminate);
        CharSequence progressText = progressTextInterface.getProgressText(this);
        if (progressText != null) {
            if (max > 0) {
                builder.setContentText(progressText);
            } else if (max == MAX_FINISHED) {
                builder.setContentText(mContext.getString(R.string.done));
            } else {
                builder.setContentText(mContext.getString(R.string.operation_running));
            }
        }
        return builder;
    }

    private boolean isRefused() {
        return mParent != null ? mParent.mRefused : mRefused;
    }

    private boolean isDetached() {
        return mDetached || (mParent != null && mParent.mDetached);
    }

    @Override
    public void onResult(@Nullable Object message) {
        if (mParent != null) {
            mParent.mChildren.remove(this);
        }
        if (!mAttachedToService) {
            mProgressNotificationManager.cancel(TAG_PROGRESS, mProgressNotificationId);
        } else {
            onProgressUpdate(MAX_FINISHED, 0, null); // Trick to remove progressbar
        }
        if (message == null) {
            return;
        }
        NotificationInfo info = (NotificationInfo) message;
        Notification notification = info
                .getBuilder(mContext, mCompletionNotificationManagerInfo)
                .build();
        notify(mContext, mCompletionNotificationManager, TAG_ALERT, NotificationUtils.nextNotificationId(TAG_ALERT), notification);
    }

    @Override
    public void onDetach(@Nullable Service service) {
        if (service != null) {
            mAttachedToService = false;
            mDetached = true;
            mProgressNotificationManager.cancel(TAG_PROGRESS, mProgressNotificationId);
        }
    }

    @Override
    @NonNull
    public ProgressHandler newSubProgressHandler() {
        NotificationProgressHandler child = new NotificationProgressHandler(mContext, mProgressNotificationManagerInfo,
                mCompletionNotificationManagerInfo, null, this);
        mChildren.add(child);
        return child;
    }

    @Override
    public int getLastMax() {
        return mLastMax;
    }

    @Override
    public float getLastProgress() {
        return mLastProgress;
    }

    @Nullable
    @Override
    public Object getLastMessage() {
        return mLastProgressNotification;
    }

    @Override
    public void postUpdate(int max, float current, @Nullable Object message) {
        // Update values immediately to avoid issues
        mLastMax = max;
        mLastProgress = current;
        super.postUpdate(max, current, message);
    }

    private static void notify(@NonNull Context context,
                               @NonNull NotificationManagerCompat notificationManager,
                               @Nullable String notificationTag,
                               int notificationId,
                               @NonNull Notification notification) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationTag, notificationId, notification);
        }
    }

    @Nullable
    @Contract("_, !null -> !null")
    private static NotificationManagerCompat getNotificationManager(@NonNull Context context,
                                                                    @Nullable NotificationManagerInfo info) {
        if (info == null) {
            return null;
        }
        NotificationChannelCompat channel = new NotificationChannelCompat
                .Builder(info.channelId, info.importance)
                .setName(info.channelName)
                .build();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.createNotificationChannel(channel);
        return managerCompat;
    }

    public static class NotificationManagerInfo {
        @NonNull
        public final String channelId;
        @NonNull
        public final CharSequence channelName;
        @NotificationUtils.NotificationImportance
        public final int importance;

        public NotificationManagerInfo(@NonNull String channelId, @NonNull CharSequence channelName,
                                       @NotificationUtils.NotificationImportance int importance) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.importance = importance;
        }
    }

    public static class NotificationInfo {
        @DrawableRes
        private int icon = R.drawable.ic_default_notification;
        private int level;
        private long time = 0L;
        @Nullable
        private CharSequence operationName;
        @Nullable
        private CharSequence title;
        @Nullable
        private CharSequence body;
        @Nullable
        private CharSequence statusBarText;
        @Nullable
        private NotificationCompat.Style style;
        private boolean autoCancel;
        @Nullable
        private PendingIntent defaultAction;
        @Nullable
        private String groupId;

        private final ArrayList<NotificationCompat.Action> actions = new ArrayList<>();


        public NotificationInfo() {
        }

        public NotificationInfo(@NonNull NotificationInfo notificationInfo) {
            icon = notificationInfo.icon;
            level = notificationInfo.level;
            time = notificationInfo.time;
            operationName = notificationInfo.operationName;
            title = notificationInfo.title;
            body = notificationInfo.body;
            statusBarText = notificationInfo.statusBarText;
            style = notificationInfo.style;
            autoCancel = notificationInfo.autoCancel;
            defaultAction = notificationInfo.defaultAction;
            groupId = notificationInfo.groupId;
            actions.addAll(notificationInfo.actions);
        }

        public NotificationInfo setIcon(int icon) {
            this.icon = icon;
            return this;
        }

        public NotificationInfo setLevel(int level) {
            this.level = level;
            return this;
        }

        public NotificationInfo setTitle(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        @Nullable
        public CharSequence getTitle() {
            return title;
        }

        public NotificationInfo setBody(@Nullable CharSequence body) {
            this.body = body;
            return this;
        }

        @Nullable
        public CharSequence getBody() {
            return body;
        }

        public NotificationInfo setStatusBarText(@Nullable CharSequence statusBarText) {
            this.statusBarText = statusBarText;
            return this;
        }

        public NotificationInfo setDefaultAction(@Nullable PendingIntent defaultAction) {
            this.defaultAction = defaultAction;
            return this;
        }

        public NotificationInfo setOperationName(@Nullable CharSequence operationName) {
            this.operationName = operationName;
            return this;
        }

        @Nullable
        public CharSequence getOperationName() {
            return operationName;
        }

        public NotificationInfo setStyle(@Nullable NotificationCompat.Style style) {
            this.style = style;
            return this;
        }

        @Nullable
        public NotificationCompat.Style getStyle() {
            return style;
        }

        public NotificationInfo setAutoCancel(boolean autoCancel) {
            this.autoCancel = autoCancel;
            return this;
        }

        public NotificationInfo setTime(long time) {
            this.time = time;
            return this;
        }

        public void setGroupId(@Nullable String groupId) {
            this.groupId = groupId;
        }

        public NotificationInfo addAction(int icon, @Nullable CharSequence title,
                                          @Nullable PendingIntent intent) {
            actions.add(new NotificationCompat.Action(icon, title, intent));
            return this;
        }

        @NonNull
        NotificationCompat.Builder getBuilder(@NonNull Context context, @NonNull NotificationManagerInfo info) {
            PendingIntent contentIntent;
            if (autoCancel && defaultAction == null) {
                // Auto-cancel requires a content Intent
                contentIntent = PendingIntentCompat.getActivity(context, 0, new Intent(), 0, false);
            } else contentIntent = defaultAction;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, info.channelId)
                    .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                    .setPriority(NotificationUtils.importanceToPriority(info.importance))
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setSmallIcon(icon, level)
                    .setSubText(operationName)
                    .setTicker(statusBarText)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(autoCancel)
                    .setGroup(groupId)
                    .setStyle(style);
            if (groupId == null) {
                builder.setGroupSummary(false);
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }
            for (NotificationCompat.Action action : actions) {
                builder.addAction(action);
            }
            if (time > 0L) {
                builder.setWhen(time);
                builder.setShowWhen(true);
            }
            return builder;
        }
    }
}
