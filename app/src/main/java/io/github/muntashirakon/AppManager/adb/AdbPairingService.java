// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static io.github.muntashirakon.AppManager.types.ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.RemoteInput;
import androidx.core.app.ServiceCompat;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adb.android.AdbMdns;

// This works as follows:
// 1. Start searching for a pairing port
// 2. A port is found, ask to enter a pairing code
// 3. Start pairing
// 4. Exit with result, or ask to retry
@RequiresApi(Build.VERSION_CODES.R)
public class AdbPairingService extends Service {
    public static final String TAG = AdbPairingService.class.getSimpleName();
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.ADB_PAIRING";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_START_SEARCHING = BuildConfig.APPLICATION_ID + ".action.START_SEARCHING";
    public static final String ACTION_STOP_SEARCHING = BuildConfig.APPLICATION_ID + ".action.STOP_SEARCHING";
    public static final String ACTION_START_PAIRING = BuildConfig.APPLICATION_ID + ".action.ENTER_CODE";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PAIRING_CODE = "pairing_code";
    public static final String INPUT_CODE = "code";

    /**
     * Port of upstream 2f2b31e89: a search nobody finishes stops on its own instead of keeping the
     * notification and the mDNS search going.
     */
    private static final long SEARCH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private NotificationCompat.Builder mNotificationBuilder;
    private boolean mStartedSearching = false;
    private AdbMdns mAdbMdnsPairing;
    // Each search has its number, so a port found by an earlier one can't come back. The LiveData
    // this replaces handed a new search the last port at once, from the pairing dialog before.
    private volatile int mSearchGeneration;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSearchTimeout = () -> {
        Log.w(TAG, "Pairing timed out.");
        stopPairingService();
    };

    @NonNull
    public static Intent getStartSearchingIntent(@NonNull Context context) {
        return new Intent(context, AdbPairingService.class).setAction(ACTION_START_SEARCHING);
    }

    @NonNull
    public static Intent getStartPairingIntent(@NonNull Context context, @NonNull AdbPairingRequest request) {
        return new Intent(context, AdbPairingService.class)
                .setAction(ACTION_START_PAIRING)
                .putExtra(EXTRA_PORT, request.getPort())
                .putExtra(EXTRA_PAIRING_CODE, request.getPairingCode());
    }

    @NonNull
    public static Intent getStopSearchingIntent(@NonNull Context context) {
        return new Intent(context, AdbPairingService.class).setAction(ACTION_STOP_SEARCHING);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        NotificationChannelCompat notificationChannel = new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("ADB Pairing")
                .setSound(null, null)
                .setShowBadge(false)
                .build();
        notificationManager.createNotificationChannel(notificationChannel);
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setDefaults(Notification.DEFAULT_ALL)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setContentTitle(getString(R.string.wireless_debugging))
                .setSubText(getText(R.string.wireless_debugging))
                .setSmallIcon(R.drawable.ic_default_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Invalid intent
            return START_NOT_STICKY;
        }
        switch (intent.getAction()) {
            // Not redelivered: a restarted process has no one waiting on the pairing, and the
            // pairing code in the intent is only good for the dialog it came from
            case ACTION_START_SEARCHING:
                startSearching();
                return START_NOT_STICKY;
            case ACTION_START_PAIRING:
                int port = intent.getIntExtra(EXTRA_PORT, -1);
                String code = intent.getStringExtra(EXTRA_PAIRING_CODE);
                Bundle remoteInputs = RemoteInput.getResultsFromIntent(intent);
                if (code == null && remoteInputs != null) {
                    code = remoteInputs.getCharSequence(INPUT_CODE, "").toString();
                }
                AdbPairingRequest request = AdbPairingRequest.create(port, code);
                if (request != null) {
                    startPairing(request.getPort(), request.getPairingCode());
                } else if (AdbPairingRequest.isValidPort(port)) {
                    inputPairingCode(port);
                } else {
                    // Wrong inputs, continue searching
                    startSearching();
                }
                return START_NOT_STICKY;
            case ACTION_STOP_SEARCHING:
                stopPairingService();
            default:
                return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mSearchTimeout);
        if (mStartedSearching) {
            // Still looking for a port, hence the pairing wasn't successful
            // Fail intentionally to avoid looping forever
            Log.i(TAG, "Stop searching for an active port...");
            cancelPairing();
            stopSearching();
        }
    }

    @MainThread
    private void startSearching() {
        if (mStartedSearching) {
            return;
        }
        mStartedSearching = true;
        int generation = ++mSearchGeneration;
        restartSearchTimeout();
        AdbPairingSession.searching();
        // Each search gets its own listener, so a port an earlier one reports late still carries
        // that search's number and is ignored
        mAdbMdnsPairing = new AdbMdns(getApplication(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (hostAddress, port) -> {
            if (port != -1) {
                ThreadUtils.postOnMainThread(() -> onPairingPortFound(generation, port));
            }
        });
        PendingIntent stopPendingIntent = getStopIntent();
        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_stop_searching), stopPendingIntent).build();
        mNotificationBuilder.setContentText(getText(R.string.adb_pairing_searching_for_port))
                .clearActions()
                .addAction(stopAction);
        ServiceCompat.startForeground(this, NOTIFICATION_ID, mNotificationBuilder.build(),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        mAdbMdnsPairing.start();
    }

