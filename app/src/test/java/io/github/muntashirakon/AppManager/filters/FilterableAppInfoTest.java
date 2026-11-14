// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.filters.options.AppOpsOption;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;

@RunWith(RobolectricTestRunner.class)
public class FilterableAppInfoTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void appOpsAreReadOnceAndKeptOnLaterCalls() throws IOException {
        List<AppOpsManagerCompat.OpEntry> ops = Collections.singletonList(new AppOpsManagerCompat.OpEntry(null));
        int[] lookups = {0};
        FilterableAppInfo info = new FilterableAppInfo(installedPackage(), null) {
            @Override
            List<AppOpsManagerCompat.OpEntry> lookUpAppOps() {
                ++lookups[0];
                return ops;
            }
        };

        assertSame(ops, info.getAppOps());
        assertSame(ops, info.getAppOps());
        assertEquals(1, lookups[0]);
    }

    @Test
    public void appOpsThatCouldntBeReadAreAskedForAgain() throws IOException {
        List<AppOpsManagerCompat.OpEntry> ops = Collections.singletonList(new AppOpsManagerCompat.OpEntry(null));
        int[] lookups = {0};
        FilterableAppInfo info = new FilterableAppInfo(installedPackage(), null) {
            @Override
            List<AppOpsManagerCompat.OpEntry> lookUpAppOps() {
                // The first read fails (app ops service not up yet), the next one works
                return ++lookups[0] == 1 ? null : ops;
            }
        };

        assertTrue(info.getAppOps().isEmpty());
        assertSame(ops, info.getAppOps());
        assertSame(ops, info.getAppOps());
        assertEquals(2, lookups[0]);
    }

    @Test
    public void appOpsFromTheHostDontThrowOutOfTheFilter() throws IOException {
        // Whatever the host's app ops service does with this package, the filter gets a list. AppOpsOrNullTest
        // covers a service that refuses or isn't there.
        FilterableAppInfo info = new FilterableAppInfo(installedPackage(), null);

        assertNotNull(info.getAppOps());
        assertFalse(appOpsFilterMatches(info));
    }

    @Test
    public void anAppThatIsntInstalledHasNoOpsComponentsOrPermissions() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "com.example.gone";
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageInfo.packageName;
        applicationInfo.uid = 10124;
        packageInfo.applicationInfo = applicationInfo;
        FilterableAppInfo info = new FilterableAppInfo(packageInfo, null);

        assertTrue(info.getAppOps().isEmpty());
        assertTrue(info.getAllComponents().isEmpty());
        assertTrue(info.getAllPermissions().isEmpty());
        assertFalse(appOpsFilterMatches(info));
    }

    private static boolean appOpsFilterMatches(@NonNull IFilterableAppInfo info) {
        AppOpsOption option = new AppOpsOption();
        option.setKeyValue(FilterOption.KEY_ALL, null);
        return option.test(info, new FilterOption.TestResult()).isMatched();
    }

    @NonNull
    private PackageInfo installedPackage() throws IOException {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "com.example.ops";
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageInfo.packageName;
        applicationInfo.uid = 10123;
        applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED | ApplicationInfo.FLAG_HAS_CODE;
        applicationInfo.processName = packageInfo.packageName;
        applicationInfo.publicSourceDir = temporaryFolder.newFile("base.apk").getAbsolutePath();
        packageInfo.applicationInfo = applicationInfo;
        return packageInfo;
    }
}
