// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LeftoverScannerTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private File makeAndroidRoot() throws IOException {
        File root = tmp.newFolder("ext-storage");
        new File(root, "Android/data").mkdirs();
        new File(root, "Android/obb").mkdirs();
        new File(root, "Android/media").mkdirs();
        return root;
    }

    private static Set<String> installed(String... pkgs) {
        return new HashSet<>(Arrays.asList(pkgs));
    }

    private static void mkPackageDir(File root, String subpath, String packageDir) {
        new File(root, "Android/" + subpath + "/" + packageDir).mkdirs();
    }

    @Test
    public void scanReturnsEmptyWhenNoOrphans() throws IOException {
        File root = makeAndroidRoot();
        mkPackageDir(root, "data", "com.example.installed");
        mkPackageDir(root, "obb", "com.example.installed");

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(
                root, installed("com.example.installed"));
        assertTrue(result.isEmpty());
    }

    @Test
    public void scanFlagsOrphanAcrossAllThreeRoots() throws IOException {
        File root = makeAndroidRoot();
        mkPackageDir(root, "data", "com.example.gone");
        mkPackageDir(root, "obb", "com.example.gone");
        mkPackageDir(root, "media", "com.example.gone");
        mkPackageDir(root, "data", "com.example.still.installed");

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(
                root, installed("com.example.still.installed"));

        assertEquals(3, result.size());
        Set<Integer> kinds = new HashSet<>();
        for (LeftoverScanner.Leftover l : result) {
            assertEquals("com.example.gone", l.packageName);
            kinds.add(l.kind);
        }
        assertTrue(kinds.contains(LeftoverScanner.KIND_DATA));
        assertTrue(kinds.contains(LeftoverScanner.KIND_OBB));
        assertTrue(kinds.contains(LeftoverScanner.KIND_MEDIA));
    }

    @Test
    public void scanIgnoresHiddenAndUnnamedDirectories() throws IOException {
        File root = makeAndroidRoot();
        mkPackageDir(root, "data", ".nomedia");          // hidden marker dir
        mkPackageDir(root, "data", "lost+found");        // OEM oddball
        mkPackageDir(root, "data", "com.example.gone");  // real orphan

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(root, installed());
        assertEquals(1, result.size());
        assertEquals("com.example.gone", result.get(0).packageName);
    }

    @Test
    public void scanIsRobustToMissingSubdirectories() throws IOException {
        File root = tmp.newFolder("ext-storage");
        new File(root, "Android/data").mkdirs();   // only data, no obb/media
        mkPackageDir(root, "data", "com.example.gone");

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(root, installed());
        assertEquals(1, result.size());
    }

    @Test
    public void scanReturnsEmptyWhenAndroidDirAbsent() throws IOException {
        File root = tmp.newFolder("ext-storage");
        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(root, installed());
        assertTrue(result.isEmpty());
    }

    @Test
    public void scanReturnsEmptyWhenRootDoesNotExist() {
        File root = new File(tmp.getRoot(), "does-not-exist");
        List<LeftoverScanner.Leftover> result = LeftoverScanner.scan(root, installed());
        assertTrue(result.isEmpty());
    }

    @Test
    public void selectorSkipsInvalidPackageLikeNames() {
        // Names that fail the looksLikePackageName predicate must be filtered
        // by the selector regardless of installed-package set membership.
        List<File> dirs = Arrays.asList(
                new File("/x/com.valid.pkg"),
                new File("/x/.hidden"),
                new File("/x/lost+found"),
                new File("/x/nopackage"),
                new File("/x/123.numeric.lead"));
        List<LeftoverScanner.Leftover> result = LeftoverScanner.selectOrphans(
                dirs, Collections.<String>emptySet(), LeftoverScanner.KIND_DATA);
        assertEquals(1, result.size());
        assertEquals("com.valid.pkg", result.get(0).packageName);
    }

    @Test
    public void packageLikePredicateAcceptsRealPackages() {
        assertTrue(LeftoverScanner.looksLikePackageName("com.android.chrome"));
        assertTrue(LeftoverScanner.looksLikePackageName("io.github.sysadmindoc.AppManagerNG"));
        assertTrue(LeftoverScanner.looksLikePackageName("a.b"));
        assertTrue(LeftoverScanner.looksLikePackageName("_underscore.start"));
    }

    @Test
    public void packageLikePredicateRejectsInvalidNames() {
        assertFalse(LeftoverScanner.looksLikePackageName(""));
        assertFalse(LeftoverScanner.looksLikePackageName("nopackage"));
        assertFalse(LeftoverScanner.looksLikePackageName(".leading.dot"));
        assertFalse(LeftoverScanner.looksLikePackageName("trailing.dot."));
        assertFalse(LeftoverScanner.looksLikePackageName("com..double.dot"));
        assertFalse(LeftoverScanner.looksLikePackageName("9starts.with.digit"));
        assertFalse(LeftoverScanner.looksLikePackageName("path/with/slash"));
        assertFalse(LeftoverScanner.looksLikePackageName("space in.name"));
        assertFalse(LeftoverScanner.looksLikePackageName("dash-in.name"));
    }

    @Test
    public void sizeOnDiskWalksFiles() throws IOException {
        File dir = tmp.newFolder("orphan");
        File a = new File(dir, "a.bin");
        File b = new File(dir, "sub/b.bin");
        b.getParentFile().mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(a)) {
            out.write(new byte[123]);
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(b)) {
            out.write(new byte[456]);
        }
        assertEquals(579L, LeftoverScanner.sizeOnDisk(dir));
    }

    @Test
    public void sizeOnDiskReturnsZeroForMissingPath() {
        assertEquals(0L, LeftoverScanner.sizeOnDisk(new File(tmp.getRoot(), "missing")));
    }

    @Test
    public void scanInternalDataStubsFlagsOrphanPackageDirs() throws IOException {
        File dataData = tmp.newFolder("data-data");
        new File(dataData, "com.example.gone").mkdirs();
        new File(dataData, "com.example.still.installed").mkdirs();
        new File(dataData, "com.example.also.gone").mkdirs();

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scanInternalDataStubs(
                dataData, installed("com.example.still.installed"));

        assertEquals(2, result.size());
        Set<String> orphanNames = new HashSet<>();
        for (LeftoverScanner.Leftover l : result) {
            orphanNames.add(l.packageName);
            assertEquals(LeftoverScanner.KIND_INTERNAL_STUB, l.kind);
            assertEquals("internal-stub", l.kindLabel());
        }
        assertTrue(orphanNames.contains("com.example.gone"));
        assertTrue(orphanNames.contains("com.example.also.gone"));
    }

    @Test
    public void scanInternalDataStubsSkipsHiddenAndNonPackageDirs() throws IOException {
        // /data/data on a real device contains things like 'lost+found' and
        // ".cache" alongside real package dirs; the selector must filter
        // these before emitting orphans, just like the external-storage path.
        File dataData = tmp.newFolder("data-data");
        new File(dataData, "lost+found").mkdirs();
        new File(dataData, ".something").mkdirs();
        new File(dataData, "androidshmem").mkdirs();      // no dot, not a package name
        new File(dataData, "com.real.orphan").mkdirs();

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scanInternalDataStubs(
                dataData, installed());
        assertEquals(1, result.size());
        assertEquals("com.real.orphan", result.get(0).packageName);
        assertEquals(LeftoverScanner.KIND_INTERNAL_STUB, result.get(0).kind);
    }

    @Test
    public void scanInternalDataStubsReturnsEmptyOnUnreadableRoot() {
        // Unprivileged caller: typical case where the literal /data/data
        // dir is not enumerable. The scanner must degrade to empty rather
        // than throw.
        File missing = new File(tmp.getRoot(), "does-not-exist-data-data");
        List<LeftoverScanner.Leftover> result = LeftoverScanner.scanInternalDataStubs(
                missing, installed("com.example.installed"));
        assertTrue(result.isEmpty());
    }

    @Test
    public void scanInternalDataStubsReturnsEmptyWhenAllInstalled() throws IOException {
        File dataData = tmp.newFolder("data-data");
        new File(dataData, "com.a.b").mkdirs();
        new File(dataData, "com.c.d").mkdirs();

        List<LeftoverScanner.Leftover> result = LeftoverScanner.scanInternalDataStubs(
                dataData, installed("com.a.b", "com.c.d"));
        assertTrue(result.isEmpty());
    }
}
