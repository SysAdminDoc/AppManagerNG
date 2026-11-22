// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * The connection to Dhizuku.
 *
 * <p>Dhizuku isn't an operating mode next to root, ADB and Shizuku. Those run AppManagerNG's
 * server as root or shell, and every privileged feature goes through it. Dhizuku can't do that:
 * its remote process runs as the Dhizuku app's own uid, which holds no shell permissions, so
 * {@code pm}, {@code am}, {@code cmd} and the IPC calls that need shell permissions all fail
 * there. What it can do is call Android's device policy and package installer APIs as the
 * device owner. So it's a capability layer: the operations listed in
 * {@link DhizukuCapabilities} check here when the current mode lacks the permission they need,
 * whatever that mode is.
 *
 * <p>Main-thread gates read the state the last check cached. Every operation checks again on its
 * worker thread before it calls Dhizuku, so a server that died or a consent the user took back
 * turns into the old behavior instead of a crash.
 */
public final class DhizukuSession {
    private static final String TAG = DhizukuSession.class.getSimpleName();
    private static final long REQUEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    /**
     * What the session reads from the device. Tests replace it with fakes.
     */
    @VisibleForTesting
    public interface Environment {
        @NonNull
        DhizukuBridge.Result probe(@NonNull Context context);

        /**
         * @return {@code null} below Android 6.0, where Dhizuku-API can't load
         */
        @Nullable
        DhizukuClient client();

        @NonNull
        DhizukuDevicePolicy newPolicy(@NonNull Context context, @NonNull DhizukuClient client) throws Exception;

        /**
         * {@code PackageManager#checkPermission}, used to confirm a permission change took effect.
         */
        int checkPermission(@NonNull Context context, @NonNull String permission, @NonNull String packageName);
    }

    private static final class SystemEnvironment implements Environment {
        @Nullable
        private DhizukuClient mClient;

        @NonNull
        @Override
        public DhizukuBridge.Result probe(@NonNull Context context) {
            return DhizukuBridge.probe(context);
        }

        @Nullable
        @Override
        public synchronized DhizukuClient client() {
            if (mClient == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mClient = new DhizukuApiClient();
            }
            return mClient;
        }

        @NonNull
        @Override
        public DhizukuDevicePolicy newPolicy(@NonNull Context context, @NonNull DhizukuClient client) throws Exception {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                throw new UnsupportedOperationException("Dhizuku needs Android 6.0 or later.");
            }
            return DhizukuDevicePolicyImpl.create(context, client);
        }

