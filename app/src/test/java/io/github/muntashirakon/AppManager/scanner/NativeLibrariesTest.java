// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class NativeLibrariesTest {
    @Test
    public void parseElf64LoadSegmentAlignment_accepts16KbAlignedLibrary() throws Exception {
        NativeLibraries.NativeLib nativeLib = NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libaligned.so",
                120,
                new ByteArrayInputStream(createElf64(0x4000)));

        assertTrue(nativeLib instanceof NativeLibraries.ElfLib);
        NativeLibraries.ElfLib elfLib = (NativeLibraries.ElfLib) nativeLib;
        assertTrue(elfLib.hasKnownLoadSegmentAlignment());
        assertEquals(0x4000, elfLib.getMinLoadSegmentAlignment());
        assertTrue(elfLib.has16KbLoadSegmentAlignment());
    }

    @Test
    public void parseElf64LoadSegmentAlignment_rejects4KbAlignedLibrary() throws Exception {
        NativeLibraries.NativeLib nativeLib = NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/liblegacy.so",
                120,
                new ByteArrayInputStream(createElf64(0x1000)));

        assertTrue(nativeLib instanceof NativeLibraries.ElfLib);
        NativeLibraries.ElfLib elfLib = (NativeLibraries.ElfLib) nativeLib;
        assertTrue(elfLib.hasKnownLoadSegmentAlignment());
        assertEquals(0x1000, elfLib.getMinLoadSegmentAlignment());
        assertFalse(elfLib.has16KbLoadSegmentAlignment());
    }

    @Test
    public void parseElf64LoadSegmentAlignment_reportsUnknownWithoutProgramHeaders() throws Exception {
        NativeLibraries.NativeLib nativeLib = NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libunknown.so",
                64,
                new ByteArrayInputStream(createElf64WithoutProgramHeaders()));

        assertTrue(nativeLib instanceof NativeLibraries.ElfLib);
        NativeLibraries.ElfLib elfLib = (NativeLibraries.ElfLib) nativeLib;
        assertFalse(elfLib.hasKnownLoadSegmentAlignment());
        assertFalse(elfLib.has16KbLoadSegmentAlignment());
    }

    @Test
    public void parseElf64SectionHeaders_reportsStaticSymbolsAndStripping() throws Exception {
        NativeLibraries.ElfLib withSymbols = (NativeLibraries.ElfLib) NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libsymbols.so",
                248,
                new ByteArrayInputStream(createElf64WithStaticSymbolTable(true)));
        assertTrue(withSymbols.hasKnownStaticSymbolTable());
        assertTrue(withSymbols.hasStaticSymbolTable());
        assertFalse(withSymbols.isStripped());

        NativeLibraries.ElfLib stripped = (NativeLibraries.ElfLib) NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libstripped.so",
                248,
                new ByteArrayInputStream(createElf64WithStaticSymbolTable(false)));
        assertTrue(stripped.hasKnownStaticSymbolTable());
        assertFalse(stripped.hasStaticSymbolTable());
        assertTrue(stripped.isStripped());
    }

    @Test
    public void fileConstructor_reportsStoredZipDataOffset() throws Exception {
        String name = "lib/arm64-v8a/libnative.so";
        byte[] elf = createElf64(0x4000);
        File apk = Files.createTempFile("native-libraries", ".apk").toFile();
        try {
            CRC32 crc = new CRC32();
            crc.update(elf);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apk.toPath()))) {
                ZipEntry entry = new ZipEntry(name);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(elf.length);
                entry.setCompressedSize(elf.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(elf);
                zip.closeEntry();
            }

            NativeLibraries libraries = new NativeLibraries(apk);
            assertEquals(1, libraries.getLibs().size());
            NativeLibraries.NativeLib nativeLib = libraries.getLibs().get(0);
            assertTrue(nativeLib.isZipCompressionKnown());
            assertTrue(nativeLib.isZipStored());
            assertTrue(nativeLib.hasKnownZipAlignment());
            assertEquals(30L + name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    nativeLib.getZipDataOffset());
            assertEquals(nativeLib.getZipDataOffset() % 16_384 == 0,
                    nativeLib.has16KbZipAlignment());
        } finally {
            Files.deleteIfExists(apk.toPath());
        }
    }

    @Test
    public void parseShortInput_returnsInvalidLib() throws Exception {
        NativeLibraries.NativeLib nativeLib = NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libtruncated.so",
                3,
                new ByteArrayInputStream(new byte[]{0x7f, 0x45, 0x4c}));

        assertTrue(nativeLib instanceof NativeLibraries.InvalidLib);
    }

    @Test
    public void inputStreamConstructorRejectsTooManyZipEntries() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i <= NativeLibraries.MAX_APK_SCAN_ENTRIES; ++i) {
                zip.putNextEntry(new ZipEntry("res/raw/ignored-" + i + ".bin"));
                zip.closeEntry();
            }
        }

        IOException exception = assertThrows(IOException.class,
                () -> new NativeLibraries(new ByteArrayInputStream(out.toByteArray())));

        assertEquals("APK has too many entries to scan native libraries.", exception.getMessage());
    }

    private static byte[] createElf64(long loadSegmentAlignment) {
        ByteBuffer buffer = ByteBuffer.allocate(64 + 56).order(ByteOrder.LITTLE_ENDIAN);
        writeElfIdent(buffer);
        buffer.putShort(16, (short) NativeLibraries.ElfLib.TYPE_DYN);
        buffer.putShort(18, (short) 183); // AArch64
        buffer.putLong(32, 64L); // e_phoff
        buffer.putShort(54, (short) 56); // e_phentsize
        buffer.putShort(56, (short) 1); // e_phnum
        buffer.putInt(64, 1); // PT_LOAD
        buffer.putLong(64 + 48, loadSegmentAlignment); // p_align
        return buffer.array();
    }

    private static byte[] createElf64WithStaticSymbolTable(boolean withSymbols) {
        ByteBuffer buffer = ByteBuffer.allocate(64 + 56 + 2 * 64).order(ByteOrder.LITTLE_ENDIAN);
        writeElfIdent(buffer);
        buffer.putShort(16, (short) NativeLibraries.ElfLib.TYPE_DYN);
        buffer.putShort(18, (short) 183);
        buffer.putLong(32, 64L);
        buffer.putLong(40, 120L); // e_shoff
        buffer.putShort(54, (short) 56);
        buffer.putShort(56, (short) 1);
        buffer.putShort(58, (short) 64); // e_shentsize
        buffer.putShort(60, (short) 2); // e_shnum
        buffer.putInt(64, 1); // PT_LOAD
        buffer.putLong(64 + 48, 0x4000);
        buffer.putInt(120 + 64 + 4, withSymbols ? 2 : 1); // SHT_SYMTAB or SHT_PROGBITS
        return buffer.array();
    }

    private static byte[] createElf64WithoutProgramHeaders() {
        ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        writeElfIdent(buffer);
        buffer.putShort(16, (short) NativeLibraries.ElfLib.TYPE_DYN);
        buffer.putShort(18, (short) 183); // AArch64
        buffer.putShort(54, (short) 56); // e_phentsize
        buffer.putShort(56, (short) 0); // e_phnum
        return buffer.array();
    }

    private static void writeElfIdent(ByteBuffer buffer) {
        buffer.put(0, (byte) 0x7f);
        buffer.put(1, (byte) 'E');
        buffer.put(2, (byte) 'L');
        buffer.put(3, (byte) 'F');
        buffer.put(4, (byte) NativeLibraries.ElfLib.ARCH_64BIT);
        buffer.put(5, (byte) NativeLibraries.ElfLib.ENDIANNESS_LITTLE_ENDIAN);
    }
}
