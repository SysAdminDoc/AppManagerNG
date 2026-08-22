// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.backup.BackupException;

public class SwiftBackupManifestTest {
    @Test
    public void acceptsValidManifest() throws BackupException {
        SwiftBackupManifest.validateComment(
                "{\"packageName\":\"com.example.app\",\"versionCode\":42}",
                "com.example.app");
    }

    @Test
    public void acceptsLegacyEmptyComment() throws BackupException {
        SwiftBackupManifest.validateComment("", "com.example.app");
    }

    @Test
    public void rejectsMalformedManifest() {
        assertFailure("{\"packageName\":", "Malformed JSON");
    }

    @Test
    public void rejectsPackageMismatch() {
        assertFailure("{\"packageName\":\"com.other.app\"}", "Package name mismatch");
    }

    @Test
    public void findsNestedPackageManifest() throws BackupException {
        SwiftBackupManifest.validateComment(
                "{\"app\":{\"application_id\":\"com.example.app\"}}",
                "com.example.app");
    }

    @Test
    public void validatesZipCommentFromArchive() throws IOException, BackupException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            zip.setComment("{\"packageName\":\"com.example.app\"}");
        }
        String comment = SwiftBackupManifest.readZipComment(
                new ByteArrayInputStream(outputStream.toByteArray()));
        SwiftBackupManifest.validateComment(comment, "com.example.app");
    }

    private static void assertFailure(String comment, String expectedMessage) {
        try {
            SwiftBackupManifest.validateComment(comment, "com.example.app");
            fail("Expected Swift Backup manifest validation to fail");
        } catch (BackupException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedMessage));
        }
    }
}
