// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android 17 (API 37) replaced the binder call behind installed-package enumeration: the list now
 * comes back as a paginated {@code PackageInfoList} through {@code IPackageManagerV37}, and
 * {@link PackageManagerCompat} picks that path by SDK level. A wrong branch there does not throw —
 * it returns an empty or short list, which reaches the user as an app list that is silently missing
 * entries.
 *
 * <p>These tests pin the compat layer against the platform's own {@link PackageManager} on whatever
 * API level they are running on, so the API-37 branch is checked by the same assertions that cover
 * the older ones rather than by a separate claim about it.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PackageEnumerationInstrumentedTest {
    private static int myUserId() {
        return UserHandle.getUserHandleForUid(android.os.Process.myUid()).hashCode();
    }

    @Test
    public void installedPackagesAreNotEmpty() {
        List<PackageInfo> packages = PackageManagerCompat.getInstalledPackages(0, myUserId());

        assertNotNull("compat layer returned null on API " + Build.VERSION.SDK_INT, packages);
        assertFalse("compat layer enumerated no packages at all on API " + Build.VERSION.SDK_INT
                + " — the paginated API-37 path returns an empty list rather than throwing when it "
                + "is wired wrong", packages.isEmpty());
    }

    @Test
    public void compatEnumerationCoversWhatThePlatformReports() {
        Context context = ApplicationProvider.getApplicationContext();
        List<PackageInfo> platformPackages = context.getPackageManager().getInstalledPackages(0);
        List<PackageInfo> compatPackages = PackageManagerCompat.getInstalledPackages(0, myUserId());

        Set<String> platformNames = new HashSet<>();
        for (PackageInfo info : platformPackages) {
            platformNames.add(info.packageName);
        }
        Set<String> compatNames = new HashSet<>();
        for (PackageInfo info : compatPackages) {
            compatNames.add(info.packageName);
        }

        // Without privileges the compat layer sees at most what the platform grants this app, so
        // the platform set is the floor: anything it can see, the compat layer must also see.
        Set<String> missing = new HashSet<>(platformNames);
        missing.removeAll(compatNames);
        assertTrue("compat enumeration dropped packages the platform reports on API "
                + Build.VERSION.SDK_INT + ": " + missing, missing.isEmpty());
    }

    @Test
    public void ownPackageIsEnumeratedWithItsApplicationInfo() {
        Context context = ApplicationProvider.getApplicationContext();
        String self = context.getPackageName();

        List<PackageInfo> packages = PackageManagerCompat.getInstalledPackages(
                PackageManager.GET_META_DATA, myUserId());

        PackageInfo found = null;
        for (PackageInfo info : packages) {
            if (self.equals(info.packageName)) {
                found = info;
                break;
            }
        }
        assertNotNull("the app did not find itself in its own enumeration on API "
                + Build.VERSION.SDK_INT, found);
        // A short read on the paginated path can return entries whose bodies were dropped.
        assertNotNull("enumerated package carried no ApplicationInfo", found.applicationInfo);
    }

    @Test
    public void installedApplicationsAgreeWithInstalledPackages() throws RemoteException {
        List<PackageInfo> packages = PackageManagerCompat.getInstalledPackages(0, myUserId());
        List<ApplicationInfo> applications = PackageManagerCompat.getInstalledApplications(0, myUserId());

        assertFalse("no applications enumerated on API " + Build.VERSION.SDK_INT,
                applications.isEmpty());
        Set<String> packageNames = new HashSet<>();
        for (PackageInfo info : packages) {
            packageNames.add(info.packageName);
        }
        Set<String> applicationNames = new HashSet<>();
        for (ApplicationInfo info : applications) {
            applicationNames.add(info.packageName);
        }
        // Both go through the same privilege level, so one silently truncating while the other
        // does not is the signature of a wrong per-API branch in exactly one of them.
        Set<String> onlyInApplications = new HashSet<>(applicationNames);
        onlyInApplications.removeAll(packageNames);
        assertTrue("getInstalledApplications saw packages getInstalledPackages missed on API "
                + Build.VERSION.SDK_INT + ": " + onlyInApplications, onlyInApplications.isEmpty());
    }
}
