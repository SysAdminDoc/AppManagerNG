// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;

/**
 * The confirmation prompt is the last point at which an install can be declined, so what it says
 * about permissions and SDK levels has to be right — and silent about anything it cannot
 * establish, rather than reassuring.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallerManifestSummaryTest {
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mPackageManager = context.getPackageManager();
        // Robolectric ships no platform permission database, so the protection levels the real
        // device would report have to be registered before they can be resolved.
        declarePermission(Manifest.permission.CAMERA, PermissionInfo.PROTECTION_DANGEROUS);
        declarePermission(Manifest.permission.ACCESS_FINE_LOCATION, PermissionInfo.PROTECTION_DANGEROUS);
        declarePermission(Manifest.permission.INTERNET, PermissionInfo.PROTECTION_NORMAL);
    }

    private void declarePermission(String name, int protectionLevel) {
        PermissionInfo info = new PermissionInfo();
        info.name = name;
        info.packageName = "android";
        info.protectionLevel = protectionLevel;
        Shadows.shadowOf(mPackageManager).addPermissionInfo(info);
    }

    private static PackageInfo packageInfo(String[] permissions, int minSdk, int targetSdk) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.example.app";
        info.requestedPermissions = permissions;
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.minSdkVersion = minSdk;
        applicationInfo.targetSdkVersion = targetSdk;
        info.applicationInfo = applicationInfo;
        return info;
    }

    @Test
    public void dangerousPermissionsAreSeparatedFromTheRest() {
        PackageInfo info = packageInfo(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
        }, 21, 36);

        InstallerManifestSummary.Permissions permissions =
                InstallerManifestSummary.summarizePermissions(mPackageManager, info);

        assertEquals(3, permissions.getTotal());
        assertTrue(permissions.dangerous.contains(Manifest.permission.CAMERA));
        assertTrue(permissions.dangerous.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertTrue(permissions.other.contains(Manifest.permission.INTERNET));
        assertFalse(permissions.dangerous.contains(Manifest.permission.INTERNET));
    }

    @Test
    public void aRepeatedPermissionIsCountedOnce() {
        PackageInfo info = packageInfo(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.CAMERA,
                " " + Manifest.permission.CAMERA + " ",
        }, 21, 36);

        InstallerManifestSummary.Permissions permissions =
                InstallerManifestSummary.summarizePermissions(mPackageManager, info);

        assertEquals(1, permissions.getTotal());
    }

    @Test
    public void anUnknownPermissionIsReportedRatherThanDropped() {
        PackageInfo info = packageInfo(new String[]{"com.example.CUSTOM_THING"}, 21, 36);

        InstallerManifestSummary.Permissions permissions =
                InstallerManifestSummary.summarizePermissions(mPackageManager, info);

        assertEquals(1, permissions.getTotal());
        assertTrue(permissions.other.contains("com.example.CUSTOM_THING"));
        assertFalse("an unclassifiable permission must not be claimed to be sensitive",
                permissions.dangerous.contains("com.example.CUSTOM_THING"));
    }

    @Test
    public void blankAndNullEntriesAreIgnored() {
        PackageInfo info = packageInfo(new String[]{null, "", "   ", Manifest.permission.INTERNET}, 21, 36);

        InstallerManifestSummary.Permissions permissions =
                InstallerManifestSummary.summarizePermissions(mPackageManager, info);

        assertEquals(1, permissions.getTotal());
    }

    @Test
    public void anApkThatRequestsNothingReportsNothing() {
        assertTrue(InstallerManifestSummary.summarizePermissions(
                mPackageManager, packageInfo(null, 21, 36)).isEmpty());
        assertTrue(InstallerManifestSummary.summarizePermissions(
                mPackageManager, packageInfo(new String[0], 21, 36)).isEmpty());
        assertTrue(InstallerManifestSummary.summarizePermissions(mPackageManager, null).isEmpty());
    }

    @Test
    public void withoutAPackageManagerNothingIsClassifiedAsDangerous() {
        InstallerManifestSummary.Permissions permissions = InstallerManifestSummary.summarizePermissions(
                null, packageInfo(new String[]{Manifest.permission.CAMERA}, 21, 36));

        assertEquals(1, permissions.getTotal());
        assertTrue(permissions.dangerous.isEmpty());
    }

    @Test
    public void sdkLevelsAreReadFromTheManifest() {
        PackageInfo info = packageInfo(new String[0], 21, 36);
        assertEquals(21, InstallerManifestSummary.getMinSdk(info));
        assertEquals(36, InstallerManifestSummary.getTargetSdk(info));
    }

    @Test
    public void anUndeclaredSdkLevelReadsAsUnknownNotAsZero() {
        PackageInfo info = packageInfo(new String[0], 0, 0);
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN, InstallerManifestSummary.getMinSdk(info));
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN, InstallerManifestSummary.getTargetSdk(info));

        PackageInfo noApplicationInfo = new PackageInfo();
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN,
                InstallerManifestSummary.getMinSdk(noApplicationInfo));
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN,
                InstallerManifestSummary.getTargetSdk(noApplicationInfo));
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN, InstallerManifestSummary.getMinSdk(null));
        assertEquals(InstallerManifestSummary.SDK_UNKNOWN, InstallerManifestSummary.getTargetSdk(null));
    }

    @Test
    public void permissionNamesAreShortenedForDisplayWithoutLosingCustomOnes() {
        assertEquals(Arrays.asList("CAMERA", "ACCESS_FINE_LOCATION", "CUSTOM_THING", "BARE"),
                PackageInstallerActivity.shortenPermissionNames(Arrays.asList(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        "com.example.CUSTOM_THING",
                        "BARE")));
    }

    @Test
    public void aTrailingDotDoesNotProduceAnEmptyDisplayName() {
        assertEquals(Arrays.asList("android.permission."),
                PackageInstallerActivity.shortenPermissionNames(
                        Arrays.asList("android.permission.")));
    }
}
