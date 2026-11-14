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
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    /**
     * @param wirelessDebuggingOnly look only for Wireless debugging's own service
     */
    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit,
                                                           boolean wirelessDebuggingOnly)
            throws InterruptedException, IOException {
        if (!isAdbdRunning()) {
            throw new IOException("ADB daemon not running.");
        }
        List<String> serviceTypes = serviceTypes(Build.VERSION.SDK_INT, wirelessDebuggingOnly);
        DaemonChooser chooser = new DaemonChooser(serviceTypes.contains(AdbMdns.SERVICE_TYPE_ADB),
                PLAIN_PORT_GRACE_MILLIS);
        List<Scanner> scanners = new ArrayList<>(serviceTypes.size());
        for (String serviceType : serviceTypes) {
            boolean plain = AdbMdns.SERVICE_TYPE_ADB.equals(serviceType);
            AdbMdns.OnAdbDaemonDiscoveredListener listener = (hostAddress, port) ->
                    chooser.onFound(plain, hostAddress != null ? hostAddress.getHostAddress() : null, port);
            scanners.add(scanner(context, serviceType, listener));
        }
        scan(scanners, chooser::await, timeout, unit);

        Pair<String, Integer> daemon = chooser.result();
        if (daemon == null) {
            throw new IOException("Could not find any valid host address or port");
        }
        return daemon;
    }

    /**
     * How long a scan that found Wireless debugging's TLS port keeps waiting for the plain port.
     */
    private static final long PLAIN_PORT_GRACE_MILLIS = 1500;

    /**
     * Picks the daemon to connect to from what the mDNS searches report. When the plain port that
     * {@code adb tcpip} opens is searched for too, it wins over Wireless debugging's TLS port even
     * when TLS answers first, and TLS is used only if no plain port turns up shortly after. Taking
     * whichever answered first put ADB over TCP on the TLS port while both were on.
     */
    @VisibleForTesting
    static final class DaemonChooser {
        private final boolean mPlainSearched;
        private final long mGraceNanos;
        private final CountDownLatch mAnyFound = new CountDownLatch(1);
        private final CountDownLatch mPlainFound = new CountDownLatch(1);
        private final AtomicReference<Pair<String, Integer>> mPlain = new AtomicReference<>();
        private final AtomicReference<Pair<String, Integer>> mTls = new AtomicReference<>();

        DaemonChooser(boolean plainSearched, long graceMillis) {
            mPlainSearched = plainSearched;
            mGraceNanos = TimeUnit.MILLISECONDS.toNanos(graceMillis);
        }

        void onFound(boolean plain, @Nullable String host, int port) {
            AtomicReference<Pair<String, Integer>> slot = plain ? mPlain : mTls;
            if (port <= 0) {
                // The service went away
                slot.set(null);
                return;
            }
            if (host == null) {
                return;
            }
            slot.set(new Pair<>(host, port));
            if (plain) {
                mPlainFound.countDown();
            }
            mAnyFound.countDown();
        }

        boolean await(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            if (!mAnyFound.await(timeout, unit)) {
                return false;
            }
            if (mPlainSearched && mPlain.get() == null) {
                long wait = Math.min(mGraceNanos, deadline - System.nanoTime());
                if (wait > 0) {
                    mPlainFound.await(wait, TimeUnit.NANOSECONDS);
                }
            }
            return true;
        }

        @Nullable
        Pair<String, Integer> result() {
            Pair<String, Integer> plain = mPlain.get();
            return plain != null ? plain : mTls.get();
        }
    }

    @VisibleForTesting
    @NonNull
    static List<String> serviceTypes(int sdk, boolean wirelessDebuggingOnly) {
        List<String> serviceTypes = new ArrayList<>(2);
        // Wireless debugging needs its own TLS port. A plain port that adb tcpip opened earlier is
        // advertised too, and taking whichever answered first could land on it.
        if (!wirelessDebuggingOnly) {
            serviceTypes.add(AdbMdns.SERVICE_TYPE_ADB);
        }
        if (sdk >= Build.VERSION_CODES.P) {
            serviceTypes.add(AdbMdns.SERVICE_TYPE_TLS_CONNECT);
        }
        return serviceTypes;
    }

    /**
     * An mDNS search, which tests can stand in for.
     */
    interface Scanner {
        void start();

        void stop();
    }

    /**
     * Whether a scan follows each service it finds instead of resolving it once. Android 17 keeps Wireless
     * debugging's name when it moves to a new port, and Android 14 and later answer a resolve for a known name from
     * their cache, so only a followed service shows the new port.
     */
    @VisibleForTesting
    static boolean followsServices(int sdk, @NonNull String serviceType) {
        return sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && AdbMdns.SERVICE_TYPE_TLS_CONNECT.equals(serviceType);
    }

    @VisibleForTesting
    @NonNull
    static Scanner scanner(@NonNull Context context, @NonNull String serviceType,
                           @NonNull AdbMdns.OnAdbDaemonDiscoveredListener listener) {
        // The SDK check is repeated here for lint, which can't see it inside followsServices()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && followsServices(Build.VERSION.SDK_INT, serviceType)) {
            NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            if (nsd != null) {
                return new AdbServiceFollower(nsd, serviceType, listener);
            }
        }
        AdbMdns adbMdns = new AdbMdns(context, serviceType, listener);
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
     * Waits until a scan has its answer, as {@link CountDownLatch#await(long, TimeUnit)} does.
     */
    interface Waiter {
        boolean await(long timeout, @NonNull TimeUnit unit) throws InterruptedException;
    }

    /**
     * Starts every scanner and waits for a result. Each scanner that was asked to start is stopped
     * afterwards whatever happened, even when another one failed to start or to stop: a scanner
     * left running holds a multicast lock and can make later scans fail at once.
     */
    @VisibleForTesting
    @WorkerThread
    static void scan(@NonNull List<Scanner> scanners, @NonNull Waiter found, long timeout,
                     @NonNull TimeUnit unit) throws IOException, InterruptedException {
        List<Scanner> started = new ArrayList<>(scanners.size());
        try {
            for (Scanner scanner : scanners) {
                started.add(scanner);
                scanner.start();
            }
            if (!found.await(timeout, unit)) {
                // Not an InterruptedException: callers take that for a cancel, and a scan that
                // found nothing should fall back to the saved port instead
                throw new IOException("Timed out while trying to find a valid host address and port");
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