    /**
     * A port only counts for the search that found it and while that search is still running.
     */
    @MainThread
    @VisibleForTesting
    void onPairingPortFound(int generation, int port) {
        if (!mStartedSearching || generation != mSearchGeneration) {
            Log.d(TAG, "Ignoring port %d from an earlier search", port);
            return;
        }
        Log.i(TAG, "Found port %d", port);
        inputPairingCode(port);
    }

    @VisibleForTesting
    int getSearchGeneration() {
        return mSearchGeneration;
    }

    @AnyThread
    private void restartSearchTimeout() {
        mHandler.removeCallbacks(mSearchTimeout);
        mHandler.postDelayed(mSearchTimeout, SEARCH_TIMEOUT_MILLIS);
    }

    @MainThread
    private void inputPairingCode(int port) {
        // A port found late in the search still leaves the user the full time to type the code
        restartSearchTimeout();
        AdbPairingSession.portFound(port);
        Intent inputIntent = new Intent(this, getClass())
                .setAction(ACTION_START_PAIRING)
                .putExtra(EXTRA_PORT, port);
        // Mutable is required: RemoteInput needs the system to fill the typed pairing code in.
        // The base intent already names this service explicitly, so nothing else can be reached.
        PendingIntent inputPendingIntent = PendingIntentCompat.getForegroundService(this, 2, inputIntent, PendingIntent.FLAG_UPDATE_CURRENT, true);
        Intent inAppInputIntent = new Intent(this, AdbPairingInputActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent inAppInputPendingIntent = PendingIntentCompat.getActivity(this, 4, inAppInputIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        RemoteInput pairingCodeInput = new RemoteInput.Builder(INPUT_CODE)
                .setLabel(getString(R.string.adb_pairing_pairing_code))
                .build();
        NotificationCompat.Action inputAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_input_pairing_code), inputPendingIntent)
                .addRemoteInput(pairingCodeInput)
                .build();
        NotificationCompat.Action inAppInputAction = new NotificationCompat.Action.Builder(null,
                getString(R.string.adb_pairing_input_code_in_app), inAppInputPendingIntent)
                .build();
        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_found_pairing_service_with_port, port))
                .setContentIntent(inAppInputPendingIntent)
                .clearActions()
                .addAction(inputAction)
                .addAction(inAppInputAction);
        if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mNotificationBuilder.build());
        }
    }

    @MainThread
    private void startPairing(int port, String code) {
        // The pairing has its own time limit, and the search's must not cancel it partway
        mHandler.removeCallbacks(mSearchTimeout);
        AdbPairingSession.pairing(port);
        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_pairing_in_progress))
                .clearActions();
        ServiceCompat.startForeground(this, NOTIFICATION_ID, mNotificationBuilder.build(),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean isSuccess;
            boolean timedOut = false;
            try {
                AdbConnectionManager.getInstance().pairLiveData(ServerConfig.getAdbHost(this), port, code);
                isSuccess = true;
            } catch (Exception e) {
                Log.w(TAG, "Pairing failed.", e);
                isSuccess = false;
                timedOut = e instanceof SocketTimeoutException;
            }
            ThreadUtils.postOnMainThread(this::stopSearching);
            if (isSuccess) {
                AdbPairingSession.succeeded(port);
                mNotificationBuilder.setContentText(getString(R.string.paired_successfully)).clearActions();
                stopSelf();
            } else {
                if (timedOut) {
                    AdbPairingSession.timedOut(port);
                } else {
                    AdbPairingSession.failed(port);
                }
                PendingIntent deleteIntent = getStopIntent();
                Intent retryIntent = new Intent(this, getClass()).setAction(ACTION_START_SEARCHING);
                PendingIntent retryPendingIntent = PendingIntentCompat.getForegroundService(this, 3, retryIntent, 0, false);
                NotificationCompat.Action retryAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_retry_pairing), retryPendingIntent).build();
                mNotificationBuilder.setContentText(getString(timedOut ? R.string.adb_pairing_timed_out : R.string.failed))
                        .clearActions()
                        .setDeleteIntent(deleteIntent)
                        .addAction(retryAction);
            }
            if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mNotificationBuilder.build());
            }
        });
    }

    @MainThread
    private void stopSearching() {
        if (!mStartedSearching) {
            return;
        }
        mStartedSearching = false;
        mAdbMdnsPairing.stop();
    }

    @MainThread
    private void stopPairingService() {
        mHandler.removeCallbacks(mSearchTimeout);
        cancelPairing();
        stopSearching();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @NonNull
    private PendingIntent getStopIntent() {
        return PendingIntentCompat.getForegroundService(this, 1, getStopSearchingIntent(this), 0, false);
    }

    private void cancelPairing() {
        AdbPairingSession.cancelled();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                AdbConnectionManager.getInstance().notifyPairingCancelled();
            } catch (Exception e) {
                Log.w(TAG, "Could not notify pairing cancellation.", e);
            }
        });
    }
}
