// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.android.internal.app.IAppOpsService;

import io.github.muntashirakon.AppManager.history.ops.MutationState;
import io.github.muntashirakon.AppManager.permission.PermUtils;

/**
 * An op under a restriction reads as ignored whatever is stored: the camera privacy toggle, a suspended app's
 * camera and microphone, a user restriction. The stored-mode read looks past that, so a deny still writes and an
 * image still records what rollback has to put back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AppOpRestrictedModeTest {
    private static final int UID = Process.FIRST_APPLICATION_UID + 31;
    private static final String PACKAGE = "com.example.camera";
    private static final int OP_CAMERA = 26;

    @Test
    public void aRestrictedOpReadsAsTheModeStoredForIt() throws Exception {
        Map<Integer, Integer> uidModes = new HashMap<>();
        uidModes.put(OP_CAMERA, AppOpsManager.MODE_FOREGROUND);
        AppOpsManagerCompat appOps = appOps(restrictedService(uidModes, new HashMap<>()));

        assertEquals(AppOpsManager.MODE_FOREGROUND, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));
        assertEquals(String.valueOf(AppOpsManager.MODE_FOREGROUND),
                MutationState.readAppOp(appOps, OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void aRestrictedOpWithNothingStoredReadsAsTheDefault() throws Exception {
        AppOpsManagerCompat appOps = appOps(restrictedService(new HashMap<>(), new HashMap<>()));

        assertEquals(AppOpsManager.MODE_ALLOWED, AppOpsManagerCompat.opToDefaultMode(OP_CAMERA));
        assertEquals(AppOpsManager.MODE_ALLOWED, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void theUidModeComesBeforeThePackageMode() throws Exception {
        Map<Integer, Integer> uidModes = new HashMap<>();
        uidModes.put(OP_CAMERA, AppOpsManager.MODE_ERRORED);
        Map<Integer, Integer> packageModes = new HashMap<>();
        packageModes.put(OP_CAMERA, AppOpsManager.MODE_FOREGROUND);
        AppOpsManagerCompat appOps = appOps(restrictedService(uidModes, packageModes));

        assertEquals(AppOpsManager.MODE_ERRORED, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));

        uidModes.clear();
        assertEquals(AppOpsManager.MODE_FOREGROUND, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void denyingARestrictedOpStillWrites() throws Exception {
        Map<Integer, Integer> uidModes = new HashMap<>();
        AppOpsManagerCompat appOps = appOps(restrictedService(uidModes, new HashMap<>()));

        assertTrue(PermUtils.setAppOpMode(appOps, OP_CAMERA, PACKAGE, UID, AppOpsManager.MODE_IGNORED));

        assertEquals(Integer.valueOf(AppOpsManager.MODE_IGNORED), uidModes.get(OP_CAMERA));
        assertEquals(AppOpsManager.MODE_IGNORED, appOps.checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void listsThatCantBeReadKeepTheRawAnswer() throws Exception {
        IAppOpsService refusing = service((method, args) -> {
            switch (method.getName()) {
                case "checkOperationRaw":
                    return AppOpsManager.MODE_IGNORED;
                case "getUidOps":
                case "getOpsForPackage":
                    throw new SecurityException("uid does not have android.permission.GET_APP_OPS_STATS");
                default:
                    return defaultValue(method);
            }
        });

        assertEquals(AppOpsManager.MODE_IGNORED, appOps(refusing).checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void aVendorErrorFromTheListsKeepsTheRawAnswer() throws Exception {
        IAppOpsService failing = service((method, args) -> {
            switch (method.getName()) {
                case "checkOperationRaw":
                    return AppOpsManager.MODE_IGNORED;
                case "getUidOps":
                case "getOpsForPackage":
                    throw new IllegalStateException("op lists are being rebuilt");
                default:
                    return defaultValue(method);
            }
        });

        assertEquals(AppOpsManager.MODE_IGNORED, appOps(failing).checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N_MR1)
    public void beforeAndroid8TheAnswerStandsSinceUidModesArentListed() throws Exception {
        IAppOpsService service = service((method, args) -> {
            switch (method.getName()) {
                case "checkOperation":
                    return AppOpsManager.MODE_IGNORED;
                case "getUidOps":
                case "getOpsForPackage":
                    throw new AssertionError("Read the op lists, which leave out UID modes before Android 8");
                default:
                    return defaultValue(method);
            }
        });

        assertEquals(AppOpsManager.MODE_IGNORED, appOps(service).checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void onAndroid10TheThreeArgumentRawReadAnswers() throws Exception {
        IAppOpsService service = service((method, args) -> {
            switch (method.getName()) {
                case "checkOperationRaw":
                    assertEquals("Android 10 has only the call without an attribution tag", 3, args.length);
                    return AppOpsManager.MODE_FOREGROUND;
                case "checkOperation":
                    return AppOpsManager.MODE_IGNORED;
                default:
                    return defaultValue(method);
            }
        });

        assertEquals(AppOpsManager.MODE_FOREGROUND, appOps(service).checkOperationRaw(OP_CAMERA, UID, PACKAGE));
    }

    @NonNull
    private static AppOpsManagerCompat appOps(@NonNull IAppOpsService service) {
        return new AppOpsManagerCompat(service, uid -> new String[]{PACKAGE});
    }

    /**
     * A service with the camera under a restriction: both checks answer ignored, and the op lists and writes work
     * on the stored modes, keyed by switch, as AppOpsService keeps them.
     */
    @NonNull
    private static IAppOpsService restrictedService(@NonNull Map<Integer, Integer> uidModes,
                                                    @NonNull Map<Integer, Integer> packageModes) {
        return service((method, args) -> {
            switch (method.getName()) {
                case "checkOperation":
                case "checkOperationRaw":
                    return AppOpsManager.MODE_IGNORED;
                case "getUidOps":
                    return AppOpsListFixtures.opList(null, (int) args[0], uidModes, (int[]) args[1]);
                case "getOpsForPackage":
                    return AppOpsListFixtures.opList((String) args[1], (int) args[0], packageModes, (int[]) args[2]);
                case "setUidMode":
                    uidModes.put(AppOpsManagerCompat.opToSwitch((int) args[0]), (int) args[2]);
                    return null;
                default:
                    return defaultValue(method);
            }
        });
    }

    private interface Answer {
        Object answer(@NonNull Method method, @Nullable Object[] args) throws Throwable;
    }

    @NonNull
    private static IAppOpsService service(@NonNull Answer answer) {
        return (IAppOpsService) Proxy.newProxyInstance(IAppOpsService.class.getClassLoader(),
                new Class<?>[]{IAppOpsService.class}, (proxy, method, args) -> answer.answer(method, args));
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
