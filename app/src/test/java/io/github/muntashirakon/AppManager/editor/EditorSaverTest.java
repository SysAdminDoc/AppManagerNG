// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Saving used to truncate the file before the text was encoded and written, so any failure left an
 * empty or partial file. Every failure here must leave the original bytes, except when putting them
 * back fails too, which the save must then say.
 */
public class EditorSaverTest {
    private static final byte[] ORIGINAL = "original contents\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] UPDATED = "updated contents, a little longer\n".getBytes(StandardCharsets.UTF_8);

    @Test
    public void aSuccessfulSaveWritesAndReadsBackTheNewBytes() throws Exception {
        FakeTarget target = new FakeTarget(ORIGINAL);

        EditorSaver.save(() -> UPDATED, target, 1024);

        assertArrayEquals(UPDATED, target.bytes);
        assertTrue("the save must read the file back", target.reads >= 2);
    }

    @Test
    public void anEncodingFailureNeverTouchesTheFile() {
        FakeTarget target = new FakeTarget(ORIGINAL);

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class, () -> EditorSaver.save(() -> {
            throw new IOException("unclosed tag");
        }, target, 1024));

        assertTrue(e.originalIntact);
        assertEquals(0, target.opens);
        assertArrayEquals(ORIGINAL, target.bytes);
    }

    @Test
    public void anOpenFailureLeavesTheOriginal() {
        FakeTarget target = new FakeTarget(ORIGINAL);
        target.failOpens = 1;

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertTrue(e.originalIntact);
        assertArrayEquals(ORIGINAL, target.bytes);
    }

    @Test
    public void aShortWriteIsRolledBack() {
        FakeTarget target = new FakeTarget(ORIGINAL);
        target.failAfterBytes = 5;

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertTrue(e.originalIntact);
        assertArrayEquals(ORIGINAL, target.bytes);
    }

    @Test
    public void aFlushFailureIsRolledBack() {
        FakeTarget target = new FakeTarget(ORIGINAL);
        target.failFlushes = 1;

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertTrue(e.originalIntact);
        assertArrayEquals(ORIGINAL, target.bytes);
    }

    @Test
    public void aReplacementThatDoesNotReadBackIsRolledBack() {
        // The write seems to succeed, but the file holds something else afterwards.
        FakeTarget target = new FakeTarget(ORIGINAL);
        target.corruptNextWrite = true;

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertTrue(e.originalIntact);
        assertTrue(e.getMessage().contains("did not read back"));
        assertArrayEquals(ORIGINAL, target.bytes);
    }

    @Test
    public void aFailedRestoreIsReported() {
        FakeTarget target = new FakeTarget(ORIGINAL);
        target.failAfterBytes = 5;
        target.failOpens = 0;
        target.failRestore = true;

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertFalse(e.originalIntact);
    }

    @Test
    public void aFileLargerThanTheEditorCanKeepIsLeftAlone() {
        FakeTarget target = new FakeTarget(new byte[2048]);

        EditorSaver.SaveException e = assertThrows(EditorSaver.SaveException.class,
                () -> EditorSaver.save(() -> UPDATED, target, 1024));

        assertTrue(e.originalIntact);
        assertEquals(0, target.opens);
    }

    @Test
    public void aNewFileNeedsNoRestore() throws Exception {
        FakeTarget target = new FakeTarget(null);

        EditorSaver.save(() -> UPDATED, target, 1024);

        assertArrayEquals(UPDATED, target.bytes);
    }

    /** An in-memory file whose opens, writes and flushes can be made to fail. */
    private static final class FakeTarget implements EditorSaver.Target {
        byte[] bytes;
        int opens;
        int reads;
        int failOpens;
        int failFlushes;
        int failAfterBytes = -1;
        boolean corruptNextWrite;
        boolean failRestore;

        FakeTarget(@Nullable byte[] bytes) {
            this.bytes = bytes;
        }

        @Nullable
        @Override
        public byte[] read(int limit) {
            ++reads;
            return bytes == null ? null : Arrays.copyOf(bytes, Math.min(bytes.length, limit));
        }

        @NonNull
        @Override
        public OutputStream openTruncating() throws IOException {
            boolean restoring = opens > 0;
            ++opens;
            if (failOpens > 0) {
                --failOpens;
                throw new IOException("permission denied");
            }
            if (restoring && failRestore) {
                throw new IOException("the disk is gone");
            }
            bytes = new byte[0];
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int limit = restoring ? -1 : failAfterBytes;
            boolean corrupt = corruptNextWrite && !restoring;
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (limit >= 0 && buffer.size() >= limit) {
                        throw new IOException("no space left on device");
                    }
                    buffer.write(b);
                    bytes = buffer.toByteArray();
                }

                @Override
                public void write(@NonNull byte[] b, int off, int len) throws IOException {
                    for (int i = 0; i < len; ++i) {
                        write(b[off + i]);
                    }
                }

                @Override
                public void flush() throws IOException {
                    if (failFlushes > 0 && !restoring) {
                        --failFlushes;
                        throw new IOException("flush failed");
                    }
                }

                @Override
                public void close() {
                    if (corrupt) {
                        bytes = Arrays.copyOf(bytes, bytes.length / 2);
                    }
                }
            };
        }
    }
}
