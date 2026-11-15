// SPDX-License-Identifier: GPL-3.0-or-later

import com.reandroid.apk.AndroidFrameworks;
import com.reandroid.apk.FrameworkApk;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.item.SpecFlag;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the framework resource data the binary XML decoder reads.
 *
 * <p>Private framework resource IDs move between API levels, and between minor SDKs of one level
 * (36 and 36.1, 37.0 to 37.2) that write the same compileSdkVersion into an APK. The decoder names
 * an ID only when every table the APK could have been built against agrees on it. This writes:
 * <ul>
 * <li>{@code android-<newest>.apk}: the newest SDK's table, optimized by ARSCLib, which the
 * decoder uses when it has no closer table (ARSCLib bundles 23 to 36 itself);</li>
 * <li>{@code disputed-ids.txt}: for each API level, the table the decoder uses for it and the IDs
 * that some table of that level names differently; plus two sets for a file that carries no
 * compile metadata. {@code any} is for attribute names in files other than manifests targeting an
 * API level before 28, where an ID the newest table exports is disputed only by a table that
 * exported it too (see {@link #disagreements}).
 * {@code any references} is for reference values, where any two tables naming an ID differently
 * dispute it.</li>
 * </ul>
 *
 * <p>Run it through {@code scripts/generate_framework_tables.py}, which finds the SDK, the ARSCLib
 * jar and a JDK. Every {@code platforms/android-<N>} from 21 up must be installed, minor SDKs
 * included, or the sets will miss IDs.
 */
public class GenerateFrameworkTables {
    private static final Pattern PLATFORM = Pattern.compile("android-(\\d+)(?:\\.(\\d+))?");
    private static final Pattern PLACEHOLDER = Pattern.compile("private_resource_pad\\d+");
    private static final String EXPORTED = "+";
    private static final int OLDEST_API = 21;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GenerateFrameworkTables <sdk platforms dir> <output dir>");
        }
        File platformsDir = new File(args[0]);
        File outDir = new File(args[1]);
        if (!outDir.mkdirs() && !outDir.isDirectory()) {
            throw new IOException("Could not create " + outDir);
        }

        // Every SDK table by version ("21", "36.1", "37.2"), oldest first
        TreeMap<Version, Map<Integer, String>> sdkTables = new TreeMap<>();
        File[] platforms = platformsDir.listFiles();
        if (platforms == null) {
            throw new IOException("Not a directory: " + platformsDir);
        }
        for (File platform : platforms) {
            Matcher m = PLATFORM.matcher(platform.getName());
            File jar = new File(platform, "android.jar");
            if (!m.matches() || !jar.isFile()) {
                continue;
            }
            Version version = new Version(Integer.parseInt(m.group(1)), m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
            if (version.major < OLDEST_API) {
                continue;
            }
            sdkTables.put(version, names(FrameworkApk.loadApkFile(jar)));
            System.out.println("read " + platform.getName());
        }
        if (sdkTables.isEmpty()) {
            throw new IOException("No SDK platforms under " + platformsDir);
        }

        // The newest table ships with the app; read it back the way the app will
        Version newest = sdkTables.lastKey();
        File newestJar = new File(platformsDir, "android-" + newest.label() + File.separator + "android.jar");
        if (!newestJar.isFile()) {
            newestJar = new File(platformsDir, "android-" + newest.major + File.separator + "android.jar");
        }
        for (File old : outDir.listFiles((dir, name) -> name.startsWith("android-") && name.endsWith(".apk"))) {
            Files.delete(old.toPath());
        }
        File newestOut = new File(outDir, "android-" + newest.label() + ".apk");
        FrameworkApk.optimize(newestJar, newestOut, null);
        Map<Integer, String> newestNames = names(FrameworkApk.loadApkFile(newestOut));
        if (!newestNames.equals(sdkTables.get(newest))) {
            throw new IllegalStateException("The optimized table names resources differently from " + newestJar);
        }

        // The tables ARSCLib bundles, as the app loads them
        TreeMap<Integer, Map<Integer, String>> bundled = new TreeMap<>();
        for (int api = OLDEST_API; api < newest.major; ++api) {
            FrameworkApk apk = AndroidFrameworks.getFrameworkManager().getBestMatch(api);
            if (apk != null && apk.getVersionCode() == api) {
                bundled.put(api, names(apk));
            }
        }
        if (bundled.isEmpty()) {
            throw new IllegalStateException("ARSCLib bundles no framework tables");
        }

        // Every table an APK of a given API level could have been compiled against
        TreeMap<Integer, List<Map<Integer, String>>> plausible = new TreeMap<>();
        for (Map.Entry<Integer, Map<Integer, String>> e : bundled.entrySet()) {
            plausible.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
        for (Map.Entry<Version, Map<Integer, String>> e : sdkTables.entrySet()) {
            List<Map<Integer, String>> tables = plausible.computeIfAbsent(e.getKey().major, k -> new ArrayList<>());
            if (!tables.contains(e.getValue())) {
                tables.add(e.getValue());
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("# Generated by scripts/generate_framework_tables.py from SDK platforms ");
        StringBuilder labels = new StringBuilder();
        for (Version v : sdkTables.keySet()) {
            labels.append(labels.length() == 0 ? "" : ", ").append(v.label());
        }
        out.append(labels).append(" and ARSCLib's bundled ").append(bundled.firstKey()).append(" to ")
                .append(bundled.lastKey()).append(". Do not edit.\n");
        out.append("newest ").append(newest.label()).append('\n');

        TreeSet<Integer> any = new TreeSet<>();
        TreeSet<Integer> anyReferences = new TreeSet<>();
        for (Map.Entry<Integer, List<Map<Integer, String>>> e : plausible.entrySet()) {
            int api = e.getKey();
            String tableName;
            Map<Integer, String> table;
            if (bundled.containsKey(api)) {
                tableName = Integer.toString(api);
                table = bundled.get(api);
            } else if (api == newest.major) {
                tableName = "newest";
                table = newestNames;
            } else if (api < bundled.firstKey()) {
                tableName = Integer.toString(bundled.firstKey());
                table = bundled.get(bundled.firstKey());
            } else {
                throw new IllegalStateException("No table to decode API " + api + " with");
            }
            // The APK names its API level, so every table of that level counts, private entries included
            TreeSet<Integer> disputed = new TreeSet<>();
            for (Map<Integer, String> other : e.getValue()) {
                disputed.addAll(disagreements(table, other, false));
            }
            out.append("api ").append(api).append(" table ").append(tableName).append('\n');
            appendRanges(out, disputed);
            for (Map<Integer, String> other : e.getValue()) {
                any.addAll(disagreements(newestNames, other, true));
                anyReferences.addAll(disagreements(newestNames, other, false));
            }
        }
        out.append("any table newest\n");
        appendRanges(out, any);
        out.append("any references\n");
        appendRanges(out, anyReferences);

        File disputedOut = new File(outDir, "disputed-ids.txt");
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(disputedOut.toPath()), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        }
        System.out.printf("wrote %s (%d bytes) and %s (%d disputed IDs without metadata)%n", newestOut.getName(),
                newestOut.length(), disputedOut.getName(), any.size());
    }

    /**
     * IDs both tables declare under different names. With {@code trustExported}, an ID {@code table}
     * exports (public) doesn't count as disputed by an {@code other} that kept it private. That
     * holds only for attribute names: aapt and aapt2 refuse an {@code android:} attribute that the
     * SDK doesn't export, and an exported name never changes in a later release, so only a
     * platform build could mean the private entry. Platform builds before API 28 carry no compile
     * metadata (the Android 5.0 framework manifest sets its private 0x01010591), so the decoder
     * uses {@code any} only for a manifest targeting API 28 or later, and {@code any references}
     * for names too below that. A reference value can name a private entry in any build
     * ({@code @*android:dimen/x}), so references are always checked without it.
     */
    private static TreeSet<Integer> disagreements(Map<Integer, String> table, Map<Integer, String> other,
                                                  boolean trustExported) {
        TreeSet<Integer> ids = new TreeSet<>();
        for (Map.Entry<Integer, String> e : other.entrySet()) {
            String mine = table.get(e.getKey());
            if (mine == null || bare(mine).equals(bare(e.getValue()))) {
                continue;
            }
            if (trustExported && exported(mine) && !exported(e.getValue())) {
                continue;
            }
            ids.add(e.getKey());
        }
        return ids;
    }

    private static boolean exported(String entry) {
        return entry.startsWith(EXPORTED);
    }

    private static String bare(String entry) {
        return exported(entry) ? entry.substring(EXPORTED.length()) : entry;
    }

    private static void appendRanges(StringBuilder out, TreeSet<Integer> ids) {
        Iterator<Integer> it = ids.iterator();
        if (!it.hasNext()) {
            return;
        }
        int start = it.next();
        int end = start;
        while (it.hasNext()) {
            int id = it.next();
            if (id == end + 1) {
                end = id;
                continue;
            }
            appendRange(out, start, end);
            start = end = id;
        }
        appendRange(out, start, end);
    }

    private static void appendRange(StringBuilder out, int start, int end) {
        out.append(String.format("%08x", start));
        if (end != start) {
            out.append('-').append(String.format("%08x", end));
        }
        out.append('\n');
    }

    /**
     * Resource ID to "type/name" for every resource the table declares, with {@link #EXPORTED} in
     * front of the public ones. API 21's {@code private_resource_padN} slots are public
     * placeholders that later releases fill with real attributes; nothing can reference them, so
     * they count as undeclared.
     */
    static Map<Integer, String> names(FrameworkApk apk) {
        PackageBlock pkg = apk.getTableBlock().getAllPackages().next();
        Map<Integer, String> out = new HashMap<>();
        Iterator<ResourceEntry> it = pkg.getResources();
        while (it.hasNext()) {
            ResourceEntry e = it.next();
            if (!e.isDeclared() || PLACEHOLDER.matcher(e.getName()).matches()) {
                continue;
            }
            Entry any = e.any();
            SpecFlag flag = any != null ? any.getSpecFlag() : null;
            boolean exported = flag != null && flag.isPublic();
            out.put(e.getResourceId(), (exported ? EXPORTED : "") + e.getType() + "/" + e.getName());
        }
        return out;
    }

    private static final class Version implements Comparable<Version> {
        final int major;
        final int minor;

        Version(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        String label() {
            return major >= 36 || minor != 0 ? major + "." + minor : Integer.toString(major);
        }

        @Override
        public int compareTo(Version o) {
            return major != o.major ? Integer.compare(major, o.major) : Integer.compare(minor, o.minor);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Version && compareTo((Version) o) == 0;
        }

        @Override
        public int hashCode() {
            return major * 31 + minor;
        }
    }
}
