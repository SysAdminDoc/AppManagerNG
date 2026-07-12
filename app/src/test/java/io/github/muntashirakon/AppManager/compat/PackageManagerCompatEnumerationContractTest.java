// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.pm.IPackageManagerV37;
import android.content.pm.PackageInfoList;
import android.content.pm.ParceledListSlice;

import org.junit.Test;

import java.lang.reflect.Method;

import misc.utils.VersionCodes;

/**
 * Host-verifiable structural contract for the Android 17 (API 37) installed-package
 * enumeration path in {@link PackageManagerCompat#getInstalledPackages(int, int)}.
 * <p>
 * The real paginated binder call only runs on an API-37 device, but the branch selection
 * relies on three invariants that must hold at compile/link time. If any of these break,
 * the {@code SDK_INT >= CINNAMON_BUN} branch silently routes to the wrong type and the
 * main app list comes back empty on Android 17.
 */
public class PackageManagerCompatEnumerationContractTest {
    @Test
    public void cinnamonBunGateIsApi37() {
        // The gate constant that routes API 37+ to the PackageInfoList path.
        assertEquals(37, VersionCodes.CINNAMON_BUN);
    }

    @Test
    public void packageInfoListIsAParceledListSlice() {
        // getInstalledPackagesInternal() calls .getList() on the returned value; that only
        // works if the A17 return type stays a ParceledListSlice subtype.
        assertTrue(ParceledListSlice.class.isAssignableFrom(PackageInfoList.class));
    }

    @Test
    public void v37InterfaceReturnsPackageInfoList() throws NoSuchMethodException {
        // The Refine.<IPackageManagerV37>unsafeCast(pm).getInstalledPackages(long, int) call
        // depends on this exact signature returning PackageInfoList (not the bare slice).
        Method m = IPackageManagerV37.class.getMethod("getInstalledPackages", long.class, int.class);
        assertEquals(PackageInfoList.class, m.getReturnType());
    }
}
