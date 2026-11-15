// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.reandroid.apk.AndroidFrameworks;
import com.reandroid.apk.FrameworkApk;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.item.SpecFlag;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Picks the framework resource table a binary XML file is decoded with, and which framework IDs
 * that table may name.
 *
 * <p>Public framework IDs never change meaning, but private ones move between API levels and
 * between minor SDKs of one level, and aapt2 lets an app reference a private one
 * ({@code @*android:dimen/status_bar_height}). Decoding with the wrong table prints a different
 * resource's name. The data comes from {@code scripts/generate_framework_tables.py}: for each API
 * level, the table to use and the IDs some SDK of that level names differently, plus two sets for a
 * file that doesn't say what it was compiled against. A disputed ID is printed as hex instead.
 */
final class FrameworkTables {
    /** {@code android:compileSdkVersion}, which aapt2 writes into manifests compiled against API 28 and later. */
    static final int ATTR_COMPILE_SDK_VERSION = 0x01010572;
    /** The file doesn't say which API it was compiled against. */
    static final int UNKNOWN_API = 0;

    private static final String DIR = "/io/github/muntashirakon/AppManager/apk/parser/frameworks/";
    private static final String DATA = "disputed-ids.txt";
    private static final String NEWEST = "newest";
    // A tablet with two APKs of different API levels open at once shouldn't reload either one
    private static final int CACHED_TABLES = 2;

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    @Nullable
    private static Data sData;
    @GuardedBy("sLock")
    @Nullable
    private static PackageBlock sNewest;
    @GuardedBy("sLock")
    @Nullable
    private static IdSet sNewestPublic;
    @GuardedBy("sLock")
    private static final LinkedHashMap<Integer, PackageBlock> sTables = new LinkedHashMap<Integer, PackageBlock>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, PackageBlock> eldest) {
            return size() > CACHED_TABLES;
        }
    };

    private FrameworkTables() {
    }

    static boolean isFrameworkId(int id) {
        return (id >>> 24) == 0x01;
    }

    /**
     * The table and naming rules for a file compiled against {@code api}, or {@link #UNKNOWN_API}.
     */
    @NonNull
    static Choice forApi(int api) {
        Data data = data();
        if (api == UNKNOWN_API) {
            return new Choice(NEWEST, data.anyNames, data.anyReferences, false);
        }
        Section section = data.sections.get(api);
        if (section == null) {
            // Older than any table here, or newer than the newest: only public IDs are certain
            return new Choice(NEWEST, IdSet.EMPTY, IdSet.EMPTY, true);
        }
        return new Choice(section.table, section.disputed, section.disputed, false);
    }

    /**
     * The naming rules for a manifest that doesn't say what it was compiled against and targets an
     * API level from before aapt2 wrote that down. An app built with the public SDK names only public
     * attributes, which never change meaning, but the platform builds its own manifests against its
     * full table, and before API 28 that table gave private attributes IDs a later SDK made public:
     * the Android 5.0 framework manifest sets 0x01010591, its private primaryUserOnly, which the
     * newest table calls isSplitRequired. So every ID two SDKs name differently is printed as hex,
     * attribute names included.
     */
    @NonNull
    static Choice forOldManifestWithoutMetadata() {
        Data data = data();
        return new Choice(NEWEST, data.anyReferences, data.anyReferences, false);
    }

    /** The newest SDK's framework package, which also serves where no closer table exists. */
    @NonNull
    static PackageBlock newest() {
        synchronized (sLock) {
            if (sNewest == null) {
                String name = "android-" + data().newest + ".apk";
                try (InputStream in = open(name)) {
                    sNewest = firstPackage(FrameworkApk.loadApkBuffer(name, in), name);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read " + name, e);
                }
            }
            return sNewest;
        }
    }

    @NonNull
    private static PackageBlock table(@NonNull String name) {
        if (NEWEST.equals(name)) {
            return newest();
        }
        int api = Integer.parseInt(name);
        synchronized (sLock) {
            PackageBlock block = sTables.get(api);
            if (block == null) {
                FrameworkApk apk = AndroidFrameworks.getFrameworkManager().getBestMatch(api);
                // getBestMatch hands back the closest table, which would decode with the wrong IDs
                if (apk == null || apk.getVersionCode() != api) {
                    throw new IllegalStateException("ARSCLib has no framework table for API " + api);
                }
                block = firstPackage(apk, "API " + api);
                sTables.put(api, block);
            }
            return block;
        }
    }

    @NonNull
    private static IdSet newestPublic() {
        PackageBlock newest = newest();
        synchronized (sLock) {
            if (sNewestPublic == null) {
                List<Integer> ids = new ArrayList<>();
                Iterator<ResourceEntry> it = newest.getResources();
                while (it.hasNext()) {
                    ResourceEntry e = it.next();
                    Entry any = e.isDeclared() ? e.any() : null;
                    SpecFlag flag = any != null ? any.getSpecFlag() : null;
                    if (flag != null && flag.isPublic()) {
                        ids.add(e.getResourceId());
                    }
                }
                Collections.sort(ids);
                sNewestPublic = IdSet.of(ids);
            }
            return sNewestPublic;
        }
    }

    @NonNull
    private static PackageBlock firstPackage(@NonNull FrameworkApk apk, @NonNull String what) {
        Iterator<PackageBlock> it = apk.getTableBlock().getAllPackages();
        if (!it.hasNext()) {
            throw new IllegalStateException("No package in the framework table for " + what);
        }
        return it.next();
    }

    @NonNull
    private static Data data() {
        synchronized (sLock) {
            if (sData == null) {
                try (InputStream in = open(DATA)) {
                    sData = parse(in);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read " + DATA, e);
                }
            }
            return sData;
        }
    }

    @NonNull
    private static InputStream open(@NonNull String name) throws IOException {
        InputStream in = FrameworkTables.class.getResourceAsStream(DIR + name);
        if (in == null) {
            throw new IOException(name + " is missing from the app");
        }
        return in;
    }

    /**
     * Reads {@code disputed-ids.txt}. A defect in it is a packaging bug, never the input's fault, so
     * it throws {@link IllegalStateException} rather than letting a file decode with wrong names.
     */
    @VisibleForTesting
    @NonNull
    static Data parse(@NonNull InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String newest = null;
        TreeMap<Integer, Section> sections = new TreeMap<>();
        IdSet anyNames = null;
        IdSet anyReferences = null;
        // The set the range lines that follow belong to
        String current = null;
        List<int[]> ranges = new ArrayList<>();
        Integer currentApi = null;
        String currentTable = null;
        String line;
        int lineNo = 0;
        while (true) {
            line = reader.readLine();
            ++lineNo;
            boolean header = line == null || !isRange(line);
            if (header && current != null) {
                IdSet set = IdSet.ofRanges(ranges, current);
                switch (current) {
                    case "api":
                        sections.put(currentApi, new Section(currentTable, set));
                        break;
                    case "any":
                        anyNames = set;
                        break;
                    case "any references":
                        anyReferences = set;
                        break;
                }
                current = null;
                ranges.clear();
            }
            if (line == null) {
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!header) {
                if (current == null) {
                    throw new IllegalStateException("Range outside a section at line " + lineNo);
                }
                ranges.add(range(line, lineNo));
                continue;
            }
            String[] words = line.split(" ");
            if (words.length == 2 && words[0].equals("newest")) {
                newest = words[1];
            } else if (words.length == 4 && words[0].equals("api") && words[2].equals("table")) {
                current = "api";
                currentApi = Integer.parseInt(words[1]);
                currentTable = words[3];
                if (!currentTable.equals(NEWEST)) {
                    Integer.parseInt(currentTable);
                }
                if (sections.containsKey(currentApi)) {
                    throw new IllegalStateException("API " + currentApi + " twice at line " + lineNo);
                }
            } else if (line.equals("any table newest")) {
                current = "any";
            } else if (line.equals("any references")) {
                current = "any references";
            } else {
                throw new IllegalStateException("Unexpected line " + lineNo + ": " + line);
            }
        }
        if (newest == null || sections.isEmpty() || anyNames == null || anyReferences == null) {
            throw new IllegalStateException(DATA + " is incomplete");
        }
        return new Data(newest, sections, anyNames, anyReferences);
    }

    /** {@code 0102005f} or {@code 0102005f-010201ae}: eight hex digits, optionally a dash and eight more. */
    private static boolean isRange(@NonNull String line) {
        if (line.length() == 8) {
            return isHex(line, 0, 8);
        }
        return line.length() == 17 && line.charAt(8) == '-' && isHex(line, 0, 8) && isHex(line, 9, 17);
    }

    private static boolean isHex(@NonNull String s, int from, int to) {
        for (int i = from; i < to; ++i) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static int[] range(@NonNull String line, int lineNo) {
        try {
            int dash = line.indexOf('-');
            int start = (int) Long.parseLong(dash < 0 ? line : line.substring(0, dash), 16);
            int end = dash < 0 ? start : (int) Long.parseLong(line.substring(dash + 1), 16);
            if (Integer.compareUnsigned(start, end) > 0) {
                throw new IllegalStateException("Backwards range at line " + lineNo);
            }
            return new int[]{start, end};
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Bad range at line " + lineNo + ": " + line, e);
        }
    }

    /** Which table decodes a file, and which framework IDs it may print by name. */
    static final class Choice {
        @NonNull
        final String table;
        @NonNull
        private final IdSet mDisputedNames;
        @NonNull
        private final IdSet mDisputedReferences;
        private final boolean mPublicOnly;

        Choice(@NonNull String table, @NonNull IdSet disputedNames, @NonNull IdSet disputedReferences,
               boolean publicOnly) {
            this.table = table;
            mDisputedNames = disputedNames;
            mDisputedReferences = disputedReferences;
            mPublicOnly = publicOnly;
        }

        @NonNull
        PackageBlock packageBlock() {
            return FrameworkTables.table(table);
        }

        /** Whether the table may print this attribute ID's name. */
        boolean canNameAttribute(int id) {
            return canName(id, mDisputedNames);
        }

        /** Whether the table may print the name of the resource a reference value points at. */
        boolean canNameReference(int id) {
            return canName(id, mDisputedReferences);
        }

        private boolean canName(int id, @NonNull IdSet disputed) {
            if (!isFrameworkId(id)) {
                return true;
            }
            if (mPublicOnly) {
                return newestPublic().contains(id);
            }
            return !disputed.contains(id);
        }
    }

    @VisibleForTesting
    static final class Data {
        @NonNull
        final String newest;
        @NonNull
        final TreeMap<Integer, Section> sections;
        @NonNull
        final IdSet anyNames;
        @NonNull
        final IdSet anyReferences;

        Data(@NonNull String newest, @NonNull TreeMap<Integer, Section> sections, @NonNull IdSet anyNames,
             @NonNull IdSet anyReferences) {
            this.newest = newest;
            this.sections = sections;
            this.anyNames = anyNames;
            this.anyReferences = anyReferences;
        }
    }

    @VisibleForTesting
    static final class Section {
        @NonNull
        final String table;
        @NonNull
        final IdSet disputed;

        Section(@NonNull String table, @NonNull IdSet disputed) {
            this.table = table;
            this.disputed = disputed;
        }
    }

    /** Sorted, non-overlapping ID ranges. */
    @VisibleForTesting
    static final class IdSet {
        static final IdSet EMPTY = new IdSet(new int[0], new int[0]);

        @NonNull
        private final int[] mStarts;
        @NonNull
        private final int[] mEnds;

        private IdSet(@NonNull int[] starts, @NonNull int[] ends) {
            mStarts = starts;
            mEnds = ends;
        }

        @NonNull
        static IdSet of(@NonNull List<Integer> sortedIds) {
            List<int[]> ranges = new ArrayList<>();
            for (int id : sortedIds) {
                int[] last = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
                if (last != null && last[1] + 1 == id) {
                    last[1] = id;
                } else {
                    ranges.add(new int[]{id, id});
                }
            }
            return ofRanges(ranges, "public IDs");
        }

        @NonNull
        static IdSet ofRanges(@NonNull List<int[]> ranges, @NonNull String what) {
            int[] starts = new int[ranges.size()];
            int[] ends = new int[ranges.size()];
            for (int i = 0; i < ranges.size(); ++i) {
                starts[i] = ranges.get(i)[0];
                ends[i] = ranges.get(i)[1];
                if (i > 0 && Integer.compareUnsigned(starts[i], ends[i - 1]) <= 0) {
                    throw new IllegalStateException("Ranges out of order in " + what);
                }
            }
            return new IdSet(starts, ends);
        }

        boolean contains(int id) {
            int lo = 0;
            int hi = mStarts.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (Integer.compareUnsigned(id, mStarts[mid]) < 0) {
                    hi = mid - 1;
                } else if (Integer.compareUnsigned(id, mEnds[mid]) > 0) {
                    lo = mid + 1;
                } else {
                    return true;
                }
            }
            return false;
        }

        int rangeCount() {
            return mStarts.length;
        }

        @NonNull
        @Override
        public String toString() {
            return "IdSet" + Arrays.toString(mStarts) + Arrays.toString(mEnds);
        }
    }
}
