// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.io.IoUtils;

/**
 * Guards archive extraction against decompression bombs (zip/tar bombs).
 * <p>
 * Four independent limits are enforced while extracting:
 * <ul>
 *     <li><b>Total uncompressed bytes</b> &mdash; defends against a single entry (or the
 *     whole archive) that decompresses to a disk-filling size. The ceiling is derived
 *     from the compressed input size times {@link #DEFAULT_MAX_RATIO} (so legitimate
 *     archives, whose ratio is ordinary, are never affected), with a generous
 *     {@link #DEFAULT_BYTE_FLOOR} so small-but-legitimately-dense archives still extract.
 *     When the compressed size is unknown a fixed {@link #UNKNOWN_SIZE_CEILING} is used.</li>
 *     <li><b>Entry count</b> &mdash; defends against archives with millions of tiny entries.</li>
 *     <li><b>Single-entry bytes</b> &mdash; prevents one expanded entry from monopolizing the
 *     total budget.</li>
 *     <li><b>Temporary bytes</b> &mdash; bounds expanded data retained concurrently on disk.</li>
 * </ul>
 * Limits are checked <em>before</em> each chunk is written, so the amount of data that can
 * ever reach disk is bounded by the configured ceiling.
 */
public final class ArchiveExtractionGuard {
    /** Uncompressed-to-compressed ratio above which extraction is treated as a bomb. */
    public static final long DEFAULT_MAX_RATIO = 200L;
    /** Minimum allowed uncompressed output regardless of how small the archive is (256 MiB). */
    public static final long DEFAULT_BYTE_FLOOR = 256L * 1024 * 1024;
    /** Uncompressed ceiling used when the compressed input size is unknown (32 GiB). */
    public static final long UNKNOWN_SIZE_CEILING = 32L * 1024 * 1024 * 1024;
    /** Maximum number of archive entries that may be extracted. */
    public static final int DEFAULT_MAX_ENTRIES = 1_000_000;

    private final long mMaxBytes;
    private final int mMaxEntries;
    private final long mMaxEntryBytes;
    private final long mMaxTemporaryBytes;

    private long mBytesSoFar;
    private int mEntriesSoFar;
    private long mEntryBytes;
    private long mTemporaryBytes;

    /**
     * @param compressedSize Total size in bytes of the compressed archive input, or a
     *                       non-positive value if it cannot be determined.
     */
    public ArchiveExtractionGuard(long compressedSize) {
        this(byteCeilingFor(compressedSize), DEFAULT_MAX_ENTRIES,
                byteCeilingFor(compressedSize), byteCeilingFor(compressedSize));
    }

    public ArchiveExtractionGuard(long maxBytes, int maxEntries) {
        this(maxBytes, maxEntries, maxBytes, maxBytes);
    }

    public ArchiveExtractionGuard(long maxBytes, int maxEntries, long maxEntryBytes,
                                  long maxTemporaryBytes) {
        if (maxBytes <= 0 || maxEntries <= 0 || maxEntryBytes <= 0 || maxTemporaryBytes <= 0) {
            throw new IllegalArgumentException("Archive extraction limits must be positive.");
        }
        mMaxBytes = maxBytes;
        mMaxEntries = maxEntries;
        mMaxEntryBytes = Math.min(maxEntryBytes, maxBytes);
        mMaxTemporaryBytes = Math.min(maxTemporaryBytes, maxBytes);
    }

    @NonNull
    public static ArchiveExtractionGuard forCompressedSize(long compressedSize, int maxEntries,
                                                            long maxEntryBytes,
                                                            long maxTemporaryBytes) {
        long maxBytes = byteCeilingFor(compressedSize);
        return new ArchiveExtractionGuard(maxBytes, maxEntries, maxEntryBytes, maxTemporaryBytes);
    }

    private static long byteCeilingFor(long compressedSize) {
        if (compressedSize <= 0) {
            return UNKNOWN_SIZE_CEILING;
        }
        long ratioCeiling;
        // Guard against overflow on absurdly large (but plausible) compressed sizes.
        if (compressedSize > Long.MAX_VALUE / DEFAULT_MAX_RATIO) {
            ratioCeiling = Long.MAX_VALUE;
        } else {
            ratioCeiling = compressedSize * DEFAULT_MAX_RATIO;
        }
        return Math.max(DEFAULT_BYTE_FLOOR, ratioCeiling);
    }

    /** Register a new archive entry. Call once per entry before extracting its content. */
    public void onNewEntry() throws IOException {
        if (++mEntriesSoFar > mMaxEntries) {
            throw new IOException("Archive bomb detected: more than " + mMaxEntries
                    + " entries. Aborting extraction.");
        }
        mEntryBytes = 0;
    }

    /** Reject a declared entry size before allocating an output or temporary file. */
    public void assertEntrySize(long declaredSize) throws IOException {
        if (declaredSize > mMaxEntryBytes) {
            throw new IOException("Archive bomb detected: one entry exceeds "
                    + mMaxEntryBytes + " bytes. Aborting extraction.");
        }
    }

    /** Account for {@code count} uncompressed bytes, throwing if the ceiling is exceeded. */
    public void addBytes(long count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("Byte count cannot be negative.");
        }
        if (count > mMaxBytes - mBytesSoFar) {
            throw new IOException("Archive bomb detected: uncompressed output exceeds "
                    + mMaxBytes + " bytes. Aborting extraction.");
        }
        if (count > mMaxEntryBytes - mEntryBytes) {
            throw new IOException("Archive bomb detected: one entry exceeds "
                    + mMaxEntryBytes + " bytes. Aborting extraction.");
        }
        mBytesSoFar += count;
        mEntryBytes += count;
    }

    /**
     * Copy {@code in} to {@code out}, accounting every byte against the byte ceiling.
     * The limit is checked before each write, so disk usage never exceeds the ceiling
     * by more than one buffer.
     */
    public void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            addBytes(len);
            out.write(buffer, 0, len);
        }
    }

    /**
     * Copy an expanded entry to temporary storage while enforcing both expanded-byte and
     * concurrently-reserved temporary-disk ceilings. Call {@link #releaseTemporaryBytes(long)}
     * after deleting a temporary file that will not be retained for later entries.
     */
    public void copyToTemporary(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            addBytes(len);
            reserveTemporaryBytes(len);
            out.write(buffer, 0, len);
        }
    }

    /** Drain an entry that will not be imported while still charging its expanded bytes. */
    public void drain(@NonNull InputStream in) throws IOException {
        byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            addBytes(len);
        }
    }

    public void reserveTemporaryBytes(long count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("Temporary byte count cannot be negative.");
        }
        if (count > mMaxTemporaryBytes - mTemporaryBytes) {
            throw new IOException("Archive bomb detected: temporary output exceeds "
                    + mMaxTemporaryBytes + " bytes. Aborting extraction.");
        }
        mTemporaryBytes += count;
    }

    public void releaseTemporaryBytes(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Temporary byte count cannot be negative.");
        }
        mTemporaryBytes = Math.max(0, mTemporaryBytes - count);
    }

    public long getBytesExtracted() {
        return mBytesSoFar;
    }

    public int getEntriesExtracted() {
        return mEntriesSoFar;
    }

    public long getCurrentEntryBytes() {
        return mEntryBytes;
    }

    public long getTemporaryBytes() {
        return mTemporaryBytes;
    }
}
