// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.topjohnwu.superuser.Shell;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Upstream App Manager #2048 and #2036: choosing no-root still probed for root, which raised a
 * superuser prompt, and connected to a leftover privileged server until it timed out. Each shadow
 * below records a call that an explicit no-root start must never make.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        OpsNoRootInitTest.RecordingRunnerUtils.class,
        OpsNoRootInitTest.RecordingShell.class,
        OpsNoRootInitTest.RecordingShizukuBridge.class,
        OpsNoRootInitTest.RecordingLocalServer.class,
        OpsNoRootInitTest.RecordingLocalServices.class,
        OpsNoRootInitTest.RecordingUsers.class,
        OpsNoRootInitTest.RecordingAdbUtils.class,
})
public class OpsNoRootInitTest {
    static final List<String> sCalls = new ArrayList<>();
    static boolean sServicesAlive;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        // Only what the test itself triggers counts, not what the application did while starting.
        sCalls.clear();
    }

    @After
    public void tearDown() {
        sCalls.clear();
        sServicesAlive = false;
    }

    @Test
    public void explicitNoRootStartMakesNoPrivilegedCall() {
        int status = Ops.init(mContext, false, Ops.MODE_NO_ROOT);

        assertEquals(Ops.STATUS_SUCCESS, status);
        assertEquals(Collections.emptyList(), sCalls);
        assertNoPrivilegedMode();
    }

    @Test
    public void persistedNoRootModeTakesTheSameSideEffectFreePath() {
        // This is the call the startup view model makes.
        Ops.setMode(Ops.MODE_NO_ROOT);

        int status = Ops.init(mContext, false);

        assertEquals(Ops.STATUS_SUCCESS, status);
        assertEquals(Collections.emptyList(), sCalls);
        assertNoPrivilegedMode();
    }

    @Test
    public void switchingToNoRootTearsDownAHeldConnectionBeforeReturning() {
        sServicesAlive = true;

        int status = Ops.init(mContext, true, Ops.MODE_NO_ROOT);

        assertEquals(Ops.STATUS_SUCCESS, status);
        assertEquals(Collections.singletonList("LocalServices.stopServices"), sCalls);
        assertFalse(LocalServices.alive());
        assertNoPrivilegedMode();
    }

    @Test
    public void rootModeStillProbesForRoot() {
        // Positive control: the recorders do see a root probe when a mode asks for one.
        Ops.init(mContext, false, Ops.MODE_ROOT);

        assertTrue(sCalls.toString(), sCalls.contains("RunnerUtils.isAppGrantedRoot"));
    }

    @Test
    public void shizukuModeNoLongerProbesForRoot() {
        Ops.init(mContext, false, Ops.MODE_SHIZUKU);

        assertFalse(sCalls.toString(), sCalls.contains("RunnerUtils.isAppGrantedRoot"));
        assertFalse(Ops.isDirectRoot());
    }

    private static void assertNoPrivilegedMode() {
        assertFalse(Ops.isDirectRoot());
        assertFalse(Ops.isAdb());
        assertFalse(Ops.isShizuku());
        assertFalse(Ops.isSystem());
    }

    @Implements(RunnerUtils.class)
    public static class RecordingRunnerUtils {
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            sCalls.add("RunnerUtils.isAppGrantedRoot");
            return false;
        }
    }

    @Implements(Shell.class)
    public static class RecordingShell {
        @Implementation
        protected static Shell getShell() {
            sCalls.add("Shell.getShell");
            return null;
        }
    }

    @Implements(ShizukuBridge.class)
    public static class RecordingShizukuBridge {
        @Implementation
        protected static boolean isBinderAlive() {
            sCalls.add("ShizukuBridge.isBinderAlive");
            return false;
        }

        @Implementation
        protected static boolean isUsable() {
            sCalls.add("ShizukuBridge.isUsable");
            return false;
        }

        @Implementation
        protected static boolean supportsUserService() {
            sCalls.add("ShizukuBridge.supportsUserService");
            return false;
        }

        @Implementation
        protected static boolean hasPermission() {
            sCalls.add("ShizukuBridge.hasPermission");
            return false;
        }
    }

    @Implements(LocalServer.class)
    public static class RecordingLocalServer {
        @Implementation
        protected static LocalServer getInstance() {
            sCalls.add("LocalServer.getInstance");
            return null;
        }

        @Implementation
        protected static void restart() {
            sCalls.add("LocalServer.restart");
        }

        @Implementation
        protected static boolean alive(Context context) {
            sCalls.add("LocalServer.alive");
            return false;
        }

        @Implementation
        protected static boolean alive(Context context, int port) {
            sCalls.add("LocalServer.alive");
            return false;
        }
    }

    @Implements(LocalServices.class)
    public static class RecordingLocalServices {
        @Implementation
        protected static boolean alive() {
            return sServicesAlive;
        }

        @Implementation
        protected static void stopServices() {
            sCalls.add("LocalServices.stopServices");
            sServicesAlive = false;
        }

        @Implementation
        protected static void bindServices() {
            sCalls.add("LocalServices.bindServices");
        }

        @Implementation
        protected static void bindServicesIfNotAlready() {
            sCalls.add("LocalServices.bindServicesIfNotAlready");
        }
    }

    @Implements(Users.class)
    public static class RecordingUsers {
        @Implementation
        protected static int getSelfOrRemoteUid() {
            sCalls.add("Users.getSelfOrRemoteUid");
            return Process.myUid();
        }
    }

    @Implements(AdbUtils.class)
    public static class RecordingAdbUtils {
        @Implementation
        protected static boolean isAdbdRunning() {
            sCalls.add("AdbUtils.isAdbdRunning");
            return false;
        }

        @Implementation
        protected static boolean enableWirelessDebugging(Context context) {
            sCalls.add("AdbUtils.enableWirelessDebugging");
            return false;
        }

        @Implementation
        protected static boolean startAdb(int port) {
            sCalls.add("AdbUtils.startAdb");
            return false;
        }
    }
}
