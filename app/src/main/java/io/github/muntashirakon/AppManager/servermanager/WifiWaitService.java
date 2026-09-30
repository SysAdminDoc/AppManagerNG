// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Restores the wireless ADB connection after a boot, once Wi-Fi is up. Port of upstream 22d439d61
 * and 355813cae: a transient failure is retried a few times on the same network, a network that
 * replaced the one being tried is tried at once, and the service goes away when the mode changes.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class WifiWaitService extends Service {
    private static final String TAG = WifiWaitService.class.getSimpleName();
    private static final long RETRY_DELAY_MILLIS = 2_000;
    private static final long NETWORK_WAIT_MILLIS = 120_000;
    @VisibleForTesting
    static final int MAX_RETRY_ATTEMPTS = 5;
    /**
     * Retries start over on each new network, so a Wi-Fi that keeps dropping and coming back
     * would keep the service trying for good without a limit across networks too.
     */
    @VisibleForTesting
    static final int MAX_TOTAL_ATTEMPTS = 30;
    /**
     * On a network Wireless debugging hasn't been allowed on, Android asks the user and switches it
     * back off right after it was switched on.
     */
    private static final long TURNED_BACK_OFF_WINDOW_MILLIS = 3_000;
    private static final long TURNED_BACK_OFF_POLL_MILLIS = 250;
    @VisibleForTesting
    static final long APPROVAL_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(10);
    @VisibleForTesting
    static final String APPROVAL_NOTIFICATION_TAG = "wireless_debugging_approval";
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.WIFI_WAIT_SERVICE";

    @VisibleForTesting
    enum ConnectionResult {
        SUCCESS,
        RETRY,
        TERMINAL_FAILURE,
        MODE_CHANGED,
        // Android wants Wireless debugging allowed on this network first
        NEEDS_APPROVAL
    }

    private final Object mStateLock = new Object();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Network mWifiNetwork;
    private final Runnable mNetworkWaitTimeout = () -> {
        Log.w(TAG, "Autoconnect failed: Wi-Fi didn't come back");
        finishService();
    };
    private final Runnable mApprovalTimeout = () -> {
        Log.w(TAG, "Autoconnect failed: Wireless debugging wasn't allowed on this network");
        finishService();
    };
    private final Runnable mRetryRunnable = () -> {
        Network network;
        synchronized (mStateLock) {
            network = mWifiNetwork;
        }
        if (network != null) {
            connectAdbWifi(network);
        }
    };

    private final ConnectivityManager.NetworkCallback mNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.d(TAG, "Wi-Fi network available");
        }

        @Override
        public void onLost(@NonNull Network network) {
            Log.d(TAG, "Network lost");
            synchronized (mStateLock) {
                if (network.equals(mWifiNetwork)) {
                    mWifiNetwork = null;
                    mRetryCount = 0;
                    mSwitchedOn = false;
                    mHandler.removeCallbacks(mRetryRunnable);
                    // Wi-Fi was up once, so the boot is behind us. Wait a while for it to come
                    // back, not for as long as the notification can stay up.
                    mHandler.removeCallbacks(mNetworkWaitTimeout);
                    mHandler.postDelayed(mNetworkWaitTimeout, NETWORK_WAIT_MILLIS);
                }
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                                          @NonNull NetworkCapabilities networkCapabilities) {
            if (!isWifiNetwork(networkCapabilities)) {
                return;
            }
            synchronized (mStateLock) {
                if (network.equals(mWifiNetwork)) {
                    // Signal strength and the like change all the time. The network already has an
                    // attempt running or a retry waiting, and starting over would skip the delay.
                    return;
                }
                mWifiNetwork = network;
                mRetryCount = 0;
                // Android trusts Wireless debugging per access point, so a new network may ask again
                mSwitchedOn = false;
                mHandler.removeCallbacks(mNetworkWaitTimeout);
            }
            connectAdbWifi(network);
        }
    };
    private ConnectivityManager mConnectivityManager;
    @Nullable
    private Future<?> mConnectionTask;
    private boolean mConnecting;
    private boolean mCallbackRegistered;
    private boolean mDestroyed;
    private int mRetryCount;
    private int mAttempts;
    private int mLastStartId;
    private WirelessDebuggingSwitch mSwitch;
    // This run switched Wireless debugging on for the current network
    private volatile boolean mSwitchedOn;
    // Main thread only
    private boolean mAwaitingApproval;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Wi-Fi Wait Service",
                NotificationManagerCompat.IMPORTANCE_LOW);
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mSwitch = new WirelessDebuggingSwitch.SystemSetting(this, mHandler);
    }

    @VisibleForTesting
    void setWirelessDebuggingSwitch(@NonNull WirelessDebuggingSwitch wirelessDebuggingSwitch) {
        mSwitch = wirelessDebuggingSwitch;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.waiting_for_wifi))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        // Android 15 doesn't let a boot receiver start a data sync service
        ForegroundService.start(this, NotificationUtils.nextNotificationId(null),
                notification, ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        mLastStartId = startId;

        if (!isWirelessAdbMode() || LocalServer.alive(getApplicationContext())) {
            finishService();
            return START_NOT_STICKY;
        }

        registerNetworkCallback();

        return START_NOT_STICKY; // Don't restart if killed
    }

    private void registerNetworkCallback() {
        synchronized (mStateLock) {
            if (mCallbackRegistered || mDestroyed) {
                return;
            }
            mCallbackRegistered = true;
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            try {
                mConnectivityManager.registerNetworkCallback(networkRequest, mNetworkCallback);
                Log.d(TAG, "Network callback registered");
            } catch (Exception e) {
                mCallbackRegistered = false;
                Log.e(TAG, "Failed to register network callback", e);
                finishService();
            }
        }
    }

    private void connectAdbWifi(@NonNull Network network) {
        if (!isWirelessAdbMode()) {
            finishService();
            return;
        }
        FutureTask<Void> task = new FutureTask<>(() -> {
            ConnectionResult result = runConnectionAttempt(this::doConnectAdbWifi);
            mHandler.post(() -> handleConnectionResult(network, result));
        }, null);
        boolean outOfAttempts;
        synchronized (mStateLock) {
            if (mDestroyed || mConnecting || !network.equals(mWifiNetwork)) {
                return;
            }
            outOfAttempts = mAttempts >= MAX_TOTAL_ATTEMPTS;
            if (!outOfAttempts) {
                ++mAttempts;
                mConnecting = true;
                // Network callbacks come on another thread than the results, so the task is
                // recorded together with the flag
                mConnectionTask = task;
                mHandler.removeCallbacks(mRetryRunnable);
            }
        }
        if (outOfAttempts) {
            Log.w(TAG, "Autoconnect failed: gave up after " + MAX_TOTAL_ATTEMPTS + " attempts");
            finishService();
            return;
        }
        ThreadUtils.postOnBackgroundThread(task);
    }

    /**
     * An attempt that throws still reports back, or the service would count it as running forever
     * and ignore every network after it.
     */
    @VisibleForTesting
    @WorkerThread
    @NonNull
    static ConnectionResult runConnectionAttempt(@NonNull Callable<ConnectionResult> attempt) {
        try {
            return attempt.call();
        } catch (Throwable th) {
            Log.e(TAG, "Autoconnect failed", th);
            return ConnectionResult.TERMINAL_FAILURE;
        }
    }

    @WorkerThread
    @NonNull
    private ConnectionResult doConnectAdbWifi() {
        Context context = getApplicationContext();
        if (!isWirelessAdbMode()) {
            return ConnectionResult.MODE_CHANGED;
        }

        boolean wasOn = mSwitch.isOn();
        if (!wasOn && mSwitchedOn) {
            // Switched on earlier on this network and off again: Android wants the network allowed,
            // and switching it on again would only ask again
            return ConnectionResult.NEEDS_APPROVAL;
        }
        if (!mSwitch.switchOn()) {
            Log.w(TAG, "Autoconnect deferred: Could not enable wireless debugging.");
            return ConnectionResult.RETRY;
        }
        if (!wasOn) {
            mSwitchedOn = true;
            if (turnedBackOff()) {
                Log.w(TAG, "Autoconnect paused: Wireless debugging has to be allowed on this network");
                return ConnectionResult.NEEDS_APPROVAL;
            }
        }
        if (!isWirelessAdbMode()) {
            return ConnectionResult.MODE_CHANGED;
        }

        int status = Ops.autoConnectWirelessDebuggingInBackground(context);
        if (status == Ops.STATUS_SUCCESS) {
            Log.i(TAG, "Autoconnect success!");
            return ConnectionResult.SUCCESS;
        } else if (isRetryableStatus(status)) {
            Log.w(TAG, "Autoconnect deferred: status " + status);
            return ConnectionResult.RETRY;
        }
        Log.w(TAG, "Autoconnect failed: status " + status);
        return ConnectionResult.TERMINAL_FAILURE;
    }

    @WorkerThread
    private boolean turnedBackOff() {
        long deadline = SystemClock.elapsedRealtime() + TURNED_BACK_OFF_WINDOW_MILLIS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!mSwitch.isOn()) {
                return true;
            }
            SystemClock.sleep(TURNED_BACK_OFF_POLL_MILLIS);
        }
        return !mSwitch.isOn();
    }

    /**
     * Android is asking the user to allow Wireless debugging on this network. One notification
     * says so, and the reconnect goes on by itself once the setting comes on.
     */
    @MainThread
    private void waitForApproval() {
        mHandler.removeCallbacks(mApprovalTimeout);
        mHandler.postDelayed(mApprovalTimeout, APPROVAL_WAIT_MILLIS);
        if (mAwaitingApproval) {
            return;
        }
        mAwaitingApproval = true;
        mSwitch.observe(this::onWirelessDebuggingChanged);
        postApprovalNotification();
        // It may have been allowed before the observer was in place
        onWirelessDebuggingChanged();
    }

    @MainThread
    private void onWirelessDebuggingChanged() {
        if (!mAwaitingApproval || !mSwitch.isOn()) {
            return;
        }
        mAwaitingApproval = false;
        mHandler.removeCallbacks(mApprovalTimeout);
        NotificationUtils.cancelHighPriorityNotification(this, APPROVAL_NOTIFICATION_TAG);
        Network network;
        synchronized (mStateLock) {
            network = mWifiNetwork;
        }
        if (network != null) {
            connectAdbWifi(network);
        }
    }

    private void postApprovalNotification() {
        Intent settings = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openSettings = PendingIntentCompat.getActivity(this, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
        CharSequence text = getText(R.string.wireless_debugging_allow_network_text);
        NotificationUtils.displayHighPriorityNotification(this, APPROVAL_NOTIFICATION_TAG, builder -> builder
                .setSmallIcon(R.drawable.ic_default_notification)
                .setContentTitle(getText(R.string.wireless_debugging_allow_network_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openSettings)
                .setAutoCancel(true)
                .build());
    }

    @VisibleForTesting
    static boolean isWifiNetwork(@NonNull NetworkCapabilities capabilities) {
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * Statuses another try a moment later can fix, such as no Wireless Debugging port found yet.
     * Pairing, permissions and the local network grant need the user, and a server that won't
     * start or answer was already retried and reported by the server manager.
     */
    @VisibleForTesting
    static boolean isRetryableStatus(@Ops.Status int status) {
        return status == Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED
                || status == Ops.STATUS_FAILURE;
    }

    private void handleConnectionResult(@NonNull Network network, @NonNull ConnectionResult result) {
        boolean wirelessAdbMode = isWirelessAdbMode();
        NextStep step;
        Network currentNetwork;
        synchronized (mStateLock) {
            if (mDestroyed) {
                return;
            }
            mConnecting = false;
            mConnectionTask = null;
            currentNetwork = mWifiNetwork;
            step = nextStep(network, currentNetwork, result, mRetryCount, wirelessAdbMode);
            if (step == NextStep.RETRY_LATER) {
                ++mRetryCount;
            }
        }
        switch (step) {
            case TRY_REPLACEMENT:
                // Wi-Fi changed while the last attempt ran. Try the new network now instead of
                // stopping on the stale attempt's result.
                connectAdbWifi(currentNetwork);
                break;
            case RETRY_LATER:
                mHandler.postDelayed(mRetryRunnable, RETRY_DELAY_MILLIS);
                break;
            case WAIT_FOR_NETWORK:
                Log.d(TAG, "Wi-Fi went away during the attempt, waiting for it to come back");
                break;
            case WAIT_FOR_APPROVAL:
                waitForApproval();
                break;
            case FINISH:
            default:
                if (wirelessAdbMode && result == ConnectionResult.RETRY) {
                    Log.w(TAG, "Autoconnect failed: retry limit reached");
                }
                finishService();
                break;
        }
    }

    @VisibleForTesting
    enum NextStep {
        FINISH,
        TRY_REPLACEMENT,
        RETRY_LATER,
        WAIT_FOR_NETWORK,
        WAIT_FOR_APPROVAL
    }

    /**
     * What to do once an attempt on {@code attemptedNetwork} has ended, {@code currentNetwork} being
     * the Wi-Fi network now, if any, and {@code retryCount} the retries it has had so far.
     */
    @VisibleForTesting
    @NonNull
    static NextStep nextStep(@NonNull Network attemptedNetwork, @Nullable Network currentNetwork,
                             @NonNull ConnectionResult result, int retryCount, boolean wirelessAdbMode) {
        if (!wirelessAdbMode) {
            return NextStep.FINISH;
        }
        if (result == ConnectionResult.NEEDS_APPROVAL) {
            return NextStep.WAIT_FOR_APPROVAL;
        }
        if (result != ConnectionResult.RETRY) {
            return NextStep.FINISH;
        }
        if (currentNetwork == null) {
            // The network went away during the attempt, and losing it started the clock on its return
            return NextStep.WAIT_FOR_NETWORK;
        }
        if (shouldTryReplacementNetwork(attemptedNetwork, currentNetwork, true)) {
            return NextStep.TRY_REPLACEMENT;
        }
        return retryCount < MAX_RETRY_ATTEMPTS ? NextStep.RETRY_LATER : NextStep.FINISH;
    }

    @VisibleForTesting
    static boolean shouldTryReplacementNetwork(@NonNull Network attemptedNetwork,
                                               @Nullable Network currentNetwork,
                                               boolean retryable) {
        return retryable && currentNetwork != null && !attemptedNetwork.equals(currentNetwork);
    }

    private boolean isWirelessAdbMode() {
        return Ops.MODE_ADB_WIFI.equals(Ops.getMode());
    }

    private void finishService() {
        unregisterNetworkCallback();
        stopSelfResult(mLastStartId);
    }

    private void unregisterNetworkCallback() {
        synchronized (mStateLock) {
            if (!mCallbackRegistered) {
                return;
            }
            mCallbackRegistered = false;
        }
        try {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            Log.d(TAG, "Network callback unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering callback", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Future<?> connectionTask;
        synchronized (mStateLock) {
            mDestroyed = true;
            mWifiNetwork = null;
            connectionTask = mConnectionTask;
            mConnectionTask = null;
        }
        mHandler.removeCallbacks(mRetryRunnable);
        mHandler.removeCallbacks(mNetworkWaitTimeout);
        mHandler.removeCallbacks(mApprovalTimeout);
        mSwitch.stopObserving();
        if (mAwaitingApproval) {
            mAwaitingApproval = false;
            NotificationUtils.cancelHighPriorityNotification(this, APPROVAL_NOTIFICATION_TAG);
        }
        if (connectionTask != null) {
            connectionTask.cancel(true);
        }
        unregisterNetworkCallback();
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }
}
