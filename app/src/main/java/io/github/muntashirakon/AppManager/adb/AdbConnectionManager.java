// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

public class AdbConnectionManager extends AbsAdbConnectionManager {
    public static final String TAG = AdbConnectionManager.class.getSimpleName();

    public static final String ADB_KEY_ALIAS = "adb_rsa";

    /**
     * Port of upstream a488f27a2: a pairing attempt that hangs (Wi-Fi gone mid-pairing, a broken
     * TLS stack) gives up after this long instead of leaving the pairing notification stuck.
     */
    @VisibleForTesting
    static final long PAIRING_ATTEMPT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final int ADB_REACH_TIMEOUT_MILLIS = 3000;
    private static final ExecutorService sPairingExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ADB pairing");
        // A pairing stuck in the platform's TLS code must not keep the process alive
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The outcome of one pairing attempt, numbered: LiveData hands a new observer the last value it
     * holds, which can be the outcome of a pairing from before the observer started waiting.
     */
    public static final class PairingResult {
        public final long number;
        @Nullable
        public final Exception error;

        PairingResult(long number, @Nullable Exception error) {
            this.number = number;
            this.error = error;
        }
    }

    private static final AtomicLong sPairingResults = new AtomicLong();
    // Shared by every instance, since a timed-out attempt replaces the instance
    private static final MutableLiveData<PairingResult> sPairingObserver = new MutableLiveData<>();

    private static AdbConnectionManager sInstance;

    public static synchronized AdbConnectionManager getInstance() throws Exception {
        // Synchronized: concurrent first-callers could otherwise both construct an
        // AdbConnectionManager and race in KeyStoreManager#addKeyPair, where the
        // second insert of ADB_KEY_ALIAS fails or overwrites the first.
        if (sInstance == null) {
            sInstance = new AdbConnectionManager();
        }
        return sInstance;
    }

    @NonNull
    private final KeyPair mKeyPair;
    private final Object mTargetLock = new Object();
    // Where the open connection goes, guarded by mTargetLock
    @Nullable
    private String mTargetHost;
    private int mTargetPort = -1;

    public AdbConnectionManager() throws Exception {
        setApi(Build.VERSION.SDK_INT);
        KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
        KeyPair keyPair = keyStoreManager.getKeyPairNoThrow(ADB_KEY_ALIAS);
        if (keyPair == null) {
            String subject = "CN=App Manager";
            keyPair = KeyStoreUtils.generateRSAKeyPair(subject, 2048, System.currentTimeMillis() + 86400000);
            keyStoreManager.addKeyPair(ADB_KEY_ALIAS, keyPair, true);
        }
        mKeyPair = keyPair;
    }

    @VisibleForTesting
    AdbConnectionManager(@NonNull KeyPair keyPair) {
        setApi(Build.VERSION.SDK_INT);
        mKeyPair = keyPair;
    }

    /**
     * Whether the open connection goes to this host and port.
     */
    public boolean isConnectedTo(@NonNull String host, int port) {
        synchronized (mTargetLock) {
            return port == mTargetPort && host.equals(mTargetHost) && isConnected();
        }
    }

    /**
     * Returns false when a connection to this host and port is already open. A connection to
     * anywhere else is closed first: libadb only checks that some connection is open, so a new
     * Wireless debugging port kept going to the old one.
     * <p>
     * libadb opens a plain Socket with no timeout. A host that drops the connection instead of
     * refusing it (10.0.2.2 on an emulator with nothing listening) held the first launch on
     * "Initializing..." for about two and a half minutes.
     */
    @Override
    public boolean connect(@NonNull String host, int port)
            throws IOException, InterruptedException, AdbPairingRequiredException {
        synchronized (mTargetLock) {
            if (isConnected()) {
                if (port == mTargetPort && host.equals(mTargetHost)) {
                    return false;
                }
                disconnect();
            }
            mTargetHost = null;
            mTargetPort = -1;
            ensureReachable(host, port);
            boolean connected = super.connect(host, port);
            if (connected) {
                mTargetHost = host;
                mTargetPort = port;
            }
            return connected;
        }
    }

    @VisibleForTesting
    static void ensureReachable(@NonNull String host, int port) throws ConnectException {
        if (!AdbTcpipProbe.isPortReachable(host, port, ADB_REACH_TIMEOUT_MILLIS)) {
            throw new ConnectException("Nothing answered at " + host + ":" + port);
        }
    }

    @NonNull
    public static LiveData<PairingResult> getPairingObserver() {
        return sPairingObserver;
    }

    /**
     * The number of the last pairing outcome published. A waiter ignores outcomes up to this one.
     */
    public static long getLastPairingResultNumber() {
        return sPairingResults.get();
    }

    @VisibleForTesting
    static void publishPairingResult(@Nullable Exception error) {
        sPairingObserver.postValue(new PairingResult(sPairingResults.incrementAndGet(), error));
    }

    @WorkerThread
    public void pairLiveData(@NonNull String host, int port, @NonNull String pairingCode) throws Exception {
        try {
            ThreadUtils.ensureWorkerThread();
            Future<Boolean> pairingTask = sPairingExecutor.submit(() -> pair(host, port, pairingCode));
            try {
                if (!awaitPairingAttempt(pairingTask, PAIRING_ATTEMPT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw new IOException("ADB pairing failed");
                }
            } catch (SocketTimeoutException | InterruptedException e) {
                discardInstance(this);
                throw e;
            }
            ServerConfig.setLastAdbPairing(host, port);
            publishPairingResult(null);
        } catch (Exception e) {
            Log.w(TAG, "Pairing failed.", e);
            publishPairingResult(e);
            throw e;
        }
    }

    public void notifyPairingCancelled() {
        publishPairingResult(new Exception("Pairing cancelled"));
    }

    private static synchronized void discardInstance(@NonNull AdbConnectionManager instance) {
        if (sInstance == instance) {
            // libadb runs one operation at a time on a manager, so a pairing stuck in it would
            // block every later connect made through the same instance
            sInstance = null;
        }
    }

    @VisibleForTesting
    static <T> T awaitPairingAttempt(@NonNull Future<T> pairingTask, long timeout, @NonNull TimeUnit unit)
            throws Exception {
        try {
            return pairingTask.get(timeout, unit);
        } catch (TimeoutException e) {
            pairingTask.cancel(true);
            SocketTimeoutException timeoutException = new SocketTimeoutException("ADB pairing timed out");
            timeoutException.initCause(e);
            throw timeoutException;
        } catch (InterruptedException e) {
            pairingTask.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * A copy of the stored key for each caller. libadb destroys the key of every connection it
     * closes, and the stored one is Bouncy Castle's when it comes from the keystore, so it really
     * goes: after a switch to a new port each later connection failed its TLS handshake with
     * "key destroyed", or its authentication on a plain port.
     */
    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        PrivateKey key = mKeyPair.getPrivateKey();
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            return key;
        }
        try {
            return KeyFactory.getInstance(key.getAlgorithm()).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "Could not copy the ADB key", e);
            return key;
        }
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return mKeyPair.getCertificate();
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "AppManager";
    }
}
