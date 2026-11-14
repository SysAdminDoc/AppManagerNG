// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.internal.app.IAppOpsService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * Reading an app's ops for the Finder never throws. A failed read comes back as null, so the Finder can ask again
 * later instead of keeping "no ops" for an app that has some.
 */
@RunWith(RobolectricTestRunner.class)
public class AppOpsOrNullTest {
    private static final int UID = 10123;
    private static final String PACKAGE = "com.example.app";

    @Test
    public void aServiceThatRefusesGivesNull() {
        int[] calls = {0};
        IAppOpsService refusing = service((proxy, method, args) -> {
            if (method.getName().equals("getOpsForPackage")) {
                ++calls[0];
                throw new SecurityException("uid " + UID + " does not have android.permission.GET_APP_OPS_STATS");
            }
            return null;
        });

        List<AppOpsManagerCompat.OpEntry> ops = AppOpsManagerCompat.getOpsForPackageOrNull(
                () -> new AppOpsManagerCompat(refusing, uid -> new String[]{PACKAGE}), UID, PACKAGE);

        assertEquals(1, calls[0]);
        assertNull(ops);
    }

    @Test
    public void aDeadServiceGivesNull() {
        IAppOpsService dead = service((proxy, method, args) -> {
            if (method.getName().equals("getOpsForPackage")) {
                throw new RemoteException("binder died");
            }
            return null;
        });

        assertNull(AppOpsManagerCompat.getOpsForPackageOrNull(
                () -> new AppOpsManagerCompat(dead, uid -> new String[]{PACKAGE}), UID, PACKAGE));
    }

    @Test
    public void noServiceAtAllGivesNull() {
        assertNull(AppOpsManagerCompat.getOpsForPackageOrNull(() -> {
            throw new IllegalStateException("app ops service isn't running");
        }, UID, PACKAGE));
    }

    @Test
    public void aPackageTheServiceDoesntKnowGivesNoOps() {
        IAppOpsService empty = service((proxy, method, args) ->
                method.getName().equals("getOpsForPackage") ? Collections.emptyList() : null);

        List<AppOpsManagerCompat.OpEntry> ops = AppOpsManagerCompat.getOpsForPackageOrNull(
                () -> new AppOpsManagerCompat(empty, uid -> new String[]{PACKAGE}), UID, PACKAGE);

        assertNotNull("a read that worked is an answer, even with nothing in it", ops);
        assertTrue(ops.isEmpty());
    }

    @NonNull
    private static IAppOpsService service(@NonNull java.lang.reflect.InvocationHandler handler) {
        return (IAppOpsService) Proxy.newProxyInstance(IAppOpsService.class.getClassLoader(),
                new Class<?>[]{IAppOpsService.class}, handler);
    }
}
