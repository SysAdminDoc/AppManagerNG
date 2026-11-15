// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.android.internal.app.IAppOpsService;

/**
 * A hidden-API accessor that falls back after a linkage failure says so in the log, once per process.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HiddenApiFallbacksTest {
    private static final int UID = Process.FIRST_APPLICATION_UID + 21;
    private static final String PACKAGE = "com.example.maps";
    private static final int OP_CAMERA = 26;

    @Before
    public void setUp() {
        // Load the class first, so only what the test does is counted
        AppOpsManagerCompat.getAllOps();
        HiddenApiFallbacks.resetForTesting();
        ShadowLog.clear();
    }

    @After
    public void tearDown() {
        HiddenApiFallbacks.resetForTesting();
    }

    @Test
    public void aLinkageFailureIsLoggedOnceAndTheFallbackStillAnswers() throws Exception {
        AppOpsManagerCompat appOps = new AppOpsManagerCompat(serviceWithoutRawReads(), uid -> new String[]{PACKAGE});

        assertEquals(AppOpsManager.MODE_IGNORED, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));
        assertEquals(AppOpsManager.MODE_IGNORED, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));

        List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag(HiddenApiFallbacks.TAG);
        assertEquals(1, logs.size());
        ShadowLog.LogItem item = logs.get(0);
        assertEquals(Log.WARN, item.type);
        assertTrue(item.msg, item.msg.contains("AppOpsManagerCompat#checkOperationRaw"));
        assertTrue(item.msg, item.msg.contains("API 36"));
        assertTrue(item.msg, item.msg.contains("NoSuchMethodError"));
        assertTrue(item.throwable instanceof NoSuchMethodError);
        assertEquals(Collections.singletonList("AppOpsManagerCompat#checkOperationRaw"),
                HiddenApiFallbacks.getMembers());
    }

    @Test
    public void eachMemberGetsItsOwnLine() {
        HiddenApiFallbacks.log("PermissionCompat", "grantRuntimePermission", new NoSuchMethodError());
        HiddenApiFallbacks.log("PermissionCompat", "grantRuntimePermission(deviceId)", new NoSuchMethodError());
        HiddenApiFallbacks.log("PermissionCompat", "grantRuntimePermission", new NoSuchMethodError());

        assertEquals(2, ShadowLog.getLogsForTag(HiddenApiFallbacks.TAG).size());
        assertEquals(Arrays.asList("PermissionCompat#grantRuntimePermission",
                "PermissionCompat#grantRuntimePermission(deviceId)"), HiddenApiFallbacks.getMembers());
    }

    /**
     * An app-ops service built against an interface that lacks the raw read, as on a build whose hidden
     * interface changed: the call fails to link and the plain check answers instead.
     */
    @NonNull
    private static IAppOpsService serviceWithoutRawReads() {
        return (IAppOpsService) Proxy.newProxyInstance(IAppOpsService.class.getClassLoader(),
                new Class<?>[]{IAppOpsService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "checkOperationRaw":
                            throw new NoSuchMethodError("checkOperationRaw");
                        case "checkOperation":
                            return AppOpsManager.MODE_IGNORED;
                        default:
                            return defaultValue(method);
                    }
                });
    }

    @Nullable
    private static Object defaultValue(@NonNull Method method) {
        Class<?> type = method.getReturnType();
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        return null;
    }
}
