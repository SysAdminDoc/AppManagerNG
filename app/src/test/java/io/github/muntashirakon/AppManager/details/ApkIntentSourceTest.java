// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class ApkIntentSourceTest {
    private static final Uri DATA = Uri.parse("content://com.example.files/data/app.apk");
    private static final Uri CLIP = Uri.parse("content://com.example.files/clip/app.apk");
    private static final Uri STREAM = Uri.parse("content://com.example.files/stream/app.apk");

    @Test
    public void explicitDataOutranksClipDataAndStream() {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(DATA, "application/vnd.android.package-archive");
        intent.setClipData(ClipData.newRawUri(null, CLIP));
        intent.putExtra(Intent.EXTRA_STREAM, STREAM);

        assertResolved(DATA, ApkIntentSource.resolve(intent));
    }

    @Test
    public void clipDataOutranksStream() {
        Intent intent = share(STREAM);
        intent.setClipData(ClipData.newRawUri(null, CLIP));

        assertResolved(CLIP, ApkIntentSource.resolve(intent));
    }

    @Test
    public void streamIsUsedWhenItIsTheOnlyPayload() {
        assertResolved(STREAM, ApkIntentSource.resolve(share(STREAM)));
    }

    @Test
    public void shareCopiedIntoClipDataBySystemResolvesToThatFile() {
        // startActivity() copies EXTRA_STREAM into the clip data, so both carry the same URI.
        Intent intent = share(STREAM);
        intent.setClipData(ClipData.newRawUri(null, STREAM));

        assertResolved(STREAM, ApkIntentSource.resolve(intent));
    }

    @Test
    public void clipDataWithoutAUriFallsBackToStream() {
        Intent intent = share(STREAM);
        intent.setClipData(ClipData.newPlainText(null, "Look at this app"));

        assertResolved(STREAM, ApkIntentSource.resolve(intent));
    }

    @Test
    public void clipDataRepeatingOneFileIsNotMultipleFiles() {
        Intent intent = share(null);
        ClipData clipData = ClipData.newRawUri(null, CLIP);
        clipData.addItem(new ClipData.Item(CLIP));
        intent.setClipData(clipData);

        assertResolved(CLIP, ApkIntentSource.resolve(intent));
    }

    @Test
    public void clipDataWithTwoFilesIsRefused() {
        Intent intent = share(null);
        ClipData clipData = ClipData.newRawUri(null, CLIP);
        clipData.addItem(new ClipData.Item(STREAM));
        intent.setClipData(clipData);

        assertRefused(R.string.apk_intent_multiple, ApkIntentSource.resolve(intent));
    }

    @Test
    public void sendMultipleIsRefusedEvenWithOneFile() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/vnd.android.package-archive");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(Collections.singletonList(STREAM)));
        intent.setClipData(ClipData.newRawUri(null, STREAM));

        assertRefused(R.string.apk_intent_multiple, ApkIntentSource.resolve(intent));
    }

    @Test
    public void shareWithoutAFileIsRefused() {
        Intent intent = share(null);
        intent.putExtra(Intent.EXTRA_TEXT, "https://example.com/app.apk");

        assertRefused(R.string.apk_intent_unusable, ApkIntentSource.resolve(intent));
    }

    @Test
    public void onlyContentAndFileUrisAreAccepted() {
        String[] refused = {
                "https://example.com/app.apk",
                "package:com.example.app",
                "vfs://1/base.apk",
                "/sdcard/Download/app.apk",
                // Opaque URIs: any app can send one, and the sanitizer used to throw on file:x.
                "file:x.apk",
                "content:x.apk",
        };
        for (String uri : refused) {
            assertRefused(R.string.apk_intent_unusable, ApkIntentSource.resolve(share(Uri.parse(uri))));
            assertRefused(R.string.apk_intent_unusable,
                    ApkIntentSource.resolve(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))));
        }
        Uri file = Uri.parse("file:///sdcard/Download/app.apk");
        assertResolved(file, ApkIntentSource.resolve(share(file)));
        assertResolved(file, ApkIntentSource.resolve(new Intent(Intent.ACTION_VIEW, file)));
    }

    @Test
    public void aMalformedDeepLinkEndsTheWayItAlwaysDid() {
        for (String link : new String[]{"app-manager://details", "am://app/"}) {
            assertRefused(R.string.failed_to_fetch_package_info,
                    ApkIntentSource.resolve(new Intent(Intent.ACTION_VIEW, Uri.parse(link))));
        }
    }

    @Test
    public void intentWithoutAnyUriLeavesTheOtherSourcesInCharge() {
        // Internal launches pass a package name or a parcelled ApkSource instead of a URI.
        ApkIntentSource source = ApkIntentSource.resolve(new Intent().putExtra("src", new android.os.Bundle()));

        assertNull(source.uri);
        assertEquals(0, source.error);
    }

    private static Intent share(Uri stream) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive");
        if (stream != null) {
            intent.putExtra(Intent.EXTRA_STREAM, stream);
        }
        return intent;
    }

    private static void assertResolved(Uri expected, ApkIntentSource source) {
        assertEquals(expected, source.uri);
        assertEquals(0, source.error);
    }

    private static void assertRefused(int error, ApkIntentSource source) {
        assertNull(source.uri);
        assertEquals(error, source.error);
    }
}
