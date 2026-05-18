// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmSearchUtilsTest {
    private Path tempDir;
    private io.github.muntashirakon.io.Path rootPath;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("am-fm-search");
        Files.createDirectories(tempDir.resolve("data/shared_prefs"));
        Files.createDirectories(tempDir.resolve(".secret"));
        Files.write(tempDir.resolve("data/shared_prefs/settings.xml"), "prefs".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("data/cache.bin"), "cache".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve(".secret/hidden-token.txt"), "hidden".getBytes(StandardCharsets.UTF_8));
        rootPath = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir == null) {
            return;
        }
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
    }

    @Test
    public void searchRecursive_matchesNestedFileNameAndStoresLocation() {
        List<FmItem> results = FmSearchUtils.searchRecursive(rootPath, "settings", false);

        assertEquals(1, results.size());
        assertEquals("settings.xml", results.get(0).getName());
        assertEquals("data/shared_prefs", results.get(0).getSearchLocation());
    }

    @Test
    public void searchRecursive_matchesPathQueryWhenQueryContainsSeparator() {
        List<FmItem> results = FmSearchUtils.searchRecursive(rootPath, "shared_prefs/settings", false);

        assertEquals(1, results.size());
        assertEquals("settings.xml", results.get(0).getName());
    }

    @Test
    public void searchRecursive_respectsHiddenFileOption() {
        List<FmItem> hiddenDisabled = FmSearchUtils.searchRecursive(rootPath, "hidden", false);
        List<FmItem> hiddenEnabled = FmSearchUtils.searchRecursive(rootPath, "hidden", true);

        assertTrue(hiddenDisabled.isEmpty());
        assertFalse(hiddenEnabled.isEmpty());
    }
}
