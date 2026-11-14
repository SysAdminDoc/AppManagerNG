// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * The archive reader the thumbnail decoder uses on untrusted zips. Each damaged archive here is a
 * real zip with one field patched, the way a hostile file would arrive.
 */
public class BoundedZipReaderTest {
    private static final byte[] STORED = "hello, stored".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DEFLATED = patterned(100_000);

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void readsStoredAndDeflatedEntries() throws IOException {
        byte[] zip = sampleZip(null);
        assertArrayEquals(STORED, read(zip, "a.txt", 1024));
        assertArrayEquals(DEFLATED, read(zip, "b.bin", DEFLATED.length));
    }

    @Test
    public void readsArchivesWithAComment() throws IOException {
        assertArrayEquals(STORED, read(sampleZip("an archive comment"), "a.txt", 1024));
    }

    @Test
    public void missingEntryIsNull() throws IOException {
        assertNull(read(sampleZip(null), "nothing.png", 1024));
    }

    @Test
    public void emptyDeflatedEntryReads() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry("empty"));
            zos.closeEntry();
        }
        assertArrayEquals(new byte[0], read(bytes.toByteArray(), "empty", 16));
    }

    @Test
    public void entryOverTheLimitIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        assertThrows(ZipException.class, () -> read(zip, "b.bin", DEFLATED.length - 1));
    }

    @Test
    public void entryInflatingPastItsDeclaredSizeIsRefused() throws IOException {
        // A bomb: a megabyte of zeros that claims to be 1000 bytes, under the caller's limit
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry("bomb"));
            zos.write(new byte[1024 * 1024]);
            zos.closeEntry();
        }
        byte[] zip = bytes.toByteArray();
        putInt(zip, centralHeader(zip, 0) + 24, 1000);
        ZipException e = assertThrows(ZipException.class, () -> read(zip, "bomb", 4096));
        // Stopped at byte 1001, not after inflating the whole megabyte one byte at a time
        assertEquals("Entry is larger than it declares", e.getMessage());
    }

    @Test
    public void entryShorterThanItsDeclaredSizeIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        int header = centralHeader(zip, 1);
        putInt(zip, header + 24, DEFLATED.length + 100);
        assertThrows(ZipException.class, () -> read(zip, "b.bin", DEFLATED.length + 100));
    }

    @Test
    public void truncatedOrGarbageFilesAreRefused() throws IOException {
        byte[] zip = sampleZip(null);
        assertThrows(ZipException.class, () -> open(Arrays.copyOf(zip, zip.length - 10)));
        assertThrows(ZipException.class, () -> open(new byte[10]));
        byte[] garbage = patterned(4096);
        assertThrows(ZipException.class, () -> open(garbage));
    }

    @Test
    public void entryCutShortByTheFileIsRefused() throws IOException {
        // The central directory is intact, but the data it points at ends early
        byte[] zip = sampleZip(null);
        putInt(zip, centralHeader(zip, 1) + 20, readInt(zip, centralHeader(zip, 1) + 20) + 1_000_000);
        assertThrows(ZipException.class, () -> read(zip, "b.bin", DEFLATED.length));
    }

    @Test
    public void localHeaderOutsideTheArchiveIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        int header = centralHeader(zip, 0);
        putInt(zip, header + 42, centralDirectoryOffset(zip));
        assertThrows(ZipException.class, () -> read(zip, "a.txt", 1024));
    }

    @Test
    public void localHeaderWithoutItsSignatureIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        putInt(zip, centralHeader(zip, 0) + 42, 1);
        assertThrows(ZipException.class, () -> read(zip, "a.txt", 1024));
    }

    @Test
    public void zip64MarkersAreRefused() throws IOException {
        byte[] countMarker = sampleZip(null);
        int eocd = countMarker.length - 22;
        putShort(countMarker, eocd + 8, 0xFFFF);
        putShort(countMarker, eocd + 10, 0xFFFF);
        assertThrows(ZipException.class, () -> open(countMarker));

        byte[] sizeMarker = sampleZip(null);
        putInt(sizeMarker, centralHeader(sizeMarker, 0) + 24, 0xFFFFFFFF);
        assertThrows(ZipException.class, () -> open(sizeMarker));
    }

    @Test
    public void centralDirectoryOverlappingItsEndRecordIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        int eocd = zip.length - 22;
        putInt(zip, eocd + 12, readInt(zip, eocd + 12) + 1);
        assertThrows(ZipException.class, () -> open(zip));
    }

    @Test
    public void oversizedCentralDirectoryIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        putInt(zip, zip.length - 22 + 12, BoundedZipReader.MAX_CENTRAL_DIRECTORY_BYTES + 1);
        assertThrows(ZipException.class, () -> open(zip));
    }

    @Test
    public void splitArchivesAreRefused() throws IOException {
        byte[] zip = sampleZip(null);
        putShort(zip, zip.length - 22 + 4, 1);
        assertThrows(ZipException.class, () -> open(zip));
    }

    @Test
    public void encryptedEntriesAreRefused() throws IOException {
        byte[] zip = sampleZip(null);
        int header = centralHeader(zip, 0);
        putShort(zip, header + 8, readShort(zip, header + 8) | 1);
        assertThrows(ZipException.class, () -> read(zip, "a.txt", 1024));
    }

    @Test
    public void unsupportedCompressionIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        // 12 is bzip2
        putShort(zip, centralHeader(zip, 1) + 10, 12);
        assertThrows(ZipException.class, () -> read(zip, "b.bin", DEFLATED.length));
    }

    @Test
    public void crcMismatchIsRefused() throws IOException {
        byte[] stored = sampleZip(null);
        int header = centralHeader(stored, 0);
        putInt(stored, header + 16, readInt(stored, header + 16) ^ 1);
        assertThrows(ZipException.class, () -> read(stored, "a.txt", 1024));

        byte[] deflated = sampleZip(null);
        header = centralHeader(deflated, 1);
        putInt(deflated, header + 16, readInt(deflated, header + 16) ^ 1);
        assertThrows(ZipException.class, () -> read(deflated, "b.bin", DEFLATED.length));
    }

    @Test
    public void corruptDeflateDataIsRefused() throws IOException {
        byte[] zip = sampleZip(null);
        int local = readInt(zip, centralHeader(zip, 1) + 42);
        int dataStart = local + 30 + readShort(zip, local + 26) + readShort(zip, local + 28);
        // 0xFF starts a reserved block type
        Arrays.fill(zip, dataStart, dataStart + 8, (byte) 0xFF);
        assertThrows(ZipException.class, () -> read(zip, "b.bin", DEFLATED.length));
    }

    // Helpers

    private byte[] read(byte[] zip, String name, int maxBytes) throws IOException {
        File file = write(zip);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return new BoundedZipReader(raf.getChannel()).read(name, maxBytes);
        }
    }

    private void open(byte[] zip) throws IOException {
        File file = write(zip);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            new BoundedZipReader(raf.getChannel());
        }
    }

    private File write(byte[] bytes) throws IOException {
        File file = mTemp.newFile();
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static byte[] sampleZip(String comment) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            if (comment != null) {
                zos.setComment(comment);
            }
            ZipEntry stored = new ZipEntry("a.txt");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(STORED.length);
            stored.setCompressedSize(STORED.length);
            CRC32 crc = new CRC32();
            crc.update(STORED);
            stored.setCrc(crc.getValue());
            zos.putNextEntry(stored);
            zos.write(STORED);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("b.bin"));
            zos.write(DEFLATED);
            zos.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] patterned(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; ++i) {
            bytes[i] = (byte) ((i * 31) ^ (i >> 7));
        }
        return bytes;
    }

    private static int endOfCentralDirectory(byte[] zip) {
        for (int i = zip.length - 22; i >= 0; --i) {
            if (readInt(zip, i) == 0x06054b50) {
                return i;
            }
        }
        throw new AssertionError("no end record");
    }

    private static int centralDirectoryOffset(byte[] zip) {
        return readInt(zip, endOfCentralDirectory(zip) + 16);
    }

    private static int centralHeader(byte[] zip, int index) {
        int position = centralDirectoryOffset(zip);
        for (int i = 0; i < index; ++i) {
            position += 46 + readShort(zip, position + 28) + readShort(zip, position + 30)
                    + readShort(zip, position + 32);
        }
        return position;
    }

    private static int readInt(byte[] zip, int index) {
        return ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).getInt(index);
    }

    private static int readShort(byte[] zip, int index) {
        return ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).getShort(index) & 0xFFFF;
    }

    private static void putInt(byte[] zip, int index, int value) {
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putInt(index, value);
    }

    private static void putShort(byte[] zip, int index, int value) {
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putShort(index, (short) value);
    }
}
