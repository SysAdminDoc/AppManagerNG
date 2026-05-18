// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmBatchApkInstallUtilsTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-batch-apk-install");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void isSupportedInstallSourceName_acceptsInstallerContainers() {
        assertTrue(FmBatchApkInstallUtils.isSupportedInstallSourceName("base.apk"));
        assertTrue(FmBatchApkInstallUtils.isSupportedInstallSourceName("bundle.apks"));
        assertTrue(FmBatchApkInstallUtils.isSupportedInstallSourceName("store.apkm"));
        assertTrue(FmBatchApkInstallUtils.isSupportedInstallSourceName("archive.xapk"));
        assertTrue(FmBatchApkInstallUtils.isSupportedInstallSourceName("BASE.APK"));
    }

    @Test
    public void isSupportedInstallSourceName_rejectsNonPackages() {
        assertFalse(FmBatchApkInstallUtils.isSupportedInstallSourceName("notes.txt"));
        assertFalse(FmBatchApkInstallUtils.isSupportedInstallSourceName("apk"));
        assertFalse(FmBatchApkInstallUtils.isSupportedInstallSourceName(".apk"));
        assertFalse(FmBatchApkInstallUtils.isSupportedInstallSourceName(null));
    }

    @Test
    public void canOfferInstall_requiresOnlyReadablePackageFiles() throws IOException {
        Path apk = root.createNewFile("one.apk", null);
        Path apks = root.createNewFile("two.apks", null);
        Path text = root.createNewFile("notes.txt", null);

        assertTrue(FmBatchApkInstallUtils.canOfferInstall(Arrays.asList(apk, apks)));
        assertFalse(FmBatchApkInstallUtils.canOfferInstall(Arrays.asList(apk, text)));
        assertFalse(FmBatchApkInstallUtils.canOfferInstall(Arrays.asList(apk, root)));
        assertFalse(FmBatchApkInstallUtils.canOfferInstall(java.util.Collections.emptyList()));
    }

    @Test
    public void getInstallIntent_usesBatchInstallerWithContentUris() throws IOException {
        Path apk = root.createNewFile("one.apk", null);
        Path apks = root.createNewFile("two.apks", null);

        Intent intent = FmBatchApkInstallUtils.getInstallIntent(RuntimeEnvironment.getApplication(),
                Arrays.asList(apk, apks));

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.getAction());
        assertNotNull(intent.getComponent());
        assertEquals(PackageInstallerActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(BuildConfig.APPLICATION_ID + ".extra.BATCH_INSTALL", false));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertNotNull(uris);
        assertEquals(Arrays.asList(FmProvider.getContentUri(apk), FmProvider.getContentUri(apks)), uris);
        assertNotNull(intent.getClipData());
        assertEquals(2, intent.getClipData().getItemCount());
    }
}
