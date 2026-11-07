// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.CachedApkSource;

@RunWith(RobolectricTestRunner.class)
public class PackageInstallerActivityTest {
    private static final String MIME_APK = "application/vnd.android.package-archive";

    @Test
    public void aBatchStopsOnAnApkWithNoNativeCodeForThisDevice() {
        InstallDependencyChecker.Issue minSdk = new InstallDependencyChecker.Issue(
                InstallDependencyChecker.IssueKind.MIN_SDK_TOO_HIGH, 36, 34);
        InstallDependencyChecker.Issue abiSplit = new InstallDependencyChecker.Issue(
                InstallDependencyChecker.IssueKind.INCOMPATIBLE_ABI_SPLIT, 0, 0, Collections.singletonList("x86"));
        InstallDependencyChecker.Issue nativeAbi = InstallDependencyChecker.checkNativeAbis(
                Collections.singletonList("x86_64"), Collections.singletonList("arm64-v8a"));
        assertNotNull(nativeAbi);
        List<InstallDependencyChecker.Issue> all = Arrays.asList(minSdk, abiSplit, nativeAbi);

        // A single APK installed on its own listed these in its install confirmation
        assertTrue(PackageInstallerActivity.selectCompatibilityWarnings(all, false, false).isEmpty());
        // A batch only asks about a downgrade, and an x86_64-only APK went straight to the system
        assertEquals(Collections.singletonList(nativeAbi),
                PackageInstallerActivity.selectCompatibilityWarnings(all, false, true));
        assertEquals(Arrays.asList(abiSplit, nativeAbi),
                PackageInstallerActivity.selectCompatibilityWarnings(all, true, false));
        assertEquals(Arrays.asList(abiSplit, nativeAbi),
                PackageInstallerActivity.selectCompatibilityWarnings(all, true, true));
    }

