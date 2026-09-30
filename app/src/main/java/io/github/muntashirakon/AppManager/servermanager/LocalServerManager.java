// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2016 Zheng Li
class LocalServerManager {
    private static final String TAG = "LocalServerManager";
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static final int MAX_SERVER_START_ATTEMPTS = 2;
    private static final long SERVER_RETRY_DELAY_MILLIS = 150;
    /**
     * A shell that closes this soon after it opened, before any answer, was closed by libadb, not
     * by the device (libadb-android #34: on 3.1.1 the first streams after a wireless connect can
     * close at once). It's worth one more try.
     */
    @VisibleForTesting
    static final long EARLY_CLOSE_MILLIS = 1000;

    @SuppressLint("StaticFieldLeak")
    private static LocalServerManager sLocalServerManager;

    @AnyThread
    @NoOps
    @NonNull
    static LocalServerManager getInstance(@NonNull Context context) {
        synchronized (LocalServerManager.class) {
            if (sLocalServerManager == null) {
                sLocalServerManager = new LocalServerManager(context);
            }
        }
        return sLocalServerManager;
    }

    private final Object mLock = new Object();
    @NonNull
    private final Context mContext;
    @Nullable
    private ClientSession mSession;

    @AnyThread
    private LocalServerManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Get current session. If no session is running, create a new one. If no server is running,
     * create one first.
     *
     * @return Currently running session
     * @throws IOException When creating session fails or server couldn't be started
     */
    @WorkerThread
    @NonNull
    @NoOps(used = true)
    private ClientSession getSession() throws IOException, AdbPairingRequiredException {
        synchronized (mLock) {
            int configuredPort = ServerConfig.getLocalServerPort();
            if (mSession != null
                    && (!mSession.isRunning() || mSession.getPort() != configuredPort)) {
                IoUtils.closeQuietly(mSession);
                mSession = null;
            }
            if (mSession == null) {
                ServerConnectionFailure sessionFailure = null;
                try {
                    mSession = createSession(configuredPort);
                } catch (Exception e) {
                    if (!Ops.isDirectRoot() && !Ops.isAdb()) {
                        // Do not bother attempting to create a new session
                        throw new IOException("Could not create session", e);
                    }
                    sessionFailure = ServerConnectionFailure.find(e);
                }
                for (int attempt = 1; mSession == null; ++attempt) {
                    try {
                        startServer(configuredPort);
                    } catch (AdbPairingRequiredException | AdbUnreachableException e) {
                        // A pairing or port problem, which the pair/connect dialogs can fix
                        throw e;
                    } catch (Exception e) {
                        if (sessionFailure != null && sessionFailure.getReason()
                                == ServerConnectionFailure.Reason.NOT_ACKNOWLEDGED) {
                            // Another server holds the port, which is why this one couldn't start
                            sessionFailure.addSuppressed(e);
                            throw sessionFailure;
                        }
                        if (isWorthRetrying(attempt, e)) {
                            Log.w(TAG, "Could not start the server, trying once more.", e);
                            SystemClock.sleep(SERVER_RETRY_DELAY_MILLIS);
                            continue;
                        }
                        throw new ServerConnectionFailure(ServerConnectionFailure.Reason.SERVER_START,
                                "Could not start server", e);
                    }
                    try {
                        mSession = createSession(configuredPort);
                    } catch (IOException e) {
                        if (!isWorthRetrying(attempt, e)) {
                            throw e;
                        }
                        // The new server went away right after it started
                        Log.w(TAG, "Could not reach the server that just started, trying once more.", e);
                        SystemClock.sleep(SERVER_RETRY_DELAY_MILLIS);
                    }
                }
            }
            return mSession;
        }
    }

    /**
     * Port of upstream 0152f468f: one more try for a start that failed quickly. A server that
     * never answered, or a known failure (another server on the port, an unresponsive one), would
     * only fail the same way again, and a timeout would double the wait.
     */
    @VisibleForTesting
    static boolean isWorthRetrying(int attempt, @NonNull Throwable failure) {
        return attempt < MAX_SERVER_START_ATTEMPTS
                && !(failure instanceof TimeoutException)
                // The ADB start already gave a shell that closed at once its one more try
                && !(failure instanceof ShellClosedEarlyException)
                // A shell that refused the launch refuses it again
                && !(failure instanceof LaunchRefusedException)
                // A cancelled start stays cancelled
                && !Thread.currentThread().isInterrupted()
                && ServerConnectionFailure.find(failure) == null;
    }

