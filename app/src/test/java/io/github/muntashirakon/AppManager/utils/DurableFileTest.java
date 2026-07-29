// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

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

public class DurableFileTest {
    private static final long MAX_BYTES = 1024L * 1024L;

    private File mDir;
    private File mBase;
    private DurableFile mFile;

    @Before
    public void setUp() throws IOException {
        mDir = Files.createTempDirectory("durable-file").toFile();
        mBase = new File(mDir, "store.json");
        mFile = new DurableFile(mBase);
    }

    @After
    public void tearDown() {
        deleteRecursively(mDir);
    }

    @Test
    public void readsNothingBeforeTheFirstWrite() {
        assertNull(mFile.read(MAX_BYTES));
        assertFalse(mFile.exists());
    }

    @Test
    public void writeThenReadRoundTrips() throws IOException {
        write("first");
        assertEquals("first", mFile.read(MAX_BYTES));
        assertTrue(mFile.exists());
        write("second");
        assertEquals("second", mFile.read(MAX_BYTES));
    }

    @Test
    public void aSuccessfulWriteLeavesNoArtefactsBehind() throws IOException {
        write("first");
        write("second");
        assertFalse(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).exists());
        assertFalse(new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX).exists());
    }

    @Test
    public void anImplausiblyLargeFileIsTreatedAsCorrupt() throws IOException {
        write("first");
        assertNull(mFile.read(2));
    }

    @Test
    public void aWriteThatCannotCreateItsNewFileFailsWithoutTouchingTheOldOne() throws IOException {
        write("first");
        // A non-empty directory in the way of the ".new" file makes the write fail at the first
        // step: it can neither be cleaned up by recovery nor opened as a stream.
        File pending = new File(mDir, "store.json" + DurableFile.NEW_SUFFIX);
        assertTrue(new File(pending, "occupied").mkdirs());
        assertThrows(IOException.class, () -> write("second"));
        assertEquals("first", mFile.read(MAX_BYTES));
    }

    @Test
    public void aWriteThatCannotMoveTheBaseAsideLeavesItIntact() throws IOException {
        write("first");
        // A non-empty directory at the backup path blocks the move-aside step.
        File backup = new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX);
        assertTrue(new File(backup, "occupied").mkdirs());
        assertThrows(IOException.class, () -> write("second"));
        assertEquals("first", mFile.read(MAX_BYTES));
        assertFalse("a failed write must not leave its partial file behind",
                new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).isFile());
    }

    @Test
    public void anInterruptionBeforeTheRenameDiscardsThePartialWrite() throws IOException {
        write("first");
        Files.write(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).toPath(),
                "half".getBytes(StandardCharsets.UTF_8));
        assertEquals("first", mFile.read(MAX_BYTES));
        assertFalse(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).exists());
    }

    @Test
    public void anInterruptionBetweenTheTwoRenamesRestoresTheOldContent() throws IOException {
        // The old file was moved aside and the new one was never renamed in.
        Files.write(new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX).toPath(),
                "first".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).toPath(),
                "second".getBytes(StandardCharsets.UTF_8));
        assertEquals("first", mFile.read(MAX_BYTES));
        assertFalse(new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX).exists());
        assertFalse(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).exists());
    }

    @Test
    public void anInterruptionAfterTheRenameKeepsTheNewContent() throws IOException {
        write("second");
        // Only the backup cleanup was interrupted.
        Files.write(new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX).toPath(),
                "first".getBytes(StandardCharsets.UTF_8));
        assertEquals("second", mFile.read(MAX_BYTES));
        assertFalse(new File(mDir, "store.json" + DurableFile.BACKUP_SUFFIX).exists());
    }

    @Test
    public void deleteRemovesEveryArtefact() throws IOException {
        write("first");
        Files.write(new File(mDir, "store.json" + DurableFile.NEW_SUFFIX).toPath(),
                "half".getBytes(StandardCharsets.UTF_8));
        mFile.delete();
        assertNull(mFile.read(MAX_BYTES));
        assertEquals(0, mDir.list().length);
    }

    @Test
    public void aMissingParentDirectoryIsCreated() throws IOException {
        DurableFile nested = new DurableFile(new File(new File(mDir, "nested"), "store.json"));
        nested.write("value".getBytes(StandardCharsets.UTF_8));
        assertEquals("value", nested.read(MAX_BYTES));
    }

    private void write(@NonNull String content) throws IOException {
        mFile.write(content.getBytes(StandardCharsets.UTF_8));
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