    @Test
    public void getBatchInstallInstanceCopiesUrisBeforeBuildingIntent() {
        Uri first = Uri.parse("content://example.test/apk/one.apk");
        Uri second = Uri.parse("content://example.test/apk/two.apks");
        Uri replacement = Uri.parse("content://example.test/apk/replacement.apk");
        ArrayList<Uri> input = new ArrayList<>(Arrays.asList(first, second));

        Intent intent = PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), input);

        input.clear();
        input.add(replacement);
        List<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertNotNull(streams);
        assertEquals(Arrays.asList(first, second), streams);
        assertNotNull(intent.getClipData());
        assertEquals(2, intent.getClipData().getItemCount());
        assertEquals(first, intent.getClipData().getItemAt(0).getUri());
        assertEquals(second, intent.getClipData().getItemAt(1).getUri());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue(PackageInstallerActivity.isBatchRequest(intent));
    }

    @Test
    public void getBatchInstallInstanceRejectsNullUri() {
        ArrayList<Uri> input = new ArrayList<>();
        input.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), input));
    }

    @Test
    public void batchInstanceForASourceCarriesThatSource() throws IOException {
        File apk = newCacheFile();
        CachedApkSource source = ApkSource.getOwnedCachedApkSource(apk, MIME_APK);

        Intent intent = PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), source);

        assertTrue(PackageInstallerActivity.isBatchRequest(intent));
        List<ApkQueueItem> items = PackageInstallerActivity.queueItemsFrom(intent, null);
        assertEquals(1, items.size());
        ApkSource queued = items.get(0).getApkSource();
        assertTrue(queued instanceof CachedApkSource);
        assertEquals(Uri.fromFile(apk), queued.getUri());
        assertEquals(apk, ((CachedApkSource) queued).getCachedFile());
    }

    @Test
    public void aSourceSentToARunningInstallerIsQueued() throws IOException {
        Intent intent = PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(),
                ApkSource.getOwnedCachedApkSource(newCacheFile(), MIME_APK));
        PackageInstallerActivity activity = Robolectric.buildActivity(PackageInstallerActivity.class).get();
        assertFalse(activity.hasNext());

        activity.onNewIntent(intent);

        assertTrue("A second app sent while the installer is open must not be dropped", activity.hasNext());
    }

    @Test
    public void onlyTheAppItselfCanSkipTheInstallPrompt() throws IOException {
        // The installer is exported, so any app can send it an APK along with the batch flag.
        // In root or ADB mode that installed the APK with no prompt at all.
        Intent fromAnotherApp = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://other.test/apk/payload.apk"), MIME_APK)
                .putExtra(PackageInstallerActivity.EXTRA_BATCH_INSTALL, true);
        assertFalse(PackageInstallerActivity.isBatchRequest(fromAnotherApp));
        fromAnotherApp.putExtra(PackageInstallerActivity.EXTRA_BATCH_INSTALL, "a guess");
        assertFalse(PackageInstallerActivity.isBatchRequest(fromAnotherApp));
        assertFalse(PackageInstallerActivity.isBatchRequest(PackageInstallerActivity.getLaunchableInstance(
                RuntimeEnvironment.getApplication(), ApkSource.getOwnedCachedApkSource(newCacheFile(), MIME_APK))));

        // The file manager and Local sync build their batches here and still skip it
        ArrayList<Uri> uris = new ArrayList<>(Collections.singletonList(Uri.parse("content://example.test/a.apk")));
        assertTrue(PackageInstallerActivity.isBatchRequest(
                PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), uris)));
        Intent fromLocalSync = PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(),
                ApkSource.getOwnedCachedApkSource(newCacheFile(), MIME_APK));
        assertTrue(PackageInstallerActivity.isBatchRequest(fromLocalSync));

        // Batch mode also stuck to the activity, so an APK another app sent while a batch was
        // running went in without a prompt too
        PackageInstallerActivity activity = Robolectric.buildActivity(PackageInstallerActivity.class).get();
        activity.onNewIntent(fromLocalSync);
        activity.onNewIntent(fromAnotherApp);
        assertEquals("An APK from another app must get the usual prompt",
                Arrays.asList(true, false), activity.queuedItemsSkippingPrompt());
    }

    @Test
    public void ownedCacheFileSurvivesParcelingAndIsDeletedOnCleanup() throws IOException {
        File apk = newCacheFile();
        CachedApkSource source = ApkSource.getOwnedCachedApkSource(apk, MIME_APK);
        Parcel parcel = Parcel.obtain();
        CachedApkSource copy;
        try {
            source.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            copy = CachedApkSource.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
        assertEquals(apk.getAbsoluteFile(), copy.getCachedFile());

        copy.cleanup();

        assertFalse(apk.exists());
        assertNull(copy.getCachedFile());
    }

    @Test
    public void developerVerificationAdbRetryAllowsCachedApkSource() {
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(
                Uri.parse("content://example.test/apk/blocked.apk"),
                "application/vnd.android.package-archive"));

        assertTrue(PackageInstallerActivity.canRetryDeveloperVerificationFailureViaAdb(
                "INSTALL_FAILED_ABORTED\nDeveloper verification: developer identity blocked or unverified (DEVELOPER_BLOCKED)",
                item,
                true));
    }

    @Test
    public void developerVerificationAdbRetryRequiresVerifierReasonAndAdb() {
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(
                Uri.parse("file:///sdcard/Download/app.apk"),
                "application/vnd.android.package-archive"));

        assertEquals(false, PackageInstallerActivity.canRetryDeveloperVerificationFailureViaAdb(
                "INSTALL_FAILED_ABORTED",
                item,
                true));
        assertEquals(false, PackageInstallerActivity.canRetryDeveloperVerificationFailureViaAdb(
                "Developer verification: network unavailable during developer verification (NETWORK_UNAVAILABLE)",
                item,
                false));
    }

    @Test
    public void developerVerificationAdbRetryRejectsInstallExistingItems() {
        Intent intent = new Intent().setData(Uri.parse("package:com.example.blocked"));
        ApkQueueItem installExistingItem = ApkQueueItem.fromIntent(intent, null).get(0);

        assertEquals(false, PackageInstallerActivity.canRetryDeveloperVerificationFailureViaAdb(
                "Developer verification: developer identity blocked or unverified (DEVELOPER_BLOCKED)",
                installExistingItem,
                true));
    }

    @Test
    public void isDowngradeDetectsLowerQueuedVersion() {
        PackageInfo installed = packageInfo(12);
        PackageInfo queued = packageInfo(7);

        assertTrue(PackageInstallerActivity.isDowngrade(installed, queued));
    }

    @Test
    public void isDowngradeRejectsSameOrNewerQueuedVersion() {
        assertEquals(false, PackageInstallerActivity.isDowngrade(packageInfo(12), packageInfo(12)));
        assertEquals(false, PackageInstallerActivity.isDowngrade(packageInfo(12), packageInfo(13)));
        assertEquals(false, PackageInstallerActivity.isDowngrade(null, packageInfo(7)));
        assertEquals(false, PackageInstallerActivity.isDowngrade(packageInfo(12), null));
    }

    private static File newCacheFile() throws IOException {
        File file = File.createTempFile("sync-", ".apk", RuntimeEnvironment.getApplication().getCacheDir());
        assertTrue(file.isFile());
        return file;
    }

    private static PackageInfo packageInfo(int versionCode) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.versionCode = versionCode;
        return packageInfo;
    }
}
