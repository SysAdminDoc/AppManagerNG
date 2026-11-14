// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.apk.installer.ShadowArchivedPackages;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.filters.options.AppOpsOption;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.test.shadows.ShadowPackageManagerCompat;

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

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anAppArchivedOnEveryUserIsKnownArchivedAndShowsTheLauncherIcon() {
        ShadowArchivedPackages.reset();
        Drawable launcherIcon = new ColorDrawable(Color.GREEN);
        ShadowArchivedPackages.keep(ARCHIVED, "Example", launcherIcon);
        installUninstalled(ARCHIVED, 1_700_000_000_000L);
        ApplicationItem item = uninstalledRow(ARCHIVED, UserHandleHidden.myUserId());
        assertFalse(item.isKnownArchived());
        assertEquals(ARCHIVED, MainRecyclerAdapter.getIconTag(item));

        assertTrue(item.isArchived());
        assertTrue(item.isKnownArchived());
        assertSame(launcherIcon, item.loadIcon(pm()));
        // Cached apart from the icon the package had, or got from an older version, under its name
        assertEquals(ARCHIVED + "#archived-own-1700000000000", MainRecyclerAdapter.getIconTag(item));
        // Still no user it's installed for, so nothing that needs the APK is offered
        assertEquals(-1, item.getUserId());
        assertEquals(Collections.singletonList(ARCHIVED), ShadowArchivedPackages.sAsked);
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anArchivedAppWithNoLauncherEntryFallsBackToTheDefaultIcon() {
        ShadowArchivedPackages.reset();
        ShadowArchivedPackages.keepNone();
        installUninstalled(ARCHIVED, 1_700_000_000_000L);
        ApplicationItem item = uninstalledRow(ARCHIVED, UserHandleHidden.myUserId());

        assertTrue(item.isArchived());
        assertNotNull(item.loadIcon(pm()));
        assertEquals(1, ShadowArchivedPackages.sAsked.size());
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anAppArchivedOnlyForAnotherUserIsNotAskedForItsLauncherEntry() {
        ShadowArchivedPackages.reset();
        Drawable launcherIcon = new ColorDrawable(Color.GREEN);
        ShadowArchivedPackages.keep(ARCHIVED, "Example", launcherIcon);
        installUninstalled(ARCHIVED, 1_700_000_000_000L);
        ApplicationItem item = uninstalledRow(ARCHIVED, UserHandleHidden.myUserId() + 10);

        assertTrue(item.isArchived());
        // Android answers getArchivedPackage for the calling user only
        Drawable icon = item.loadIcon(pm());
        assertNotNull(icon);
        assertNotSame(launcherIcon, icon);
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
        // Not the launcher's icon, so not cached where this user's archived copy would put it
        assertEquals(ARCHIVED + "#archived-other-1700000000000", MainRecyclerAdapter.getIconTag(item));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void archivingAgainCachesTheIconUnderANewKey() {
        ShadowArchivedPackages.reset();
        int me = UserHandleHidden.myUserId();
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me, uninstalledPackage(ARCHIVED, ARCHIVE_TIME));
        ApplicationItem first = uninstalledRow(ARCHIVED, me);
        assertTrue(first.isArchived());
        // Restored, updated and archived again, maybe with another launcher icon
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me, uninstalledPackage(ARCHIVED, ARCHIVE_TIME + 1));
        ApplicationItem again = uninstalledRow(ARCHIVED, me);
        assertTrue(again.isArchived());

        assertNotEquals(MainRecyclerAdapter.getIconTag(first), MainRecyclerAdapter.getIconTag(again));
        assertNotEquals(ARCHIVED, MainRecyclerAdapter.getIconTag(again));
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anUninstalledAppThatIsNotArchivedKeepsNoPackageInfo() {
        ShadowArchivedPackages.reset();
        installUninstalled(ARCHIVED, 0);
        ApplicationItem item = uninstalledRow(ARCHIVED, UserHandleHidden.myUserId());

        assertFalse(item.isArchived());
        assertTrue(item.getAllComponents().isEmpty());
        assertNotNull(item.loadIcon(pm()));
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void thisUsersCopyIsLookedUpFirstAndTheLookupStopsAtTheFirstArchivedOne() {
        ShadowArchivedPackages.reset();
        Drawable launcherIcon = new ColorDrawable(Color.GREEN);
        ShadowArchivedPackages.keep(ARCHIVED, "Example", launcherIcon);
        int me = UserHandleHidden.myUserId();
        for (int userId : new int[]{me + 10, me, me + 11}) {
            ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, userId, uninstalledPackage(ARCHIVED, ARCHIVE_TIME));
        }
        ApplicationItem item = uninstalledRow(ARCHIVED, me + 10, me, me + 11);

        assertTrue(item.isArchived());
        assertSame(launcherIcon, item.loadIcon(pm()));
        assertEquals(Collections.singletonList(ARCHIVED + "/" + me), ShadowPackageManagerCompat.getPackageInfoLookups());
    }

    @Test
    @Config(shadows = ShadowArchivedPackages.class)
    public void anAppArchivedOnlyForALaterUserIsFoundPastTheUsersItIsNotArchivedFor() {
        ShadowArchivedPackages.reset();
        ShadowArchivedPackages.keep(ARCHIVED, "Example", new ColorDrawable(Color.GREEN));
        int me = UserHandleHidden.myUserId();
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me, uninstalledPackage(ARCHIVED, 0));
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me + 10, null);
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me + 11, uninstalledPackage(ARCHIVED, 0));
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me + 12, uninstalledPackage(ARCHIVED, ARCHIVE_TIME));
        ShadowPackageManagerCompat.setPackageInfoForUser(ARCHIVED, me + 13, uninstalledPackage(ARCHIVED, ARCHIVE_TIME));
        ApplicationItem item = uninstalledRow(ARCHIVED, me + 10, me + 11, me, me + 12, me + 13);

        assertTrue(item.isArchived());
        assertEquals(Arrays.asList(ARCHIVED + "/" + me, ARCHIVED + "/" + (me + 10), ARCHIVED + "/" + (me + 11),
                ARCHIVED + "/" + (me + 12)), ShadowPackageManagerCompat.getPackageInfoLookups());
        // Archived for another user, and Android hands the launcher entry only to that user
        assertNotNull(item.loadIcon(pm()));
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
        assertEquals(-1, item.getUserId());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.TIRAMISU)
    public void beforeAndroid15AnUninstalledRowIsNeverLookedUp() {
        installUninstalled(ARCHIVED, 0);
        ShadowPackageManagerCompat.resetPackageInfo();
        ApplicationItem item = uninstalledRow(ARCHIVED, UserHandleHidden.myUserId());

        assertFalse(item.isArchived());
        // The package is readable, so any package info kept would show its activity
        assertTrue(item.getAllComponents().isEmpty());
        assertNotNull(item.loadIcon(pm()));
        // No lookup at all: Android 14 and older have no archived apps, and most rows aren't archived
        assertTrue(ShadowPackageManagerCompat.getPackageInfoLookups().isEmpty());
    }

    @Before
    @After
    public void forgetPerUserPackages() {
        // Before too: other classes' lookups land in the same lists
        ShadowPackageManagerCompat.resetPackageInfo();
    }

    static final String ARCHIVED = "com.example.archived";
    static final long ARCHIVE_TIME = 1_700_000_000_000L;

    @NonNull
    private static PackageManager pm() {
        return ApplicationProvider.getApplicationContext().getPackageManager();
    }

    private static void installUninstalled(@NonNull String packageName, long archiveTimeMillis) {
        shadowOf(pm()).installPackage(uninstalledPackage(packageName, archiveTimeMillis));
    }

    /** What Android keeps of an app whose APK is gone: the package, its activity names, maybe an archive time. */
    @NonNull
    static PackageInfo uninstalledPackage(@NonNull String packageName, long archiveTimeMillis) {
        ActivityInfo launcher = new ActivityInfo();
        launcher.packageName = packageName;
        launcher.name = packageName + ".Launcher";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.applicationInfo.uid = 12346;
        packageInfo.activities = new ActivityInfo[]{launcher};
        if (archiveTimeMillis > 0) {
            ReflectionHelpers.setField(packageInfo, "mArchiveTimeMillis", archiveTimeMillis);
        }
        return packageInfo;
    }

    @NonNull
    private static ApplicationItem uninstalledRow(@NonNull String packageName, int... uninstalledUserIds) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.label = packageName;
        item.isInstalled = false;
        item.uninstalledUserIds = uninstalledUserIds;
        return item;
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
