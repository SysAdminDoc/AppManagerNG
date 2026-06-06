// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.importers;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Importers for three trivial AM-adjacent power-user formats so users migrating
 * <em>to</em> AppManagerNG can arrive with their existing curation intact (without
 * forcing them to retype hundreds of package names):
 *
 * <ul>
 *   <li><b>Canta</b> — JSON preset, typically {@code {"packages":["pkg",…]}} or a bare
 *       JSON array. The Canta export menu writes either shape depending on version.</li>
 *   <li><b>UAD-NG</b> — {@code uad_settings.json}: nested {@code selected_user_packages_serial}
 *       map keyed by device serial → user → package list. Schema varies between
 *       UAD-NG point releases; the importer walks every JSON value recursively and
 *       collects strings that look like package names, deduplicating in insertion
 *       order so the user does not have to keep the per-device structure intact.</li>
 *   <li><b>Hail</b> — a tag-list text file: one package name per line, blank lines
 *       and lines beginning with {@code #} are ignored as comments.</li>
 * </ul>
 *
 * <p>The result is a {@link Preview} — name + package list — that the caller hands
 * to the existing {@code BaseProfile.newProfile} factory and writes through
 * {@code ProfileManager.requireProfilePathById}. The importer never touches disk
 * itself; it only parses.
 */
public final class ExternalProfileImporter {

    public enum Format {
        CANTA,
        UAD_NG,
        HAIL
    }

    private ExternalProfileImporter() {
    }

    @NonNull
    public static Preview importStream(@NonNull InputStream in, @NonNull String displayName)
            throws IOException, JSONException {
        String text = readAll(in);
        Format format = detectFormat(text, displayName);
        return importString(text, displayName, format);
    }

    @VisibleForTesting
    @NonNull
    static Preview importString(@NonNull String text, @NonNull String displayName,
                                @NonNull Format format) throws JSONException {
        switch (format) {
            case CANTA:
                return new Preview(synthesizeName("Canta", displayName), parseCanta(text), format);
            case UAD_NG:
                return new Preview(synthesizeName("UAD-NG", displayName), parseUadNg(text), format);
            case HAIL:
                return new Preview(synthesizeName("Hail", displayName), parseHail(text), format);
            default:
                throw new JSONException("Unknown external profile format: " + format);
        }
    }

    // -----------------------------------------------------------------------
    // Format detection (content-driven, file name is only a tie-breaker)
    // -----------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    static Format detectFormat(@NonNull String text, @NonNull String displayName) {
        String trimmed = text.trim();
        // JSON shapes
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // UAD-NG: nested map with selected_user_packages_serial key.
            // Fold with Locale.ROOT — the default locale would mangle ASCII
            // 'I'/'i' under Turkish/Azeri, misdetecting an otherwise-valid file.
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("selected_user_packages_serial")
                    || lower.contains("selected_user_packages")
                    || displayName.toLowerCase(Locale.ROOT).contains("uad")) {
                return Format.UAD_NG;
            }
            return Format.CANTA;
        }
        // Anything else is line-oriented = Hail tag list
        return Format.HAIL;
    }

    // -----------------------------------------------------------------------
    // Canta parser
    // -----------------------------------------------------------------------

    /**
     * Accepts:
     *   {@code {"packages":[...]} }, optionally {@code {"apps":[...]} },
     *   {@code [...]} (bare array),
     *   nested {@code {"data":{"packages":[...]}}}.
     * Strings outside of the recognised array shape are not consumed —
     * Canta export is a closed format so we don't recurse blindly.
     */
    @VisibleForTesting
    @NonNull
    static String[] parseCanta(@NonNull String text) throws JSONException {
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            return requireNonEmpty(stringArrayOf(new JSONArray(trimmed)));
        }
        JSONObject root = new JSONObject(trimmed);
        JSONArray arr = root.optJSONArray("packages");
        if (arr == null) arr = root.optJSONArray("apps");
        if (arr == null) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                arr = data.optJSONArray("packages");
                if (arr == null) arr = data.optJSONArray("apps");
            }
        }
        if (arr == null) {
            throw new JSONException("Canta preset has no recognisable packages/apps array.");
        }
        return requireNonEmpty(stringArrayOf(arr));
    }

    /**
     * Surface an empty result as a parse failure (matching the UAD-NG path)
     * rather than silently handing the caller a zero-package profile when the
     * input array was empty or held only non-package strings.
     */
    @NonNull
    private static String[] requireNonEmpty(@NonNull String[] packages) throws JSONException {
        if (packages.length == 0) {
            throw new JSONException("Canta preset contained no recognisable package names.");
        }
        return packages;
    }

    // -----------------------------------------------------------------------
    // UAD-NG parser
    // -----------------------------------------------------------------------

    /**
     * UAD-NG schema varies between point releases. Rather than encode every
     * version, walk the JSON tree and harvest any string that matches the
     * Android package-name pattern. Deduplicates in insertion order.
     */
    @VisibleForTesting
    @NonNull
    static String[] parseUadNg(@NonNull String text) throws JSONException {
        Object root = parseLenient(text);
        Set<String> packages = new LinkedHashSet<>();
        collectPackageStrings(root, packages, 0);
        if (packages.isEmpty()) {
            throw new JSONException("UAD-NG settings file contained no recognisable package names.");
        }
        return packages.toArray(new String[0]);
    }

    @NonNull
    private static Object parseLenient(@NonNull String text) throws JSONException {
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) return new JSONArray(trimmed);
        return new JSONObject(trimmed);
    }

    /** Bound on JSON nesting; a hostile/deeply-nested file cannot overflow the stack. */
    @VisibleForTesting
    static final int MAX_JSON_DEPTH = 64;

    private static void collectPackageStrings(@NonNull Object node, @NonNull Set<String> out, int depth) {
        if (depth > MAX_JSON_DEPTH) {
            // Deeply nested input (a 16 MB file of nested arrays/objects could be
            // crafted) would otherwise recurse until StackOverflowError, which is
            // an Error the importStream contract does not catch. Stop descending.
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String k = it.next();
                Object child = obj.opt(k);
                if (child == null) continue;
                collectPackageStrings(child, out, depth + 1);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); ++i) {
                Object child = arr.opt(i);
                if (child == null) continue;
                collectPackageStrings(child, out, depth + 1);
            }
        } else if (node instanceof String) {
            String s = (String) node;
            if (looksLikePackageName(s)) {
                out.add(s);
            }
        }
        // numbers / booleans are not interesting
    }

    // -----------------------------------------------------------------------
    // Hail parser
    // -----------------------------------------------------------------------

    /**
     * Hail tag-list: one package name per line; blank lines and lines beginning
     * with {@code #} are comments and dropped. Preserves order, deduplicates.
     */
    @VisibleForTesting
    @NonNull
    static String[] parseHail(@NonNull String text) {
        String[] lines = text.split("\\r?\\n");
        Set<String> packages = new LinkedHashSet<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // Hail occasionally tags entries with a trailing flag (e.g. "pkg|f");
            // strip anything past the first whitespace or pipe so the bare package
            // survives round-trip.
            int cut = indexOfFirst(line, " \t|");
            String candidate = cut < 0 ? line : line.substring(0, cut);
            candidate = candidate.trim();
            if (looksLikePackageName(candidate)) {
                packages.add(candidate);
            }
        }
        return packages.toArray(new String[0]);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int indexOfFirst(@NonNull String s, @NonNull String chars) {
        int best = -1;
        for (int i = 0; i < chars.length(); ++i) {
            int p = s.indexOf(chars.charAt(i));
            if (p >= 0 && (best < 0 || p < best)) {
                best = p;
            }
        }
        return best;
    }

    @NonNull
    private static String[] stringArrayOf(@NonNull JSONArray arr) {
        Set<String> packages = new LinkedHashSet<>();
        for (int i = 0; i < arr.length(); ++i) {
            String s = arr.optString(i, null);
            if (s == null) continue;
            s = s.trim();
            if (looksLikePackageName(s)) {
                packages.add(s);
            }
        }
        return packages.toArray(new String[0]);
    }

    /**
     * Conservative package-name shape check: at least one period, starts with a
     * letter, only ASCII letters / digits / underscores / periods. Mirrors
     * {@code PackageUtils.isPlausiblePackageName} but does not call it directly
     * because that lives in a module with Android-only deps; this importer is
     * intentionally pure-JVM so it can be unit-tested without Robolectric.
     */
    @VisibleForTesting
    static boolean looksLikePackageName(@NonNull String s) {
        int length = s.length();
        if (length < 3 || length > 255) return false;
        boolean segmentStart = true;
        boolean hasDot = false;
        for (int i = 0; i < length; ++i) {
            char c = s.charAt(i);
            if (c == '.') {
                if (segmentStart) return false;
                hasDot = true;
                segmentStart = true;
                continue;
            }
            if (segmentStart && !isAsciiLetter(c)) return false;
            if (!isAsciiLetter(c) && (c < '0' || c > '9') && c != '_') {
                return false;
            }
            segmentStart = false;
        }
        return hasDot && !segmentStart;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    @NonNull
    private static String readAll(@NonNull InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
            // Bound the read at ~16 MB so a hostile file can't blow memory on import.
            if (sb.length() > 16 * 1024 * 1024) {
                throw new IOException("External profile source is suspiciously large (>16 MB); refusing to parse.");
            }
        }
        return sb.toString();
    }

    @NonNull
    private static String synthesizeName(@NonNull String tool, @NonNull String displayName) {
        if (displayName.isEmpty()) return tool;
        // Strip extension and common URI noise so the name reads cleanly in the list.
        String name = displayName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (name.isEmpty()) return tool;
        return tool + " · " + name;
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    public static final class Preview {
        @NonNull
        public final String suggestedName;
        @NonNull
        public final String[] packages;
        @NonNull
        public final Format format;

        Preview(@NonNull String suggestedName, @NonNull String[] packages, @NonNull Format format) {
            this.suggestedName = suggestedName;
            this.packages = packages;
            this.format = format;
        }

        @NonNull
        public List<String> packagesAsList() {
            return new ArrayList<>(java.util.Arrays.asList(packages));
        }
    }
}
