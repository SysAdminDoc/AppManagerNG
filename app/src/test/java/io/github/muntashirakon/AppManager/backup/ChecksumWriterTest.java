// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

@RunWith(RobolectricTestRunner.class)
public class ChecksumWriterTest {
    @Test
    public void addSurfacesUnderlyingWriteFailure() {
        Writer failing = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void close() {
            }
        };
        BackupItems.Checksum checksum = new BackupItems.Checksum(failing);
        // Before the fix this returned normally while silently truncating checksums.txt.
        assertThrows(IOException.class, () -> checksum.add("base.apk", "deadbeef"));
    }

    @Test
    public void addSucceedsAndWritesEntryWithWorkingWriter() throws IOException {
        StringWriter sw = new StringWriter();
        BackupItems.Checksum checksum = new BackupItems.Checksum(sw);
        checksum.add("base.apk", "deadbeef");
        checksum.close();
        assertTrue(sw.toString().contains("deadbeef\tbase.apk"));
    }
}
