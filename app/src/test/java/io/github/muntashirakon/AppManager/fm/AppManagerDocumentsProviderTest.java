// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class AppManagerDocumentsProviderTest {
    private java.nio.file.Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-documents-provider");
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir == null) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(tempDir)) {
            paths.sorted((first, second) -> second.compareTo(first))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    public void buildAndResolveDocumentIdRoundTripsChildPath() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File child = new File(root, "nested/profile.am.json");
        assertEquals(true, child.getParentFile().mkdirs());
        assertEquals(true, child.createNewFile());

        String documentId = AppManagerDocumentsProvider.buildDocumentIdForFile(
                AppManagerDocumentsProvider.ROOT_ID_PROFILES, root, child);
        File resolved = AppManagerDocumentsProvider.resolveDocumentFile(documentId, roots(root));

        assertEquals("profiles:nested/profile.am.json", documentId);
        assertEquals(child.getCanonicalFile(), resolved.getCanonicalFile());
    }

    @Test
    public void resolveDocumentIdRejectsTraversal() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File sibling = tempDir.resolve("secret.txt").toFile();
        assertEquals(true, root.mkdirs());
        assertEquals(true, sibling.createNewFile());

        assertThrows(FileNotFoundException.class, () ->
                AppManagerDocumentsProvider.resolveDocumentFile("profiles:../secret.txt", roots(root)));
    }

    @Test
    public void resolveDocumentIdRejectsUnknownRoot() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        assertEquals(true, root.mkdirs());

        assertThrows(FileNotFoundException.class, () ->
                AppManagerDocumentsProvider.resolveDocumentFile("unknown:file.txt", roots(root)));
    }

    @Test
    public void listDocumentChildrenSortsDirectoriesBeforeFiles() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File zDirectory = new File(root, "z-dir");
        File aDirectory = new File(root, "a-dir");
        File bFile = new File(root, "b.json");
        File aFile = new File(root, "a.json");
        assertEquals(true, root.mkdirs());
        assertEquals(true, zDirectory.mkdirs());
        assertEquals(true, aDirectory.mkdirs());
        assertEquals(true, bFile.createNewFile());
        assertEquals(true, aFile.createNewFile());

        File[] children = AppManagerDocumentsProvider.listDocumentChildren(root);

        assertArrayEquals(new String[]{"a-dir", "z-dir", "a.json", "b.json"}, names(children));
    }

    @Test
    public void descendantsOfADirectoryCountAsChildren() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File grandchild = new File(root, "a/b/c.json");
        assertTrue(grandchild.getParentFile().mkdirs());
        assertTrue(grandchild.createNewFile());

        assertTrue(AppManagerDocumentsProvider.isDescendant("profiles", "profiles:a", roots(root)));
        assertTrue(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/b/c.json", roots(root)));
        // Not its own child, and a file has none
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a/b/c.json", "profiles:a/b/c.json", roots(root)));
    }

    @Test
    public void aSiblingThatSharesTheNamePrefixIsNotAChild() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        assertTrue(new File(root, "a").mkdirs());
        File sibling = new File(root, "ab/c.json");
        assertTrue(sibling.getParentFile().mkdirs());
        assertTrue(sibling.createNewFile());

        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:ab/c.json", roots(root)));
    }

    @Test
    public void anIdThatClimbsOrHidesASeparatorIsNotAChild() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File inside = new File(root, "a/x.json");
        File elsewhere = new File(root, "b/x.json");
        assertTrue(inside.getParentFile().mkdirs());
        assertTrue(inside.createNewFile());
        assertTrue(elsewhere.getParentFile().mkdirs());
        assertTrue(elsewhere.createNewFile());

        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/../b/x.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/./x.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a//x.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a%2F..%2Fb%2Fx.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a\\..\\b\\x.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:/a/x.json", roots(root)));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/x.json\0", roots(root)));
    }

    @Test
    public void aSymlinkIsJudgedByWhereItLeads() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File outside = tempDir.resolve("outside/secret.txt").toFile();
        File target = new File(root, "a/x.json");
        assertTrue(outside.getParentFile().mkdirs());
        assertTrue(outside.createNewFile());
        assertTrue(target.getParentFile().mkdirs());
        assertTrue(target.createNewFile());
        try {
            Files.createSymbolicLink(new File(root, "escape").toPath(), outside.getParentFile().toPath());
            Files.createSymbolicLink(new File(root, "alias").toPath(), target.getParentFile().toPath());
        } catch (UnsupportedOperationException | IOException e) {
            assumeNoException("This file system can't make symbolic links", e);
        }

        // Leads out of the root
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles", "profiles:escape/secret.txt", roots(root)));
        // Leads inside, but only the real path is the document's ID
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles", "profiles:alias/x.json", roots(root)));
        assertTrue(AppManagerDocumentsProvider.isDescendant("profiles", "profiles:a/x.json", roots(root)));
    }

    @Test
    public void aDeletedDocumentIsNotAChild() throws Exception {
        File root = tempDir.resolve("profiles").toFile();
        File gone = new File(root, "a/x.json");
        assertTrue(gone.getParentFile().mkdirs());
        assertTrue(gone.createNewFile());
        assertTrue(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/x.json", roots(root)));

        assertTrue(gone.delete());

        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles:a", "profiles:a/x.json", roots(root)));
    }

    @Test
    public void aDocumentIsNeverTheChildOfAnotherRoot() throws Exception {
        // Profiles inside the backup folder: one file, two IDs, and each ID belongs to its own root
        File backups = tempDir.resolve("AppManager").toFile();
        File profiles = new File(backups, "profiles");
        File profile = new File(profiles, "p.am.json");
        assertTrue(profiles.mkdirs());
        assertTrue(profile.createNewFile());
        Map<String, File> roots = roots(profiles);
        roots.put(AppManagerDocumentsProvider.ROOT_ID_BACKUPS, backups);

        assertTrue(AppManagerDocumentsProvider.isDescendant("backups", "backups:profiles/p.am.json", roots));
        assertTrue(AppManagerDocumentsProvider.isDescendant("profiles", "profiles:p.am.json", roots));
        assertFalse(AppManagerDocumentsProvider.isDescendant("backups", "profiles:p.am.json", roots));
        assertFalse(AppManagerDocumentsProvider.isDescendant("profiles", "backups:profiles/p.am.json", roots));
        assertFalse(AppManagerDocumentsProvider.isDescendant("unknown", "profiles:p.am.json", roots));
    }

    private static Map<String, File> roots(File profilesRoot) {
        Map<String, File> roots = new HashMap<>();
        roots.put(AppManagerDocumentsProvider.ROOT_ID_PROFILES, profilesRoot);
        return roots;
    }

    private static String[] names(File[] files) {
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            names[i] = files[i].getName();
        }
        return names;
    }
}
