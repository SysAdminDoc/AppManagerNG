// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.file.Files;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AppInfoFragmentShareIntentTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-apk-share");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void buildApkShareIntentUsesConcreteApkMimeTypeAndStreamGrant() throws IOException {
        Path apk = root.createNewFile("base.apk", null);
        Uri uri = FmProvider.getContentUri(apk);

        Intent intent = AppInfoFragment.buildApkShareIntent(apk);

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals(apk.getType(), intent.getType());
        assertNotEquals("application/*", intent.getType());
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void buildApkShareIntentUsesConcreteSplitArchiveMimeType() throws IOException {
        Path apks = root.createNewFile("bundle.apks", null);

        Intent intent = AppInfoFragment.buildApkShareIntent(apks);

        assertEquals(apks.getType(), intent.getType());
        assertNotEquals("application/*", intent.getType());
    }

    @Test
    public void buildAppVerifierShareIntentUsesPackageThenColonSeparatedSha256() {
        Intent intent = AppInfoFragment.buildAppVerifierShareIntent(" io.github.example.app ",
                "aa:bb:cc:dd");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("io.github.example.app\nAA:BB:CC:DD", intent.getStringExtra(Intent.EXTRA_TEXT));
    }
}
