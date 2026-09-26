// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.AppOpsManager;
import android.app.Application;
import android.app.usage.IUsageStatsManager;
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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Fork issue #16: when Shizuku's shell has no Usage Access but AppManagerNG does, the query has to
 * go out as the app. These tests record which binder queryEvents called and with which package.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        UsageStatsManagerCompatRoutingTest.ExecutionIdentity.class,
        UsageStatsManagerCompatRoutingTest.RemoteAppOps.class,
        UsageStatsManagerCompatRoutingTest.RecordingUsageStats.class,
})
public class UsageStatsManagerCompatRoutingTest {
    static int sExecutionUid;
    static final Map<Integer, Integer> sRemoteModes = new HashMap<>();
    static final List<String> sCalls = new ArrayList<>();

    private final Application mApp = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        sExecutionUid = Process.myUid();
        sCalls.clear();
    }

    @After
    public void tearDown() {
        sRemoteModes.clear();
        sCalls.clear();
    }

    @Test
    public void aShellWithoutUsageAccessIsBypassedForTheAppsOwnGrant() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_IGNORED);
        setAppMode(AppOpsManager.MODE_ALLOWED);

        UsageStatsManagerCompat.queryEvents(0, 1, 0);

        assertEquals(Collections.singletonList("app queryEventsForUser " + BuildConfig.APPLICATION_ID), sCalls);
    }

    @Test
    public void aShellWithUsageAccessKeepsQueryingThroughThePrivilegedBinder() {
        sExecutionUid = Ops.SHELL_UID;
        sRemoteModes.put(Ops.SHELL_UID, AppOpsManager.MODE_ALLOWED);
        setAppMode(AppOpsManager.MODE_ALLOWED);

        UsageStatsManagerCompat.queryEvents(0, 1, 0);

        assertEquals(Collections.singletonList("service queryEventsForUser com.android.shell"), sCalls);
    }

    @Test
    public void noRootModeQueriesAsTheAppThroughTheUsualLookup() {
        setAppMode(AppOpsManager.MODE_ALLOWED);

        UsageStatsManagerCompat.queryEvents(0, 1, 0);

        assertEquals(Collections.singletonList("service queryEventsForUser " + BuildConfig.APPLICATION_ID), sCalls);
    }

    private void setAppMode(int mode) {
        AppOpsManager appOps = (AppOpsManager) mApp.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), mApp.getPackageName(), mode);
    }

    private static IUsageStatsManager recorder(String route) {
        return (IUsageStatsManager) Proxy.newProxyInstance(IUsageStatsManager.class.getClassLoader(),
                new Class<?>[]{IUsageStatsManager.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("queryEvents")) {
                        sCalls.add(route + " " + method.getName() + " " + args[args.length - 1]);
                    }
                    Class<?> type = method.getReturnType();
                    return type == boolean.class ? false : type == int.class || type == long.class ? 0 : null;
                });
    }

    @Implements(Users.class)
    public static class ExecutionIdentity {
        @Implementation
        protected static int getSelfOrRemoteUid() {
            return sExecutionUid;
        }
    }

    @Implements(AppOpsManagerCompat.class)
    public static class RemoteAppOps {
        @Implementation
        protected void __constructor__() {
        }

        @Implementation
        protected int checkOpNoThrow(int op, int uid, String packageName) {
            Integer mode = sRemoteModes.get(uid);
            return mode != null ? mode : AppOpsManager.MODE_ERRORED;
        }
    }

    @Implements(UsageStatsManagerCompat.class)
    public static class RecordingUsageStats {
        @Implementation
        protected static IUsageStatsManager getUsageStatsManager() {
            return recorder("service");
        }

        @Implementation
        protected static IUsageStatsManager getUnprivilegedUsageStatsManager() {
            return recorder("app");
        }
    }
}
