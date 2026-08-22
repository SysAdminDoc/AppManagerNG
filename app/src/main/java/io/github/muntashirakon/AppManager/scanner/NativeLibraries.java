// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;
import android.text.format.Formatter;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.util.LocalizedString;

public class NativeLibraries {
    public static final String TAG = NativeLibraries.class.getSimpleName();

    private static final int ELF_MAGIC = 0x7f454c46; // 0x7f ELF
    private static final int ELF_HEADER_MAX_BYTES = 64;
    private static final int ELF_PROGRAM_HEADER_MAX_BYTES = 1024 * 1024;
    private static final int ELF_SECTION_HEADER_MAX_BYTES = 1024 * 1024;
    private static final int PT_LOAD = 1;
    private static final int SHT_SYMTAB = 2;
    private static final long PAGE_SIZE_16_KB = 16L * 1024L;
    private static final int ZIP_LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int ZIP_CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;
    private static final int ZIP_LOCAL_FILE_HEADER_SIZE = 30;
    private static final int ZIP_CENTRAL_DIRECTORY_HEADER_SIZE = 46;
    private static final int ZIP_END_OF_CENTRAL_DIRECTORY_SIZE = 22;
    private static final int ZIP_MAX_COMMENT_LENGTH = 65_535;
    @VisibleForTesting
    static final int MAX_APK_SCAN_ENTRIES = 50_000;

    public static abstract class NativeLib implements LocalizedString {
        @NonNull
        private final String mPath;
        @NonNull
        private final String mName;
        private final long mSize;
        private final byte[] mMagic;
        private long mZipDataOffset = -1;
        private boolean mZipCompressionKnown;
        private boolean mZipStored;

        protected NativeLib(@NonNull String path, long size, byte[] magic) {
            mPath = path;
            mName = new File(path).getName();
            mSize = size;
            mMagic = magic;
        }

        @NonNull
        public String getPath() {
            return mPath;
        }

        @NonNull
        public String getName() {
            return mName;
        }

        public long getSize() {
            return mSize;
        }

        public byte[] getMagic() {
            return mMagic;
        }

        public long getZipDataOffset() {
            return mZipDataOffset;
        }

        public boolean hasKnownZipAlignment() {
            return mZipDataOffset >= 0;
        }

        public boolean has16KbZipAlignment() {
            return hasKnownZipAlignment() && mZipDataOffset % PAGE_SIZE_16_KB == 0;
        }

        public boolean isZipCompressionKnown() {
            return mZipCompressionKnown;
        }

        public boolean isZipStored() {
            return mZipStored;
        }

        private void setZipMetadata(boolean stored, long dataOffset) {
            mZipCompressionKnown = true;
            mZipStored = stored;
            mZipDataOffset = stored && dataOffset >= 0 ? dataOffset : -1;
        }

