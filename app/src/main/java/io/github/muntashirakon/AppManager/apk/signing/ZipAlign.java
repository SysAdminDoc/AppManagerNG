// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.reandroid.archive.ArchiveEntry;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.writer.ApkFileWriter;
import com.reandroid.archive.writer.ZipAligner;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.Paths;

public class ZipAlign {
    public static final String TAG = ZipAlign.class.getSimpleName();

    public static final int ALIGNMENT_4 = 4;

    private static final int ALIGNMENT_PAGE = 4096;

    public static void align(@NonNull File input, @NonNull File output, int alignment, boolean pageAlignSharedLibs)
            throws IOException {
        File dir = output.getParentFile();
        if (dir != null && !Paths.exists(dir) && !dir.mkdirs()) {
            throw new IOException("Could not create output directory " + dir);
        }

        try (ArchiveFile archive = new ArchiveFile(input);
             ApkFileWriter apkWriter = new ApkFileWriter(output, archive.getInputSources())) {
            apkWriter.setZipAligner(getZipAligner(alignment, pageAlignSharedLibs));
            apkWriter.write();
        }
        if (!verify(output, alignment, pageAlignSharedLibs)) {
            throw new IOException("Could not verify aligned APK file.");
        }
    }

    public static void align(@NonNull File inFile, int alignment, boolean pageAlignSharedLibs) throws IOException {
        File tmp = toTmpFile(inFile);
        deleteIfExists(tmp, "temporary aligned APK");
        boolean replaced = false;
        try {
            align(inFile, tmp, alignment, pageAlignSharedLibs);
            replaceFile(tmp, inFile);
            replaced = true;
        } catch (IOException e) {
            throw e;
        } finally {
            if (!replaced && tmp.exists() && !tmp.delete()) {
                Log.w(TAG, "Unable to delete temporary aligned APK '%s'", tmp);
            }
        }
    }

    @VisibleForTesting
    static void replaceFile(@NonNull File source, @NonNull File target) throws IOException {
        if (!source.exists()) {
            throw new IOException("Replacement source does not exist: " + source);
        }
        File backup = toBackupFile(target);
        deleteIfExists(backup, "stale zipalign backup");

        boolean backedUp = false;
        if (target.exists()) {
            if (!target.renameTo(backup)) {
                throw new IOException("Could not back up original APK file " + target);
            }
            backedUp = true;
        }

        if (source.renameTo(target)) {
            if (backedUp && backup.exists() && !backup.delete()) {
                Log.w(TAG, "Unable to delete zipalign backup '%s'", backup);
            }
            return;
        }

        if (backedUp) {
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "Unable to delete incomplete zipalign target '%s'", target);
            }
            if (!backup.renameTo(target)) {
                throw new IOException("Could not install aligned APK and failed to restore original APK " + target);
            }
        }
        throw new IOException("Could not replace original APK with aligned APK " + target);
    }

    public static boolean verify(@NonNull File file, int alignment, boolean pageAlignSharedLibs) {
        ArchiveFile zipFile;
        boolean foundBad = false;
        Log.d(TAG, "Verifying alignment of %s...", file);

        try {
            zipFile = new ArchiveFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open '%s' for verification", e, file);
            return false;
        }
        Iterator<ArchiveEntry> entryIterator = zipFile.iterator();
        while (entryIterator.hasNext()) {
            ArchiveEntry pEntry = entryIterator.next();
            String name = pEntry.getName();
            long fileOffset = pEntry.getFileOffset();
            if (pEntry.getMethod() == ZipEntry.DEFLATED) {
                Log.d(TAG, "%8d %s (OK - compressed)", fileOffset, name);
            } else if (pEntry.isDirectory()) {
                // Directory entries do not need to be aligned.
                Log.d(TAG, "%8d %s (OK - directory)", fileOffset, name);
            } else {
                int alignTo = getAlignment(pEntry, alignment, pageAlignSharedLibs);
                if ((fileOffset % alignTo) != 0) {
                    Log.w(TAG, "%8d %s (BAD - %d)\n", fileOffset, name, (fileOffset % alignTo));
                    foundBad = true;
                    break;
                } else {
                    Log.d(TAG, "%8d %s (OK)\n", fileOffset, name);
                }
            }
        }

        Log.d(TAG, "Verification %s\n", foundBad ? "FAILED" : "successful");
        try {
            zipFile.close();
        } catch (IOException e) {
            Log.w(TAG, "Unable to close '%s'", e, file);
        }
        return !foundBad;
    }

    private static int getAlignment(@NonNull ArchiveEntry entry, int defaultAlignment, boolean pageAlignSharedLibs) {
        if (!pageAlignSharedLibs) {
            return defaultAlignment;
        }
        String name = entry.getName();
        if (name.startsWith("lib/") && name.endsWith(".so")) {
            return ALIGNMENT_PAGE;
        } else {
            return defaultAlignment;
        }
    }

    @NonNull
    public static ZipAligner getZipAligner(int defaultAlignment, boolean pageAlignSharedLibs) {
        ZipAligner zipAligner = new ZipAligner();
        zipAligner.setDefaultAlignment(defaultAlignment);
        if (pageAlignSharedLibs) {
            Pattern patternNativeLib = Pattern.compile("^lib/.+\\.so$");
            zipAligner.setFileAlignment(patternNativeLib, ALIGNMENT_PAGE);
        }
        return zipAligner;
    }

    @NonNull
    private static File toTmpFile(@NonNull File file) {
        String name = file.getName() + ".align.tmp";
        File dir = file.getParentFile();
        if (dir == null) {
            return new File(name);
        }
        return new File(dir, name);
    }

    @NonNull
    private static File toBackupFile(@NonNull File file) {
        String name = file.getName() + ".align.bak";
        File dir = file.getParentFile();
        if (dir == null) {
            return new File(name);
        }
        return new File(dir, name);
    }

    private static void deleteIfExists(@NonNull File file, @NonNull String description) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + description + " " + file);
        }
    }
}
