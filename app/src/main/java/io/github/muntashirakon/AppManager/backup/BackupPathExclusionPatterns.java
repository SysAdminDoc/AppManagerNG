// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class BackupPathExclusionPatterns {
    @VisibleForTesting
    static final String[] DEFAULT_EXCLUSION_GLOBS = {
            "**/.thumbnails/**",
            "**/thumbnails/**",
            "**/thumbs/**",
            "**/tmp/**",
            "**/temp/**",
    };
    @VisibleForTesting
    static final String[] DEFAULT_CACHE_EXCLUSION_GLOBS = {
            "**/cache/**",
            "**/code_cache/**",
            "**/no_backup/**",
    };

    private BackupPathExclusionPatterns() {
    }

    @NonNull
    public static String[] parse(@Nullable CharSequence rawPatterns) {
        if (rawPatterns == null) {
            return new String[0];
        }
        return sanitize(rawPatterns.toString().split("\\r?\\n"));
    }

    @NonNull
    public static String[] sanitize(@Nullable String[] globs) {
        if (globs == null || globs.length == 0) {
            return new String[0];
        }
        Set<String> sanitized = new LinkedHashSet<>(globs.length);
        for (String glob : globs) {
            String normalized = normalizeGlob(glob);
            if (!normalized.isEmpty()) {
                sanitized.add(normalized);
            }
        }
        return sanitized.toArray(new String[0]);
    }

    @NonNull
    public static String[] getTarExclusionRegexes(boolean backupCache, @Nullable String[]... customGlobSets) {
        List<String> globs = new ArrayList<>();
        appendAll(globs, DEFAULT_EXCLUSION_GLOBS);
        if (!backupCache) {
            appendAll(globs, DEFAULT_CACHE_EXCLUSION_GLOBS);
        }
        if (customGlobSets != null) {
            for (String[] customGlobSet : customGlobSets) {
                appendAll(globs, sanitize(customGlobSet));
            }
        }
        String[] regexes = new String[globs.size()];
        for (int i = 0; i < globs.size(); ++i) {
            regexes[i] = globToRegex(globs.get(i));
        }
        return regexes;
    }

    public static int getCustomGlobCount(@Nullable String[] globs) {
        return sanitize(globs).length;
    }

    public static int getDefaultGlobCount(boolean backupCache) {
        return DEFAULT_EXCLUSION_GLOBS.length + (backupCache ? 0 : DEFAULT_CACHE_EXCLUSION_GLOBS.length);
    }

    @VisibleForTesting
    static boolean isExcluded(@NonNull String relativePath, boolean backupCache, @Nullable String[]... customGlobSets) {
        for (String regex : getTarExclusionRegexes(backupCache, customGlobSets)) {
            if (Pattern.compile(regex).matcher(relativePath).matches()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String normalizeGlob(@Nullable String glob) {
        if (glob == null) {
            return "";
        }
        String normalized = glob.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("#")) {
            return "";
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.replaceAll("/{2,}", "/");
    }

    private static void appendAll(@NonNull List<String> target, @Nullable String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !target.contains(value)) {
                target.add(value);
            }
        }
    }

    @NonNull
    @VisibleForTesting
    static String globToRegex(@NonNull String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); ) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean isDoubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (isDoubleStar) {
                    boolean hasTrailingSlash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                    if (hasTrailingSlash) {
                        regex.append("(?:.*/)?");
                        i += 3;
                    } else {
                        regex.append(".*");
                        i += 2;
                    }
                } else {
                    regex.append("[^/]*");
                    ++i;
                }
            } else if (c == '?') {
                regex.append("[^/]");
                ++i;
            } else {
                if ("\\.[]{}()+-^$|".indexOf(c) != -1) {
                    regex.append('\\');
                }
                regex.append(c);
                ++i;
            }
        }
        if (glob.endsWith("/")) {
            regex.append(".*");
        }
        regex.append('$');
        return regex.toString();
    }
}
