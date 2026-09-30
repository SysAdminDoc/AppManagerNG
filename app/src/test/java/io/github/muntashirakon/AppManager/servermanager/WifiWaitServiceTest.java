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
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowSystemProperties;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void aNetworkWaitingForApprovalIsWaitedOnWhileTheModeLasts() {
        Network network = ShadowNetwork.newInstance(100);
        WifiWaitService.ConnectionResult approval = WifiWaitService.ConnectionResult.NEEDS_APPROVAL;

        assertEquals(WifiWaitService.NextStep.WAIT_FOR_APPROVAL, WifiWaitService.nextStep(network, network, approval, 0, true));
        assertEquals(WifiWaitService.NextStep.FINISH, WifiWaitService.nextStep(network, network, approval, 0, false));
    }

    @Test(timeout = 60_000)
    public void whenAndroidAsksToAllowTheNetworkTheReconnectAsksOnceAndGoesOnWhenAllowed() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.INTERNET, Manifest.permission.POST_NOTIFICATIONS);
        // The connect after the approval then fails at once, with nothing on the network
        ShadowSystemProperties.override("init.svc.adbd", "stopped");
        String before = Ops.getMode();
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        FakeWirelessDebugging wirelessDebugging = new FakeWirelessDebugging();
        service.setWirelessDebuggingSwitch(wirelessDebugging);
        try {
            Ops.setMode(Ops.MODE_ADB_WIFI);
            setField(service, "mWifiNetwork", ShadowNetwork.newInstance(100));
            Runnable retry = (Runnable) getField(service, "mRetryRunnable");

            retry.run();
            awaitAttemptEnd(service);
            assertEquals(1, wirelessDebugging.mSwitchedOnWhileOff.get());
            Notification asked = approvalNotification(app);
            assertNotNull(asked);
            Intent opens = shadowOf(asked.contentIntent).getSavedIntent();
            assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, opens.getAction());
            assertEquals("toggle_adb_wireless", opens.getStringExtra(":settings:fragment_args_key"));

            // Another try on the same network doesn't switch it on again, which would ask again
            retry.run();
            awaitAttemptEnd(service);
            assertEquals(1, wirelessDebugging.mSwitchedOnWhileOff.get());
            assertFalse(shadowOf(service).isStoppedBySelf());

            // A new access point is a new question, asked once
            ConnectivityManager.NetworkCallback callback = (ConnectivityManager.NetworkCallback) getField(service, "mNetworkCallback");
            NetworkCapabilities wifi = ShadowNetworkCapabilities.newInstance();
            shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            callback.onCapabilitiesChanged(ShadowNetwork.newInstance(101), wifi);
            awaitAttemptEnd(service);
            retry.run();
            awaitAttemptEnd(service);
            assertEquals(2, wirelessDebugging.mSwitchedOnWhileOff.get());

            // Allowed: the notification goes and the reconnect carries on by itself
            wirelessDebugging.userAllows();
            assertNull(approvalNotification(app));
            awaitAttemptEnd(service);
            assertEquals(5, getField(service, "mAttempts"));
            assertEquals(2, wirelessDebugging.mSwitchedOnWhileOff.get());
        } finally {
            controller.destroy();
            Ops.setMode(before);
            AdbFailure.clear();
        }
    }

    @Test(timeout = 60_000)
    public void anApprovalNobodyGivesEndsTheWaitAfterTenMinutes() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.INTERNET, Manifest.permission.POST_NOTIFICATIONS);
        String before = Ops.getMode();
        ServiceController<WifiWaitService> controller = Robolectric.buildService(WifiWaitService.class).create();
        WifiWaitService service = controller.get();
        service.setWirelessDebuggingSwitch(new FakeWirelessDebugging());
        try {
            Ops.setMode(Ops.MODE_ADB_WIFI);
            setField(service, "mWifiNetwork", ShadowNetwork.newInstance(100));
            ((Runnable) getField(service, "mRetryRunnable")).run();
            awaitAttemptEnd(service);
            assertNotNull(approvalNotification(app));

            ShadowLooper.idleMainLooper(WifiWaitService.APPROVAL_WAIT_MILLIS - 1_000, TimeUnit.MILLISECONDS);
            assertFalse(shadowOf(service).isStoppedBySelf());
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
            assertTrue(shadowOf(service).isStoppedBySelf());

            controller.destroy();
            assertNull(approvalNotification(app));
        } finally {
            Ops.setMode(before);
        }
    }

    @Nullable
    private static Notification approvalNotification(Context context) {
        return shadowOf(context.getSystemService(NotificationManager.class))
                .getNotification(WifiWaitService.APPROVAL_NOTIFICATION_TAG, 1);
    }

    private static void awaitAttemptEnd(WifiWaitService service) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (getField(service, "mConnectionTask") != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertNull(getField(service, "mConnectionTask"));
    }

    /**
     * Wireless debugging on a network it hasn't been allowed on: Android asks the user and switches
     * it straight back off.
     */
    private static final class FakeWirelessDebugging implements WirelessDebuggingSwitch {
        final AtomicInteger mSwitchedOnWhileOff = new AtomicInteger();
        private volatile boolean mOn;
        @Nullable
        private Runnable mObserver;

        @Override
        public boolean isOn() {
            return mOn;
        }

        @Override
        public boolean switchOn() {
            if (!mOn) {
                mSwitchedOnWhileOff.incrementAndGet();
            }
            return true;
        }

        @Override
        public void observe(@NonNull Runnable onChange) {
            mObserver = onChange;
        }

        @Override
        public void stopObserving() {
            mObserver = null;
        }

        void userAllows() {
            mOn = true;
            if (mObserver != null) {
                mObserver.run();
            }
        }
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
