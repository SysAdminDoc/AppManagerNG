// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

@RunWith(RobolectricTestRunner.class)
public class PathContentInfoImplTest {
    private java.nio.file.Path mTempDir;
    private Path mRoot;

    @Before
    public void setUp() throws IOException {
        mTempDir = Files.createTempDirectory("appmanagerng-path-content");
        mRoot = Paths.get(mTempDir.toFile());
    }

    @After
    public void tearDown() {
        if (mRoot != null) {
            mRoot.delete();
        }
    }

    @Test
    public void fromPathPrefersMagicBytesOverWrongExtension() throws Exception {
        Path pngWithTextExtension = mRoot.createNewFile("icon.txt", null);
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader())
                .getResourceAsStream("images/test_icon.png");
             OutputStream out = pngWithTextExtension.openOutputStream()) {
            IoUtils.copy(Objects.requireNonNull(in), out);
        }

        PathContentInfo contentInfo = pngWithTextExtension.getPathContentInfo();

        assertEquals("image/png", contentInfo.getMimeType());
    }

    @Test
    public void fromPathKeepsExtensionForPartialArchiveMatch() throws Exception {
        Path apksFile = mRoot.createNewFile("bundle.apks", null);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(apksFile.openOutputStream())) {
            zipOutputStream.putNextEntry(new ZipEntry("base.apk"));
            zipOutputStream.write("placeholder".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        PathContentInfo contentInfo = apksFile.getPathContentInfo();

        assertEquals("application/x-apks", contentInfo.getMimeType());
    }

    @Test
    public void fromPathSniffsAZipEntryWithoutExtractingIt() throws Exception {
        assertSniffedWithoutExtracting("application/zip", entry -> entry);
    }

    @Test
    public void fromPathSniffsAnApkEntryWithoutExtractingIt() throws Exception {
        assertSniffedWithoutExtracting("application/vnd.android.package-archive", entry -> entry);
    }

    @Test
    public void fromPathSniffsAnArchiveEntrySharedThroughThisAppsProviderWithoutExtractingIt() throws Exception {
        ProviderInfo info = new ProviderInfo();
        info.authority = FmProvider.AUTHORITY;
        info.grantUriPermissions = true;
        Robolectric.buildContentProvider(FmProvider.class).create(info);

        assertSniffedWithoutExtracting("application/zip", entry -> {
            Uri shared = FmProvider.getContentUri(entry);
            assertEquals(ContentResolver.SCHEME_CONTENT, shared.getScheme());
            return Paths.get(shared);
        });
    }

    private interface Sniffed {
        Path of(Path entry) throws Exception;
    }

    private void assertSniffedWithoutExtracting(String archiveType, Sniffed sniffed) throws Exception {
        // A PNG padded far past what a sniff reads, named so only its bytes say what it is
        File zip = new File(mTempDir.toFile(), "images.zip");
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader())
                .getResourceAsStream("images/test_icon.png");
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("icon.dat"));
            IoUtils.copy(Objects.requireNonNull(in), zos);
            zos.write(new byte[4 * 1024 * 1024]);
            zos.closeEntry();
        }
        Path mountPoint = Paths.get("/tmp/am_content_mount_" + System.nanoTime());
        int vfsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(zip), archiveType);
        try {
            File cacheDir = new File(FileUtils.getCachePath(), "files");
            List<String> before = listOf(cacheDir);
            Path entry = mountPoint.findFile("icon.dat");
            assertTrue(entry.isFile());

            PathContentInfo contentInfo = sniffed.of(entry).getPathContentInfo();

            assertEquals("image/png", contentInfo.getMimeType());
            List<String> added = new ArrayList<>(listOf(cacheDir));
            added.removeAll(before);
            assertEquals("nothing extracted into the archive's cache", Collections.emptyList(), added);
        } finally {
            VirtualFileSystem.unmount(vfsId);
        }
    }

    private static List<String> listOf(File dir) {
        String[] names = dir.list();
        return names != null ? Arrays.asList(names) : Collections.emptyList();
    }
}
