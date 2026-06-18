// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;

public class ConvertUtilsTest {
    @Test
    public void getRelativeBackupEntryNameStripsExpectedPackagePrefix() throws IOException {
        assertEquals("files/settings.json",
                ConvertUtils.getRelativeBackupEntryName("com.example/files/settings.json", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameNormalizesBackslashSeparators() throws IOException {
        assertEquals("files/settings.json",
                ConvertUtils.getRelativeBackupEntryName("com.example\\files\\settings.json", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameReturnsEmptyPackageRoot() throws IOException {
        assertEquals("", ConvertUtils.getRelativeBackupEntryName("com.example", "com.example/"));
        assertEquals("", ConvertUtils.getRelativeBackupEntryName("com.example/", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameRejectsEntriesOutsidePackagePrefix() {
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("other.example/files/settings.json", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameRejectsTraversalSegments() {
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("com.example/../escape.txt", "com.example/"));
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("com.example/files/../../escape.txt", "com.example/"));
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("com.example/./settings.json", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameRejectsWindowsDriveSegments() {
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("com.example/C:/escape.txt", "com.example/"));
    }

    @Test
    public void getRelativeBackupEntryNameRejectsNullBytes() {
        assertThrows(IOException.class,
                () -> ConvertUtils.getRelativeBackupEntryName("com.example/files/bad\u0000name", "com.example/"));
    }
}
