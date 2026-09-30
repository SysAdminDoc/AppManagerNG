// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbUtils {
    /**
     * Whether the device is on any Wi-Fi network. Upstream 2f2b31e89: the Wi-Fi network doesn't
     * have to be the default one or reach the Internet, since a local-only or captive-portal
     * network can still carry Wireless Debugging while mobile data stays the default.
     */
    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }
        return false;
    }

    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
        if (!isAdbdRunning()) {
            throw new IOException("ADB daemon not running.");
        }
        AtomicInteger atomicPort = new AtomicInteger(-1);
        AtomicReference<String> atomicHostAddress = new AtomicReference<>(null);
        CountDownLatch resolveHostAndPort = new CountDownLatch(1);

        AdbMdns.OnAdbDaemonDiscoveredListener listener = (hostAddress, port) -> {
            if (hostAddress != null) {
                atomicHostAddress.set(hostAddress.getHostAddress());
                atomicPort.set(port);
            }
            resolveHostAndPort.countDown();
        };
        List<Scanner> scanners = new ArrayList<>(2);
        scanners.add(scanner(new AdbMdns(context, AdbMdns.SERVICE_TYPE_ADB, listener)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            scanners.add(scanner(new AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, listener)));
        }
        scan(scanners, resolveHostAndPort, timeout, unit);

        String host = atomicHostAddress.get();
        int port = atomicPort.get();
        if (host == null || port == -1) {
            throw new IOException("Could not find any valid host address or port");
        }
        return new Pair<>(host, port);
    }

    @VisibleForTesting
    interface Scanner {
        void start();

        void stop();
    }

    @NonNull
    private static Scanner scanner(@NonNull AdbMdns adbMdns) {
        return new Scanner() {
            @Override
            public void start() {
                adbMdns.start();
            }

            @Override
            public void stop() {
                adbMdns.stop();
            }
        };
    }

    /**
     * Starts every scanner and waits for a result. Each scanner that was asked to start is stopped
     * afterwards whatever happened, even when another one failed to start or to stop: a scanner
     * left running holds a multicast lock and can make later scans fail at once.
     */
    @VisibleForTesting
    @WorkerThread
    static void scan(@NonNull List<Scanner> scanners, @NonNull CountDownLatch found, long timeout,
                     @NonNull TimeUnit unit) throws IOException, InterruptedException {
        List<Scanner> started = new ArrayList<>(scanners.size());
        try {
            for (Scanner scanner : scanners) {
                started.add(scanner);
                scanner.start();
            }
            if (!found.await(timeout, unit)) {
                throw new InterruptedException("Timed out while trying to find a valid host address and port");
            }
        } catch (RuntimeException e) {
            throw new IOException("Could not start ADB service discovery", e);
        } finally {
            for (Scanner scanner : started) {
                try {
                    scanner.stop();
                } catch (RuntimeException e) {
                    Log.w("AdbUtils", "Could not stop ADB service discovery", e);
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean isWirelessDebuggingOff(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(), SettingsHidden.Global.ADB_WIFI_ENABLED, 0) == 0;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean enableWirelessDebugging(@NonNull Context context) {
        ContentResolver resolver = context.getContentResolver();
        boolean wirelessDebuggingEnabled = Settings.Global.getInt(resolver, SettingsHidden.Global.ADB_WIFI_ENABLED, 0) != 0;
        if (wirelessDebuggingEnabled && isAdbdRunning()) {
            return true;
        }
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            // No permission
            return false;
        }
        try {
            if (Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            if (!wirelessDebuggingEnabled) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", SettingsHidden.Global.ADB_WIFI_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            // Try at most 3 times to figure out if something has altered
            for (int i = 0; i < 5; ++i) {
                if (isAdbdRunning()) {
                    return true;
                }
                SystemClock.sleep(500);
            }
        } catch (Throwable th) {
            Log.w("AdbUtils", "Could not enable wireless debugging.", th);
        }
        return false;
    }

    public static boolean isAdbdRunning() {
        // Default is set to “running” to avoid other issues
        return "running".equals(SystemProperties.get("init.svc.adbd", "running"));
    }

    public static int getAdbPortOrDefault() {
        return SystemProperties.getInt("service.adb.tcp.port", ServerConfig.DEFAULT_ADB_PORT);
    }

    public static boolean startAdb(int port) {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", String.valueOf(port)}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }

    public static boolean stopAdb() {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", "-1"}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }
}
