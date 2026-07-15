// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.io.Paths;

public final class ArchiveImportFuzzTarget {
    private static final long BYTE_LIMIT = 1024;
    private static final int ENTRY_LIMIT = 16;
    private static final long ENTRY_BYTE_LIMIT = 256;
    private static final long TEMP_BYTE_LIMIT = 512;

    private ArchiveImportFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] data) {
        fuzzPathContainment(data);
        fuzzAllocationBudgets(data);
    }

    private static void fuzzPathContainment(byte[] data) {
        String input = new String(data, StandardCharsets.UTF_8);
        int separator = input.indexOf('\0');
        String linkName = separator >= 0 ? input.substring(0, separator) : input;
        String entryName = separator >= 0 ? input.substring(separator + 1) : "files/link";
        String destination = "/data/data/com.example";
        boolean contained = TarUtils.isSymlinkTargetContained(linkName, entryName, destination);
        if (!contained) {
            return;
        }
        String normalized;
        if (linkName.startsWith(Paths.PATH_SEPARATOR)) {
            normalized = Paths.normalize(linkName);
            if (normalized == null || !(normalized.equals(destination)
                    || normalized.startsWith(destination + Paths.PATH_SEPARATOR))) {
                throw new AssertionError("Absolute archive link escaped the extraction root");
            }
        } else {
            java.io.File parent = new java.io.File(entryName).getParentFile();
            String combined = (parent != null ? parent.getPath() + Paths.PATH_SEPARATOR : "") + linkName;
            normalized = Paths.normalize(combined);
            if (normalized == null || normalized.equals("..") || normalized.startsWith("../")) {
                throw new AssertionError("Relative archive link escaped the extraction root");
            }
        }
    }

    private static void fuzzAllocationBudgets(byte[] data) {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(
                BYTE_LIMIT, ENTRY_LIMIT, ENTRY_BYTE_LIMIT, TEMP_BYTE_LIMIT);
        for (byte value : data) {
            long bytesBefore = guard.getBytesExtracted();
            int entriesBefore = guard.getEntriesExtracted();
            long entryBytesBefore = guard.getCurrentEntryBytes();
            long temporaryBefore = guard.getTemporaryBytes();
            int unsigned = Byte.toUnsignedInt(value);
            try {
                switch (unsigned & 3) {
                    case 0:
                        guard.onNewEntry();
                        break;
                    case 1:
                        guard.addBytes(unsigned);
                        break;
                    case 2:
                        guard.reserveTemporaryBytes(unsigned);
                        break;
                    default:
                        guard.releaseTemporaryBytes(unsigned);
                        break;
                }
            } catch (IOException expectedBudgetError) {
                if (bytesBefore != guard.getBytesExtracted()
                        || entriesBefore != guard.getEntriesExtracted()
                        || entryBytesBefore != guard.getCurrentEntryBytes()
                        || temporaryBefore != guard.getTemporaryBytes()) {
                    throw new AssertionError("Rejected archive operation partially mutated its budget", expectedBudgetError);
                }
            }
            if (guard.getBytesExtracted() > BYTE_LIMIT
                    || guard.getEntriesExtracted() > ENTRY_LIMIT
                    || guard.getCurrentEntryBytes() > ENTRY_BYTE_LIMIT
                    || guard.getTemporaryBytes() > TEMP_BYTE_LIMIT) {
                throw new AssertionError("Archive import exceeded a configured allocation budget");
            }
        }
    }
}
