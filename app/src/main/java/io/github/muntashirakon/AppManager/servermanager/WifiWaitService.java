// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.app.Notification;
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
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
    @VisibleForTesting
    static final int MAX_RETRY_ATTEMPTS = 5;
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.WIFI_WAIT_SERVICE";

    private enum ConnectionResult {
        SUCCESS,
        RETRY,
        TERMINAL_FAILURE,
        MODE_CHANGED
    }

    private final Object mStateLock = new Object();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Network mWifiNetwork;
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
                    mHandler.removeCallbacks(mRetryRunnable);
                }
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                                          @NonNull NetworkCapabilities networkCapabilities) {
            if (isWifiNetwork(networkCapabilities)) {
                synchronized (mStateLock) {
                    if (!network.equals(mWifiNetwork)) {
                        mRetryCount = 0;
                    }
                    mWifiNetwork = network;
                }
                connectAdbWifi(network);
            }
        }
    };
    private ConnectivityManager mConnectivityManager;
    @Nullable
    private Future<?> mConnectionTask;
    private boolean mConnecting;
    private boolean mCallbackRegistered;
    private boolean mDestroyed;
    private int mRetryCount;
    private int mLastStartId;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Wi-Fi Wait Service",
                NotificationManagerCompat.IMPORTANCE_LOW);
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
        synchronized (mStateLock) {
            if (mDestroyed || mConnecting || !network.equals(mWifiNetwork)) {
                return;
            }
            mConnecting = true;
            mHandler.removeCallbacks(mRetryRunnable);
        }

        mConnectionTask = ThreadUtils.postOnBackgroundThread(() -> {
            ConnectionResult result = doConnectAdbWifi();
            mHandler.post(() -> handleConnectionResult(network, result));
        });
    }

    @WorkerThread
    @NonNull
    private ConnectionResult doConnectAdbWifi() {
        Context context = getApplicationContext();
        if (!isWirelessAdbMode()) {
            return ConnectionResult.MODE_CHANGED;
        }

        if (!AdbUtils.enableWirelessDebugging(context)) {
            Log.w(TAG, "Autoconnect deferred: Could not enable wireless debugging.");
            return ConnectionResult.RETRY;
        }
        if (!isWirelessAdbMode()) {
            return ConnectionResult.MODE_CHANGED;
        }

        int status = Ops.autoConnectWirelessDebugging(context);
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
        boolean retry;
        Network replacementNetwork;
        synchronized (mStateLock) {
            if (mDestroyed) {
                return;
            }
            mConnecting = false;
            mConnectionTask = null;
            replacementNetwork = shouldTryReplacementNetwork(network, mWifiNetwork,
                    result == ConnectionResult.RETRY) ? mWifiNetwork : null;
            retry = result == ConnectionResult.RETRY && network.equals(mWifiNetwork)
                    && ++mRetryCount <= MAX_RETRY_ATTEMPTS;
        }
        if (!isWirelessAdbMode() || result == ConnectionResult.MODE_CHANGED) {
            finishService();
        } else if (replacementNetwork != null) {
            // Wi-Fi changed while the last attempt ran. Try the new network now instead of
            // stopping on the stale attempt's result.
            connectAdbWifi(replacementNetwork);
        } else if (retry) {
            mHandler.postDelayed(mRetryRunnable, RETRY_DELAY_MILLIS);
        } else {
            if (result == ConnectionResult.RETRY) {
                Log.w(TAG, "Autoconnect failed: retry limit reached");
            }
            finishService();
        }
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
        if (connectionTask != null) {
            connectionTask.cancel(true);
        }
        unregisterNetworkCallback();
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }
}
