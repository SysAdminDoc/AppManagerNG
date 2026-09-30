// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/** The decision AppDetailsActivity makes from its launch intent, before anything loads. */
@RunWith(RobolectricTestRunner.class)
public class AppDetailsTargetTest {
    private static final Uri SHARED = Uri.parse("content://com.example.files/shared/app.apk");
    private static final String APK = "application/vnd.android.package-archive";

    @Test
    public void aSharedApkIsOpened() {
        Intent share = new Intent(Intent.ACTION_SEND).setType(APK).putExtra(Intent.EXTRA_STREAM, SHARED);
        share.setClipData(ClipData.newRawUri(null, SHARED));

        AppDetailsActivity.Target target = AppDetailsActivity.resolveTarget(share);

        assertNull(target.packageName);
        assertNotNull(target.apkSource);
        assertEquals(SHARED, target.apkSource.getUri());
        assertEquals(0, target.error);
    }

    @Test
    public void anOpenedApkIsOpened() {
        AppDetailsActivity.Target target = AppDetailsActivity.resolveTarget(
                new Intent(Intent.ACTION_VIEW).setDataAndType(SHARED, APK));

        assertNotNull(target.apkSource);
        assertEquals(SHARED, target.apkSource.getUri());
    }

    @Test
    public void aDeepLinkStillOpensItsPackage() {
        AppDetailsActivity.Target target = AppDetailsActivity.resolveTarget(new Intent(Intent.ACTION_VIEW,
                Uri.parse("app-manager://details?id=com.example.app&user=10")));

        assertEquals("com.example.app", target.packageName);
        assertEquals(10, target.userId);
        assertNull(target.apkSource);
        assertEquals(0, target.error);
    }

    @Test
    public void aMalformedDeepLinkReportsWhatItAlwaysReported() {
        AppDetailsActivity.Target target = AppDetailsActivity.resolveTarget(
                new Intent(Intent.ACTION_VIEW, Uri.parse("app-manager://details")));

        assertNull(target.apkSource);
        assertEquals(R.string.failed_to_fetch_package_info, target.error);
    }

    @Test
    public void sharesWithoutOneUsableApkReportAShortReason() {
        assertEquals(R.string.apk_intent_unusable, AppDetailsActivity.resolveTarget(
                new Intent(Intent.ACTION_SEND).setType(APK)).error);
        assertEquals(R.string.apk_intent_unusable, AppDetailsActivity.resolveTarget(
                new Intent(Intent.ACTION_SEND).setType(APK).putExtra(Intent.EXTRA_STREAM, Uri.parse("file:x.apk"))).error);
        Intent twoFiles = new Intent(Intent.ACTION_SEND).setType(APK);
        ClipData clipData = ClipData.newRawUri(null, SHARED);
        clipData.addItem(new ClipData.Item(Uri.parse("content://com.example.files/other.apk")));
        twoFiles.setClipData(clipData);
        assertEquals(R.string.apk_intent_multiple, AppDetailsActivity.resolveTarget(twoFiles).error);
    }

    @Test
    public void aPackageNameExtraWinsOverAnUnusableShare() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType(APK)
                .putExtra("android.intent.extra.PACKAGE_NAME", "com.example.app");

        AppDetailsActivity.Target target = AppDetailsActivity.resolveTarget(intent);

        assertEquals("com.example.app", target.packageName);
        assertEquals(0, target.error);
    }

    @Test
    public void anEmptyIntentReportsTheOldMessage() {
        assertEquals(R.string.empty_package_name, AppDetailsActivity.resolveTarget(new Intent()).error);
    }
}
