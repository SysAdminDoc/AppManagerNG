// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code /proc/&lt;pid&gt;/status} for the T20-C memory allocations
 * inspector data layer.
 *
 * <p>Unlike {@code dumpsys meminfo}, {@code /proc/&lt;pid&gt;/status} is
 * available without root for the calling process's own children. For other
 * apps it remains privileged-readable, so the App Details memory panel
 * can fall back to this stream when {@code dumpsys} output is truncated
 * by {@code system_server}.
 *
 * <p>A representative status block looks like this:
 *
 * <pre>
 *   Name:   com.android.chrome
 *   Tgid:   12345
 *   Pid:    12345
 *   VmPeak:    987654 kB
 *   VmSize:    900000 kB
 *   VmRSS:     250000 kB
 *   RssAnon:   200000 kB
 *   RssFile:    45000 kB
 *   RssShmem:    5000 kB
 *   VmData:    600000 kB
 *   VmStk:        132 kB
 *   VmExe:          4 kB
 *   VmLib:     150000 kB
 *   VmPTE:        500 kB
 *   VmSwap:      1024 kB
 *   Threads:       42
 * </pre>
 *
 * <p>Every field is reported in <b>kilobytes</b> as a {@code long}. Missing
 * fields are returned as {@code -1} so callers can show "n/a" rather than
 * fabricated zeros. Unknown but well-formed rows are silently ignored - the
 * /proc layout adds new rows across kernel versions and we do not want a
 * single unknown row (e.g. {@code Mem*}, {@code CoreDumping}) to discard
 * the whole snapshot.
 *
 * <p>The parser is JVM-only and uses no Android APIs.
 */
public final class ProcStatusParser {

    /** Rows look like {@code Label:<whitespace>value[ kB]}. */
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.+?)\\s*$");

    /** Pull a leading positive integer out of a value like {@code "123456 kB"}. */
    private static final Pattern LEADING_KB = Pattern.compile(
            "^\\s*(\\d+)(?:\\s*kB)?\\s*$");

    public static final long UNKNOWN = -1L;
    public static final int UNKNOWN_INT = -1;

    public static final class Snapshot {
        @Nullable public final String name;
        public final int pid;
        public final int tgid;
        public final int ppid;
        public final int threads;
        public final long vmPeakKb;
        public final long vmSizeKb;
        public final long vmHwmKb;        // High-water RSS
        public final long vmRssKb;
        public final long rssAnonKb;
        public final long rssFileKb;
        public final long rssShmemKb;
        public final long vmDataKb;
        public final long vmStkKb;
        public final long vmExeKb;
        public final long vmLibKb;
        public final long vmPteKb;
        public final long vmSwapKb;

        Snapshot(@Nullable String name, int pid, int tgid, int ppid, int threads,
                 long vmPeakKb, long vmSizeKb, long vmHwmKb, long vmRssKb,
                 long rssAnonKb, long rssFileKb, long rssShmemKb,
                 long vmDataKb, long vmStkKb, long vmExeKb, long vmLibKb,
                 long vmPteKb, long vmSwapKb) {
            this.name = name;
            this.pid = pid;
            this.tgid = tgid;
            this.ppid = ppid;
            this.threads = threads;
            this.vmPeakKb = vmPeakKb;
            this.vmSizeKb = vmSizeKb;
            this.vmHwmKb = vmHwmKb;
            this.vmRssKb = vmRssKb;
            this.rssAnonKb = rssAnonKb;
            this.rssFileKb = rssFileKb;
            this.rssShmemKb = rssShmemKb;
            this.vmDataKb = vmDataKb;
            this.vmStkKb = vmStkKb;
            this.vmExeKb = vmExeKb;
            this.vmLibKb = vmLibKb;
            this.vmPteKb = vmPteKb;
            this.vmSwapKb = vmSwapKb;
        }

        public boolean hasAnyMemoryField() {
            return vmRssKb != UNKNOWN || vmSizeKb != UNKNOWN
                    || rssAnonKb != UNKNOWN || rssFileKb != UNKNOWN
                    || vmDataKb != UNKNOWN || vmSwapKb != UNKNOWN;
        }
    }

    private ProcStatusParser() {
    }

    /**
     * Parse a {@code /proc/&lt;pid&gt;/status} capture. Returns {@code null}
     * if the input does not contain a single recognised status row (so the UI
     * can detect a junk read instead of rendering an all-sentinel snapshot).
     */
    @Nullable
    public static Snapshot parse(@NonNull String content) {
        String name = null;
        int pid = UNKNOWN_INT;
        int tgid = UNKNOWN_INT;
        int ppid = UNKNOWN_INT;
        int threads = UNKNOWN_INT;
        long vmPeak = UNKNOWN;
        long vmSize = UNKNOWN;
        long vmHwm = UNKNOWN;
        long vmRss = UNKNOWN;
        long rssAnon = UNKNOWN;
        long rssFile = UNKNOWN;
        long rssShmem = UNKNOWN;
        long vmData = UNKNOWN;
        long vmStk = UNKNOWN;
        long vmExe = UNKNOWN;
        long vmLib = UNKNOWN;
        long vmPte = UNKNOWN;
        long vmSwap = UNKNOWN;
        boolean matchedAny = false;

        Matcher m = ROW_PATTERN.matcher(content);
        while (m.find()) {
            String label = m.group(1);
            String value = m.group(2);
            if (label == null || value == null) continue;
            matchedAny = true;
            switch (label) {
                case "Name":
                    name = value.isEmpty() ? null : value;
                    break;
                case "Pid":
                    pid = parseInt(value, pid);
                    break;
                case "Tgid":
                    tgid = parseInt(value, tgid);
                    break;
                case "PPid":
                    ppid = parseInt(value, ppid);
                    break;
                case "Threads":
                    threads = parseInt(value, threads);
                    break;
                case "VmPeak":
                    vmPeak = parseKb(value, vmPeak);
                    break;
                case "VmSize":
                    vmSize = parseKb(value, vmSize);
                    break;
                case "VmHWM":
                    vmHwm = parseKb(value, vmHwm);
                    break;
                case "VmRSS":
                    vmRss = parseKb(value, vmRss);
                    break;
                case "RssAnon":
                    rssAnon = parseKb(value, rssAnon);
                    break;
                case "RssFile":
                    rssFile = parseKb(value, rssFile);
                    break;
                case "RssShmem":
                    rssShmem = parseKb(value, rssShmem);
                    break;
                case "VmData":
                    vmData = parseKb(value, vmData);
                    break;
                case "VmStk":
                    vmStk = parseKb(value, vmStk);
                    break;
                case "VmExe":
                    vmExe = parseKb(value, vmExe);
                    break;
                case "VmLib":
                    vmLib = parseKb(value, vmLib);
                    break;
                case "VmPTE":
                    vmPte = parseKb(value, vmPte);
                    break;
                case "VmSwap":
                    vmSwap = parseKb(value, vmSwap);
                    break;
                default:
                    // Unknown row; ignore so kernel-version-specific additions
                    // do not invalidate the rest of the snapshot.
                    break;
            }
        }
        if (!matchedAny) return null;
        return new Snapshot(name, pid, tgid, ppid, threads,
                vmPeak, vmSize, vmHwm, vmRss,
                rssAnon, rssFile, rssShmem,
                vmData, vmStk, vmExe, vmLib, vmPte, vmSwap);
    }

    private static int parseInt(@NonNull String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseKb(@NonNull String value, long fallback) {
        Matcher m = LEADING_KB.matcher(value);
        if (!m.matches()) return fallback;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
