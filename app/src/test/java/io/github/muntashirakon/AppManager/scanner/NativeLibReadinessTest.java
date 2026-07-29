// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * A sweep for native code that will break on Android 15+ is only useful if it never clears an app
 * it could not actually read. These fixtures are real 64-bit ELF headers with real program headers,
 * so the alignment really is parsed rather than asserted.
 */
@RunWith(RobolectricTestRunner.class)
public class NativeLibReadinessTest {
    private static final long PAGE_16_KB = 16384L;
    private static final long PAGE_4_KB = 4096L;

    /** Builds a minimal but genuinely parseable 64-bit little-endian ELF with one PT_LOAD segment. */
    @NonNull
    private static byte[] elf64(long alignment) {
        int phOffset = 64;
        int phEntrySize = 56;
        byte[] bytes = new byte[phOffset + phEntrySize];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{0x7f, 'E', 'L', 'F'});
        buffer.put((byte) 2);   // EI_CLASS = 64-bit
        buffer.put((byte) 1);   // EI_DATA = little endian
        buffer.put((byte) 1);   // EI_VERSION
        buffer.position(16);
        buffer.putShort((short) 3);   // e_type = ET_DYN
        buffer.putShort((short) 183); // e_machine = AArch64
        buffer.putInt(1);             // e_version
        buffer.putLong(0);            // e_entry
        buffer.putLong(phOffset);     // e_phoff
        buffer.putLong(0);            // e_shoff
        buffer.putInt(0);             // e_flags
        buffer.putShort((short) 64);  // e_ehsize
        buffer.putShort((short) phEntrySize); // e_phentsize
        buffer.putShort((short) 1);   // e_phnum
        buffer.putShort((short) 0);   // e_shentsize
        buffer.putShort((short) 0);   // e_shnum
        buffer.putShort((short) 0);   // e_shstrndx
        buffer.position(phOffset);
        buffer.putInt(1);             // p_type = PT_LOAD
        buffer.putInt(5);             // p_flags
        buffer.putLong(0);            // p_offset
        buffer.putLong(0);            // p_vaddr
        buffer.putLong(0);            // p_paddr
        buffer.putLong(0x1000);       // p_filesz
        buffer.putLong(0x1000);       // p_memsz
        buffer.putLong(alignment);    // p_align
        return bytes;
    }

    @NonNull
    private static NativeLibraries.NativeLib lib(@NonNull String path, @NonNull byte[] content)
            throws IOException {
        return NativeLibraries.NativeLib.parse(path, content.length, new ByteArrayInputStream(content));
    }

    @NonNull
    private static NativeLibraries.NativeLib aligned64(@NonNull String path) throws IOException {
        return lib(path, elf64(PAGE_16_KB));
    }

    @NonNull
    private static NativeLibraries.NativeLib unaligned64(@NonNull String path) throws IOException {
        return lib(path, elf64(PAGE_4_KB));
    }

    @Test
    public void aLibraryAlignedTo16KbIsReady() throws Exception {
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Collections.singletonList(aligned64("lib/arm64-v8a/libfoo.so")),
                Collections.singletonList("arm64-v8a"), false);

        assertTrue(readiness.hasNativeLibraries);
        assertTrue(readiness.sixteenKbReady);
        assertFalse(readiness.needsSixteenKbAttention());
        assertFalse(readiness.alignmentUnknown);
        assertEquals(Collections.singleton("arm64-v8a"), readiness.abis);
    }

    @Test
    public void oneUnalignedLibraryMakesTheWholePackageUnready() throws Exception {
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Arrays.asList(aligned64("lib/arm64-v8a/libgood.so"),
                        unaligned64("lib/arm64-v8a/libbad.so")),
                Collections.singletonList("arm64-v8a"), false);

        assertFalse(readiness.sixteenKbReady);
        assertTrue(readiness.needsSixteenKbAttention());
    }

    @Test
    public void anUnreadableLibraryIsNeverReportedAsReady() throws Exception {
        // Not an ELF image at all: nothing can be concluded, so nothing reassuring is claimed.
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Collections.singletonList(lib("lib/arm64-v8a/libjunk.so", new byte[]{1, 2, 3, 4, 5, 6, 7, 8})),
                Collections.singletonList("arm64-v8a"), false);

        assertTrue(readiness.hasNativeLibraries);
        assertFalse(readiness.sixteenKbReady);
        assertTrue(readiness.alignmentUnknown);
        assertTrue(readiness.needsSixteenKbAttention());
    }

    @Test
    public void aPackageWithNoNativeCodeIsNotAReadinessProblem() {
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Collections.emptyList(), Collections.emptyList(), false);

        assertFalse(readiness.hasNativeLibraries);
        assertTrue(readiness.sixteenKbReady);
        assertFalse(readiness.needsSixteenKbAttention());
        assertFalse(readiness.isUnknown());
    }

    @Test
    public void anUnexaminedPackageIsUnknownAndNotReady() {
        assertTrue(NativeLibReadiness.UNKNOWN.isUnknown());
        assertFalse(NativeLibReadiness.UNKNOWN.sixteenKbReady);
        assertFalse(NativeLibReadiness.UNKNOWN.hasNativeLibraries);
    }

    @Test
    public void a32BitOnlyPackageIsFlaggedAndAMixedOneIsNot() throws Exception {
        NativeLibReadiness mixed = NativeLibReadiness.from(
                Arrays.asList(aligned64("lib/arm64-v8a/libfoo.so"),
                        lib("lib/armeabi-v7a/libfoo.so", elf32(PAGE_16_KB))),
                Arrays.asList("arm64-v8a", "armeabi-v7a"), false);
        assertFalse(mixed.thirtyTwoBitOnly);

        NativeLibReadiness only32 = NativeLibReadiness.from(
                Collections.singletonList(lib("lib/armeabi-v7a/libfoo.so", elf32(PAGE_16_KB))),
                Collections.singletonList("armeabi-v7a"), false);
        assertTrue(only32.thirtyTwoBitOnly);
    }

    @Test
    public void compressedLibrariesAreReportedSeparatelyFromAlignment() throws Exception {
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Collections.singletonList(aligned64("lib/arm64-v8a/libfoo.so")),
                Collections.singletonList("arm64-v8a"), true);

        assertTrue("compression is a size problem, not a compatibility one", readiness.sixteenKbReady);
        assertTrue(readiness.compressed);
    }

    @Test
    public void blankAbiNamesAreIgnored() throws Exception {
        NativeLibReadiness readiness = NativeLibReadiness.from(
                Collections.singletonList(aligned64("lib/arm64-v8a/libfoo.so")),
                Arrays.asList("arm64-v8a", "", "   ", null), false);
        assertEquals(Collections.singleton("arm64-v8a"), readiness.abis);
    }

    @Test
    public void chipsDescribeExactlyWhatWasEstablished() throws Exception {
        List<Integer> unknown = NativeLibReadiness.UNKNOWN.getChipLabelResources();
        assertEquals(Collections.singletonList(R.string.native_lib_readiness_unknown), unknown);

        List<Integer> none = NativeLibReadiness.from(Collections.emptyList(), null, false)
                .getChipLabelResources();
        assertEquals(Collections.singletonList(R.string.native_lib_readiness_none), none);

        List<Integer> ready = NativeLibReadiness.from(
                        Collections.singletonList(aligned64("lib/arm64-v8a/libfoo.so")), null, false)
                .getChipLabelResources();
        assertEquals(Collections.singletonList(R.string.native_lib_readiness_ready), ready);

        List<Integer> worst = NativeLibReadiness.from(
                        Collections.singletonList(lib("lib/armeabi-v7a/libfoo.so", elf32(PAGE_4_KB))),
                        null, true)
                .getChipLabelResources();
        assertEquals(Arrays.asList(R.string.native_lib_readiness_not_ready,
                R.string.native_lib_readiness_32bit_only,
                R.string.native_lib_readiness_compressed), worst);
    }

    /** 32-bit little-endian ELF with one PT_LOAD segment. */
    @NonNull
    private static byte[] elf32(long alignment) {
        int phOffset = 52;
        int phEntrySize = 32;
        byte[] bytes = new byte[phOffset + phEntrySize];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{0x7f, 'E', 'L', 'F'});
        buffer.put((byte) 1);   // EI_CLASS = 32-bit
        buffer.put((byte) 1);   // EI_DATA = little endian
        buffer.put((byte) 1);   // EI_VERSION
        buffer.position(16);
        buffer.putShort((short) 3);  // e_type = ET_DYN
        buffer.putShort((short) 40); // e_machine = ARM
        buffer.putInt(1);            // e_version
        buffer.putInt(0);            // e_entry
        buffer.putInt(phOffset);     // e_phoff
        buffer.putInt(0);            // e_shoff
        buffer.putInt(0);            // e_flags
        buffer.putShort((short) 52); // e_ehsize
        buffer.putShort((short) phEntrySize); // e_phentsize
        buffer.putShort((short) 1);  // e_phnum
        buffer.putShort((short) 0);  // e_shentsize
        buffer.putShort((short) 0);  // e_shnum
        buffer.putShort((short) 0);  // e_shstrndx
        buffer.position(phOffset);
        buffer.putInt(1);            // p_type = PT_LOAD
        buffer.putInt(0);            // p_offset
        buffer.putInt(0);            // p_vaddr
        buffer.putInt(0);            // p_paddr
        buffer.putInt(0x1000);       // p_filesz
        buffer.putInt(0x1000);       // p_memsz
        buffer.putInt(5);            // p_flags
        buffer.putInt((int) alignment); // p_align
        return bytes;
    }
}
