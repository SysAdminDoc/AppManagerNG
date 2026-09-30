// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.adb.AdbSync;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.io.IoUtils;

/**
 * Where ADB mode keeps the files the shell user runs. SELinux denies the shell domain every app
 * data directory whatever the file modes, so the launcher script, am.jar and main.jar live in a
 * directory of their own under /data/local/tmp. They get there over the ADB connection or straight
 * out of the installed APK, never by way of the app's own storage.
 */
public final class AdbLaunchFiles {
    public static final String TAG = AdbLaunchFiles.class.getSimpleName();

    public static final String STAGING_DIR = "/data/local/tmp/" + BuildConfig.APPLICATION_ID;
    static final String SERVER_SCRIPT = STAGING_DIR + "/" + ServerConfig.SERVER_RUNNER_EXEC_NAME;
    static final String SERVER_JAR = STAGING_DIR + "/" + Constants.JAR_NAME;
    public static final String MAIN_JAR_NAME = "main.jar";
    public static final String MAIN_JAR = STAGING_DIR + "/" + MAIN_JAR_NAME;
    @VisibleForTesting
    static final String LOCKED_MARKER = "AMNG_STAGING_LOCKED";
    // A mkdir and a chmod. A shell still quiet after this closed without libadb noticing.
    private static final long LOCK_DIR_TIMEOUT_MILLIS = 10_000;
    /**
     * What run_server.sh starts, spelled the same way.
     */
    @VisibleForTesting
    static final String SERVER_MAIN_CLASS = "io.github.muntashirakon.AppManager.server.ServerRunner";

    private AdbLaunchFiles() {
    }

    /**
     * Push am.jar and the launcher script over the ADB connection, reading each back to check it
     * matches the copy bundled in the APK.
     */
    @WorkerThread
    static void stageServer(@NonNull Context context, @NonNull AbsAdbConnectionManager manager) throws IOException {
        byte[] jar = AssetsUtils.readAsset(context, Constants.JAR_NAME);
        byte[] script = AssetsUtils.buildServerExecScript(context, SERVER_JAR, SERVER_JAR)
                .getBytes(StandardCharsets.UTF_8);
        long mtime = System.currentTimeMillis() / 1000;
        lockStagingDir(manager);
        try (AdbSync sync = AdbSync.open(manager)) {
            sync.pushVerified(jar, SERVER_JAR, 0644, mtime);
            sync.pushVerified(script, SERVER_SCRIPT, 0644, mtime);
        }
    }

