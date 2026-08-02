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
    public void manifestIdentityAcceptsMatchingPackageAndVersion() throws ApkFile.ApkFileException {
        ApkFile.validateManifestIdentity(manifestAttrs("com.example.app", "42", null),
                manifestAttrs("com.example.app", "42", "config.en"), "split_config.en.apk");
    }

    @Test
    public void manifestIdentityRejectsForeignPackage() {
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.validateManifestIdentity(manifestAttrs("com.example.app", "42", null),
                        manifestAttrs("com.other.app", "42", "config.en"), "foreign.apk"));
    }

    @Test
    public void manifestIdentityRejectsMixedVersion() {
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.validateManifestIdentity(manifestAttrs("com.example.app", "42", null),
                        manifestAttrs("com.example.app", "43", "config.en"), "newer.apk"));
    }

    @Test
    public void manifestIdentityRejectsMissingVersion() {
        HashMap<String, String> missingVersion = new HashMap<>();
        missingVersion.put("package", "com.example.app");
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.validateManifestIdentity(manifestAttrs("com.example.app", "42", null),
                        missingVersion, "missing-version.apk"));
    }

    @Test
    public void containerTypePreservesApkmProvenance() {
        assertEquals(ApkFile.CONTAINER_APKM_ENCRYPTED,
                ApkFile.getContainerTypeForExtension("apkm", false));
        assertEquals(ApkFile.CONTAINER_APKM_DRM_FREE,
                ApkFile.getContainerTypeForExtension("apks", true));
        assertEquals(ApkFile.CONTAINER_APKS,
                ApkFile.getContainerTypeForExtension("apks", false));
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

    private static HashMap<String, String> manifestAttrs(String packageName, String versionCode,
                                                         String splitName) {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        manifestAttrs.put("package", packageName);
        manifestAttrs.put("android:versionCode", versionCode);
        if (splitName != null) manifestAttrs.put("split", splitName);
        return manifestAttrs;
    }
}
