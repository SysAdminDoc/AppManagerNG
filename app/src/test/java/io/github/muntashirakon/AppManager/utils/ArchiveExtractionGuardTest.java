// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

public class ArchiveExtractionGuardTest {
    @Test
    public void byteFloorAppliesToTinyArchives() throws IOException {
        // A 1 KiB archive would have a ratio ceiling far below the floor; the floor wins.
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(1024);
        // Writing up to the floor must succeed.
        guard.addBytes(ArchiveExtractionGuard.DEFAULT_BYTE_FLOOR);
        assertEquals(ArchiveExtractionGuard.DEFAULT_BYTE_FLOOR, guard.getBytesExtracted());
    }

    @Test
    public void exceedingByteCeilingThrows() {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(1000, ArchiveExtractionGuard.DEFAULT_MAX_ENTRIES);
        assertThrows(IOException.class, () -> guard.addBytes(1001));
    }

    @Test
    public void ratioCeilingScalesWithCompressedSize() throws IOException {
        long compressed = 4L * 1024 * 1024 * 1024; // 4 GiB -> ratio ceiling 800 GiB > floor
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(compressed);
        long expected = compressed * ArchiveExtractionGuard.DEFAULT_MAX_RATIO;
        // Just below the ceiling is fine.
        guard.addBytes(expected - 1);
        // Crossing it fails.
        assertThrows(IOException.class, () -> guard.addBytes(2));
    }

    @Test
    public void unknownCompressedSizeUsesFixedCeiling() {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(0);
        assertThrows(IOException.class,
                () -> guard.addBytes(ArchiveExtractionGuard.UNKNOWN_SIZE_CEILING + 1));
    }

    @Test
    public void entryCountIsBounded() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(Long.MAX_VALUE, 3);
        guard.onNewEntry();
        guard.onNewEntry();
        guard.onNewEntry();
        assertThrows(IOException.class, guard::onNewEntry);
        assertEquals(3, guard.getEntriesExtracted());
    }

    @Test
    public void copyStopsBeforeWritingOverflowingChunk() {
        // Ceiling of 100 bytes; input is 1 KiB. The copy must abort before exceeding it.
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(100, ArchiveExtractionGuard.DEFAULT_MAX_ENTRIES);
        byte[] input = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> guard.copy(new ByteArrayInputStream(input), out));
        // Bounded: never wrote past the ceiling (single read of <=ceiling here, so 0 written).
        assertTrue(out.size() <= 100);
    }

    @Test
    public void copyPassesThroughWhenUnderLimit() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(Long.MAX_VALUE, 10);
        byte[] input = "hello world".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        guard.copy(new ByteArrayInputStream(input), out);
        assertEquals(input.length, out.size());
        assertEquals(input.length, guard.getBytesExtracted());
    }

    @Test
    public void manySmallEntriesShareOneCumulativeBudget() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(10, 10, 8, 10);
        guard.onNewEntry();
        guard.addBytes(6);
        guard.onNewEntry();
        assertThrows(IOException.class, () -> guard.addBytes(5));
        assertEquals(6, guard.getBytesExtracted());
    }

    @Test
    public void singleEntryLimitIsIndependentOfTotalBudget() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(100, 10, 8, 100);
        guard.onNewEntry();
        assertThrows(IOException.class, () -> guard.addBytes(9));
        assertEquals(0, guard.getBytesExtracted());
    }

    @Test
    public void temporaryBudgetCanBeReleasedBetweenEntries() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(100, 10, 100, 6);
        guard.onNewEntry();
        guard.copyToTemporary(new ByteArrayInputStream(new byte[6]), new ByteArrayOutputStream());
        assertEquals(6, guard.getTemporaryBytes());
        guard.releaseTemporaryBytes(6);
        guard.onNewEntry();
        guard.copyToTemporary(new ByteArrayInputStream(new byte[6]), new ByteArrayOutputStream());
        assertEquals(6, guard.getTemporaryBytes());
        assertEquals(12, guard.getBytesExtracted());
    }

    @Test
    public void temporaryBudgetFailureDoesNotPartiallyChargeExpandedBytes() throws IOException {
        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(100, 10, 100, 4);
        guard.onNewEntry();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(IOException.class,
                () -> guard.copyToTemporary(new ByteArrayInputStream(new byte[6]), out));

        assertEquals(0, guard.getBytesExtracted());
        assertEquals(0, guard.getCurrentEntryBytes());
        assertEquals(0, guard.getTemporaryBytes());
        assertEquals(0, out.size());
    }

    @Test
    public void hostileZipFixtureStopsAtExpandedByteBudget() throws IOException {
        ByteArrayOutputStream fixture = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(fixture)) {
            zos.putNextEntry(new ZipEntry("dense.bin"));
            zos.write(new byte[32]);
            zos.closeEntry();
        }

        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(16, 10, 16, 16);
        ByteArrayOutputStream extracted = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fixture.toByteArray()))) {
            ZipEntry entry = zis.getNextEntry();
            guard.onNewEntry();
            guard.assertEntrySize(entry.getSize());
            assertThrows(IOException.class, () -> guard.copyToTemporary(zis, extracted));
        }
        assertTrue(extracted.size() <= 16);
    }

    @Test
    public void hostileTarFixtureRejectsDeclaredOversizedEntry() throws IOException {
        ByteArrayOutputStream fixture = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(fixture)) {
            TarArchiveEntry entry = new TarArchiveEntry("oversized.bin");
            entry.setSize(32);
            tos.putArchiveEntry(entry);
            tos.write(new byte[32]);
            tos.closeArchiveEntry();
        }

        ArchiveExtractionGuard guard = new ArchiveExtractionGuard(64, 10, 16, 64);
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new ByteArrayInputStream(fixture.toByteArray()))) {
            TarArchiveEntry entry = tis.getNextEntry();
            guard.onNewEntry();
            assertThrows(IOException.class, () -> guard.assertEntrySize(entry.getSize()));
        }
        assertEquals(0, guard.getBytesExtracted());
    }
}
