// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.net.Uri;
import android.system.OsConstants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Note: We don't have to test weird paths such as ./, ../, etc. because they're taken care of by the Path API.
@RunWith(RobolectricTestRunner.class)
public class ZipFileSystemTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private final List<Integer> mountedFileSystems = new ArrayList<>();
    private final List<File> temporaryArchives = new ArrayList<>();
    private Path tmpPath;

    @Before
    public void setUp() throws Exception {
        tmpPath = Paths.get("/tmp");
    }

    @After
    public void tearDown() throws Exception {
        IOException unmountFailure = null;
        for (int i = mountedFileSystems.size() - 1; i >= 0; --i) {
            try {
                VirtualFileSystem.unmount(mountedFileSystems.get(i));
            } catch (IOException e) {
                if (unmountFailure == null) unmountFailure = e;
                else unmountFailure.addSuppressed(e);
            }
        }
        mountedFileSystems.clear();
        for (File archive : temporaryArchives) {
            if (archive.exists() && !archive.delete()) archive.deleteOnExit();
        }
        temporaryArchives.clear();
        if (unmountFailure != null) throw unmountFailure;
    }

    @Test
    public void isFileOrDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_1");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
    }

    @Test
    public void isHidden() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_hidden");
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            temporaryArchives.add(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        mountPoint.createNewFile(".hidden", null);
        assertFalse("AndroidManifest.xml should not be hidden", fs.isHidden("/AndroidManifest.xml"));
        assertTrue("dot-prefixed ZIP entries should be hidden", fs.isHidden("/.hidden"));
        unmountTracked(fsId);
    }

    @Test
    public void lastAccess() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_access");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        long access = mountPoint.findFile("AndroidManifest.xml").lastAccess();
        assertTrue("lastAccess should use the ZIP entry timestamp", access > 0);
        unmountTracked(fsId);
    }

    @Test
    public void creationTime() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_creation");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        long creation = mountPoint.findFile("AndroidManifest.xml").creationTime();
        assertTrue("creationTime should use the ZIP entry timestamp", creation > 0);
        unmountTracked(fsId);
    }

    @Test
    public void createNewFileReadOnly() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_2");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        assertThrows(IOException.class, () -> mountPoint.createNewFile("test.txt", null));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
    }

    @Test
    public void createNewFileRWNoChange() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_3");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void createNewFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_4");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createNewFileRWInplace() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path tmpApkFile = apkFile.copyTo(tmpPath);
        assertNotNull(tmpApkFile);
        Path mountPoint = Paths.get("/tmp/am_mount_point_5");
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> false);
        int fsId = mountTracked(mountPoint.getUri(), tmpApkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), tmpApkFile, "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        unmountTracked(fsId);
        assertTrue(tmpApkFile.delete());
    }

    @Test
    public void deleteReadOnly() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_6");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        assertFalse(mountPoint.findFile("resources.arsc").delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        unmountTracked(fsId);
    }

    @Test
    public void deleteRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_7");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertFalse(mountPoint.hasFile("resources.arsc"));
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_8");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewFile("resources.arsc", null);
        assertEquals(0, arsc.length());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertEquals(0, mountPoint.findFile("resources.arsc").length());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateDirFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_9");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewDirectory("resources.arsc");
        assertEquals(0, arsc.length());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isDirectory());
        assertEquals(0, mountPoint.findFile("resources.arsc").length());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateDeleteFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_10");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewFile("resources.arsc", null);
        assertEquals(0, arsc.length());
        assertTrue(arsc.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertFalse(mountPoint.hasFile("resources.arsc"));
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createDeleteFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_11");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        assertTrue(testText.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test.txt"));
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createDeleteDirRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_12");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testDir = mountPoint.createNewDirectory("test_dir");
        assertTrue(testDir.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void list() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_list");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        Path[] children = mountPoint.listFiles();
        assertNotNull(children);
        assertTrue("mount root should have children", children.length > 0);
        boolean foundManifest = false;
        for (Path child : children) {
            if ("AndroidManifest.xml".equals(child.getName())) {
                foundManifest = true;
                break;
            }
        }
        assertTrue("listing should include AndroidManifest.xml", foundManifest);
        unmountTracked(fsId);
    }

    @Test
    public void mkdir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_13");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdir("/test_dir"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirMultipleDirs() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_14");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdir("/test_dir/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        unmountTracked(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void mkdirExistingFileOrDir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_15");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdir("/assets"));
        assertFalse(fs.mkdir("/classes.dex"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        unmountTracked(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void mkdirs() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_16");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdirs("/test_dir/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("test_dir_2").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("test_dir_2").isDirectory());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirsInsideExistingDir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_17");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdirs("/assets/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("test_dir_2").isDirectory());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("test_dir_2").isDirectory());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirsAllExistingDirsOrFiles() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_18");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdirs("/assets/dnsfilter.conf"));
        assertFalse(fs.mkdirs("/res/layout"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void renameToExistingFileSameDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_19");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileDifferentExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_20");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/assets/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileDifferentNonExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_21");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/test_dir/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("Manifest.xml").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("Manifest.xml").isFile());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileExistingFile() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_22");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        String manifestContents = mountPoint.findFile("AndroidManifest.xml").getContentAsString();
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/assets/dnsfilter.conf"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertEquals(manifestContents, mountPoint.findFile("assets").findFile("dnsfilter.conf").getContentAsString());
        unmountTracked(fsId);
    }

    @Test
    public void renameToNewFileExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_23");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(fs.renameTo("/test.txt", "/assets"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertFalse(mountPoint.findFile("assets").hasFile("test.txt"));
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertFalse(mountPoint.findFile("assets").hasFile("test.txt"));
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingDirectoryWithContentsSameDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_24");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/assets", "/abs"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("abs").isDirectory());
        assertTrue(mountPoint.findFile("abs").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("abs").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("abs").isDirectory());
        assertTrue(mountPoint.findFile("abs").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("abs").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingDirectoryWithContentsDifferentExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_25");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.renameTo("/assets", "/res"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void renameToExistingDirectoryWithContentsDifferentNonExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_26");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/assets", "/res/new"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        Path res = mountPoint.findFile("res");
        assertTrue(res.isDirectory());
        assertTrue(res.findFile("new").isDirectory());
        assertTrue(res.findFile("new").findFile("dnsfilter.conf").isFile());
        assertTrue(res.findFile("new").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = mountTracked(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        res = mountPoint.findFile("res");
        assertTrue(res.isDirectory());
        assertTrue(res.findFile("new").isDirectory());
        assertTrue(res.findFile("new").findFile("dnsfilter.conf").isFile());
        assertTrue(res.findFile("new").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void setLastModified() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_setmod");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        Path manifest = mountPoint.findFile("AndroidManifest.xml");
        long now = System.currentTimeMillis();
        assertTrue(manifest.setLastModified(now));
        assertEquals(now, manifest.lastModified());
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void newOutputStreamAppend() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_append");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip", options);
        mountPoint.createNewFile("append_test.txt", null);
        Path created = mountPoint.findFile("append_test.txt");
        try (OutputStream os = created.openOutputStream(true)) {
            os.write("hello".getBytes());
        }
        try (OutputStream os = created.openOutputStream(true)) {
            os.write(" world".getBytes());
        }
        try (InputStream is = created.openInputStream()) {
            byte[] data = new byte[256];
            int len = is.read(data);
            assertEquals("hello world", new String(data, 0, len));
        }
        unmountTracked(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void lastModified() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_lastmod");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        long lastMod = mountPoint.findFile("AndroidManifest.xml").lastModified();
        assertTrue("lastModified should be positive for a zip entry with a timestamp", lastMod > 0);
        unmountTracked(fsId);
    }

    @Test
    public void length() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_length");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        long len = mountPoint.findFile("AndroidManifest.xml").length();
        assertTrue("AndroidManifest.xml should have positive length", len > 0);
        long dirLen = mountPoint.findFile("res").length();
        assertTrue("directory length should be non-negative", dirLen >= 0);
        unmountTracked(fsId);
    }

    @Test
    public void checkAccess() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_access2");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        assertTrue("existing file should be accessible", mountPoint.findFile("AndroidManifest.xml").canRead());
        unmountTracked(fsId);
    }

    @Test
    public void getMode() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_mode");
        int fsId = mountTracked(mountPoint.getUri(), apkFile, "application/zip");
        int mode = mountPoint.findFile("AndroidManifest.xml").getMode();
        assertTrue("ZIP entry should report a regular-file mode", OsConstants.S_ISREG(mode));
        assertEquals("read-only ZIP entry should expose 0444 permissions", 0444, mode & 0777);
        unmountTracked(fsId);
    }

    private int mountTracked(Uri mountPoint, Path file, String type) throws IOException {
        int fsId = VirtualFileSystem.mount(mountPoint, file, type);
        mountedFileSystems.add(fsId);
        return fsId;
    }

    private int mountTracked(Uri mountPoint, Path file, String type,
                             VirtualFileSystem.MountOptions options) throws IOException {
        int fsId = VirtualFileSystem.mount(mountPoint, file, type, options);
        mountedFileSystems.add(fsId);
        return fsId;
    }

    private void unmountTracked(int fsId) throws IOException {
        VirtualFileSystem.unmount(fsId);
        mountedFileSystems.remove(Integer.valueOf(fsId));
    }

    private VirtualFileSystem.MountOptions getRWOptions(VirtualFileSystem.OnFileSystemUnmounted event) {
        return new VirtualFileSystem.MountOptions.Builder()
                .setReadWrite(true)
                .setOnFileSystemUnmounted(event)
                .build();
    }
}
