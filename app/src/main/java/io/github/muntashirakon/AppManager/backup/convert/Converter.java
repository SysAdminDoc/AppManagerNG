// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.utils.ArchiveExtractionGuard;
import io.github.muntashirakon.io.Path;

public abstract class Converter {
    static final int MAX_ARCHIVE_ENTRIES = 100_000;
    static final long MAX_SINGLE_ENTRY_BYTES = 64L * 1024 * 1024 * 1024;
    static final long MAX_TEMPORARY_BYTES = 64L * 1024 * 1024 * 1024;

    public abstract void convert() throws BackupException;

    public abstract void cleanup();

    public abstract String getPackageName();

    @NonNull
    protected static ArchiveExtractionGuard createExtractionGuard(@NonNull Iterable<Path> sourceFiles) {
        long compressedBytes = 0;
        for (Path sourceFile : sourceFiles) {
            long size = sourceFile.length();
            if (size <= 0) continue;
            if (size > Long.MAX_VALUE - compressedBytes) {
                compressedBytes = Long.MAX_VALUE;
                break;
            }
            compressedBytes += size;
        }
        return ArchiveExtractionGuard.forCompressedSize(compressedBytes, MAX_ARCHIVE_ENTRIES,
                MAX_SINGLE_ENTRY_BYTES, MAX_TEMPORARY_BYTES);
    }
}
