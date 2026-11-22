// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.os.DeadObjectException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Proxy;
import java.util.function.IntPredicate;

/**
 * Google Play services on Android 10 is too big for one binder reply: getPackageInfo with every
 * component flag fails with a DeadObjectException, and so does its activity list with meta-data.
 * App details then closed straight away. The split fetch has to keep going without the extras
 * and still bring back the requested permissions.
 */
@RunWith(RobolectricTestRunner.class)
public class PackageManagerCompatSplitFetchTest {
    private static final String PKG = "com.google.android.gms";
    // What App details asks for
    private static final int FLAGS = PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS
            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
            | PackageManager.GET_SERVICES | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SIGNING_CERTIFICATES
            | PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.GET_URI_PERMISSION_PATTERNS;
    private static final int PARTS = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
            | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS;

    @Test
    public void activitiesTooBigWithMetaDataAreFetchedWithout() throws Exception {
        // More than one part, or activities with meta-data, is over the limit
        IPackageManager pm = fakePackageManager(flags -> Integer.bitCount(flags & PARTS) > 1
                || ((flags & PackageManager.GET_ACTIVITIES) != 0 && (flags & PackageManager.GET_META_DATA) != 0));
        PackageInfo info = PackageManagerCompat.getPackageInfo(pm, PKG, FLAGS, 0);
        assertNotNull(info.activities);
        assertEquals(PKG + ".Main", info.activities[0].name);
        assertEquals(PKG + ".Sync", info.services[0].name);
        assertArrayEquals(new String[]{"android.permission.CAMERA"}, info.requestedPermissions);
        assertEquals(PackageInfo.REQUESTED_PERMISSION_GRANTED, info.requestedPermissionsFlags[0]);
        assertEquals(PKG + ".permission.C2D", info.permissions[0].name);
    }

    @Test
    public void aPartTooBigEvenWithoutExtrasIsLeftOutAndTheRestStays() throws Exception {
        IPackageManager pm = fakePackageManager(flags -> Integer.bitCount(flags & PARTS) > 1
                || (flags & PackageManager.GET_ACTIVITIES) != 0);
        PackageInfo info = PackageManagerCompat.getPackageInfo(pm, PKG, FLAGS, 0);
        assertNull(info.activities);
        assertEquals(PKG + ".Sync", info.services[0].name);
        assertArrayEquals(new String[]{"android.permission.CAMERA"}, info.requestedPermissions);
    }

    private static IPackageManager fakePackageManager(IntPredicate tooBig) {
        return (IPackageManager) Proxy.newProxyInstance(IPackageManager.class.getClassLoader(),
                new Class<?>[]{IPackageManager.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getPackageInfo")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    int flags = ((Number) args[1]).intValue();
                    if (tooBig.test(flags)) {
                        // What the binder reports when the reply is over the limit
                        throw new DeadObjectException("Transaction failed on small parcel; remote process probably died");
                    }
                    return packageInfo(flags);
                });
    }

    private static PackageInfo packageInfo(int flags) {
        PackageInfo info = new PackageInfo();
        info.packageName = PKG;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = PKG;
        if ((flags & PackageManager.GET_ACTIVITIES) != 0) {
            ActivityInfo a = new ActivityInfo();
            a.name = PKG + ".Main";
            info.activities = new ActivityInfo[]{a};
        }
        if ((flags & PackageManager.GET_SERVICES) != 0) {
            ServiceInfo s = new ServiceInfo();
            s.name = PKG + ".Sync";
            info.services = new ServiceInfo[]{s};
        }
        if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
            info.requestedPermissions = new String[]{"android.permission.CAMERA"};
            info.requestedPermissionsFlags = new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED};
            PermissionInfo p = new PermissionInfo();
            p.name = PKG + ".permission.C2D";
            info.permissions = new PermissionInfo[]{p};
        }
        return info;
    }
}
