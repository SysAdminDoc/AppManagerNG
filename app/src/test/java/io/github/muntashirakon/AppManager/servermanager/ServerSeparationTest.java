// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * AppManagerNG installs beside upstream App Manager, so its privileged server must not share
 * upstream's port, process name, jar or log.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerSeparationTest {
    private static final String UPSTREAM_SERVER_NAME = "am_local_server";
    private static final int UPSTREAM_PORT_BASE = 60001;
    // Linux's default net.ipv4.ip_local_port_range is 32768 to 60999
    private static final int LAST_EPHEMERAL_PORT = 60999;

    private AppPref mAppPref;

    @Before
    public void setUp() {
        mAppPref = AppPref.getNewInstance(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void defaultPortIsOutOfUpstreamsRangeAndTheEphemeralRange() {
        int port = (int) mAppPref.getValue(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT);

        assertEquals(62001, port);
        assertEquals(ServerConfig.DEFAULT_LOCAL_SERVER_PORT_BASE, port);
        assertNotEquals(UPSTREAM_PORT_BASE, port);
        assertTrue(port > LAST_EPHEMERAL_PORT);
        // Upstream's range would only reach it for a user id past 2000
        assertTrue(port - UPSTREAM_PORT_BASE >= 2000);
    }

    @Test
    public void userSetPortIsKept() {
        mAppPref.setPref(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT, UPSTREAM_PORT_BASE);

        assertEquals(UPSTREAM_PORT_BASE, mAppPref.getValue(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT));
    }

    @Test
    public void processNameIsOursAndFitsTheKernelLimit() {
        assertNotEquals(UPSTREAM_SERVER_NAME, Constants.SERVER_NAME);
        // The kernel keeps 15 characters of a process name; killall matches what it keeps
        assertTrue(Constants.SERVER_NAME.length() <= 15);
        assertFalse(Constants.SERVER_NAME, Constants.SERVER_NAME.matches(".*[^a-z0-9_].*"));
    }

    @Test
    public void jarAndLogDontUseUpstreamsFiles() {
        assertNotEquals("/data/local/tmp/am.jar", ServerConfig.ROOT_EXEC_JAR);
        assertNotEquals("/data/local/tmp/am.txt", Constants.SERVER_LOG);
        assertTrue(ServerConfig.ROOT_EXEC_JAR.startsWith("/data/local/tmp/"));
        assertTrue(Constants.SERVER_LOG.startsWith("/data/local/tmp/"));
    }

    @Test
    public void restartKillsOnlyTheServersOwnName() throws IOException {
        String manager = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java");

        assertTrue(manager.contains("\"killall \" + Constants.SERVER_NAME"));
        assertFalse(manager.contains("killall am_local_server\""));
        assertFalse(read("libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/FLog.java")
                .contains("/data/local/tmp/am.txt"));
        assertFalse(read("app/src/main/assets/run_server.sh").contains("/data/local/tmp\"\n"));
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
