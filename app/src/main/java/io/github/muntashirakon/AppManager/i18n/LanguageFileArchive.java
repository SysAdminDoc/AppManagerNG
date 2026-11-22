// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Language files packed in a zip, the way translation forums want them shared. A zip from a forum
 * post is untrusted: nothing in it is written to disk, entries are read with hard caps on their
 * count and inflated size (so a small entry that inflates to gigabytes stops at the cap), folders,
 * odd paths and nested archives are skipped, and the language file picked from it goes through
 * the same checks as a plain .json import.
 */
public final class LanguageFileArchive {
    public static final String ZIP_SUFFIX = ".zip";
    public static final String MIME_ZIP = "application/zip";
    /** More entries than this isn't a language file someone packed. */
    @VisibleForTesting
    static final int MAX_ENTRIES = 64;
    /** Everything read out of one zip, across all its entries. */
    @VisibleForTesting
    static final long MAX_TOTAL_BYTES = 4L * LanguagePackReader.MAX_FILE_BYTES;
    private static final String[] ARCHIVE_SUFFIXES = {".zip", ".jar", ".apk", ".apks", ".xapk", ".gz", ".tgz",
            ".bz2", ".xz", ".7z", ".rar", ".tar", ".zst"};

    /** The language file found in a zip. */
    public static final class Picked {
        /** The entry's name inside the zip, as shown to the user. */
        @NonNull
        public final String entryName;
        @NonNull
        public final byte[] data;
        /** How many .json files the zip had, so the user can be told which one was used. */
        public final int jsonFiles;

        Picked(@NonNull String entryName, @NonNull byte[] data, int jsonFiles) {
            this.entryName = entryName;
            this.data = data;
            this.jsonFiles = jsonFiles;
        }
    }

    private LanguageFileArchive() {
    }

    /** True when {@code data} starts like a zip file (including an empty one). */
    public static boolean isZip(@NonNull byte[] data) {
        return data.length >= 4 && data[0] == 'P' && data[1] == 'K'
                && ((data[2] == 3 && data[3] == 4) || (data[2] == 5 && data[3] == 6) || (data[2] == 7 && data[3] == 8));
    }

    /** Packs one language file into a zip, under {@code entryName}. */
    @NonNull
    public static byte[] zip(@NonNull String entryName, @NonNull byte[] json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length / 3 + 256);
        write(out, entryName, json);
        return out.toByteArray();
    }

    public static void write(@NonNull OutputStream os, @NonNull String entryName, @NonNull byte[] json)
            throws IOException {
        ZipOutputStream zos = new ZipOutputStream(os);
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(json);
        zos.closeEntry();
        zos.finish();
        zos.flush();
    }

    /**
     * Returns the first .json in the zip that {@link LanguagePackReader} accepts. When none does,
     * the first one's problem is thrown, so the user hears why it was refused.
     */
    @NonNull
    public static Picked pick(@NonNull byte[] zip) throws LanguagePackException {
        int entries = 0;
        int jsonFiles = 0;
        long total = 0;
        Picked picked = null;
        LanguagePackException firstProblem = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new LanguagePackException(LanguagePackException.Reason.TOO_LARGE,
                            "More than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                if (entry.isDirectory() || !isPlainJsonName(name)) {
                    continue;
                }
                ++jsonFiles;
                if (picked != null) {
                    // Only counted, so the user can be told which file was used.
                    continue;
                }
                byte[] data = readCapped(zis, Math.min(LanguagePackReader.MAX_FILE_BYTES, MAX_TOTAL_BYTES - total));
                if (data == null) {
                    // Stop here: moving on to the next entry would inflate the rest of this one.
                    throw new LanguagePackException(LanguagePackException.Reason.TOO_LARGE,
                            name + " inflates past the size a language file can have");
                }
                total += data.length;
                if (LanguageFileArchive.isZip(data)) {
                    // A zip named .json: archives inside archives are never opened.
                    if (firstProblem == null) {
                        firstProblem = new LanguagePackException(LanguagePackException.Reason.NOT_A_PACK,
                                name + " is an archive");
                    }
                    continue;
                }
                try {
                    LanguagePackReader.read(data);
                    picked = new Picked(displayName(name), data, 0);
                } catch (LanguagePackException e) {
                    if (firstProblem == null) {
                        firstProblem = e;
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // A damaged zip, or one whose entry names aren't valid in any charset Java reads.
            throw new LanguagePackException(LanguagePackException.Reason.NOT_A_PACK, "Not a readable zip", e);
        }
        if (picked != null) {
            return new Picked(picked.entryName, picked.data, jsonFiles);
        }
        if (firstProblem != null) {
            throw firstProblem;
        }
        throw new LanguagePackException(LanguagePackException.Reason.NO_FILE_IN_ZIP, "No .json in the zip");
    }

    /**
     * A .json entry with an ordinary relative name. Paths that climb out of the zip, absolute
     * paths, drive letters and anything named like an archive are skipped. Nothing is extracted
     * to disk, but a name like that never comes from someone simply zipping their file.
     */
    @VisibleForTesting
    static boolean isPlainJsonName(@Nullable String name) {
        if (name == null || name.isEmpty() || name.length() > 255) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json")) {
            return false;
        }
        for (String suffix : ARCHIVE_SUFFIXES) {
            if (lower.endsWith(suffix + ".json")) {
                return false;
            }
        }
        if (name.startsWith("/") || name.startsWith("\\") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        for (String part : name.replace('\\', '/').split("/")) {
            if (part.equals("..") || part.equals(".")) {
                return false;
            }
        }
        // macOS adds these resource forks to zips it makes
        return !lower.startsWith("__macosx/") && !displayName(name).startsWith("._");
    }

    @NonNull
    private static String displayName(@NonNull String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** Reads at most {@code cap} bytes; {@code null} when the entry has more than that. */
    @Nullable
    private static byte[] readCapped(@NonNull InputStream in, long cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        long read = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            read += n;
            if (read > cap) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
