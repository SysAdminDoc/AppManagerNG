// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class FmSearchUtils {
    private FmSearchUtils() {
    }

    @NonNull
    static List<FmItem> searchRecursive(@NonNull Path root, @Nullable String query, boolean displayDotFiles) {
        String normalizedQuery = normalizeQuery(query);
        if (TextUtils.isEmpty(normalizedQuery)) {
            return new ArrayList<>();
        }
        List<FmItem> results = new ArrayList<>();
        ArrayDeque<Path> pendingDirectories = new ArrayDeque<>();
        pendingDirectories.add(root);
        boolean queryContainsPath = normalizedQuery.contains(Paths.PATH_SEPARATOR);
        while (!pendingDirectories.isEmpty()) {
            if (ThreadUtils.isInterrupted()) {
                return results;
            }
            Path directory = pendingDirectories.removeFirst();
            for (Path child : safeListFiles(directory)) {
                if (ThreadUtils.isInterrupted()) {
                    return results;
                }
                if (!displayDotFiles && child.getName().startsWith(".")) {
                    continue;
                }
                String relativePath = Paths.relativePath(child, root);
                if (matches(child.getName(), normalizedQuery)
                        || (queryContainsPath && matches(relativePath, normalizedQuery))) {
                    FmItem item = new FmItem(child);
                    item.setSearchLocation(getSearchLocation(relativePath));
                    results.add(item);
                }
                if (child.isDirectory() && !child.isSymbolicLink()) {
                    pendingDirectories.add(child);
                }
            }
        }
        return results;
    }

    private static boolean matches(@NonNull String value, @NonNull String normalizedQuery) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    @NonNull
    private static String normalizeQuery(@Nullable String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replace('\\', Paths.PATH_SEPARATOR_CHAR).toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static Path[] safeListFiles(@NonNull Path directory) {
        try {
            return directory.listFiles();
        } catch (RuntimeException e) {
            return new Path[0];
        }
    }

    @Nullable
    private static String getSearchLocation(@NonNull String relativePath) {
        String path = relativePath;
        while (path.endsWith(Paths.PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSeparator = path.lastIndexOf(Paths.PATH_SEPARATOR_CHAR);
        if (lastSeparator <= 0) {
            return null;
        }
        return path.substring(0, lastSeparator);
    }
}
