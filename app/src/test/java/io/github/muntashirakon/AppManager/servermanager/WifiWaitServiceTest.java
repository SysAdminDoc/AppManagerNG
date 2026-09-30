// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.adb.AdbPairingService;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Upstream 22d439d61 and 355813cae: the boot-time reconnect retries what time can fix, follows a
 * replacement Wi-Fi network and stops once the mode isn't wireless ADB. It runs as a special use
 * service because Android 15 won't let a boot receiver start a data sync one.
 */
@RunWith(RobolectricTestRunner.class)
public class WifiWaitServiceTest {
    @Test
    public void onlyStatusesTimeCanFixAreRetried() {
        assertTrue(WifiWaitService.isRetryableStatus(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED));
        assertTrue(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE));

        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_SUCCESS));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_ADB_PAIRING_REQUIRED));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS));
        // The server manager already retried these and told the user
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE_SERVER_START));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED));
        assertEquals(5, WifiWaitService.MAX_RETRY_ATTEMPTS);
    }

    @Test
    public void aNetworkThatReplacedTheAttemptedOneIsTriedAfterARetryableFailure() {
        Network attempted = ShadowNetwork.newInstance(100);
        Network replacement = ShadowNetwork.newInstance(101);

        assertTrue(WifiWaitService.shouldTryReplacementNetwork(attempted, replacement, true));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, replacement, false));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, ShadowNetwork.newInstance(100), true));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, null, true));
    }

    @Test
    public void eachOutcomeLeadsToOneNextStep() {
        Network attempted = ShadowNetwork.newInstance(100);
        Network replacement = ShadowNetwork.newInstance(101);
        WifiWaitService.ConnectionResult retry = WifiWaitService.ConnectionResult.RETRY;

        for (WifiWaitService.ConnectionResult done : new WifiWaitService.ConnectionResult[]{
                WifiWaitService.ConnectionResult.SUCCESS, WifiWaitService.ConnectionResult.TERMINAL_FAILURE,
                WifiWaitService.ConnectionResult.MODE_CHANGED}) {
            assertEquals(WifiWaitService.NextStep.FINISH,
                    WifiWaitService.nextStep(attempted, attempted, done, 0, true));
        }
        assertEquals(WifiWaitService.NextStep.FINISH, WifiWaitService.nextStep(attempted, attempted, retry, 0, false));
        assertEquals(WifiWaitService.NextStep.TRY_REPLACEMENT,
                WifiWaitService.nextStep(attempted, replacement, retry, 0, true));
        // Wi-Fi went away during the attempt. The service used to stop here for good.
        assertEquals(WifiWaitService.NextStep.WAIT_FOR_NETWORK, WifiWaitService.nextStep(attempted, null, retry, 3, true));

        assertEquals(WifiWaitService.NextStep.RETRY_LATER, WifiWaitService.nextStep(attempted, attempted, retry, 0, true));
        assertEquals(WifiWaitService.NextStep.RETRY_LATER, WifiWaitService.nextStep(attempted, attempted, retry,
                WifiWaitService.MAX_RETRY_ATTEMPTS - 1, true));
        assertEquals(WifiWaitService.NextStep.FINISH, WifiWaitService.nextStep(attempted, attempted, retry,
                WifiWaitService.MAX_RETRY_ATTEMPTS, true));
    }

    @Test
    public void anAttemptThatThrowsStillReportsBack() {
        assertEquals(WifiWaitService.ConnectionResult.RETRY,
                WifiWaitService.runConnectionAttempt(() -> WifiWaitService.ConnectionResult.RETRY));
        // What setAdbPort throws for a port mDNS reported as 0. Left uncaught, the service thought
        // the attempt still ran and ignored every network after it.
        assertEquals(WifiWaitService.ConnectionResult.TERMINAL_FAILURE, WifiWaitService.runConnectionAttempt(() -> {
            throw new IllegalArgumentException("Invalid ADB port: 0");
        }));
        assertEquals(WifiWaitService.ConnectionResult.TERMINAL_FAILURE, WifiWaitService.runConnectionAttempt(() -> {
            throw new NoClassDefFoundError("hidden API");
        }));
    }

    @Test
    public void onlyWifiNetworksCount() {
        NetworkCapabilities wifi = ShadowNetworkCapabilities.newInstance();
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        NetworkCapabilities localOnlyWifi = ShadowNetworkCapabilities.newInstance();
        shadowOf(localOnlyWifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        shadowOf(localOnlyWifi).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(localOnlyWifi).removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        NetworkCapabilities cellular = ShadowNetworkCapabilities.newInstance();
        shadowOf(cellular).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        shadowOf(cellular).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        assertTrue(WifiWaitService.isWifiNetwork(wifi));
        // Wireless debugging works on a Wi-Fi network without Internet access
        assertTrue(WifiWaitService.isWifiNetwork(localOnlyWifi));
        assertFalse(WifiWaitService.isWifiNetwork(cellular));
    }

    @Test
    public void leavingWirelessAdbStopsAPendingReconnect() {
        String before = Ops.getMode();
        Application app = ApplicationProvider.getApplicationContext();
        try {
            while (shadowOf(app).getNextStoppedService() != null) ;

            Ops.setMode(Ops.MODE_ADB_WIFI);
            assertNull(shadowOf(app).getNextStoppedService());

            Ops.setMode(Ops.MODE_ROOT);
            Intent stopped = shadowOf(app).getNextStoppedService();
            assertNotNull(stopped);
            assertEquals(WifiWaitService.class.getName(), stopped.getComponent().getClassName());
            Intent stoppedPairing = shadowOf(app).getNextStoppedService();
            assertNotNull(stoppedPairing);
            assertEquals(AdbPairingService.class.getName(), stoppedPairing.getComponent().getClassName());
        } finally {
            Ops.setMode(before);
        }
    }

    @Test
    public void wifiThatDoesNotComeBackEndsTheWaitAfterTwoMinutes() throws ReflectiveOperationException {
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        try {
            Network wifi = ShadowNetwork.newInstance(100);
            setField(service, "mWifiNetwork", wifi);
            ConnectivityManager.NetworkCallback callback = (ConnectivityManager.NetworkCallback) getField(service, "mNetworkCallback");

            // Another network going away starts no clock
            callback.onLost(ShadowNetwork.newInstance(101));
            ShadowLooper.idleMainLooper(3, TimeUnit.MINUTES);
            assertFalse(shadowOf(service).isStoppedBySelf());

            callback.onLost(wifi);
            ShadowLooper.idleMainLooper(119, TimeUnit.SECONDS);
            assertFalse(shadowOf(service).isStoppedBySelf());
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
            assertTrue(shadowOf(service).isStoppedBySelf());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void aWifiThatKeepsComingBackIsNotTriedForever() throws ReflectiveOperationException {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.INTERNET);
        String before = Ops.getMode();
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        try {
            Ops.setMode(Ops.MODE_ADB_WIFI);
            setField(service, "mWifiNetwork", ShadowNetwork.newInstance(100));
            // A new network resets the retries for it, not this count
            setField(service, "mAttempts", WifiWaitService.MAX_TOTAL_ATTEMPTS);

            ((Runnable) getField(service, "mRetryRunnable")).run();

            assertTrue(shadowOf(service).isStoppedBySelf());
            assertNull(getField(service, "mConnectionTask"));
        } finally {
            controller.destroy();
            Ops.setMode(before);
        }
    }

    @Test
    public void theBootReconnectIsASpecialUseService() throws IOException {
        String manifest = read("app/src/main/AndroidManifest.xml");
        int service = manifest.indexOf("android:name=\".servermanager.WifiWaitService\"");
        int end = manifest.indexOf("</service>", service);
        assertTrue(service != -1 && end > service);
        String entry = manifest.substring(service, end);
        assertTrue(entry.contains("android:foregroundServiceType=\"specialUse\""));
        assertTrue(entry.contains("android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\""));
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_SPECIAL_USE\" />"));

        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/WifiWaitService.java");
        assertTrue(source.contains("ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE"));
        assertFalse(source.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"));
    }

    @Test
    public void theReconnectUsesTheEntryThatStopsWhenTheServiceDoes() throws IOException {
        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/WifiWaitService.java");
        // The plain entry can't be interrupted and doesn't look at the mode once it has the lock
        assertTrue(source.contains("Ops.autoConnectWirelessDebuggingInBackground(context)"));
        assertFalse(source.contains("Ops.autoConnectWirelessDebugging(context)"));
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
