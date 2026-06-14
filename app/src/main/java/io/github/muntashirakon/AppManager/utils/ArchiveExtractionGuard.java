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
 * Two independent limits are enforced while extracting:
 * <ul>
 *     <li><b>Total uncompressed bytes</b> &mdash; defends against a single entry (or the
 *     whole archive) that decompresses to a disk-filling size. The ceiling is derived
 *     from the compressed input size times {@link #DEFAULT_MAX_RATIO} (so legitimate
 *     archives, whose ratio is ordinary, are never affected), with a generous
 *     {@link #DEFAULT_BYTE_FLOOR} so small-but-legitimately-dense archives still extract.
 *     When the compressed size is unknown a fixed {@link #UNKNOWN_SIZE_CEILING} is used.</li>
 *     <li><b>Entry count</b> &mdash; defends against archives with millions of tiny entries.</li>
 * </ul>
 * Limits are checked <em>before</em> each chunk is written, so the amount of data that can
 * ever reach disk is bounded by the configured ceiling plus one buffer.
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

    private long mBytesSoFar;
    private int mEntriesSoFar;

    /**
     * @param compressedSize Total size in bytes of the compressed archive input, or a
     *                       non-positive value if it cannot be determined.
     */
    public ArchiveExtractionGuard(long compressedSize) {
        this(byteCeilingFor(compressedSize), DEFAULT_MAX_ENTRIES);
    }

    public ArchiveExtractionGuard(long maxBytes, int maxEntries) {
        mMaxBytes = maxBytes;
        mMaxEntries = maxEntries;
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
    }

    /** Account for {@code count} uncompressed bytes, throwing if the ceiling is exceeded. */
    public void addBytes(long count) throws IOException {
        mBytesSoFar += count;
        if (mBytesSoFar > mMaxBytes) {
            throw new IOException("Archive bomb detected: uncompressed output exceeds "
                    + mMaxBytes + " bytes. Aborting extraction.");
        }
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

    public long getBytesExtracted() {
        return mBytesSoFar;
    }

    public int getEntriesExtracted() {
        return mEntriesSoFar;
    }
}
