// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    public void parseShortInput_returnsInvalidLib() throws Exception {
        NativeLibraries.NativeLib nativeLib = NativeLibraries.NativeLib.parse(
                "lib/arm64-v8a/libtruncated.so",
                3,
                new ByteArrayInputStream(new byte[]{0x7f, 0x45, 0x4c}));

        assertTrue(nativeLib instanceof NativeLibraries.InvalidLib);
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
