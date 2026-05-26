// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summarises {@code /proc/&lt;pid&gt;/maps} entries into a tiny set of
 * memory-region buckets the App Details memory panel can render.
 *
 * <p>{@code /proc/&lt;pid&gt;/maps} is unprivileged-readable for the calling
 * process's own children and root-readable for everyone else, which makes
 * it the natural fallback when {@code dumpsys meminfo} truncates. Each
 * line is shaped like:
 *
 * <pre>
 *   START-END PERMS OFFSET DEV INODE   PATHNAME
 *   12c00000-13000000 rw-p 00000000 00:00 0      [anon:dalvik-main space (region space)]
 *   720dab2000-720dab4000 r--p 00002000 fc:00 12345 /system/lib64/libc.so
 * </pre>
 *
 * <p>The summary buckets virtual size (in bytes) by region category:
 * <ul>
 *   <li>{@code dalvikHeap} - anonymous regions whose pathname starts with
 *       {@code [anon:dalvik-} (main space, large object space, etc.).</li>
 *   <li>{@code nativeHeap} - anonymous regions whose pathname starts with
 *       {@code [anon:libc_malloc]} or {@code [anon:scudo:}.</li>
 *   <li>{@code stack} - regions whose pathname is {@code [stack]} or starts
 *       with {@code [stack:}.</li>
 *   <li>{@code code} - file-backed read+exec regions ({@code .so} files,
 *       APK base.apk segments, the linker, etc.). Identified by
 *       {@code r-x} or {@code r-xp} permissions and a file path.</li>
 *   <li>{@code library} - file-backed read-only or read-write regions
 *       backing {@code .so} files outside of the executable mapping.</li>
 *   <li>{@code otherAnon} - anonymous regions not in any of the above
 *       categories (graphics buffers, JIT, etc.).</li>
 *   <li>{@code otherFile} - any other file-backed region.</li>
 * </ul>
 *
 * <p>The summary is intentionally lossy: the goal is to give a UI three or
 * four headline numbers, not to reproduce {@code dumpsys meminfo}. A
 * full per-pathname breakdown would belong in a follow-up; this is the
 * data-layer slice that lets the inspector run without root for the
 * current process and serve as a fallback elsewhere.
 */
public final class ProcMapsSummary {

    public static final class Summary {
        public final long dalvikHeapBytes;
        public final long nativeHeapBytes;
        public final long stackBytes;
        public final long codeBytes;
        public final long libraryBytes;
        public final long otherAnonBytes;
        public final long otherFileBytes;
        public final long totalBytes;
        public final int regions;
        public final int unparsedRegions;

        Summary(long dalvikHeapBytes, long nativeHeapBytes, long stackBytes,
                long codeBytes, long libraryBytes, long otherAnonBytes,
                long otherFileBytes, long totalBytes, int regions, int unparsedRegions) {
            this.dalvikHeapBytes = dalvikHeapBytes;
            this.nativeHeapBytes = nativeHeapBytes;
            this.stackBytes = stackBytes;
            this.codeBytes = codeBytes;
            this.libraryBytes = libraryBytes;
            this.otherAnonBytes = otherAnonBytes;
            this.otherFileBytes = otherFileBytes;
            this.totalBytes = totalBytes;
            this.regions = regions;
            this.unparsedRegions = unparsedRegions;
        }

        /** Convenience accessor used by the test fixtures and (eventually) the UI. */
        @NonNull
        public Map<String, Long> asLinkedMap() {
            Map<String, Long> map = new LinkedHashMap<>();
            map.put("dalvikHeap", dalvikHeapBytes);
            map.put("nativeHeap", nativeHeapBytes);
            map.put("stack", stackBytes);
            map.put("code", codeBytes);
            map.put("library", libraryBytes);
            map.put("otherAnon", otherAnonBytes);
            map.put("otherFile", otherFileBytes);
            return map;
        }
    }

    private ProcMapsSummary() {
    }

    /**
     * Summarise a {@code /proc/&lt;pid&gt;/maps} dump. Returns {@code null}
     * if the input contains no parseable region line, so callers can
     * distinguish a junk capture from a process with literally no
     * mappings (impossible in practice).
     */
    @Nullable
    public static Summary parse(@NonNull String content) {
        long dalvik = 0L;
        long nativeHeap = 0L;
        long stack = 0L;
        long code = 0L;
        long library = 0L;
        long otherAnon = 0L;
        long otherFile = 0L;
        long total = 0L;
        int regions = 0;
        int unparsed = 0;
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            // Strip a leading carriage-return that survives Windows clipboard captures.
            if (line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
                if (line.isEmpty()) continue;
            }
            long size = parseRegionSize(line);
            if (size < 0L) {
                ++unparsed;
                continue;
            }
            ++regions;
            total += size;
            String perms = extractPerms(line);
            String pathname = extractPathname(line);
            if (isAnonymous(pathname)) {
                if (isDalvik(pathname)) {
                    dalvik += size;
                } else if (isNativeHeap(pathname)) {
                    nativeHeap += size;
                } else if (isStack(pathname)) {
                    stack += size;
                } else {
                    otherAnon += size;
                }
            } else if (pathname != null) {
                if (isExecutable(perms)) {
                    code += size;
                } else if (pathname.endsWith(".so") || pathname.contains(".so@")) {
                    library += size;
                } else {
                    otherFile += size;
                }
            } else {
                // No pathname but permissions parsed cleanly - treat as anon.
                otherAnon += size;
            }
        }
        if (regions == 0) return null;
        return new Summary(dalvik, nativeHeap, stack, code, library,
                otherAnon, otherFile, total, regions, unparsed);
    }

    /**
     * Returns the byte size of the address range on the line, or {@code -1}
     * if the line does not parse as a /proc/maps entry.
     */
    static long parseRegionSize(@NonNull String line) {
        // Address range up to the first space.
        int dashIdx = line.indexOf('-');
        if (dashIdx <= 0) return -1L;
        int spaceIdx = line.indexOf(' ', dashIdx + 1);
        if (spaceIdx <= dashIdx + 1) return -1L;
        try {
            long start = Long.parseUnsignedLong(line.substring(0, dashIdx), 16);
            long end = Long.parseUnsignedLong(line.substring(dashIdx + 1, spaceIdx), 16);
            if (end < start) return -1L;
            return end - start;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Nullable
    static String extractPerms(@NonNull String line) {
        int dashIdx = line.indexOf('-');
        if (dashIdx <= 0) return null;
        int permsStart = line.indexOf(' ', dashIdx + 1);
        if (permsStart < 0) return null;
        int permsEnd = line.indexOf(' ', permsStart + 1);
        if (permsEnd < 0 || permsEnd - permsStart != 5) return null;
        return line.substring(permsStart + 1, permsEnd);
    }

    /**
     * Returns the pathname column (column 6) of a /proc/maps line, or
     * {@code null} for an anonymous region with no pathname. The path
     * keeps brackets ({@code [stack]}, {@code [anon:dalvik-...]}).
     */
    @Nullable
    static String extractPathname(@NonNull String line) {
        // /proc/maps columns are whitespace-separated; the pathname (col 6)
        // can contain spaces (e.g. "[anon:dalvik-main space]") so we walk the
        // first five whitespace-delimited columns and take the rest as the
        // pathname.
        int idx = 0;
        int len = line.length();
        for (int col = 0; col < 5; ++col) {
            while (idx < len && line.charAt(idx) != ' ') ++idx;
            while (idx < len && line.charAt(idx) == ' ') ++idx;
            if (idx >= len) return null;
        }
        String rest = line.substring(idx).trim();
        return rest.isEmpty() ? null : rest;
    }

    private static boolean isAnonymous(@Nullable String pathname) {
        return pathname != null
                && (pathname.startsWith("[anon:") || pathname.equals("[heap]")
                || pathname.equals("[stack]") || pathname.startsWith("[stack:"));
    }

    private static boolean isDalvik(@Nullable String pathname) {
        return pathname != null && pathname.startsWith("[anon:dalvik-");
    }

    private static boolean isNativeHeap(@Nullable String pathname) {
        return pathname != null
                && (pathname.startsWith("[anon:libc_malloc")
                || pathname.startsWith("[anon:scudo:")
                || pathname.equals("[heap]"));
    }

    private static boolean isStack(@Nullable String pathname) {
        return pathname != null
                && (pathname.equals("[stack]") || pathname.startsWith("[stack:"));
    }

    private static boolean isExecutable(@Nullable String perms) {
        return perms != null && perms.length() >= 3 && perms.charAt(2) == 'x';
    }
}
