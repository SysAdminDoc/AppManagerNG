// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.internal.app.IAppOpsService;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.history.ops.MutationJournal;
import io.github.muntashirakon.AppManager.history.ops.MutationRecorder;
import io.github.muntashirakon.AppManager.history.ops.MutationState;
import io.github.muntashirakon.AppManager.permission.PermUtils;

/**
 * On Android 10 and later the plain check evaluates a foreground-only mode by where the app is right now, so an
 * app in the background reads as ignored. Images and the "already set" compare have to see the stored mode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AppOpForegroundModeTest {
    private static final int UID = Process.FIRST_APPLICATION_UID + 14;
    private static final String PACKAGE = "com.example.camera";
    private static final int OP_CAMERA = 26;

    @Test
    public void anImageReadsTheStoredForegroundMode() throws Exception {
        Map<Integer, Integer> modes = new HashMap<>();
        modes.put(OP_CAMERA, AppOpsManager.MODE_FOREGROUND);
        AppOpsManagerCompat appOps = appOps(modes);

        assertEquals(String.valueOf(AppOpsManager.MODE_FOREGROUND),
                MutationState.readAppOp(appOps, OP_CAMERA, UID, PACKAGE));
    }

    @Test
    public void denyingAnOpThatIsForegroundOnlyStillWrites() throws Exception {
        Map<Integer, Integer> modes = new HashMap<>();
        modes.put(OP_CAMERA, AppOpsManager.MODE_FOREGROUND);
        AppOpsManagerCompat appOps = appOps(modes);

        assertTrue(PermUtils.setAppOpMode(appOps, OP_CAMERA, PACKAGE, UID, AppOpsManager.MODE_IGNORED));

        assertEquals(Integer.valueOf(AppOpsManager.MODE_IGNORED), modes.get(OP_CAMERA));
    }

    @Test
    public void settingForegroundOnlyIsRecordedAsApplied() throws Exception {
        assertEquals("Camera should be its own switch", OP_CAMERA, AppOpsManagerCompat.opToSwitch(OP_CAMERA));
        Map<Integer, Integer> modes = new HashMap<>();
        AppOpsManagerCompat appOps = appOps(modes);
        List<MutationImage> stored = new ArrayList<>();
        MutationRecorder recorder = new MutationRecorder("exec-1", "single:app_op_set", "root",
                (kind, target, pkg, user) -> {
                    try {
                        return MutationState.readAppOp(appOps, Integer.parseInt(target), UID, pkg);
                    } catch (RemoteException e) {
                        return null;
                    }
                }, stored::add);

        MutationRecorder.Pending image = recorder.begin(PACKAGE, 0, MutationState.KIND_APP_OP,
                String.valueOf(OP_CAMERA), String.valueOf(AppOpsManager.MODE_FOREGROUND));
        assertTrue(PermUtils.setAppOpMode(appOps, OP_CAMERA, PACKAGE, UID, AppOpsManager.MODE_FOREGROUND));
        image.finish(true);

        MutationImage written = stored.get(0);
        assertEquals(String.valueOf(AppOpsManagerCompat.opToDefaultMode(OP_CAMERA)), written.beforeValue);
        assertEquals(String.valueOf(AppOpsManager.MODE_FOREGROUND), written.postValue);
        assertEquals(MutationJournal.RESULT_APPLIED, written.result);
    }

    @NonNull
    private static AppOpsManagerCompat appOps(@NonNull Map<Integer, Integer> modes) {
        return new AppOpsManagerCompat(backgroundAppService(modes), uid -> new String[]{PACKAGE});
    }

    /**
     * UID modes stored under each op's switch, as AppOpsService keeps them, for an app that's in the background:
     * the plain check turns a foreground-only mode into ignored and the raw check returns what's stored.
     */
    @NonNull
    private static IAppOpsService backgroundAppService(@NonNull Map<Integer, Integer> modes) {
        return (IAppOpsService) Proxy.newProxyInstance(IAppOpsService.class.getClassLoader(),
                new Class<?>[]{IAppOpsService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "checkOperation": {
                            int mode = stored(modes, (int) args[0]);
                            return mode == AppOpsManager.MODE_FOREGROUND ? AppOpsManager.MODE_IGNORED : mode;
                        }
                        case "checkOperationRaw":
                            return stored(modes, (int) args[0]);
                        case "setUidMode":
                            modes.put(AppOpsManagerCompat.opToSwitch((int) args[0]), (int) args[2]);
                            return null;
                        default:
                            return defaultValue(method);
                    }
                });
    }

    private static int stored(@NonNull Map<Integer, Integer> modes, int op) {
        int code = AppOpsManagerCompat.opToSwitch(op);
        Integer mode = modes.get(code);
        return mode != null ? mode : AppOpsManagerCompat.opToDefaultMode(code);
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
