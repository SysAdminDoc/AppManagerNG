// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

// Copyright 2016 Zheng Li
public class LocalServer {
    @GuardedBy("lockObject")
    private static final Object sLock = new Object();

    @SuppressLint("StaticFieldLeak")
    @Nullable
    private static LocalServer sLocalServer;

    @GuardedBy("lockObject")
    @WorkerThread
    @NoOps(used = true)
    public static LocalServer getInstance() throws IOException, AdbPairingRequiredException {
        // Non-null check must be done outside the synchronised block to prevent deadlock on ADB over TCP mode.
        if (sLocalServer != null) return sLocalServer;
        synchronized (sLock) {
            try {
                Log.d("IPC", "Init: Local server");
                sLocalServer = new LocalServer();
            } finally {
                sLock.notifyAll();
            }
        }
        return sLocalServer;
    }

    public static void die() {
        synchronized (sLock) {
            try {
                if (sLocalServer != null) {
                    sLocalServer.destroy();
                }
            } finally {
                sLocalServer = null;
            }
        }
    }

    @WorkerThread
    @NoOps
    public static boolean alive(Context context) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(ServerConfig.getLocalServerHost(context),
                    ServerConfig.getLocalServerPort()), 1);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final LocalServerManager mLocalServerManager;

    @WorkerThread
    @NoOps(used = true)
    private LocalServer() throws IOException, AdbPairingRequiredException {
        mContext = ContextUtils.getDeContext(ContextUtils.getContext());
        mLocalServerManager = LocalServerManager.getInstance(mContext);
        // Initialise necessary files and permissions
        ServerConfig.init(mContext);
        // Start server if not already
        checkConnect();
    }

    private final Object mConnectLock = new Object();
    private boolean mConnectStarted = false;

    @GuardedBy("connectLock")
    @WorkerThread
    @NoOps(used = true)
    public void checkConnect() throws IOException, AdbPairingRequiredException {
        synchronized (mConnectLock) {
            if (mConnectStarted) {
                try {
                    mConnectLock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
            mConnectStarted = true;
            try {
                mLocalServerManager.start();
            } catch (IOException | AdbPairingRequiredException e) {
                mConnectStarted = false;
                mConnectLock.notify();
                logBootstrapFailureSignature(e);
                throw e;
            }
            mConnectStarted = false;
            mConnectLock.notify();
        }
    }

    /**
     * Emit a single-line bootstrap-failure signature when the privileged shell
     * handshake throws — captures device fingerprint, LineageOS property, root
     * mode, and the exception's class + cause chain so bug-reporters can paste
     * one line into an issue instead of a full audit log. Targets in particular
     * the LineageOS 23.2 / Android 16 root-binder regression (upstream AM
     * #1962, [S185]) where the SELinux denial in {@code system_server} kills
     * the handshake silently and users have no signal to act on.
     */
    private static void logBootstrapFailureSignature(@NonNull Exception e) {
        try {
            Log.e("IPC", buildBootstrapSignature("failed", e, -1, null));
        } catch (Throwable ignored) {
            // Diagnostic logging must never mask the original failure.
        }
    }

    @NonNull
    public static String buildBootstrapSignature(@NonNull String outcome,
                                                 @Nullable Throwable throwable,
                                                 long elapsedMillis,
                                                 @Nullable Shell.Result probeResult) {
        try {
            Integer probeStatusCode = null;
            String probeMessage = null;
            if (probeResult != null) {
                probeStatusCode = probeResult.getStatusCode();
                probeMessage = probeResult.getMessage();
            }
            return buildBootstrapSignature(outcome, throwable, elapsedMillis, probeStatusCode, probeMessage,
                    Build.MANUFACTURER, Build.PRODUCT, Build.DEVICE, Build.VERSION.SDK_INT, Build.ID,
                    SystemProperties.get("ro.lineage.version", ""), Ops.getMode(), Users.getSelfOrRemoteUid(),
                    Process.myUid());
        } catch (Throwable diagnosticFailure) {
            Throwable reported = throwable != null ? throwable : diagnosticFailure;
            return buildBootstrapSignature(outcome, reported, elapsedMillis, null, null,
                    "unknown", "unknown", "unknown", -1, "unknown", null, "unknown", -1, -1);
        }
    }

    @VisibleForTesting
    @NonNull
    static String buildBootstrapSignature(@NonNull String outcome,
                                          @Nullable Throwable throwable,
                                          long elapsedMillis,
                                          @Nullable Integer probeStatusCode,
                                          @Nullable String probeMessage,
                                          @NonNull String manufacturer,
                                          @NonNull String product,
                                          @NonNull String device,
                                          int sdk,
                                          @NonNull String buildId,
                                          @Nullable String lineage,
                                          @NonNull String mode,
                                          int remoteUid,
                                          int appUid) {
        StringBuilder sig = new StringBuilder("LocalServer bootstrap ")
                .append(outcome)
                .append(": ")
                .append(manufacturer).append('/')
                .append(product).append('/')
                .append(device)
                .append(" (sdk=").append(sdk)
                .append(", id=").append(buildId)
                .append(", mode=").append(mode)
                .append(", uid=").append(remoteUid)
                .append(", appUid=").append(appUid)
                .append(')');
        if (lineage != null && !lineage.isEmpty()) {
            sig.append(" [LineageOS ").append(lineage).append(']');
        }
        if (throwable != null) {
            sig.append(" — ").append(throwable.getClass().getSimpleName());
            String msg = singleLine(throwable.getMessage());
            if (msg != null && !msg.isEmpty()) {
                sig.append(": ").append(msg);
            }
            Throwable cause = throwable.getCause();
            if (cause != null && cause != throwable) {
                sig.append(" (caused by ").append(cause.getClass().getSimpleName());
                String causeMsg = singleLine(cause.getMessage());
                if (causeMsg != null && !causeMsg.isEmpty()) {
                    sig.append(": ").append(causeMsg);
                }
                sig.append(')');
            }
        } else {
            sig.append(" — OK");
        }
        if (elapsedMillis >= 0) {
            sig.append(" elapsed=").append(elapsedMillis).append("ms");
        }
        if (probeStatusCode != null) {
            sig.append(" probeExit=").append(probeStatusCode);
        }
        String probe = singleLine(probeMessage);
        if (probe != null && !probe.isEmpty()) {
            sig.append(" probeOutput=").append(probe);
        }
        return sig.toString();
    }

    @Nullable
    private static String singleLine(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String compact = value.trim().replaceAll("\\s+", " ");
        if (compact.length() > 180) {
            return compact.substring(0, 177) + "...";
        }
        return compact;
    }

    public Shell.Result runCommand(String command) throws IOException {
        ShellCaller shellCaller = new ShellCaller(command);
        CallerResult callerResult = exec(shellCaller);
        Throwable th = callerResult.getThrowable();
        if (th != null) {
            throw new IOException(th);
        }
        return (Shell.Result) callerResult.getReplyObj();
    }

    @WorkerThread
    public CallerResult exec(Caller caller) throws IOException {
        try {
            checkConnect();
            return mLocalServerManager.execNew(caller);
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            closeBgServer();
            // Retry
            try {
                checkConnect();
                return mLocalServerManager.execNew(caller);
            } catch (AdbPairingRequiredException e2) {
                throw new IOException(e2);
            }
        } catch (AdbPairingRequiredException e) {
            throw new IOException(e);
        }
    }

    @AnyThread
    public boolean isRunning() {
        return mLocalServerManager.isRunning();
    }

    public void destroy() {
        mLocalServerManager.stop();
    }

    @WorkerThread
    public void closeBgServer() throws IOException {
        mLocalServerManager.closeBgServer();
        mLocalServerManager.stop();
    }

    @WorkerThread
    @NoOps(used = true)
    public static void restart() throws IOException, AdbPairingRequiredException {
        if (sLocalServer != null) {
            LocalServerManager manager = sLocalServer.mLocalServerManager;
            manager.closeBgServer();
            manager.stop();
            manager.start();
        } else {
            getInstance();
        }
    }
}
