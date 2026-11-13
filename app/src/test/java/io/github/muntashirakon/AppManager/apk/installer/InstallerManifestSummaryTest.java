// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

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

    // Issue #22: what an update adds and removes, shown next to the session's SHA-256

    @Test
    public void anUpdateListsWhatItAddsAndRemovesKindByKind() {
        PackageInfo installed = packageInfo(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET,
        }, 21, 36);
        installed.versionName = "1.0";
        installed.setLongVersionCode(10);
        installed.activities = components(new ActivityInfo[2], "com.example.app.Main", "com.example.app.Old");
        installed.services = components(new ServiceInfo[1], "com.example.app.Sync");
        installed.providers = components(new ProviderInfo[1], "com.example.app.Files");
        PackageInfo apk = packageInfo(new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET,
        }, 21, 36);
        apk.versionName = "1.1";
        apk.setLongVersionCode(11);
        apk.activities = components(new ActivityInfo[2], "com.example.app.Settings", "com.example.app.Main");
        apk.services = components(new ServiceInfo[1], "com.example.app.Sync");
        apk.receivers = components(new ActivityInfo[1], "com.example.app.Boot");

        InstallerManifestSummary.Changes changes = InstallerManifestSummary.compare(installed, apk);

        assertFalse(changes.newInstall);
        assertEquals("1.0", changes.oldVersionName);
        assertEquals(10, changes.oldVersionCode);
        assertEquals("1.1", changes.newVersionName);
        assertEquals(11, changes.newVersionCode);
        assertEquals(InstallerManifestSummary.Kind.values().length, changes.kinds.size());
        InstallerManifestSummary.KindChange permissions = changes.get(InstallerManifestSummary.Kind.PERMISSIONS);
        assertEquals(Collections.singletonList(Manifest.permission.ACCESS_FINE_LOCATION), permissions.added);
        assertEquals(Collections.singletonList(Manifest.permission.CAMERA), permissions.removed);
        assertEquals("a repeated permission counts once", 2, permissions.total);
        InstallerManifestSummary.KindChange activities = changes.get(InstallerManifestSummary.Kind.ACTIVITIES);
        assertEquals(Collections.singletonList("com.example.app.Settings"), activities.added);
        assertEquals(Collections.singletonList("com.example.app.Old"), activities.removed);
        assertEquals(2, activities.total);
        InstallerManifestSummary.KindChange services = changes.get(InstallerManifestSummary.Kind.SERVICES);
        assertFalse(services.hasChanges());
        assertEquals(1, services.total);
        InstallerManifestSummary.KindChange receivers = changes.get(InstallerManifestSummary.Kind.RECEIVERS);
        assertEquals(Collections.singletonList("com.example.app.Boot"), receivers.added);
        assertTrue(receivers.removed.isEmpty());
        InstallerManifestSummary.KindChange providers = changes.get(InstallerManifestSummary.Kind.PROVIDERS);
        assertTrue(providers.added.isEmpty());
        assertEquals(Collections.singletonList("com.example.app.Files"), providers.removed);
        assertEquals(0, providers.total);
    }

    @Test
    public void namesAreSortedSoTheListReadsTheSameEveryTime() {
        PackageInfo installed = packageInfo(new String[0], 21, 36);
        PackageInfo apk = packageInfo(new String[0], 21, 36);
        apk.activities = components(new ActivityInfo[3], "com.example.app.C", "com.example.app.A", "com.example.app.B");

        InstallerManifestSummary.Changes changes = InstallerManifestSummary.compare(installed, apk);

        assertEquals(Arrays.asList("com.example.app.A", "com.example.app.B", "com.example.app.C"),
                changes.get(InstallerManifestSummary.Kind.ACTIVITIES).added);
    }

    @Test
    public void aNewInstallOnlyCountsWhatTheApkDeclares() {
        PackageInfo apk = packageInfo(new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET}, 21, 36);
        apk.versionName = "2.0";
        apk.setLongVersionCode(20);
        apk.activities = components(new ActivityInfo[1], "com.example.app.Main");

        InstallerManifestSummary.Changes changes = InstallerManifestSummary.compare(null, apk);

        assertTrue(changes.newInstall);
        assertNull(changes.oldVersionName);
        assertEquals("2.0", changes.newVersionName);
        assertEquals(20, changes.newVersionCode);
        assertEquals(2, changes.get(InstallerManifestSummary.Kind.PERMISSIONS).total);
        assertEquals(1, changes.get(InstallerManifestSummary.Kind.ACTIVITIES).total);
        for (InstallerManifestSummary.KindChange kind : changes.kinds) {
            assertFalse(kind.kind + " has nothing to compare with", kind.hasChanges());
        }
    }

    @Test
    public void componentNamesReadRelativeToTheirOwnPackage() {
        assertEquals(".Main", InstallerManifestSummary.displayName("com.example.app", "com.example.app.Main"));
        assertEquals(".ui.Main", InstallerManifestSummary.displayName("com.example.app", "com.example.app.ui.Main"));
        assertEquals("com.example.appx.Main",
                InstallerManifestSummary.displayName("com.example.app", "com.example.appx.Main"));
        assertEquals("androidx.work.impl.WorkerService",
                InstallerManifestSummary.displayName("com.example.app", "androidx.work.impl.WorkerService"));
        assertEquals("com.example.app.", InstallerManifestSummary.displayName("com.example.app", "com.example.app."));
        assertEquals("com.example.app.Main", InstallerManifestSummary.displayName(null, "com.example.app.Main"));
    }

    @Test
    public void loadReadsTheApkOnDiskAgainstTheInstalledCopy() {
        File apkFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("oandbackups/ademar.textlauncher/base.apk")).getFile());
        PackageInfo parsed = mPackageManager.getPackageArchiveInfo(apkFile.getAbsolutePath(),
                InstallerManifestSummary.CHANGE_FLAGS);
        assertNotNull(parsed);
        assertNotNull("the parser has to be asked for components", parsed.activities);

        InstallerManifestSummary.Changes fresh = InstallerManifestSummary.load(mPackageManager, apkFile);

        assertNotNull(fresh);
        assertTrue(fresh.newInstall);
        assertEquals(parsed.activities.length, fresh.get(InstallerManifestSummary.Kind.ACTIVITIES).total);

        PackageInfo installed = new PackageInfo();
        installed.packageName = parsed.packageName;
        installed.versionName = "0.1";
        installed.setLongVersionCode(1);
        installed.applicationInfo = new ApplicationInfo();
        installed.applicationInfo.packageName = parsed.packageName;
        String gone = parsed.packageName + ".Gone";
        installed.activities = components(new ActivityInfo[1], gone);
        installed.activities[0].applicationInfo = installed.applicationInfo;
        Shadows.shadowOf(mPackageManager).installPackage(installed);

        InstallerManifestSummary.Changes update = InstallerManifestSummary.load(mPackageManager, apkFile);

        assertNotNull(update);
        assertFalse(update.newInstall);
        assertEquals("0.1", update.oldVersionName);
        assertEquals(1, update.oldVersionCode);
        assertEquals(Collections.singletonList(gone), update.get(InstallerManifestSummary.Kind.ACTIVITIES).removed);
        assertEquals(parsed.activities.length, update.get(InstallerManifestSummary.Kind.ACTIVITIES).added.size());
    }

    @Test
    public void aFileThatIsNotAnApkHasNothingToShow() throws IOException {
        File notAnApk = File.createTempFile("not-an-apk", ".apk");
        try {
            Files.write(notAnApk.toPath(), "hello".getBytes(StandardCharsets.UTF_8));

            assertNull(InstallerManifestSummary.load(mPackageManager, notAnApk));
        } finally {
            assertTrue(notAnApk.delete());
        }
    }

    private static <T extends ComponentInfo> T[] components(T[] out, String... names) {
        for (int i = 0; i < names.length; ++i) {
            ComponentInfo info = out instanceof ServiceInfo[] ? new ServiceInfo()
                    : out instanceof ProviderInfo[] ? new ProviderInfo() : new ActivityInfo();
            info.name = names[i];
            info.packageName = "com.example.app";
            @SuppressWarnings("unchecked")
            T typed = (T) info;
            out[i] = typed;
        }
        return out;
    }
}
