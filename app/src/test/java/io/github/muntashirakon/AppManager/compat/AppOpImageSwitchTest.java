// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;

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
 * The service keeps an op's mode under its switch, so a write to GPS lands on coarse location, and the package's
 * own entries never list GPS's new mode. The image has to see the write anyway.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AppOpImageSwitchTest {
    private static final int UID = Process.FIRST_APPLICATION_UID + 12;
    private static final String PACKAGE = "com.example.one";
    private static final int OP_COARSE_LOCATION = 0;
    private static final int OP_GPS = 2;

    @Test
    public void aWriteToAnOpUnderAnotherSwitchShowsInItsImage() throws Exception {
        assertEquals("GPS should switch with coarse location", OP_COARSE_LOCATION,
                AppOpsManagerCompat.opToSwitch(OP_GPS));
        Map<Integer, Integer> uidModes = new HashMap<>();
        AppOpsManagerCompat appOps = new AppOpsManagerCompat(switchingService(uidModes),
                uid -> new String[]{PACKAGE});
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
                String.valueOf(OP_GPS), String.valueOf(AppOpsManager.MODE_IGNORED));
        PermUtils.setAppOpMode(appOps, OP_GPS, PACKAGE, UID, AppOpsManager.MODE_IGNORED);
        image.finish(true);

        assertEquals(1, uidModes.size());
        MutationImage written = stored.get(0);
        assertEquals(String.valueOf(AppOpsManagerCompat.opToDefaultMode(OP_GPS)), written.beforeValue);
        assertEquals(String.valueOf(AppOpsManager.MODE_IGNORED), written.postValue);
        assertEquals(MutationJournal.RESULT_APPLIED, written.result);
    }

    /**
     * UID modes stored under each op's switch, read back through it and listed from the same store, as
     * AppOpsService does.
     */
    @NonNull
    private static IAppOpsService switchingService(@NonNull Map<Integer, Integer> uidModes) {
        return (IAppOpsService) Proxy.newProxyInstance(IAppOpsService.class.getClassLoader(),
                new Class<?>[]{IAppOpsService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "checkOperation":
                        case "checkOperationRaw": {
                            int code = AppOpsManagerCompat.opToSwitch((int) args[0]);
                            Integer mode = uidModes.get(code);
                            return mode != null ? mode : AppOpsManagerCompat.opToDefaultMode(code);
                        }
                        case "getUidOps":
                            return AppOpsListFixtures.opList(null, (int) args[0], uidModes, (int[]) args[1]);
                        case "setUidMode":
                            uidModes.put(AppOpsManagerCompat.opToSwitch((int) args[0]), (int) args[2]);
                            return null;
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
