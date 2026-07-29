// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.muntashirakon.AppManager.debloat.DebloatDefinitionStore.GenerationFile;

public class DebloatDefinitionStoreTest {
    private static final String DEBLOAT = "debloat.json";
    private static final String SUGGESTIONS = "suggestions.json";

    private File mRoot;
    private DebloatDefinitionStore mStore;

    @Before
    public void setUp() throws IOException {
        mRoot = Files.createTempDirectory("debloat-store").toFile();
        mStore = new DebloatDefinitionStore(mRoot);
    }

    @After
    public void tearDown() {
        deleteRecursively(mRoot);
    }

    @Test
    public void nothingIsPublishedInitially() {
        assertEquals(0, mStore.getActiveGeneration());
        assertNull(mStore.read(DEBLOAT));
        assertNull(mStore.read(SUGGESTIONS));
    }

    @Test
    public void publishMakesTheWholeGenerationVisibleAtOnce() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        assertEquals(1, mStore.getActiveGeneration());
        assertEquals("debloat-1", mStore.read(DEBLOAT));
        assertEquals("suggestions-1", mStore.read(SUGGESTIONS));
    }

    @Test
    public void aNewGenerationReplacesBothFilesTogether() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        publish(2, "debloat-2", "suggestions-2");
        assertEquals(2, mStore.getActiveGeneration());
        assertEquals("debloat-2", mStore.read(DEBLOAT));
        assertEquals("suggestions-2", mStore.read(SUGGESTIONS));
    }

    @Test
    public void theLastKnownGoodGenerationIsRetainedAndOlderOnesArePruned() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        publish(2, "debloat-2", "suggestions-2");
        publish(3, "debloat-3", "suggestions-3");
        assertTrue(new File(mRoot, "g3").isDirectory());
        assertTrue(new File(mRoot, "g2").isDirectory());
        assertFalse(new File(mRoot, "g1").exists());
    }

    @Test
    public void anInterruptedPublishLeavesThePreviousGenerationActive() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        // Simulate a crash after the new generation was written but before the pointer was swapped.
        File pending = new File(mRoot, "g2");
        assertTrue(pending.mkdirs());
        write(new File(pending, DEBLOAT), "debloat-2");
        assertEquals(1, mStore.getActiveGeneration());
        assertEquals("debloat-1", mStore.read(DEBLOAT));
        assertEquals("suggestions-1", mStore.read(SUGGESTIONS));
        // The next publish rebuilds the directory from scratch rather than reusing the partial one.
        publish(2, "debloat-2", "suggestions-2");
        assertEquals("debloat-2", mStore.read(DEBLOAT));
        assertEquals("suggestions-2", mStore.read(SUGGESTIONS));
    }

    @Test
    public void aDanglingPointerReadsAsNoPublishedGeneration() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        deleteRecursively(new File(mRoot, "g1"));
        assertEquals(0, mStore.getActiveGeneration());
        assertNull(mStore.read(DEBLOAT));
    }

    @Test
    public void aGarbagePointerReadsAsNoPublishedGeneration() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        write(new File(mRoot, DebloatDefinitionStore.POINTER_FILE), "../../etc");
        assertEquals(0, mStore.getActiveGeneration());
        assertNull(mStore.read(DEBLOAT));
    }

    @Test
    public void publishRejectsAnInvalidGeneration() {
        assertThrows(IOException.class, () -> publish(0, "debloat", "suggestions"));
        assertThrows(IOException.class, () -> mStore.publish(1, DebloatDefinitionStore.generationFiles()));
    }

    @Test
    public void clearRemovesEverything() throws IOException {
        publish(1, "debloat-1", "suggestions-1");
        mStore.clear();
        assertEquals(0, mStore.getActiveGeneration());
        assertNull(mStore.read(DEBLOAT));
    }

    private void publish(long generation, String debloat, String suggestions) throws IOException {
        mStore.publish(generation, DebloatDefinitionStore.generationFiles(
                new GenerationFile(DEBLOAT, debloat.getBytes(StandardCharsets.UTF_8)),
                new GenerationFile(SUGGESTIONS, suggestions.getBytes(StandardCharsets.UTF_8))));
    }

    private static void write(@NonNull File file, @NonNull String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
