// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.security.SecureRandom;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// Copyright 2016 Zheng Li
public final class ServerConfig {
    public static final String TAG = ServerConfig.class.getSimpleName();

    public static final int DEFAULT_ADB_PORT = 5555;
    static final String SERVER_RUNNER_EXEC_NAME = "run_server.sh";
    private static final String LOCAL_TOKEN = "l_token";
    private static final String ADB_LAST_PAIRING_HOST = "adb_last_pairing_host";
    private static final String ADB_LAST_PAIRING_PORT = "adb_last_pairing_port";
    private static final String ADB_LAST_PAIRING_TIME = "adb_last_pairing_time";
    private static final File[] SERVER_RUNNER_EXEC = new File[2];
    private static final File[] SERVER_RUNNER_JAR = new File[2];
    private static final SharedPreferences sPreferences = ContextUtils.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);
    private static volatile boolean sInitialised = false;

    @WorkerThread
    @NoOps
    public static void init(@NonNull Context context) throws IOException {
        if (sInitialised) {
            return;
        }

        // Setup paths
        File deStorage = ContextUtils.getDeContext(context).getCacheDir();
        File serverRunnerExec = new File(deStorage, SERVER_RUNNER_EXEC_NAME);
        File serverRunnerJar = new File(deStorage, Constants.JAR_NAME);
        SERVER_RUNNER_EXEC[0] = serverRunnerExec;
        SERVER_RUNNER_EXEC[1] = serverRunnerExec;
        SERVER_RUNNER_JAR[0] = serverRunnerJar;
        SERVER_RUNNER_JAR[1] = serverRunnerJar;
        // Copy JAR
        AssetsUtils.copyFile(context, Constants.JAR_NAME, serverRunnerJar, true);
        // Write script
        AssetsUtils.writeServerExecScript(context, serverRunnerExec, serverRunnerJar.getAbsolutePath());
        // Update permission
        File deStorageRoot = deStorage.getParentFile();
        if (deStorageRoot != null) {
            FileUtils.chmod711(deStorageRoot);
        }
        FileUtils.chmod711(deStorage);
        FileUtils.chmod644(serverRunnerJar);
        FileUtils.chmod644(serverRunnerExec);

        sInitialised = true;
    }

    @AnyThread
    @NonNull
    public static File getDestJarFile() {
        // For compatibility only
        return SERVER_RUNNER_JAR[0];
    }

    @AnyThread
    @NonNull
    public static String getServerRunnerCommand(int index) throws IndexOutOfBoundsException {
        Log.e(TAG, "Classpath: %s", SERVER_RUNNER_JAR[index]);
        Log.e(TAG, "Exec path: %s", SERVER_RUNNER_EXEC[index]);
        return "sh " + SERVER_RUNNER_EXEC[index] + " " + getLocalServerPort() + " " + getLocalToken();
    }

    @AnyThread
    @NonNull
    public static String getServerRunnerAdbCommand() throws IndexOutOfBoundsException {
        return getServerRunnerCommand(1);
    }

    /**
     * Get existing or generate new 16-digit token for client session
     *
     * @return Existing or new token
     */
    @AnyThread
    @NonNull
    public static String getLocalToken() {
        String token = sPreferences.getString(LOCAL_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            token = generateToken();
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply();
        }
        return token;
    }

    @AnyThread
    public static boolean getAllowBgRunning() {
        return sPreferences.getBoolean("allow_bg_running", true);
    }

    @AnyThread
    @IntRange(from = 0, to = 65535)
    @NoOps
    public static int getAdbPort() {
        return sPreferences.getInt("adb_port", DEFAULT_ADB_PORT);
    }

    @AnyThread
    @NoOps
    public static void setAdbPort(@IntRange(from = 0, to = 65535) int port) {
        sPreferences.edit().putInt("adb_port", port).apply();
    }

    @AnyThread
    @NoOps
    public static void setLastAdbPairing(@NonNull String host, @IntRange(from = 0, to = 65535) int port) {
        sPreferences.edit()
                .putString(ADB_LAST_PAIRING_HOST, host)
                .putInt(ADB_LAST_PAIRING_PORT, port)
                .putLong(ADB_LAST_PAIRING_TIME, System.currentTimeMillis())
                .apply();
    }

    @AnyThread
    @NoOps
    public static boolean hasPairedAdbDevice() {
        return sPreferences.getLong(ADB_LAST_PAIRING_TIME, 0) > 0;
    }

    @AnyThread
    @NoOps
    public static int getLastAdbPairingPort() {
        return sPreferences.getInt(ADB_LAST_PAIRING_PORT, DEFAULT_ADB_PORT);
    }

    @AnyThread
    public static int getLocalServerPort() {
        return Prefs.Misc.getAdbLocalServerPort();
    }

    @WorkerThread
    @NonNull
    public static String getAdbHost(Context context) {
        return getHostIpAddress(context);
    }

    @WorkerThread
    @NonNull
    public static String getLocalServerHost(Context context) {
        String ipAddress = Inet4Address.getLoopbackAddress().getHostAddress();
        if (ipAddress == null || ipAddress.equals("::1")) return "127.0.0.1";
        return ipAddress;
    }

    @WorkerThread
    @NonNull
    private static String getHostIpAddress(@NonNull Context context) {
        if (isEmulator(context)) return "10.0.2.2";
        String ipAddress = Inet4Address.getLoopbackAddress().getHostAddress();
        if (ipAddress == null || ipAddress.equals("::1")) return "127.0.0.1";
        return ipAddress;
    }

    // https://github.com/firebase/firebase-android-sdk/blob/7d86138304a6573cbe2c61b66b247e930fa05767/firebase-crashlytics/src/main/java/com/google/firebase/crashlytics/internal/common/CommonUtils.java#L402
    private static final String GOLDFISH = "goldfish";
    private static final String RANCHU = "ranchu";
    private static final String SDK = "sdk";

    private static boolean isEmulator(@NonNull Context context) {
        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return Build.PRODUCT.contains(SDK)
                || Build.HARDWARE.contains(GOLDFISH)
                || Build.HARDWARE.contains(RANCHU)
                || androidId == null;
    }


    @AnyThread
    @NonNull
    private static String generateToken() {
        // The token is the SOLE authenticator for the privileged command
        // channel (DataTransmission#shakeHands). The previous 3-5 word phrase
        // drawn from a ~1300-entry word list was only ~31-51 bits of entropy —
        // brute-forceable by a same-device app that can reach the loopback
        // listener, since a failed handshake just drops the connection and the
        // server keeps accepting. Use a 256-bit cryptographically-random hex
        // token instead. Hex contains no ',' (the handshake splits on comma)
        // and no whitespace (the token is passed as a shell argument).
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