        @Override
        public int checkPermission(@NonNull Context context, @NonNull String permission, @NonNull String packageName) {
            return context.getPackageManager().checkPermission(permission, packageName);
        }
    }

    private static final Environment SYSTEM = new SystemEnvironment();

    @Nullable
    private static volatile Environment sTestEnvironment;
    @Nullable
    private static volatile DhizukuState sState;

    private DhizukuSession() {
    }

    @VisibleForTesting
    public static void setEnvironmentForTesting(@Nullable Environment environment) {
        sTestEnvironment = environment;
        sState = null;
    }

    @NonNull
    private static Environment env() {
        Environment environment = sTestEnvironment;
        return environment != null ? environment : SYSTEM;
    }

    /**
     * The state the last check found, or {@code null} when nothing checked yet.
     */
    @AnyThread
    @Nullable
    public static DhizukuState getCachedState() {
        return sState;
    }

    @AnyThread
    public static boolean isPermittedCached() {
        return sState == DhizukuState.PERMITTED;
    }

    /**
     * Look at the device again and connect to Dhizuku when it's the owner.
     */
    @WorkerThread
    @NonNull
    public static DhizukuState refresh(@NonNull Context context) {
        Environment environment = env();
        DhizukuBridge.Result probe = environment.probe(context);
        boolean connected = false;
        boolean permitted = false;
        // Forks serve the same protocol from their own package, so any owner gets a try
        if (probe.sdk >= DhizukuBridge.MIN_SUPPORTED_SDK && probe.ownerComponent != null) {
            DhizukuClient client = environment.client();
            if (client != null && client.connect(context)) {
                connected = true;
                permitted = client.isPermissionGranted();
            }
        }
        DhizukuState state = DhizukuState.of(probe.sdk, probe.isInstalled(), probe.isOfficialOwner(),
                connected, permitted);
        DhizukuState previous = sState;
        sState = state;
        if (previous != state) {
            Log.i(TAG, "Dhizuku state: %s", state);
        }
        return state;
    }

    @WorkerThread
    public static void refreshQuietly(@NonNull Context context) {
        try {
            refresh(context);
        } catch (Throwable e) {
            Log.w(TAG, "Could not check Dhizuku.", e);
        }
    }

    /**
     * Check again that Dhizuku answers and allows this app.
     */
    @WorkerThread
    public static boolean isReady(@NonNull Context context) {
        try {
            return refresh(context) == DhizukuState.PERMITTED;
        } catch (Throwable e) {
            Log.w(TAG, "Could not check Dhizuku.", e);
            return false;
        }
    }

    /**
     * The connected client, or {@code null} when Dhizuku doesn't answer or doesn't allow this app.
     */
    @WorkerThread
    @Nullable
    public static DhizukuClient permittedClient(@NonNull Context context) {
        return isReady(context) ? env().client() : null;
    }

    /**
     * A device policy manager acting as the owner, or {@code null} when Dhizuku doesn't answer
     * or doesn't allow this app.
     */
    @WorkerThread
    @Nullable
    public static DhizukuDevicePolicy policy(@NonNull Context context) {
        DhizukuClient client = permittedClient(context);
        if (client == null) {
            return null;
        }
        try {
            // Built for each call: the owner can change, and building one costs no IPC
            return env().newPolicy(context, client);
        } catch (Throwable e) {
            Log.w(TAG, "Could not reach the device policy service through Dhizuku.", e);
            return null;
        }
    }

    @WorkerThread
    public static int checkPermission(@NonNull Context context, @NonNull String permission,
                                      @NonNull String packageName) {
        return env().checkPermission(context, permission, packageName);
    }

    /**
     * Open Dhizuku's prompt for this app and report the state once the user answered.
     * {@code onFinished} runs on the main thread.
     */
    @AnyThread
    public static void requestPermission(@NonNull Context context, @NonNull Consumer<DhizukuState> onFinished) {
        Context appContext = context.getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            DhizukuState state = requestPermissionAndWait(appContext, REQUEST_TIMEOUT_MILLIS);
            ThreadUtils.postOnMainThread(() -> onFinished.accept(state));
        });
    }

    /**
     * Ask Dhizuku to allow this app and wait for the answer. Nothing is asked unless Dhizuku
     * answered and hasn't allowed this app yet.
     */
    @WorkerThread
    @NonNull
    public static DhizukuState requestPermissionAndWait(@NonNull Context context, long timeoutMillis) {
        DhizukuState state = refresh(context);
        if (state != DhizukuState.NOT_PERMITTED) {
            return state;
        }
        DhizukuClient client = env().client();
        if (client == null) {
            return state;
        }
        CountDownLatch answered = new CountDownLatch(1);
        try {
            client.requestPermission(granted -> answered.countDown());
            if (!answered.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Dhizuku didn't answer the request in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            Log.w(TAG, "Could not ask Dhizuku for permission.", e);
        }
        // Dhizuku's answer is the source of truth, whatever the callback said
        return refresh(context);
    }

    /**
     * Whether the package manager says {@code permission} is granted to {@code packageName}.
     */
    @WorkerThread
    static boolean isGranted(@NonNull Context context, @NonNull String permission, @NonNull String packageName) {
        return checkPermission(context, permission, packageName) == PackageManager.PERMISSION_GRANTED;
    }
}
