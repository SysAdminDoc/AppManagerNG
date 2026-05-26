// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Heuristic header parser for the APK bundle container formats consumed by
 * the T19-C duplicate finder. Recognises:
 *
 * <ul>
 *   <li><b>APKS</b> (Android App Bundle output, "split APKs"): a ZIP that
 *       contains a {@code base.apk} entry and one or more {@code split_*.apk}
 *       siblings. Some toolchains include a {@code BundleConfig.pb} (Bundletool)
 *       or {@code toc.pb} (Apks Helper) sidecar.</li>
 *   <li><b>APKM</b> (APKMirror Installer): a ZIP with an
 *       {@code info.json} entry plus the canonical APK splits.</li>
 *   <li><b>XAPK</b> (APKPure): a ZIP with a {@code manifest.json} entry
 *       plus the splits and OBB data.</li>
 *   <li><b>APK</b> (single-APK fallback): a ZIP that contains
 *       {@code AndroidManifest.xml} and {@code classes.dex} but none of the
 *       above sidecars.</li>
 * </ul>
 *
 * <p>The parser intentionally only reads the central directory entry names
 * (and a small constant number of headers) - never the entry payloads. That
 * lets the duplicate finder fingerprint a bundle in O(entries) without
 * unpacking gigabyte-class XAPK archives.
 *
 * <p>All static methods are JVM-clean (use only {@code java.util.zip}) and
 * therefore JVM-unit-testable against in-memory ZIP fixtures.
 */
public final class ApkBundleHeaderParser {

    public enum BundleFormat {
        APK,
        APKS,
        APKM,
        XAPK,
        UNKNOWN
    }

    public static final class Header {
        @NonNull
        public final BundleFormat format;
        public final boolean hasBaseApk;
        public final int splitApkCount;
        public final boolean hasManifestJson;
        public final boolean hasInfoJson;
        public final boolean hasBundleConfig;
        public final boolean hasObbData;

        Header(@NonNull BundleFormat format,
               boolean hasBaseApk, int splitApkCount,
               boolean hasManifestJson, boolean hasInfoJson,
               boolean hasBundleConfig, boolean hasObbData) {
            this.format = format;
            this.hasBaseApk = hasBaseApk;
            this.splitApkCount = splitApkCount;
            this.hasManifestJson = hasManifestJson;
            this.hasInfoJson = hasInfoJson;
            this.hasBundleConfig = hasBundleConfig;
            this.hasObbData = hasObbData;
        }
    }

    /** ZIP local file header magic: PK\003\004. */
    private static final byte[] ZIP_LOCAL_HEADER_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** Largest reasonable bundle archive central directory. Bundle parsing past this is rejected. */
    public static final int MAX_ZIP_ENTRIES = 50_000;

    private ApkBundleHeaderParser() {
    }

    /**
     * Detect the bundle format from a fully buffered byte array. Returns
     * {@link BundleFormat#UNKNOWN} when the input is not even a ZIP, and
     * {@link BundleFormat#APK} when it's a ZIP but contains no recognisable
     * bundle sidecar (best-effort treat-as-single-APK).
     *
     * <p>Throws {@link IOException} only if the underlying {@link ZipInputStream}
     * cannot be read; never throws for malformed central-directory data.
     */
    @NonNull
    public static Header parse(@NonNull byte[] bytes) throws IOException {
        if (!hasZipMagic(bytes)) {
            return new Header(BundleFormat.UNKNOWN, false, 0, false, false, false, false);
        }
        return parse(new ByteArrayInputStream(bytes));
    }

    @NonNull
    public static Header parse(@NonNull InputStream in) throws IOException {
        Set<String> entries = collectEntryNames(in);
        return classify(entries);
    }

    static boolean hasZipMagic(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length < ZIP_LOCAL_HEADER_MAGIC.length) return false;
        for (int i = 0; i < ZIP_LOCAL_HEADER_MAGIC.length; ++i) {
            if (bytes[i] != ZIP_LOCAL_HEADER_MAGIC[i]) return false;
        }
        return true;
    }

    @NonNull
    static Set<String> collectEntryNames(@NonNull InputStream in) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (entries.size() >= MAX_ZIP_ENTRIES) break;
                String name = e.getName();
                if (name != null && !name.isEmpty()) entries.add(name);
            }
        }
        return entries;
    }

    @NonNull
    static Header classify(@NonNull Set<String> entries) {
        boolean hasBase = false;
        boolean hasManifestJson = false;
        boolean hasInfoJson = false;
        boolean hasBundleConfig = false;
        boolean hasObbData = false;
        boolean hasAndroidManifest = false;
        boolean hasClassesDex = false;
        List<String> splitApks = new ArrayList<>();
        for (String entry : entries) {
            String lower = entry.toLowerCase(Locale.ROOT);
            if (lower.equals("base.apk")) hasBase = true;
            else if (lower.equals("androidmanifest.xml")) hasAndroidManifest = true;
            else if (lower.equals("classes.dex") || lower.startsWith("classes") && lower.endsWith(".dex"))
                hasClassesDex = true;
            else if (lower.equals("manifest.json")) hasManifestJson = true;
            else if (lower.equals("info.json")) hasInfoJson = true;
            else if (lower.equals("bundleconfig.pb") || lower.equals("toc.pb"))
                hasBundleConfig = true;
            else if ((lower.endsWith(".apk") && lower.startsWith("split_"))
                    || (lower.endsWith(".apk") && lower.startsWith("config."))) {
                splitApks.add(lower);
            } else if (lower.startsWith("obb/") || lower.startsWith("android/obb/")) {
                hasObbData = true;
            }
        }
        BundleFormat format;
        if (hasManifestJson && (hasBase || !splitApks.isEmpty())) {
            format = BundleFormat.XAPK;
        } else if (hasInfoJson && (hasBase || !splitApks.isEmpty())) {
            format = BundleFormat.APKM;
        } else if (hasBase || hasBundleConfig || !splitApks.isEmpty()) {
            format = BundleFormat.APKS;
        } else if (hasAndroidManifest && hasClassesDex) {
            format = BundleFormat.APK;
        } else {
            format = BundleFormat.UNKNOWN;
        }
        return new Header(format, hasBase, splitApks.size(),
                hasManifestJson, hasInfoJson, hasBundleConfig, hasObbData);
    }
}