        @NonNull
        public static NativeLib parse(@NonNull String path, long size, @NonNull InputStream is) throws IOException {
            byte[] header = readPrefix(is, ELF_HEADER_MAX_BYTES);
            if (header.length < 4) {
                Log.w(TAG, "Invalid header size %d at path %s", header.length, path);
                return new InvalidLib(path, size, header);
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            int magic = buffer.getInt();
            if (magic != ELF_MAGIC) {
                // Invalid library
                Log.w(TAG, "Invalid header magic 0x%x at path %s", magic, path);
                return new InvalidLib(path, size, header);
            }
            if (header.length < 20) {
                Log.w(TAG, "Incomplete ELF header size %d at path %s", header.length, path);
                return new InvalidLib(path, size, header);
            }
            ElfLib elfLib = new ElfLib(path, size);
            elfLib.mArch = buffer.get(); // EI_CLASS
            elfLib.mEndianness = buffer.get(); // EI_DATA
            if (elfLib.mEndianness == ElfLib.ENDIANNESS_LITTLE_ENDIAN) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            buffer.position(16);
            elfLib.mType = buffer.getChar(); // e_type
            elfLib.mIsa = buffer.getChar(); // e_machine
            elfLib.readLoadSegmentAlignment(header, is);
            elfLib.readStaticSymbolTable(header, is);
            return elfLib;
        }

        @NonNull
        private static byte[] readPrefix(@NonNull InputStream is, int length) throws IOException {
            byte[] buffer = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = is.read(buffer, offset, length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            if (offset == length) {
                return buffer;
            }
            byte[] compact = new byte[offset];
            System.arraycopy(buffer, 0, compact, 0, offset);
            return compact;
        }

        @NonNull
        private static byte[] readUntil(@NonNull byte[] prefix, @NonNull InputStream is, int length)
                throws IOException {
            if (length <= prefix.length) {
                return prefix;
            }
            byte[] buffer = new byte[length];
            System.arraycopy(prefix, 0, buffer, 0, prefix.length);
            int offset = prefix.length;
            while (offset < length) {
                int read = is.read(buffer, offset, length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            if (offset == length) {
                return buffer;
            }
            byte[] compact = new byte[offset];
            System.arraycopy(buffer, 0, compact, 0, offset);
            return compact;
        }
    }

    public static class InvalidLib extends NativeLib {
        protected InvalidLib(@NonNull String path, long size, byte[] magic) {
            super(path, size, magic);
        }

        @NonNull
        @Override
        public CharSequence toLocalizedString(@NonNull Context context) {
            StringBuilder sb = new StringBuilder();
            if (getSize() != -1) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ");
            }
            sb.append("Magic")
                    .append(LangUtils.getSeparatorString())
                    .append(HexEncoding.encodeToString(getMagic()))
                    .append("\n")
                    .append(getPath());
            return sb;
        }

        @NonNull
        @Override
        public String toString() {
            return "InvalidLib{" +
                    "mPath='" + getPath() + '\'' +
                    ", mName='" + getName() + '\'' +
                    '}';
        }
    }

    public static class ElfLib extends NativeLib {
        public static final int ARCH_NONE = 0; // ELFCLASSNONE
        public static final int ARCH_32BIT = 1; // ELFCLASS32
        public static final int ARCH_64BIT = 2; // ELFCLASS64

        @IntDef({ARCH_NONE, ARCH_32BIT, ARCH_64BIT})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Arch {
        }

        public static final int ENDIANNESS_NONE = 0; // ELFDATANONE
        public static final int ENDIANNESS_LITTLE_ENDIAN = 1; // ELFDATA2LSB
        public static final int ENDIANNESS_BIG_ENDIAN = 2; // ELFDATA2MSB

        @IntDef({ENDIANNESS_NONE, ENDIANNESS_LITTLE_ENDIAN, ENDIANNESS_BIG_ENDIAN})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Endianness {
        }

        public static final int TYPE_NONE = 0;
        public static final int TYPE_REL = 1;
        public static final int TYPE_EXEC = 2;
        public static final int TYPE_DYN = 3;
        public static final int TYPE_CORE = 4;

        @IntDef({TYPE_NONE, TYPE_REL, TYPE_EXEC, TYPE_DYN, TYPE_CORE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
        }

        @Arch
        private int mArch;
        @Endianness
        private int mEndianness;
        @Type
        private int mType;
        private int mIsa;
        private long mMinLoadSegmentAlignment = -1;
        private Boolean mHasStaticSymbolTable;
        private long mStreamPosition;

        private ElfLib(@NonNull String path, long size) {
            super(path, size, new byte[]{0x7f, 0x45, 0x4c, 0x46});
            mStreamPosition = ELF_HEADER_MAX_BYTES;
        }

        @Arch
        public int getArch() {
            return mArch;
        }

        @Endianness
        public int getEndianness() {
            return mEndianness;
        }

        @Type
        public int getType() {
            return mType;
        }

        public int getIsa() {
            return mIsa;
        }

        public long getMinLoadSegmentAlignment() {
            return mMinLoadSegmentAlignment;
        }

        public boolean hasKnownLoadSegmentAlignment() {
            return mMinLoadSegmentAlignment >= 0;
        }

        public boolean has16KbLoadSegmentAlignment() {
            return hasKnownLoadSegmentAlignment() && mMinLoadSegmentAlignment >= PAGE_SIZE_16_KB;
        }

        public boolean hasKnownStaticSymbolTable() {
            return mHasStaticSymbolTable != null;
        }

        public boolean hasKnownSymbolTable() {
            return hasKnownStaticSymbolTable();
        }

        public boolean hasStaticSymbolTable() {
            return Boolean.TRUE.equals(mHasStaticSymbolTable);
        }

        public boolean hasSymbolTable() {
            return hasStaticSymbolTable();
        }

        public boolean isStripped() {
            return hasKnownStaticSymbolTable() && !hasStaticSymbolTable();
        }

        public String getIsaString() {
            // https://elixir.bootlin.com/linux/latest/source/include/uapi/linux/elf-em.h
            switch (mIsa) {
                case 0:
                    return "Unknown";
                case 3:
                    return "x86";
                case 8:
                    return "MIPS";
                case 40:
                    return "ARM";
                case 62:
                    return "x86_64";
                case 92:
                    return "OpenRISC";
                case 183:
                    return "AArch64";
                case 0xF3:
                    return "RISC-V";
                default:
                    // Not available in Android, but just in case
                    return String.format("Unknown(0x%x)", mIsa);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "ElfLib{" +
                    "mPath='" + getPath() + '\'' +
                    ", mName='" + getName() + '\'' +
                    ", mArch=" + mArch +
                    ", mEndianness=" + mEndianness +
                    ", mType=" + mType +
                    ", mIsa=" + getIsaString() +
                    '}';
        }

        @NonNull
        @Override
        public CharSequence toLocalizedString(@NonNull Context context) {
            StringBuilder sb = new StringBuilder();
            if (getSize() != -1) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ");
            }
            switch (mArch) {
                case ARCH_32BIT:
                    sb.append(context.getString(R.string.binary_32_bit)).append(", ");
                    break;
                case ARCH_64BIT:
                    sb.append(context.getString(R.string.binary_64_bit)).append(", ");
                    break;
                case ARCH_NONE:
                    break;
            }
            switch (mEndianness) {
                case ENDIANNESS_BIG_ENDIAN:
                    sb.append(context.getString(R.string.endianness_big_endian)).append(", ");
                    break;
                case ENDIANNESS_LITTLE_ENDIAN:
                    sb.append(context.getString(R.string.endianness_little_endian)).append(", ");
                    break;
                case ENDIANNESS_NONE:
                    break;
            }
            switch (mType) {
                case TYPE_NONE:
                case TYPE_CORE:
                case TYPE_REL:
                    // Not available in Android
                    break;
                case TYPE_DYN:
                    sb.append(context.getString(R.string.so_type_shared_library)).append(", ");
                    break;
                case TYPE_EXEC:
                    sb.append(context.getString(R.string.so_type_executable)).append(", ");
                    break;
            }
            if (hasKnownLoadSegmentAlignment()) {
                sb.append(context.getString(has16KbLoadSegmentAlignment()
                        ? R.string.native_lib_16kb_aligned
                        : R.string.native_lib_16kb_not_aligned)).append(", ");
            } else {
                sb.append(context.getString(R.string.native_lib_16kb_alignment_unknown)).append(", ");
            }
            if (hasKnownStaticSymbolTable()) {
                sb.append(context.getString(isStripped()
                        ? R.string.native_lib_symbols_stripped
                        : R.string.native_lib_symbols_present)).append(", ");
            } else {
                sb.append(context.getString(R.string.native_lib_symbols_unknown)).append(", ");
            }
            if (hasKnownZipAlignment()) {
                sb.append(context.getString(has16KbZipAlignment()
                        ? R.string.native_lib_zip_aligned
                        : R.string.native_lib_zip_not_aligned, getZipDataOffset())).append(", ");
            } else if (isZipCompressionKnown() && !isZipStored()) {
                sb.append(context.getString(R.string.native_lib_zip_alignment_compressed)).append(", ");
            } else {
                sb.append(context.getString(R.string.native_lib_zip_alignment_unknown)).append(", ");
            }
            sb.append(getIsaString()).append("\n").append(getPath());
            return sb;
        }

        private void readStaticSymbolTable(@NonNull byte[] header, @NonNull InputStream is) throws IOException {
            if (mArch != ARCH_32BIT && mArch != ARCH_64BIT) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            if (mEndianness == ENDIANNESS_LITTLE_ENDIAN) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            long sectionHeaderOffset;
            int sectionHeaderEntrySize;
            int sectionHeaderCount;
            if (mArch == ARCH_32BIT) {
                if (header.length < 52) {
                    return;
                }
                sectionHeaderOffset = Integer.toUnsignedLong(buffer.getInt(32));
                sectionHeaderEntrySize = Short.toUnsignedInt(buffer.getShort(46));
                sectionHeaderCount = Short.toUnsignedInt(buffer.getShort(48));
            } else {
                if (header.length < 64) {
                    return;
                }
                sectionHeaderOffset = buffer.getLong(40);
                sectionHeaderEntrySize = Short.toUnsignedInt(buffer.getShort(58));
                sectionHeaderCount = Short.toUnsignedInt(buffer.getShort(60));
            }
            if (sectionHeaderOffset == 0 && sectionHeaderCount == 0) {
                mHasStaticSymbolTable = false;
                return;
            }
            if (sectionHeaderOffset < 0 || sectionHeaderEntrySize <= 0 || sectionHeaderCount <= 0) {
                return;
            }
            int minimumEntrySize = mArch == ARCH_32BIT ? 40 : 64;
            if (sectionHeaderEntrySize < minimumEntrySize) {
                return;
            }
            long bytesNeeded = sectionHeaderOffset + (long) sectionHeaderEntrySize * sectionHeaderCount;
            if (bytesNeeded <= 0 || bytesNeeded > ELF_SECTION_HEADER_MAX_BYTES
                    || bytesNeeded > Integer.MAX_VALUE) {
                return;
            }
            byte[] sectionHeaders = readSectionHeaders(header, is, sectionHeaderOffset,
                    (int) (bytesNeeded - sectionHeaderOffset));
            if (sectionHeaders == null || sectionHeaders.length < bytesNeeded - sectionHeaderOffset) {
                return;
            }
            ByteBuffer sectionBuffer = ByteBuffer.wrap(sectionHeaders);
            if (mEndianness == ENDIANNESS_LITTLE_ENDIAN) {
                sectionBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            boolean hasStaticSymbolTable = false;
            for (int i = 0; i < sectionHeaderCount; ++i) {
                int offset = i * sectionHeaderEntrySize;
                if (offset < 0 || offset + sectionHeaderEntrySize > sectionHeaders.length) {
                    return;
                }
                if (sectionBuffer.getInt(offset + 4) == SHT_SYMTAB) {
                    hasStaticSymbolTable = true;
                    break;
                }
            }
            mHasStaticSymbolTable = hasStaticSymbolTable;
        }

        @Nullable
        private byte[] readSectionHeaders(@NonNull byte[] header, @NonNull InputStream is,
                                           long offset, int length) throws IOException {
            if (offset < 0 || length <= 0 || offset > Integer.MAX_VALUE - length) {
                return null;
            }
            if (offset + length <= header.length) {
                return Arrays.copyOfRange(header, (int) offset, (int) offset + length);
            }
            if (offset < mStreamPosition) {
                return null;
            }
            if (offset > mStreamPosition) {
                long remaining = offset - mStreamPosition;
                while (remaining > 0) {
                    long skipped = is.skip(remaining);
                    if (skipped > 0) {
                        remaining -= skipped;
                        continue;
                    }
                    if (is.read() == -1) {
                        return null;
                    }
                    --remaining;
                }
                mStreamPosition = offset;
            }
            byte[] sectionHeaders = new byte[length];
            int position = 0;
            while (position < length) {
                int read = is.read(sectionHeaders, position, length - position);
                if (read == -1) {
                    return Arrays.copyOf(sectionHeaders, position);
                }
                position += read;
            }
            mStreamPosition += length;
            return sectionHeaders;
        }

        private void readLoadSegmentAlignment(@NonNull byte[] header, @NonNull InputStream is) throws IOException {
            if (mArch != ARCH_32BIT && mArch != ARCH_64BIT) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            if (mEndianness == ENDIANNESS_LITTLE_ENDIAN) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            long programHeaderOffset;
            int programHeaderEntrySize;
            int programHeaderCount;
            if (mArch == ARCH_32BIT) {
                if (header.length < 46) {
                    return;
                }
                programHeaderOffset = Integer.toUnsignedLong(buffer.getInt(28));
                programHeaderEntrySize = Short.toUnsignedInt(buffer.getShort(42));
                programHeaderCount = Short.toUnsignedInt(buffer.getShort(44));
            } else {
                if (header.length < 58) {
                    return;
                }
                programHeaderOffset = buffer.getLong(32);
                programHeaderEntrySize = Short.toUnsignedInt(buffer.getShort(54));
                programHeaderCount = Short.toUnsignedInt(buffer.getShort(56));
            }
            if (programHeaderOffset < 0 || programHeaderEntrySize <= 0 || programHeaderCount <= 0) {
                return;
            }
            long bytesNeeded = programHeaderOffset + (long) programHeaderEntrySize * programHeaderCount;
            if (bytesNeeded <= 0 || bytesNeeded > ELF_PROGRAM_HEADER_MAX_BYTES || bytesNeeded > Integer.MAX_VALUE) {
                return;
            }
            byte[] programHeaders = NativeLib.readUntil(header, is, (int) bytesNeeded);
            if (programHeaders.length < bytesNeeded) {
                return;
            }
            if (bytesNeeded > mStreamPosition) {
                mStreamPosition = bytesNeeded;
            }
            ByteBuffer phBuffer = ByteBuffer.wrap(programHeaders);
            if (mEndianness == ENDIANNESS_LITTLE_ENDIAN) {
                phBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            long minLoadSegmentAlignment = Long.MAX_VALUE;
            for (int i = 0; i < programHeaderCount; ++i) {
                int offset = (int) programHeaderOffset + i * programHeaderEntrySize;
                if (offset < 0 || offset + programHeaderEntrySize > programHeaders.length) {
                    break;
                }
                int segmentType = phBuffer.getInt(offset);
                if (segmentType != PT_LOAD) {
                    continue;
                }
                if (mArch == ARCH_32BIT && offset + 32 > programHeaders.length) {
                    break;
                }
                if (mArch == ARCH_64BIT && offset + 56 > programHeaders.length) {
                    break;
                }
                long alignment = mArch == ARCH_32BIT
                        ? Integer.toUnsignedLong(phBuffer.getInt(offset + 28))
                        : phBuffer.getLong(offset + 48);
                minLoadSegmentAlignment = Math.min(minLoadSegmentAlignment, alignment);
            }
            if (minLoadSegmentAlignment != Long.MAX_VALUE) {
                mMinLoadSegmentAlignment = minLoadSegmentAlignment;
            }
        }
    }

    private final List<NativeLib> mLibs = new ArrayList<>();
    private final Set<String> mUniqueLibs = new HashSet<>();

    @WorkerThread
    public NativeLibraries(@NonNull File apkFile) throws IOException {
        Map<String, Long> zipDataOffsets = readZipDataOffsets(apkFile);
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            int entryCount = 0;
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                enforceEntryLimit(++entryCount);
                if (zipEntry.getName().endsWith(".so")) {
                    try (InputStream is = zipFile.getInputStream(zipEntry)) {
                        NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), is);
                        nativeLib.setZipMetadata(zipEntry.getMethod() == ZipEntry.STORED,
                                zipDataOffsets.getOrDefault(zipEntry.getName(), -1L));
                        mLibs.add(nativeLib);
                        mUniqueLibs.add(nativeLib.getName());
                    } catch (IOException e) {
                        Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                    }
                }
            }
        }
    }

    @WorkerThread
    public NativeLibraries(@NonNull InputStream apkInputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
            ZipEntry zipEntry;
            int entryCount = 0;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                enforceEntryLimit(++entryCount);
                if (zipEntry.getName().endsWith(".so")) {
                    try {
                        NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), zipInputStream);
                        mLibs.add(nativeLib);
                        mUniqueLibs.add(nativeLib.getName());
                    } catch (IOException e) {
                        Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                    }
                }
            }
        }
    }

    @AnyThread
    public NativeLibraries(@NonNull ZipFile zipFile) throws IOException {
        String zipPath = zipFile.getName();
        Map<String, Long> zipDataOffsets = zipPath == null
                ? new HashMap<>()
                : readZipDataOffsets(new File(zipPath));
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        int entryCount = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            enforceEntryLimit(++entryCount);
            if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".so")) {
                try (InputStream is = zipFile.getInputStream(zipEntry)) {
                    NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), is);
                    nativeLib.setZipMetadata(zipEntry.getMethod() == ZipEntry.STORED,
                            zipDataOffsets.getOrDefault(zipEntry.getName(), -1L));
                    mLibs.add(nativeLib);
                    mUniqueLibs.add(nativeLib.getName());
                } catch (IOException e) {
                    Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                }
            }
        }
    }

    @NonNull
    public List<NativeLib> getLibs() {
        return mLibs;
    }

    @NonNull
    public Collection<String> getUniqueLibs() {
        return mUniqueLibs;
    }

    @NonNull
    private static Map<String, Long> readZipDataOffsets(@NonNull File apkFile) {
        Map<String, Long> offsets = new HashMap<>();
        try (RandomAccessFile zip = new RandomAccessFile(apkFile, "r")) {
            long fileLength = zip.length();
            int tailLength = (int) Math.min(fileLength,
                    ZIP_END_OF_CENTRAL_DIRECTORY_SIZE + ZIP_MAX_COMMENT_LENGTH);
            if (tailLength < ZIP_END_OF_CENTRAL_DIRECTORY_SIZE) {
                return offsets;
            }
            byte[] tail = new byte[tailLength];
            zip.seek(fileLength - tailLength);
            zip.readFully(tail);
            int endOfCentralDirectory = findLastSignature(tail, ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE);
            if (endOfCentralDirectory < 0 || endOfCentralDirectory + ZIP_END_OF_CENTRAL_DIRECTORY_SIZE > tail.length) {
                return offsets;
            }
            int entries = readUnsignedShort(tail, endOfCentralDirectory + 10);
            long centralDirectorySize = readUnsignedInt(tail, endOfCentralDirectory + 12);
            long centralDirectoryOffset = readUnsignedInt(tail, endOfCentralDirectory + 16);
            if (entries == 0xffff || centralDirectorySize == 0xffff_ffffL
                    || centralDirectoryOffset == 0xffff_ffffL
                    || centralDirectoryOffset < 0
                    || centralDirectorySize > fileLength - centralDirectoryOffset) {
                return offsets;
            }
            zip.seek(centralDirectoryOffset);
            for (int i = 0; i < entries; ++i) {
                byte[] centralHeader = new byte[ZIP_CENTRAL_DIRECTORY_HEADER_SIZE];
                zip.readFully(centralHeader);
                if (readInt(centralHeader, 0) != ZIP_CENTRAL_DIRECTORY_SIGNATURE) {
                    return offsets;
                }
                int nameLength = readUnsignedShort(centralHeader, 28);
                int extraLength = readUnsignedShort(centralHeader, 30);
                int commentLength = readUnsignedShort(centralHeader, 32);
                byte[] nameBytes = new byte[nameLength];
                zip.readFully(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                zip.seek(zip.getFilePointer() + extraLength + commentLength);
                long localHeaderOffset = readUnsignedInt(centralHeader, 42);
                if (localHeaderOffset < 0 || localHeaderOffset > fileLength - ZIP_LOCAL_FILE_HEADER_SIZE) {
                    continue;
                }
                long savedPosition = zip.getFilePointer();
                zip.seek(localHeaderOffset);
                byte[] localHeader = new byte[ZIP_LOCAL_FILE_HEADER_SIZE];
                zip.readFully(localHeader);
                if (readInt(localHeader, 0) == ZIP_LOCAL_FILE_HEADER_SIGNATURE) {
                    int localNameLength = readUnsignedShort(localHeader, 26);
                    int localExtraLength = readUnsignedShort(localHeader, 28);
                    long dataOffset = localHeaderOffset + ZIP_LOCAL_FILE_HEADER_SIZE
                            + localNameLength + localExtraLength;
                    if (dataOffset >= 0 && dataOffset <= fileLength) {
                        offsets.put(name, dataOffset);
                    }
                }
                zip.seek(savedPosition);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read ZIP native-library offsets.", e);
        }
        return offsets;
    }

    private static int findLastSignature(@NonNull byte[] bytes, int signature) {
        for (int i = bytes.length - 4; i >= 0; --i) {
            if (readInt(bytes, i) == signature) {
                return i;
            }
        }
        return -1;
    }

    private static int readInt(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int readUnsignedShort(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long readUnsignedInt(@NonNull byte[] bytes, int offset) {
        return Integer.toUnsignedLong(readInt(bytes, offset));
    }

    private static void enforceEntryLimit(int entryCount) throws IOException {
        if (entryCount > MAX_APK_SCAN_ENTRIES) {
            throw new IOException("APK has too many entries to scan native libraries.");
        }
    }
}
