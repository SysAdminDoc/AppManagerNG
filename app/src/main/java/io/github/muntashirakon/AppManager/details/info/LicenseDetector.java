// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the license files an APK ships and names the common licenses in them, offline, by the phrases each license
 * text starts with. The APK can be anyone's, so it reads at most {@link #MAX_CANDIDATES} files and at most
 * {@link #MAX_READ_BYTES} of each, whatever size the zip claims.
 */
public final class LicenseDetector {
    public static final int MAX_CANDIDATES = 32;
    public static final int MAX_READ_BYTES = 64 * 1024;
    /**
     * How far after a license's title its version line can be.
     */
    private static final int VERSION_WINDOW = 300;

    private static final Pattern SPDX = Pattern.compile("SPDX-License-Identifier:\\s*([A-Za-z0-9.+-]+)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static final class Result {
        /**
         * Each license file in the APK with the license found in it, or {@code null} when none was recognized.
         */
        @NonNull
        public final Map<String, String> files;
        /**
         * The licenses found, each once, in the order their files come in the APK.
         */
        @NonNull
        public final List<String> licenses;

        Result(@NonNull Map<String, String> files, @NonNull List<String> licenses) {
            this.files = files;
            this.licenses = licenses;
        }
    }

    private LicenseDetector() {
    }

    @WorkerThread
    @NonNull
    public static Result detect(@NonNull ZipFile zipFile) {
        Map<String, String> files = new LinkedHashMap<>();
        List<String> licenses = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && files.size() < MAX_CANDIDATES) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !isCandidate(entry.getName())) {
                continue;
            }
            String license = null;
            try {
                license = identify(readHead(zipFile, entry));
            } catch (IOException | RuntimeException ignore) {
                // A broken entry names no license, and the next one may still be readable
            }
            files.put(entry.getName(), license);
            if (license != null && !licenses.contains(license)) {
                licenses.add(license);
            }
        }
        return new Result(Collections.unmodifiableMap(files), Collections.unmodifiableList(licenses));
    }

    /**
     * The SPDX identifier of the license this text is, or {@code null}. An SPDX-License-Identifier tag is taken as
     * written. Otherwise the license whose title comes first wins, since a license often names another one further
     * down (the GPL suggests the LGPL in its last paragraph).
     */
    @Nullable
    public static String identify(@NonNull String text) {
        Matcher spdx = SPDX.matcher(text);
        if (spdx.find()) {
            return spdx.group(1);
        }
        String flat = WHITESPACE.matcher(text).replaceAll(" ").toLowerCase(Locale.ROOT);
        String best = null;
        int bestAt = Integer.MAX_VALUE;
        int at;
        if ((at = flat.indexOf("gnu affero general public license")) >= 0 && at < bestAt) {
            best = "AGPL-3.0";
            bestAt = at;
        }
        if ((at = flat.indexOf("gnu lesser general public license")) >= 0 && at < bestAt) {
            best = versionAfter(flat, at, "version 2.1") ? "LGPL-2.1" : "LGPL-3.0";
            bestAt = at;
        }
        if ((at = flat.indexOf("gnu library general public license")) >= 0 && at < bestAt) {
            best = "LGPL-2.0";
            bestAt = at;
        }
        if ((at = flat.indexOf("gnu general public license")) >= 0 && at < bestAt) {
            best = versionAfter(flat, at, "version 2") ? "GPL-2.0" : "GPL-3.0";
            bestAt = at;
        }
        if ((at = flat.indexOf("apache license")) >= 0 && at < bestAt && versionAfter(flat, at, "version 2.0")) {
            best = "Apache-2.0";
            bestAt = at;
        }
        if ((at = flat.indexOf("mozilla public license")) >= 0 && at < bestAt && versionAfter(flat, at, "2.0")) {
            best = "MPL-2.0";
            bestAt = at;
        }
        if (best != null) {
            return best;
        }
        // The permissive licenses have no title line; their grant sentence names them
        if (flat.contains("permission to use, copy, modify, and/or distribute this software for any purpose")
                || flat.contains("permission to use, copy, modify, and distribute this software for any purpose")) {
            return "ISC";
        }
        if (flat.contains("permission is hereby granted, free of charge, to any person obtaining a copy")) {
            return "MIT";
        }
        if (flat.contains("redistribution and use in source and binary forms, with or without modification, are "
                + "permitted")) {
            return flat.contains("neither the name") || flat.contains("endorse or promote") ? "BSD-3-Clause"
                    : "BSD-2-Clause";
        }
        return null;
    }

    /**
     * A file whose name says it's a license or a notice, or one inside a licenses folder. Class and dex files
     * never are, whatever their package is called.
     */
    static boolean isCandidate(@NonNull String entryName) {
        String upper = entryName.toUpperCase(Locale.ROOT);
        if (upper.endsWith(".CLASS") || upper.endsWith(".DEX") || upper.endsWith(".SO")) {
            return false;
        }
        int slash = upper.lastIndexOf('/');
        String base = upper.substring(slash + 1);
        if (base.contains("LICENSE") || base.contains("LICENCE") || base.startsWith("COPYING")
                || base.startsWith("NOTICE")) {
            return true;
        }
        String dirs = "/" + upper.substring(0, Math.max(slash, 0)) + "/";
        return dirs.contains("/LICENSES/") || dirs.contains("/LICENCES/");
    }

    private static boolean versionAfter(@NonNull String flat, int titleAt, @NonNull String version) {
        int end = Math.min(flat.length(), titleAt + VERSION_WINDOW);
        int at = flat.indexOf(version, titleAt);
        if (at < 0 || at >= end) {
            return false;
        }
        // "version 2" must not be the start of "version 2.1"
        int after = at + version.length();
        return after >= flat.length() || !(flat.charAt(after) == '.' && after + 1 < flat.length()
                && Character.isDigit(flat.charAt(after + 1)));
    }

    @NonNull
    private static String readHead(@NonNull ZipFile zipFile, @NonNull ZipEntry entry) throws IOException {
        byte[] buffer = new byte[MAX_READ_BYTES];
        int total = 0;
        try (InputStream is = zipFile.getInputStream(entry)) {
            int read;
            while (total < buffer.length && (read = is.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
        }
        return new String(buffer, 0, total, StandardCharsets.UTF_8);
    }
}
