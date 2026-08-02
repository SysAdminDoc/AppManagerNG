// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

/**
 * Pins the caller-gating contract documented at the top of {@link FileSystemService}.
 * <p>
 * The service has no path root by design — the file manager browses from {@code /}, backup
 * reaches into {@code /data}, debloating writes under {@code /system} — so {@code ../}, absolute
 * paths and symlinks that leave any particular directory are accepted inputs, and the boundary
 * is who holds the binder. What is refused is a path whose meaning would change on its way to
 * the syscall.
 */
public class FileSystemServicePathTest {
    @Test
    public void traversalAndAbsolutePathsArePartOfTheContract() {
        // If one of these ever starts being refused, the contract changed and the callers that
        // depend on whole-filesystem access (file manager, backup, debloat) break with it.
        assertTrue(FileSystemService.isUsablePath("/data/local/tmp/../../system/build.prop"));
        assertTrue(FileSystemService.isUsablePath("../../../etc/hosts"));
        assertTrue(FileSystemService.isUsablePath("/"));
        assertTrue(FileSystemService.isUsablePath("/system/bin/sh"));
        // A symlink is just a path here; it is resolved by the kernel, not filtered by us.
        assertTrue(FileSystemService.isUsablePath("/data/data/pkg/files/link-to-elsewhere"));
        assertTrue(FileSystemService.isUsablePath("relative/path"));
    }

    @Test
    public void embeddedNulIsRefused() {
        // The native layer stops at the NUL, so this would reach the syscall as
        // "/data/local/tmp" -- a different path from the one that was reviewed.
        assertFalse(FileSystemService.isUsablePath("/data/local/tmp\0/../../system"));
        assertFalse(FileSystemService.isUsablePath("\0"));
        assertFalse(FileSystemService.isUsablePath("/system/bin/sh\0"));
    }

    @Test
    public void structurallyEmptyPathsAreRefused() {
        assertFalse(FileSystemService.isUsablePath(null));
        assertFalse(FileSystemService.isUsablePath(""));
    }

    @Test
    public void checkPathReportsARefusalAsACheckedFailure() {
        assertRefused(null);
        assertRefused("");
        assertRefused("/data/local/tmp\0/../../system");
    }

    @Test
    public void checkPathReturnsAnAcceptedPathUnchanged() throws IOException {
        // Unchanged, specifically: the service must act on exactly the path it was given, so
        // no normalization happens here either.
        assertEquals("/data/local/tmp/../x", FileSystemService.checkPath("/data/local/tmp/../x"));
        assertEquals("/", FileSystemService.checkPath("/"));
    }

    private static void assertRefused(String path) {
        try {
            FileSystemService.checkPath(path);
            fail("Expected IOException for " + (path == null ? "null" : "\"" + path + "\""));
        } catch (IOException e) {
            assertEquals("Unusable path", e.getMessage());
        }
    }
}
