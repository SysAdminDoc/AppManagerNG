// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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
