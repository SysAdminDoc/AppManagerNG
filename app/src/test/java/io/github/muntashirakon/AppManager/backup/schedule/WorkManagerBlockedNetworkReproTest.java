// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.impl.constraints.ConstraintListener;
import androidx.work.impl.constraints.NetworkState;
import androidx.work.impl.constraints.controllers.NetworkConnectedController;
import androidx.work.impl.constraints.trackers.ConstraintTracker;
import androidx.work.impl.constraints.trackers.NetworkStateTrackerKt;
import androidx.work.impl.utils.SerialExecutorImpl;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Shows the WorkManager 2.10.5 defect the scheduled backup works around: on Android 15 and later
 * a network the system blocks for this app still meets the "connected" constraint once the
 * default network reports its capabilities. Fixed upstream in 2.11.1 (b/465016918,
 * b/452081708), which raises minSdk to 23.
 */
@RunWith(RobolectricTestRunner.class)
public class WorkManagerBlockedNetworkReproTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final ConnectivityManager mCm = mContext.getSystemService(ConnectivityManager.class);

    @Test
    @Config(sdk = 35)
    public void aBlockedNetworkMeetsTheConnectedConstraintOnceItsCapabilitiesArrive() throws Exception {
        shadowOf(mCm).setActiveNetworkInfo(blocked());
        Executor direct = Runnable::run;
        SerialExecutor serial = new SerialExecutorImpl(direct);
        TaskExecutor taskExecutor = new TaskExecutor() {
            @NonNull
            @Override
            public Executor getMainThreadExecutor() {
                return direct;
            }

            @NonNull
            @Override
            public SerialExecutor getSerialTaskExecutor() {
                return serial;
            }
        };
        ConstraintTracker<NetworkState> tracker = NetworkStateTrackerKt.NetworkStateTracker(mContext, taskExecutor);
        NetworkConnectedController controller = new NetworkConnectedController(tracker);
        List<NetworkState> seen = new ArrayList<>();
        ConstraintListener<NetworkState> listener = seen::add;
        tracker.addListener(listener);
        try {
            // Read when tracking starts, the block keeps the constraint unmet.
            assertFalse(tracker.getState().isConnected());
            assertTrue(isConstrained(controller, tracker.getState()));
            List<ConnectivityManager.NetworkCallback> callbacks = new ArrayList<>(shadowOf(mCm).getNetworkCallbacks());
            assertFalse("WorkManager listens to the default network", callbacks.isEmpty());

            // Android then reports the default network's capabilities, which don't carry the block.
            Network network = mCm.getActiveNetwork();
            assertNotNull(network);
            for (ConnectivityManager.NetworkCallback callback : callbacks) {
                callback.onCapabilitiesChanged(network, validatedInternet());
            }

            assertFalse("the app is still blocked", mCm.getActiveNetworkInfo().isConnected());
            assertTrue(tracker.getState().isConnected());
            assertFalse("so a network-bound backup may start without a network",
                    isConstrained(controller, tracker.getState()));
        } finally {
            tracker.removeListener(listener);
        }
    }

    /** The controller's own check, which WorkManager keeps protected. */
    private static boolean isConstrained(@NonNull NetworkConnectedController controller, @NonNull NetworkState state)
            throws ReflectiveOperationException {
        Method method = NetworkConnectedController.class.getDeclaredMethod("isConstrained", NetworkState.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, state);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    static NetworkInfo blocked() {
        return ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.BLOCKED, ConnectivityManager.TYPE_WIFI,
                0, true, NetworkInfo.State.DISCONNECTED);
    }

    @NonNull
    static NetworkCapabilities validatedInternet() {
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        return capabilities;
    }
}
