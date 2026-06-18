// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;

public class ApkFileTest {
    @Test
    public void recordSplitNameAcceptsDistinctNames() throws ApkFile.ApkFileException {
        Set<String> splitNames = new HashSet<>();

        ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "split_config.en.apk");
        ApkFile.recordSplitName(manifestAttrs("config.xxhdpi"), splitNames, "split_config.xxhdpi.apk");
    }

    @Test
    public void recordSplitNameRejectsDuplicateNames() throws ApkFile.ApkFileException {
        Set<String> splitNames = new HashSet<>();
        ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "split_config.en.apk");

        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "duplicate.apk"));
    }

    @Test
    public void recordSplitNameRejectsMissingNames() {
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.recordSplitName(manifestAttrs(null), new HashSet<>(), "missing.apk"));
    }

    @Test
    public void assertReasonableBundleEntryCountRejectsOverLimit() {
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.assertReasonableBundleEntryCount(ApkFile.MAX_BUNDLE_ZIP_ENTRIES + 1));
    }

    @Test
    public void readBoundedUtf8EntryReadsSmallMetadata() throws IOException {
        ZipEntry zipEntry = new ZipEntry("info.json");
        byte[] bytes = "{\"info_version\":1}".getBytes(StandardCharsets.UTF_8);

        String contents = ApkFile.readBoundedUtf8Entry(new ByteArrayInputStream(bytes), zipEntry, 64, "info.json");

        assertEquals("{\"info_version\":1}", contents);
    }

    @Test
    public void readBoundedUtf8EntryRejectsDeclaredOversize() {
        ZipEntry zipEntry = new ZipEntry("info.json");
        zipEntry.setSize(65);

        assertThrows(IOException.class,
                () -> ApkFile.readBoundedUtf8Entry(new ByteArrayInputStream(new byte[0]), zipEntry, 64, "info.json"));
    }

    @Test
    public void copyBoundedEntryRejectsInflatedOversize() {
        ZipEntry zipEntry = new ZipEntry("payload.idsig");
        byte[] bytes = "123456789".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> ApkFile.copyBoundedEntry(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(),
                        zipEntry, 8, "payload.idsig"));
    }

    private static HashMap<String, String> manifestAttrs(String splitName) {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        manifestAttrs.put("split", splitName);
        return manifestAttrs;
    }
}
