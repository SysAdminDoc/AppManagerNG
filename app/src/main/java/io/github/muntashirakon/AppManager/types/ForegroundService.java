// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.ServiceCompat;
import androidx.core.os.BundleCompat;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public abstract class ForegroundService extends Service {
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC;
    public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

    static {
        FOREGROUND_SERVICE_TYPE_DATA_SYNC = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        FOREGROUND_SERVICE_TYPE_SPECIAL_USE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
    }

    /**
     * Puts the service in the foreground, which Android can refuse: from Android 12 a start made while the app is in
     * the background throws {@code ForegroundServiceStartNotAllowedException} (an {@link IllegalStateException}), and
     * from Android 14 a type whose permission or conditions aren't met throws {@link SecurityException}. Android has
     * already let go of the {@code startForegroundService()} promise by then, so a refused service can stop itself
     * without the "did not then call startForeground" crash. That promise comes with every such start, so a service
     * that isn't in the foreground has to call this again for each one, even a start it only queues or stops on.
     *
     * @return {@code false} if Android refused. {@code true} doesn't promise the service is in the foreground: with
     * the app's background work restricted, for one, Android ignores the call without throwing.
     */
    @SuppressLint("ForegroundServiceType")
    public static boolean start(@NonNull Service service, int id, @NonNull Notification notification,
                                int foregroundServiceType) {
        try {
            ServiceCompat.startForeground(service, id, notification, foregroundServiceType);
            return true;
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Android refused to put %s in the foreground.", e, service.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Tells the user what a refused {@link #start} cost them. Safe from any thread.
     */
    @AnyThread
    public static void tellRefused(@StringRes int message) {
        ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(message));
    }

    private static final String TAG = ForegroundService.class.getSimpleName();

    public static class Binder extends android.os.Binder {
        private final ForegroundService mService;

        private Binder(ForegroundService service) {
            mService = service;
        }

        @SuppressWarnings("unchecked")
        public <T extends ForegroundService> T getService() {
            return (T) mService;
        }
    }

    private final String mName;
    private final IBinder mBinder = new Binder(this);
    @SuppressWarnings("FieldCanBeLocal")
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private volatile boolean mIsWorking = false;
    // Starts not yet through onHandleIntent(), counted from onStartCommand() so a job the looper has taken but not
    // begun still counts
    private final AtomicInteger mJobsLeft = new AtomicInteger();

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent intent = BundleCompat.getParcelable(msg.getData(), "intent", Intent.class);
            try {
                ThreadUtils.postOnMainThread(() -> onStartIntent(intent));
                onHandleIntent(intent);
            } finally {
                mJobsLeft.decrementAndGet();
            }
            // It works because of Handler uses FIFO
            stopSelfResult(msg.arg1);
        }
    }

    protected ForegroundService(String name) {
        mName = name;
    }

    public final boolean isWorking() {
        return mIsWorking;
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(mName, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @CallSuper
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // TODO: 15/6/21 Make it final, extended classes shouldn't need to use it
        if (mIsWorking) {
            // Service already running
            onQueued(intent);
        }
        mIsWorking = true;
        mJobsLeft.incrementAndGet();
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        Bundle args = new Bundle();
        args.putParcelable("intent", intent);
        msg.setData(args);
        mServiceHandler.sendMessage(msg);
        return START_NOT_STICKY;
    }

    @CallSuper
    @Override
    public void onDestroy() {
        super.onDestroy();
        // A service that finished its work stops itself with nothing left. One Android didn't put in the foreground
        // can be stopped by Android once the app leaves the screen.
        if (mJobsLeft.get() > 0) {
            boolean queueDropped = mServiceHandler.hasMessages(0);
            // quitSafely() would still run every start already queued, with nothing left to keep the process alive
            // or show their progress
            mServiceHandler.removeMessages(0);
            Log.w(TAG, "%s was stopped before its work was done.", getClass().getSimpleName());
            onStoppedUnfinished(queueDropped);
        }
        mServiceLooper.quitSafely();
    }

    /**
     * The service was stopped from outside while it still had work. Android does that to a service it didn't put in
     * the foreground once the app leaves the screen, and {@code stopService()} does it too. The running job may not
     * finish, and the queued ones are dropped without starting.
     *
     * @param queueDropped whether queued work was waiting, apart from any job that was running
     */
    @UiThread
    protected void onStoppedUnfinished(boolean queueDropped) {
    }

    /**
     * The work to be performed.
     *
     * @param intent The intent sent by {@link android.content.Context#startService(Intent)}
     */
    @WorkerThread
    protected abstract void onHandleIntent(@Nullable Intent intent);

    /**
     * The service is running and a new intent has been queued.
     *
     * @param intent The new intent that has been queued
     */
    @UiThread
    protected void onQueued(@Nullable Intent intent) {
    }

    /**
     * An intent is being processed. Called right before {@link #onHandleIntent(Intent)}.
     *
     * @param intent The intent to be processed.
     */
    @UiThread
    protected void onStartIntent(@Nullable Intent intent) {
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}