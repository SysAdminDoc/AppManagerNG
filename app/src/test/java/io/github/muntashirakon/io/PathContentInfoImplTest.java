// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

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
import java.util.concurrent.atomic.AtomicInteger;
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
    public void fromPathFallsBackToTheExtensionWhenMagicDetectionCannotLoad() throws Exception {
        // A release build died here (#49): the magic database's static initializer threw, and the
        // ExceptionInInitializerError took the whole file list down with it
        Path png = copyTestIcon("photo.png");
        Path unknown = mRoot.createNewFile("README", null);
        AtomicInteger loads = new AtomicInteger();
        PathContentInfoImpl.setContentInfoUtilFactory(() -> {
            loads.incrementAndGet();
            throw new ExceptionInInitializerError(new NullPointerException("Package.getName()"));
        });
        try {
            assertEquals("image/png", png.getPathContentInfo().getMimeType());
            assertEquals("application/octet-stream", unknown.getPathContentInfo().getMimeType());
            // The next file still gets an answer, and the broken loader isn't retried for every row
            assertEquals("image/png", png.getPathContentInfo().getMimeType());
            assertEquals(1, loads.get());
        } finally {
            PathContentInfoImpl.setContentInfoUtilFactory(null);
        }
    }

    @Test
    public void fromPathFallsBackToTheExtensionWhenTheMatcherThrowsAnError() throws Exception {
        Path png = copyTestIcon("photo.png");
        PathContentInfoImpl.setContentInfoUtilFactory(() -> new ContentInfoUtil() {
            @Override
            public ContentInfo findMatch(InputStream is) {
                throw new NoClassDefFoundError("com.j256.simplemagic.entries.MagicEntries");
            }
        });
        try {
            assertEquals("image/png", png.getPathContentInfo().getMimeType());
        } finally {
            PathContentInfoImpl.setContentInfoUtilFactory(null);
        }
    }

    @Test
    public void fromPathStillSniffsBytesAfterTheFactoryIsRestored() throws Exception {
        PathContentInfoImpl.setContentInfoUtilFactory(() -> {
            throw new ExceptionInInitializerError(new NullPointerException());
        });
        Path pngWithTextExtension = copyTestIcon("icon.txt");
        assertEquals("text/plain", pngWithTextExtension.getPathContentInfo().getMimeType());
        PathContentInfoImpl.setContentInfoUtilFactory(null);

        assertEquals("image/png", pngWithTextExtension.getPathContentInfo().getMimeType());
    }

    private Path copyTestIcon(String name) throws IOException {
        Path path = mRoot.createNewFile(name, null);
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader())
                .getResourceAsStream("images/test_icon.png");
             OutputStream out = path.openOutputStream()) {
            IoUtils.copy(Objects.requireNonNull(in), out);
        }
        return path;
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