    @AnyThread
    public boolean isRunning() {
        return mSession != null && mSession.isRunning();
    }

    @AnyThread
    void closeSession() {
        synchronized (mLock) {
            IoUtils.closeQuietly(mSession);
            mSession = null;
        }
    }

    void stop() {
        synchronized (mLock) {
            IoUtils.closeQuietly(mAdbStream);
            IoUtils.closeQuietly(mSession);
            mAdbStream = null;
            mSession = null;
        }
    }

    @WorkerThread
    @NoOps(used = true)
    void start() throws IOException, AdbPairingRequiredException {
        getSession();
    }

    @WorkerThread
    @NonNull
    private DataTransmission getSessionDataTransmission() throws IOException {
        try {
            return getSession().getDataTransmission();
        } catch (AdbPairingRequiredException e) {
            throw new IOException(e);
        }
    }

    @WorkerThread
    @NonNull
    private byte[] execPre(@NonNull byte[] params) throws IOException {
        try {
            return getSessionDataTransmission().sendAndReceiveMessage(params);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("pipe")) {
                closeSession();
                return getSessionDataTransmission().sendAndReceiveMessage(params);
            }
            throw e;
        }
    }

    @WorkerThread
    CallerResult execNew(@NonNull Caller caller) throws IOException {
        byte[] result = execPre(ParcelableUtil.marshall(new BaseCaller(caller.wrapParameters())));
        return ParcelableUtil.unmarshall(result, CallerResult.CREATOR);
    }

    @WorkerThread
    void closeBgServer() throws IOException {
        closeBgServer(ServerConfig.getLocalServerPort());
    }

    @WorkerThread
    void closeBgServer(int port) throws IOException {
        try {
            BaseCaller baseCaller = new BaseCaller(BaseCaller.TYPE_CLOSE);
            ClientSession session;
            synchronized (mLock) {
                if (mSession != null && mSession.isRunning() && mSession.getPort() == port) {
                    session = mSession;
                } else {
                    IoUtils.closeQuietly(mSession);
                    session = createSession(port);
                    mSession = session;
                }
            }
            session.getDataTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
        } catch (Exception e) {
            // The server exits without answering, so this is expected (upstream 0152f468f)
            if (isExpectedDisconnect(e)) {
                Log.d(TAG, "closeBgServer: The server closed the session.");
            } else {
                Log.w(TAG, "closeBgServer: Error", e);
            }
        }
        // Check if the server is still active
        closeSession();
        if (LocalServer.alive(mContext, port) && !waitForServerStopped(port)) {
            // Server still active, need to run killall on the server's process name
            try {
                stopServer(port);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private static boolean isExpectedDisconnect(@NonNull Throwable error) {
        return error instanceof EOFException
                || error instanceof SocketTimeoutException
                || (error instanceof SocketException && (error.getMessage() == null
                || error.getMessage().contains("closed") || error.getMessage().contains("Broken pipe")));
    }

    @Nullable
    private volatile AdbStream mAdbStream;

    private static final Pattern HEX_RUN = Pattern.compile("[0-9a-f]{4,}");

    /**
     * The shell echoes what it's sent, launch token included, on a terminal that wraps or scrolls
     * long input, so the token can come back split across lines. Every run of four or more hex
     * digits that is part of the token is masked.
     */
    @VisibleForTesting
    @NonNull
    static String redactToken(@NonNull String line, @NonNull String token) {
        Matcher matcher = HEX_RUN.matcher(line);
        StringBuilder sb = new StringBuilder(line.length());
        int end = 0;
        while (matcher.find()) {
            String run = matcher.group();
            if (token.contains(run) || run.contains(token)) {
                sb.append(line, end, matcher.start()).append("<redacted>");
                end = matcher.end();
            }
        }
        return sb.append(line, end, line.length()).toString();
    }

    /**
     * Reads a launch shell for as long as it stays open, so the server, which writes to it, never
     * backs up. Keeps the launcher's answer and when the shell closed.
     */
    @VisibleForTesting
    static final class ShellReader implements Runnable {
        @NonNull
        private final InputStream mIn;
        @NonNull
        private final String mToken;
        private final CountDownLatch mAnswered = new CountDownLatch(1);
        private volatile boolean mStarted;
        private volatile boolean mFailed;
        private volatile long mClosedAt = -1;

        ShellReader(@NonNull InputStream in, @NonNull String token) {
            mIn = in;
            mToken = token;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mIn))) {
                String s;
                while ((s = reader.readLine()) != null) {
                    Log.d(TAG, "RESPONSE: %s", redactToken(s, mToken));
                    if (s.startsWith("Success!")) {
                        mStarted = true;
                        mAnswered.countDown();
                    } else if (s.startsWith("Error!")) {
                        mFailed = true;
                        mAnswered.countDown();
                    }
                }
            } catch (Throwable e) {
                Log.d(TAG, "The ADB shell stopped: %s", e.toString());
            } finally {
                mClosedAt = SystemClock.elapsedRealtime();
                // A closed shell won't answer, so don't wait for it
                mAnswered.countDown();
            }
        }
    }

    /**
     * Sends the launch command to a shell that was just opened and waits for the launcher's
     * answer. The shell has to stay open while the server runs.
     *
     * @param openedAt When the shell was opened, in {@link SystemClock#elapsedRealtime()} time
     * @throws ShellClosedEarlyException The shell closed within {@link #EARLY_CLOSE_MILLIS} of
     *                                   opening, before any answer
     * @throws TimeoutException          The launcher didn't answer in time
     */
    @VisibleForTesting
    @WorkerThread
    static void launchInShell(@NonNull InputStream in, @NonNull OutputStream out, @NonNull String command,
                              @NonNull String token, long openedAt, long timeout, @NonNull TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {
        ShellReader reader = new ShellReader(in, token);
        Thread t = new Thread(reader, "adb-output-reader");
        t.setDaemon(true);
        t.start();
        try (OutputStream os = out) {
            os.write("id\n".getBytes());
            os.write((command + "\n").getBytes());
        } catch (IOException e) {
            if (SystemClock.elapsedRealtime() - openedAt < EARLY_CLOSE_MILLIS) {
                throw new ShellClosedEarlyException(e);
            }
            throw e;
        }
        if (!reader.mAnswered.await(timeout, unit)) {
            throw new TimeoutException("The server launcher didn't answer.");
        }
        if (reader.mStarted) {
            return;
        }
        long closedAt = reader.mClosedAt;
        if (!reader.mFailed && closedAt >= 0) {
            if (closedAt - openedAt < EARLY_CLOSE_MILLIS) {
                throw new ShellClosedEarlyException(null);
            }
            // It used to wait out the whole minute for an answer that could no longer come
            throw new IOException("The ADB shell closed before the server started.");
        }
        // The launcher said "Error!"
        throw new LaunchRefusedException("Server wasn't started.");
    }

    @WorkerThread
    private void useAdbStartServer(int localServerPort) throws Exception {
        AdbConnectionManager manager = AdbConnectionManager.getInstance();
        manager.setTimeout(10, TimeUnit.SECONDS);
        if (!manager.isConnected()) {
            connectToAdbd(manager);
        }
        try {
            stageAndLaunch(manager, localServerPort);
        } catch (ShellClosedEarlyException e) {
            Log.w(TAG, "useAdbStartServer: The ADB shell closed as soon as it opened, trying once more.", e);
            if (!manager.isConnected()) {
                connectToAdbd(manager);
            }
            stageAndLaunch(manager, localServerPort);
        }
        Log.d(TAG, "useAdbStartServer: Server has started.");
    }

    @WorkerThread
    private void connectToAdbd(@NonNull AdbConnectionManager manager) throws Exception {
        String adbHost = ServerConfig.getAdbHost(mContext);
        int adbPort = ServerConfig.getAdbPort();
        Log.d(TAG, "useAdbStartServer: Connecting using host=%s, port=%d", adbHost, adbPort);
        boolean connected;
        try {
            connected = manager.connect(adbHost, adbPort);
        } catch (IOException e) {
            throw new AdbUnreachableException(e);
        }
        if (!connected) {
            throw new AdbUnreachableException(null);
        }
    }

    @WorkerThread
    private void stageAndLaunch(@NonNull AdbConnectionManager manager, int localServerPort) throws Exception {
        // Port of upstream 03298fafa: a shell kept from an earlier start can sit on a connection
        // that's gone, and a launch written to it waited a minute for nothing. Every start gets a
        // fresh one.
        IoUtils.closeQuietly(mAdbStream);
        mAdbStream = null;
        // The shell user can't read anything in the app's data directories, so the launcher has to
        // be where the shell user can find it before the shell is asked to run it.
        AdbLaunchFiles.stageServer(mContext, manager);
        Log.d(TAG, "useAdbStartServer: Opening shell...");
        AdbStream stream = manager.openStream("shell:");
        // Counted from when adbd accepted the shell, so a slow open doesn't eat the early-close window
        long openedAt = SystemClock.elapsedRealtime();
        mAdbStream = stream;
        Log.d(TAG, "useAdbStartServer: Launching privileged server.");
        try {
            launchInShell(stream.openInputStream(), stream.openOutputStream(),
                    ServerConfig.getServerRunnerAdbCommand(localServerPort), ServerConfig.getLocalToken(),
                    openedAt, 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            IoUtils.closeQuietly(stream);
            if (mAdbStream == stream) {
                mAdbStream = null;
            }
            throw e;
        }
    }

    @WorkerThread
    private void useRootStartServer(int localServerPort) throws Exception {
        if (!Ops.hasRoot()) {
            throw new Exception("Root access denied");
        }
        // Run the internal device-encrypted copy (index 1), not the external-storage copy.
        // root can always read the app-private DE cache, and unlike external storage no other
        // app can overwrite it — closing a local privilege-escalation where a malicious app
        // with external-storage write access swaps the JAR/script that root then executes.
        String command = ServerConfig.getServerRunnerCommand(1, localServerPort);
        // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
        Log.d(TAG, "useRootStartServer: Launching privileged server.");
        Runner.Result result = Runner.runCommand(command);

        Log.d(TAG, "useRootStartServer: %s", result.getOutput());
        if (!result.isSuccessful()) {
            throw new Exception("Could not start server.");
        }
        waitForServerReady(localServerPort);
        Log.d(TAG, "useRootStartServer: Server has started.");
    }

    private void waitForServerReady(int port) throws Exception {
        String host = ServerConfig.getLocalServerHost(mContext);
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress(host, port), 500);
                return;
            } catch (IOException ignored) {
            }
            SystemClock.sleep(200);
        }
        throw new TimeoutException("Server did not become ready within 10 seconds.");
    }

    private boolean waitForServerStopped(int port) {
        String host = ServerConfig.getLocalServerHost(mContext);
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress(host, port), 500);
                // Still accepting connections — server not dead yet
            } catch (IOException ignored) {
                return true;
            }
            SystemClock.sleep(200);
        }
        Log.w(TAG, "Server still accepting connections after 10s stop wait");
        return false;
    }

    /**
     * Start root or ADB server based on config
     */
    @WorkerThread
    @NoOps(used = true)
    private void startServer(int localServerPort) throws Exception {
        if (Ops.isAdb()) {
            useAdbStartServer(localServerPort);
        } else if (Ops.isDirectRoot()) {
            useRootStartServer(localServerPort);
        } else throw new Exception("Neither root nor ADB mode is enabled.");
    }

    /**
     * Stop root or ADB server based on config
     */
    @WorkerThread
    @NoOps(used = true)
    private void stopServer(int localServerPort) throws Exception {
        String command = "killall " + Constants.SERVER_NAME;
        if (Ops.isAdb()) {
            IoUtils.closeQuietly(mAdbStream);
            mAdbStream = null;
            String adbHost = ServerConfig.getAdbHost(mContext);
            int adbPort = ServerConfig.getAdbPort();
            AdbConnectionManager manager = AdbConnectionManager.getInstance();
            Log.d(TAG, "stopServer (ADB): Connecting using host=%s, port=%d", adbHost, adbPort);
            manager.setTimeout(10, TimeUnit.SECONDS);
            if (!manager.isConnected() && !manager.connect(adbHost, adbPort)) {
                throw new IOException("Could not connect to ADB.");
            }

            Log.d(TAG, "stopServer (ADB): Opening shell...");
            AdbStream stopStream = manager.openStream("shell:");
            CountDownLatch stopCommandWatcher = new CountDownLatch(1);
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stopStream.openInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "STOP RESPONSE: %s", line);
                        if (line.startsWith("AM_LOCAL_SERVER_STOP_COMMAND_FINISHED")) {
                            stopCommandWatcher.countDown();
                            return;
                        }
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "stopServer: unable to read from ADB shell.", e);
                }
            }, "adb-stop-output-reader");
            outputReader.setDaemon(true);
            outputReader.start();
            try {
                try (OutputStream os = stopStream.openOutputStream()) {
                    os.write("id\n".getBytes());
                    Log.d(TAG, "stopServer (ADB): %s", command);
                    os.write((command + "\necho AM_LOCAL_SERVER_STOP_COMMAND_FINISHED\n").getBytes());
                }
                if (!stopCommandWatcher.await(10, TimeUnit.SECONDS)) {
                    throw new Exception("Timed out while stopping the server.");
                }
            } finally {
                IoUtils.closeQuietly(stopStream);
            }
            if (!waitForServerStopped(localServerPort)) {
                throw new Exception("Server did not stop listening on port " + localServerPort + '.');
            }
            Log.d(TAG, "stopServer (ADB): Server has stopped.");
        } else if (Ops.isDirectRoot()) {
            if (!Ops.hasRoot()) {
                throw new Exception("Root access denied");
            }
            Log.d(TAG, "stopServer (root): %s", command);
            Runner.Result result = Runner.runCommand(command);
            Log.d(TAG, "stopServer (root): %s", result.getOutput());
            if (!result.isSuccessful()) {
                throw new Exception("Could not stop server.");
            }
            if (!waitForServerStopped(localServerPort)) {
                throw new Exception("Server did not stop listening on port " + localServerPort + '.');
            }
            Log.d(TAG, "stopServer (root): Server has stopped.");
        } else throw new Exception("Neither root nor ADB mode is enabled.");
    }

    /**
     * Create a client session
     *
     * @return New session if not running, running session otherwise
     * @throws IOException If session creation failed
     */
    @WorkerThread
    @NonNull
    @NoOps(used = true)
    private ClientSession createSession(int port) throws IOException {
        String host = ServerConfig.getLocalServerHost(mContext);
        Socket socket = new Socket(host, port);
        try {
            // A listener that takes the connection and never acknowledges the token isn't this
            // app's server, so it gets less time than a real request does.
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            // NOTE: (CWE-319) No need for SSL since it only runs on a random port in localhost with specific authorization.
            // TODO: 5/8/23 We could use an SSL server with a randomly generated certificate per session without requiring
            //  any other authorization methods. This session is independent of the application.
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();
            DataTransmission transfer = new DataTransmission(os, is, false);
            try {
                transfer.shakeHands(ServerConfig.getLocalToken(), DataTransmission.Role.Client);
            } catch (SocketTimeoutException e) {
                throw new ServerConnectionFailure(ServerConnectionFailure.Reason.UNRESPONSIVE,
                        "The server didn't answer the handshake.", e);
            } catch (DataTransmission.HandshakeRejectedException e) {
                throw new ServerConnectionFailure(ServerConnectionFailure.Reason.NOT_ACKNOWLEDGED,
                        e.getMessage(), e);
            }
            socket.setSoTimeout(30_000);
            return new ClientSession(port, socket, transfer);
        } catch (IOException | RuntimeException e) {
            IoUtils.closeQuietly(socket);
            throw e;
        }
    }

    /**
     * adbd itself couldn't be reached (wrong port, wireless debugging off), so the server was
     * never asked to start. Kept apart from server failures: another port or pairing can fix it.
     */
    static final class AdbUnreachableException extends IOException {
        AdbUnreachableException(@Nullable Throwable cause) {
            super("Could not connect to ADB.", cause);
        }
    }

    /**
     * A shell closed within {@link #EARLY_CLOSE_MILLIS} of opening, before it answered.
     */
    static final class ShellClosedEarlyException extends IOException {
        ShellClosedEarlyException(@Nullable Throwable cause) {
            super("The ADB shell closed as soon as it opened.", cause);
        }
    }

    /**
     * The shell ran the launch steps and said no, such as a chmod or the launcher failing. Some
     * OEM builds refuse these to ADB.
     */
    static final class LaunchRefusedException extends IOException {
        LaunchRefusedException(@NonNull String message) {
            super(message);
        }
    }

    /**
     * The client session handler
     */
    private static class ClientSession implements AutoCloseable {
        private volatile boolean mIsRunning;
        private final int mPort;
        @NonNull
        private final Socket mSocket;
        @NonNull
        private final DataTransmission mDataTransmission;

        @AnyThread
        ClientSession(int port, @NonNull Socket socket, @NonNull DataTransmission dataTransmission) {
            mPort = port;
            mSocket = socket;
            mDataTransmission = dataTransmission;
            mIsRunning = true;
        }

        /**
         * Close the session, stop any active transmission
         */
        @AnyThread
        @Override
        public void close() throws IOException {
            if (mIsRunning) {
                mIsRunning = false;
                mDataTransmission.close();
                mSocket.close();
            }
        }

        /**
         * Whether the client session is running
         */
        @AnyThread
        boolean isRunning() {
            return mIsRunning;
        }

        @AnyThread
        int getPort() {
            return mPort;
        }

        @AnyThread
        @NonNull
        DataTransmission getDataTransmission() {
            return mDataTransmission;
        }
    }
}