    /**
     * adbd hands group and others whatever the owner may do on every file it writes, so 0644
     * lands as 0666. Shutting the directory to everyone but the shell user comes first.
     */
    @WorkerThread
    private static void lockStagingDir(@NonNull AbsAdbConnectionManager manager) throws IOException {
        String output;
        long openedAt;
        try {
            AdbStream opened = manager.openStream("shell:" + lockDirCommand(STAGING_DIR));
            // Counted from when adbd accepted the shell, so a slow open doesn't eat the early-close window
            openedAt = SystemClock.elapsedRealtime();
            try (AdbStream stream = opened; InputStream in = stream.openInputStream()) {
                output = readShellAnswer(in, stream, LOCKED_MARKER, LOCK_DIR_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while preparing " + STAGING_DIR, e);
        }
        checkLocked(output, SystemClock.elapsedRealtime() - openedAt);
    }

    /**
     * Reads what a short shell command printed. libadb-android 3.1.1 never ends a shell's output
     * with -1: once adbd closes the shell, the next read throws "Stream closed.", and when the
     * close arrives before the output has been read, the read after the output waits for good.
     * So reading stops at the marker, a read that throws ends the output, and at the deadline the
     * stream is closed, which frees a read stuck that way.
     */
    @VisibleForTesting
    @WorkerThread
    @NonNull
    static String readShellAnswer(@NonNull InputStream in, @NonNull Closeable stream, @NonNull String marker,
                                  long timeoutMillis) throws InterruptedIOException {
        CountDownLatch done = new CountDownLatch(1);
        Thread watchdog = new Thread(() -> {
            try {
                if (!done.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    IoUtils.closeQuietly(stream);
                }
            } catch (InterruptedException ignore) {
            }
        }, "adb-shell-deadline");
        watchdog.setDaemon(true);
        watchdog.start();
        byte[] output = new byte[256];
        int length = 0;
        try {
            int read;
            while (length < output.length && (read = in.read(output, length, output.length - length)) != -1) {
                length += read;
                if (new String(output, 0, length, StandardCharsets.UTF_8).contains(marker)) {
                    break;
                }
            }
        } catch (IOException e) {
            if (e.getCause() instanceof InterruptedException) {
                // libadb-android wraps the interrupt and clears it. The start was cancelled, which
                // is no closed shell to try again.
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while reading the shell's answer.");
            }
            Log.d(TAG, "The shell ended: %s", e.toString());
        } finally {
            done.countDown();
        }
        return new String(output, 0, length, StandardCharsets.UTF_8);
    }

    /**
     * This is the first shell after a connect, the one libadb-android #34 can close at once. Said
     * apart from a real failure so the start is tried once more.
     */
    @VisibleForTesting
    static void checkLocked(@NonNull String output, long elapsedMillis) throws IOException {
        if (output.contains(LOCKED_MARKER)) {
            return;
        }
        if (output.isEmpty() && elapsedMillis < LocalServerManager.EARLY_CLOSE_MILLIS) {
            throw new LocalServerManager.ShellClosedEarlyException(null);
        }
        if (!output.trim().isEmpty()) {
            // The shell ran the commands and they failed
            throw new LocalServerManager.LaunchRefusedException("Could not prepare " + STAGING_DIR + ": " + output.trim());
        }
        throw new IOException("Could not prepare " + STAGING_DIR + ": the shell closed without answering.");
    }

    @VisibleForTesting
    @NonNull
    static String lockDirCommand(@NonNull String dir) {
        // The empty quotes keep the marker out of the command text, so only a finished run prints it
        return "mkdir -p " + quote(dir) + " && chmod 700 " + quote(dir)
                + " && echo " + LOCKED_MARKER.replace("_LOCKED", "_''LOCKED");
    }

    /**
     * Shell commands, ending in {@code &&}, that copy main.jar out of the installed APK into
     * {@link #MAIN_JAR}. The local server runs them as the shell user, so they work after the app
     * restarts and the ADB connection that started the server is gone, and they always copy the
     * main.jar of the installed version. The APK stores main.jar uncompressed, which is what makes
     * a byte-range copy possible.
     */
    @WorkerThread
    @NonNull
    public static String stageMainJarCommand(@NonNull Context context) throws IOException {
        byte[] jar = AssetsUtils.readAsset(context, MAIN_JAR_NAME);
        try (AssetFileDescriptor afd = context.getAssets().openFd(MAIN_JAR_NAME)) {
            if (afd.getLength() != jar.length) {
                throw new IOException("main.jar isn't stored uncompressed in the APK.");
            }
            return buildExtractCommand(getApkPath(context, afd), afd.getStartOffset(), jar, MAIN_JAR);
        }
    }

    /**
     * A command to paste into {@code adb shell} that starts the server by hand. It copies am.jar
     * out of the installed APK into {@link #STAGING_DIR}, checks it, and starts the server from
     * there, which is all run_server.sh does for this case. Nothing in it reads the app's data
     * directory, which SELinux keeps the shell out of.
     */
    @WorkerThread
    @NonNull
    public static String manualShellCommand(@NonNull Context context, int port, @NonNull String token)
            throws IOException {
        byte[] jar = AssetsUtils.readAsset(context, Constants.JAR_NAME);
        try (AssetFileDescriptor afd = context.getAssets().openFd(Constants.JAR_NAME)) {
            if (afd.getLength() != jar.length) {
                throw new IOException(Constants.JAR_NAME + " isn't stored uncompressed in the APK.");
            }
            return buildManualShellCommand(getApkPath(context, afd), afd.getStartOffset(), jar, SERVER_JAR,
                    "path:" + port + AssetsUtils.getServerArgs() + ",token:" + token);
        }
    }

    @VisibleForTesting
    @NonNull
    static String buildManualShellCommand(@NonNull String apkPath, long offset, @NonNull byte[] jar,
                                          @NonNull String dest, @NonNull String config) {
        // Only the server goes in the background, so the shell waits for the copy and can say it
        // failed. It starts from a subshell that exits at once, so the adb shell has no job to hold
        // exit up with "You have running jobs", and with hangups ignored and no hold on the
        // terminal, so closing the adb shell doesn't take the server down with it.
        return buildExtractCommand(apkPath, offset, jar, dest)
                + "( ( trap '' HUP; CLASSPATH=" + quote(dest) + " exec app_process /system/bin --nice-name="
                + Constants.SERVER_NAME + " " + SERVER_MAIN_CLASS + " " + quote(config)
                + " </dev/null >/dev/null 2>&1 ) & )"
                + " || echo \"Error! Could not copy " + Constants.JAR_NAME + " out of the APK.\"";
    }

    @VisibleForTesting
    @NonNull
    static String buildExtractCommand(@NonNull String apkPath, long offset, @NonNull byte[] expected,
                                      @NonNull String dest) {
        String dir = dest.substring(0, dest.lastIndexOf('/'));
        // Write under a per-shell name and rename, so a service still running the old copy keeps
        // its file and two launches at once can't interleave their writes.
        String tmp = "\"" + dest + ".$$\"";
        return "{ mkdir -p " + quote(dir) + " && chmod 700 " + quote(dir)
                + " && dd if=" + quote(apkPath) + " of=" + tmp + " bs=1 skip=" + offset + " count=" + expected.length
                + " 2>/dev/null"
                // Toybox has sha256sum from Android 8 on, and sha1sum and md5sum from Android 6.
                // Android 5 has none of them and goes by dd's byte count.
                + " && { if " + hashCheck("sha256sum", tmp, DigestUtils.getHexDigest(DigestUtils.SHA_256, expected))
                + "; elif " + hashCheck("sha1sum", tmp, DigestUtils.getHexDigest(DigestUtils.SHA_1, expected))
                + "; elif " + hashCheck("md5sum", tmp, DigestUtils.getHexDigest(DigestUtils.MD5, expected))
                + "; fi; }"
                + " && chmod 644 " + tmp + " && mv " + tmp + " " + quote(dest)
                + " || { rm -f " + tmp + "; false; }; } && ";
    }

    @NonNull
    private static String hashCheck(@NonNull String tool, @NonNull String file, @NonNull String digest) {
        return "command -v " + tool + " >/dev/null; then h=$(" + tool + " " + file + ") && [ \"${h%% *}\" = "
                + digest + " ]";
    }

    @NonNull
    private static String getApkPath(@NonNull Context context, @NonNull AssetFileDescriptor afd) {
        try {
            String path = Os.readlink("/proc/self/fd/" + afd.getParcelFileDescriptor().getFd());
            if (path != null && path.startsWith("/")) {
                return path;
            }
        } catch (ErrnoException e) {
            Log.w(TAG, "Could not resolve the file holding main.jar, using the base APK.", e);
        }
        return context.getApplicationInfo().sourceDir;
    }

    @NonNull
    private static String quote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
