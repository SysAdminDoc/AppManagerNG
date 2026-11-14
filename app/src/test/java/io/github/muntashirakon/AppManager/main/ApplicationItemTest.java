// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.filters.options.AppOpsOption;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;

@RunWith(RobolectricTestRunner.class)
public class ApplicationItemTest {
    @Test
    public void generateOtherInfoSkipsPerUserRuntimeFailures() {
        ApplicationItem item = app(new int[]{0, 10, 11});

        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                if (userId == 10) {
                    throw new SecurityException("private profile");
                }
                return userId == 0 ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                if (userId == 10) {
                    throw new SecurityException("private profile");
                }
                return userId == 11;
            }

            @Override
            public boolean checkCrossUserPermission(int userId) {
                return true;
            }
        });

        assertTrue(item.canReadLogs);
        assertTrue(item.isAppInactive);
    }

    @Test
    public void generateOtherInfoDefaultsUnavailableWhenPerUserQueriesFail() {
        ApplicationItem item = app(new int[]{10});

        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                throw new IllegalStateException("profile unavailable");
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                throw new IllegalStateException("profile unavailable");
            }

            @Override
            public boolean checkCrossUserPermission(int userId) {
                return true;
            }
        });

        assertFalse(item.canReadLogs);
        assertFalse(item.isAppInactive);
    }

    @Test
    public void generateOtherInfoSkipsInactiveCheckForUsersWithoutCrossUserPermission() {
        ApplicationItem item = app(new int[]{0, 11});

        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                return PackageManager.PERMISSION_DENIED;
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                // Would report the non-default user as inactive if it were ever consulted.
                return userId != 0;
            }

            @Override
            public boolean checkCrossUserPermission(int userId) {
                // No cross-user permission for the non-default user.
                return userId == 0;
            }
        });

        assertFalse("Inactive state for a non-default user must not be read without cross-user permission",
                item.isAppInactive);
    }

    @Test
    public void appOpsAreReadOnceAndKeptOnLaterCalls() {
        List<AppOpsManagerCompat.OpEntry> ops = Collections.singletonList(new AppOpsManagerCompat.OpEntry(null));
        int[] lookups = {0};
        ApplicationItem item = new ApplicationItem() {
            @Override
            List<AppOpsManagerCompat.OpEntry> lookUpAppOps() {
                ++lookups[0];
                return ops;
            }
        };

        assertSame(ops, item.getAppOps());
        assertSame(ops, item.getAppOps());
        assertEquals(1, lookups[0]);
    }

    @Test
    public void appOpsThatCouldntBeReadAreAskedForAgain() {
        List<AppOpsManagerCompat.OpEntry> ops = Collections.singletonList(new AppOpsManagerCompat.OpEntry(null));
        int[] lookups = {0};
        ApplicationItem item = new ApplicationItem() {
            @Override
            List<AppOpsManagerCompat.OpEntry> lookUpAppOps() {
                // The first read fails (app ops service not up yet), the next one works
                return ++lookups[0] == 1 ? null : ops;
            }
        };

        assertTrue(item.getAppOps().isEmpty());
        assertSame(ops, item.getAppOps());
        assertSame(ops, item.getAppOps());
        assertEquals(2, lookups[0]);
    }

    @Test
    public void withoutPackageInfoEveryListIsEmptyAndTheAppOpsFilterDoesNotThrow() {
        ApplicationItem item = app(new int[0]);

        assertTrue(item.getAppOps().isEmpty());
        assertTrue(item.getAllComponents().isEmpty());
        assertTrue(item.getAllPermissions().isEmpty());
        AppOpsOption option = new AppOpsOption();
        option.setKeyValue(FilterOption.KEY_ALL, null);
        assertFalse(option.test(item, new FilterOption.TestResult()).isMatched());
    }

    /** Tracker matching runs in native code the host can't load; these tests are about what gets kept. */
    @Implements(ComponentUtils.class)
    public static class ShadowComponentUtils {
        @Implementation
        public static boolean isTracker(String componentName) {
            return componentName.startsWith("com.google.android.gms.ads.");
        }
    }

    @Test
    @Config(shadows = ShadowComponentUtils.class)
    public void trackersAreFoundOnceThePackageCanBeReadAndThenKept() {
        ApplicationItem item = app(new int[]{0});
        // The package can't be read yet, so nothing is found and nothing is kept.
        assertTrue(item.getTrackerComponents().isEmpty());
        assertTrue(item.getAllComponents().isEmpty());

        ActivityInfo ads = new ActivityInfo();
        ads.packageName = item.packageName;
        ads.name = "com.google.android.gms.ads.AdActivity";
        ActivityInfo main = new ActivityInfo();
        main.packageName = item.packageName;
        main.name = item.packageName + ".MainActivity";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = item.packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = item.packageName;
        packageInfo.applicationInfo.uid = item.uid;
        packageInfo.activities = new ActivityInfo[]{ads, main};
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager()).installPackage(packageInfo);

        Map<ComponentInfo, Integer> trackers = item.getTrackerComponents();
        assertEquals(1, trackers.size());
        assertEquals(ads.name, trackers.keySet().iterator().next().name);
        assertSame(trackers, item.getTrackerComponents());
        assertEquals(2, item.getAllComponents().size());
        assertSame(item.getAllComponents(), item.getAllComponents());
    }

    @NonNull
    private static ApplicationItem app(@NonNull int[] userIds) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.privateprofile";
        item.userIds = userIds;
        item.uid = 12345;
        item.versionName = "1.0";
        return item;
    }
}
