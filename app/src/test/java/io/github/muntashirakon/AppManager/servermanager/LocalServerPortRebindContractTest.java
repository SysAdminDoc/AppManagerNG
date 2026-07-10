// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalServerPortRebindContractTest {
    @Test
    public void lifecycleBroadcastsRejectStaleServerPorts() {
        assertTrue(ServerStatusChangeReceiver.matchesConfiguredPort(null, 60001));
        assertTrue(ServerStatusChangeReceiver.matchesConfiguredPort("60001", 60001));
        assertFalse(ServerStatusChangeReceiver.matchesConfiguredPort("60002", 60001));
        assertFalse(ServerStatusChangeReceiver.matchesConfiguredPort("invalid", 60001));
    }

    @Test
    public void rebindStopsOldEndpointBeforeSavingAndRollsBackOnFailure() throws IOException {
        String localServer = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java");
        int stopOld = localServer.indexOf("manager.closeBgServer(oldPort)");
        int saveNew = localServer.indexOf("Prefs.Misc.setAdbLocalServerPort(newPort)", stopOld);
        assertTrue(stopOld >= 0);
        assertTrue(saveNew > stopOld);
        assertTrue(localServer.contains("Prefs.Misc.setAdbLocalServerPort(oldPort)"));
        assertTrue(localServer.contains("LocalServices.bindServices()"));
    }

    @Test
    public void sessionsAndLifecycleEventsCarryTheirBoundPort() throws IOException {
        String manager = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java");
        String lifecycle = read("server/src/main/java/io/github/muntashirakon/AppManager/server/LifecycleAgent.java");
        assertTrue(manager.contains("mSession.getPort() != configuredPort"));
        assertTrue(manager.contains("new ClientSession(port, socket, transfer)"));
        assertTrue(manager.contains("stopCommandWatcher.await(10, TimeUnit.SECONDS)"));
        assertTrue(manager.contains("waitForServerStopped(localServerPort)"));
        assertTrue(lifecycle.contains(".putExtra(PARAM_PATH, mConfigParams.getPath())"));
    }

    @Test
    public void advancedSettingUsesLiveValueAndAsyncRebind() throws IOException {
        String advanced = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/AdvancedPreferences.java");
        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue(advanced.contains("int currentPort = Prefs.Misc.getAdbLocalServerPort()"));
        assertTrue(advanced.contains("mModel.rebindLocalServerPort(c)"));
        assertTrue(advanced.contains("getLocalServerPortRebindResult().observe"));
        assertFalse(strings.contains("adb_local_server_port_restart_notice"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
