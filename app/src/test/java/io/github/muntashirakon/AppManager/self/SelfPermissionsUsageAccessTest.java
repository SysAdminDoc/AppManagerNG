// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Application;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Fork issue #16: with Shizuku active, the Usage Access check asked about the shell (UID 2000),
 * so a device that denies the shell kept sending the user to grant AppManagerNG access it already
 * had. AppManagerNG's own grant and each execution identity's ability to query are separate
 * questions.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        SelfPermissionsUsageAccessTest.ExecutionIdentity.class,
        SelfPermissionsUsageAccessTest.RemoteAppOps.class,
})
public class SelfPermissionsUsageAccessTest {
    private static final int OTHER_USER = 10;

    static int sExecutionUid;
    static final Map<Integer, Integer> sRemoteModes = new HashMap<>();
    static boolean sRemoteAppOpsUnavailable;

    private final Application mApp = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        sExecutionUid = Process.myUid();
    }

    @After
    public void tearDown() {
        sRemoteModes.clear();
        sRemoteAppOpsUnavailable = false;
    }

    @Test
    public void shizukuShellWithoutAccessCannotHideTheAppsOwnGrant() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_IGNORED);
        setAppMode(AppOpsManager.MODE_ALLOWED);

        assertTrue(SelfPermissions.hasAppUsageAccessGrant());
        assertFalse(SelfPermissions.canQueryUsageStats(Ops.SHELL_UID, 0));
        assertTrue("a granted app must never be told to grant access again",
                SelfPermissions.checkUsageStatsPermission());
        assertEquals(Process.myUid(), SelfPermissions.getUsageStatsQueryUid(0));
    }

    @Test
    public void shizukuShellWithAccessStillRunsTheQuery() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_ALLOWED);
        setAppMode(AppOpsManager.MODE_IGNORED);

        assertFalse(SelfPermissions.hasAppUsageAccessGrant());
        assertTrue(SelfPermissions.checkUsageStatsPermission());
        assertEquals(Ops.SHELL_UID, SelfPermissions.getUsageStatsQueryUid(0));
    }

    @Test
    public void noIdentityCanQueryWhenShellAndAppAreBothDenied() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_ERRORED);
        setAppMode(AppOpsManager.MODE_IGNORED);

        assertFalse(SelfPermissions.checkUsageStatsPermission());
        assertEquals(SelfPermissions.USAGE_STATS_UNAVAILABLE, SelfPermissions.getUsageStatsQueryUid(0));
    }

    @Test
    public void anUnreadableShellGrantCountsAsAbsentAndFallsBackToTheApp() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteAppOpsUnavailable = true;
        setAppMode(AppOpsManager.MODE_ALLOWED);

        assertFalse(SelfPermissions.canQueryUsageStats(Ops.SHELL_UID, 0));
        assertEquals(Process.myUid(), SelfPermissions.getUsageStatsQueryUid(0));
    }

    @Test
    public void noRootModeUsesOnlyTheAppGrant() {
        setAppMode(AppOpsManager.MODE_ALLOWED);
        assertTrue(SelfPermissions.checkUsageStatsPermission());

        setAppMode(AppOpsManager.MODE_IGNORED);
        assertFalse(SelfPermissions.checkUsageStatsPermission());
        assertEquals(SelfPermissions.USAGE_STATS_UNAVAILABLE, SelfPermissions.getUsageStatsQueryUid(0));
    }

    @Test
    public void defaultAppOpModeFallsBackToThePermission() {
        setAppMode(AppOpsManager.MODE_DEFAULT);
        assertFalse(SelfPermissions.hasAppUsageAccessGrant());

        shadowOf(mApp).grantPermissions(Manifest.permission.PACKAGE_USAGE_STATS);
        assertTrue(SelfPermissions.hasAppUsageAccessGrant());
    }

    @Test
    public void rootAndSystemReadEveryUserWithoutAGrant() {
        sRemoteAppOpsUnavailable = true;

        assertTrue(SelfPermissions.canQueryUsageStats(Ops.ROOT_UID, OTHER_USER));
        assertTrue(SelfPermissions.canQueryUsageStats(Ops.SYSTEM_UID, OTHER_USER));
    }

    @Test
    public void anotherUsersDataNeedsCrossUserAccessOnTopOfTheGrant() {
        setAppMode(AppOpsManager.MODE_ALLOWED);
        assertTrue(SelfPermissions.canQueryUsageStats(Process.myUid(), 0));
        assertFalse(SelfPermissions.canQueryUsageStats(Process.myUid(), OTHER_USER));

        shadowOf(mApp).grantPermissions("android.permission.INTERACT_ACROSS_USERS");
        assertTrue(SelfPermissions.canQueryUsageStats(Process.myUid(), OTHER_USER));

        // The shell may act across users, but only with its own grant.
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_IGNORED);
        assertFalse(SelfPermissions.canQueryUsageStats(Ops.SHELL_UID, OTHER_USER));
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_ALLOWED);
        assertTrue(SelfPermissions.canQueryUsageStats(Ops.SHELL_UID, OTHER_USER));
    }

    private void setAppMode(int mode) {
        AppOpsManager appOps = (AppOpsManager) mApp.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), mApp.getPackageName(), mode);
    }

    @Implements(Users.class)
    public static class ExecutionIdentity {
        @Implementation
        protected static int getSelfOrRemoteUid() {
            return sExecutionUid;
        }
    }

    /** The privileged app-op service, queried only for identities other than the app. */
    @Implements(AppOpsManagerCompat.class)
    public static class RemoteAppOps {
        @Implementation
        protected void __constructor__() {
        }

        @Implementation
        protected int checkOpNoThrow(int op, int uid, String packageName) {
            if (sRemoteAppOpsUnavailable) {
                throw new IllegalStateException("app-op service unavailable");
            }
            if (uid == Process.myUid()) {
                throw new AssertionError("the app's own grant must be read locally");
            }
            Integer mode = sRemoteModes.get(uid);
            return mode != null ? mode : AppOpsManager.MODE_ERRORED;
        }
    }
}
