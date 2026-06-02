// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.hex;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.Path;

public final class HexViewerUtils {
    public static final int PAGE_SIZE = 4096;
    public static final int BYTES_PER_ROW = 16;
    public static final long NO_HIGHLIGHT = -1;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private HexViewerUtils() {
    }

    @NonNull
    public static byte[] readPage(@NonNull Path path, long offset, int pageSize) throws IOException {
        if (offset < 0) {
            throw new IOException("Offset cannot be negative.");
        }
        if (pageSize <= 0) {
            return new byte[0];
        }
        try {
            return readPageWithChannel(path, offset, pageSize);
        } catch (IOException ignored) {
            return readPageWithStream(path, offset, pageSize);
        }
    }

    @NonNull
    public static List<HexLine> buildLines(@NonNull byte[] page, long pageOffset) {
        List<HexLine> lines = new ArrayList<>((page.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW);
        for (int i = 0; i < page.length; i += BYTES_PER_ROW) {
            int byteCount = Math.min(BYTES_PER_ROW, page.length - i);
            lines.add(new HexLine(pageOffset + i, formatHex(page, i, byteCount), formatAscii(page, i, byteCount),
                    byteCount));
        }
        return lines;
    }

    public static long alignToPage(long offset) {
        if (offset <= 0) {
            return 0;
        }
        return (offset / PAGE_SIZE) * PAGE_SIZE;
    }

    public static long parseOffset(@NonNull String rawOffset, long fileSize) {
        String normalized = rawOffset.trim().replace("_", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Offset is required.");
        }
        int radix = 10;
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
            radix = 16;
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Offset is required.");
        }
        long offset;
        try {
            offset = Long.parseUnsignedLong(normalized, radix);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Offset must be decimal or 0x-prefixed hex.");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative.");
        }
        if (fileSize > 0 && offset >= fileSize) {
            return fileSize - 1;
        }
        return offset;
    }

    @NonNull
    public static byte[] parseHexPattern(@NonNull String rawPattern) {
        // Strip a "0x"/"0X" prefix only at the START of each separated token,
        // never globally: a global replace would also delete a "0x" that arises
        // where one byte token ends in '0' and the next begins with 'x'-adjacent
        // text, silently dropping bytes and turning a malformed pattern into a
        // successful-but-wrong search instead of a clean "non-hex" rejection.
        StringBuilder sb = new StringBuilder();
        for (String token : rawPattern.trim().split("[\\s:_-]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.length() >= 2 && token.charAt(0) == '0'
                    && (token.charAt(1) == 'x' || token.charAt(1) == 'X')) {
                token = token.substring(2);
            }
            sb.append(token);
        }
        String normalized = sb.toString();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Hex search value is required.");
        }
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex search value must contain complete bytes.");
        }
        byte[] pattern = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Hex search value contains non-hex characters.");
            }
            pattern[i / 2] = (byte) ((high << 4) | low);
        }
        return pattern;
    }

    public static long findInPath(@NonNull Path path, @NonNull byte[] needle, long startOffset) throws IOException {
        if (needle.length == 0) {
            throw new IOException("Search pattern cannot be empty.");
        }
        long offset = Math.max(0, startOffset);
        byte[] overlap = new byte[0];
        while (true) {
            byte[] page = readPage(path, offset, PAGE_SIZE);
            if (page.length == 0) {
                return -1;
            }
            byte[] window = concat(overlap, page);
            int index = indexOf(window, needle, 0);
            if (index >= 0) {
                return offset - overlap.length + index;
            }
            if (page.length < PAGE_SIZE) {
                return -1;
            }
            overlap = tail(window, Math.max(0, needle.length - 1));
            offset += page.length;
        }
    }

    @VisibleForTesting
    static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle, int start) {
        if (needle.length == 0) {
            return 0;
        }
        int safeStart = Math.max(0, start);
        int max = haystack.length - needle.length;
        for (int i = safeStart; i <= max; ++i) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                ++j;
            }
            if (j == needle.length) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public static String formatOffset(long offset) {
        if (offset <= 0xFFFFFFFFL) {
            return String.format(Locale.ROOT, "%08X", offset);
        }
        return String.format(Locale.ROOT, "%016X", offset);
    }

    @NonNull
    private static byte[] readPageWithChannel(@NonNull Path path, long offset, int pageSize) throws IOException {
        byte[] buffer = new byte[pageSize];
        int total = 0;
        try (FileChannel channel = path.openFileChannel(FileSystemManager.MODE_READ_ONLY)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (total < pageSize) {
                int read = channel.read(byteBuffer, offset + total);
                if (read <= 0) {
                    break;
                }
                total += read;
            }
        }
        return Arrays.copyOf(buffer, total);
    }

    @NonNull
    private static byte[] readPageWithStream(@NonNull Path path, long offset, int pageSize) throws IOException {
        byte[] buffer = new byte[pageSize];
        int total = 0;
        try (InputStream inputStream = path.openInputStream()) {
            if (!skipFully(inputStream, offset)) {
                return new byte[0];
            }
            while (total < pageSize) {
                int read = inputStream.read(buffer, total, pageSize - total);
                if (read <= 0) {
                    break;
                }
                total += read;
            }
        }
        return Arrays.copyOf(buffer, total);
    }

    private static boolean skipFully(@NonNull InputStream inputStream, long byteCount) throws IOException {
        long remaining = byteCount;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (inputStream.read() == -1) {
                return false;
            }
            --remaining;
        }
        return true;
    }

    @NonNull
    private static String formatHex(@NonNull byte[] page, int offset, int byteCount) {
        StringBuilder builder = new StringBuilder(BYTES_PER_ROW * 3);
        for (int i = 0; i < BYTES_PER_ROW; ++i) {
            if (i < byteCount) {
                int value = page[offset + i] & 0xFF;
                builder.append(HEX_DIGITS[value >>> 4]).append(HEX_DIGITS[value & 0x0F]);
            } else {
                builder.append("  ");
            }
            if (i < BYTES_PER_ROW - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String formatAscii(@NonNull byte[] page, int offset, int byteCount) {
        StringBuilder builder = new StringBuilder(BYTES_PER_ROW);
        for (int i = 0; i < byteCount; ++i) {
            int value = page[offset + i] & 0xFF;
            builder.append(value >= 0x20 && value <= 0x7E ? (char) value : '.');
        }
        return builder.toString();
    }

    @NonNull
    private static byte[] concat(@NonNull byte[] first, @NonNull byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @NonNull
    private static byte[] tail(@NonNull byte[] bytes, int byteCount) {
        if (byteCount <= 0) {
            return new byte[0];
        }
        int count = Math.min(byteCount, bytes.length);
        return Arrays.copyOfRange(bytes, bytes.length - count, bytes.length);
    }

    public static final class HexLine {
        public final long offset;
        @NonNull
        public final String hex;
        @NonNull
        public final String ascii;
        public final int byteCount;

        HexLine(long offset, @NonNull String hex, @NonNull String ascii, int byteCount) {
            this.offset = offset;
            this.hex = hex;
            this.ascii = ascii;
            this.byteCount = byteCount;
        }

        public boolean contains(long absoluteOffset) {
            return absoluteOffset >= offset && absoluteOffset < offset + byteCount;
        }
    }
}
