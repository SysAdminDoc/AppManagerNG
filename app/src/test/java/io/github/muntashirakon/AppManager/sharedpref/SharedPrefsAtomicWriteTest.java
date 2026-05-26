// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class SharedPrefsAtomicWriteTest {
    private java.nio.file.Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-shared-prefs");
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
    public void writeSharedPrefsAtomicallyCommitsCompleteXml() throws Exception {
        java.nio.file.Path prefsFile = tempDir.resolve("prefs.xml");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("key", "new-value");

        SharedPrefsViewModel.writeSharedPrefsAtomically(Paths.get(prefsFile.toFile()), prefs);

        Map<String, Object> loaded = SharedPrefsUtil.readSharedPref(new ByteArrayInputStream(
                Files.readAllBytes(prefsFile)));
        assertEquals("new-value", loaded.get("key"));
        assertFalse(Files.exists(tempDir.resolve("prefs.xml.new")));
    }

    @Test
    public void writeSharedPrefsAtomicallyKeepsOriginalFileWhenWriteFails() throws Exception {
        java.nio.file.Path prefsFile = tempDir.resolve("prefs.xml");
        String originalXml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
                + "<string name=\"key\">old-value</string></map>";
        Files.write(prefsFile, originalXml.getBytes(StandardCharsets.UTF_8));

        IOException thrown = assertThrows(IOException.class, () ->
                SharedPrefsViewModel.writeSharedPrefsAtomically(Paths.get(prefsFile.toFile()), outputStream -> {
                    outputStream.write("<map><string name=\"key\">partial".getBytes(StandardCharsets.UTF_8));
                    throw new IOException("simulated write failure");
                }));

        assertEquals("simulated write failure", thrown.getMessage());
        assertEquals(originalXml, new String(Files.readAllBytes(prefsFile), StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve("prefs.xml.new")));
    }
}
