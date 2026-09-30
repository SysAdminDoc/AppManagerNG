// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        } finally {
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

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
