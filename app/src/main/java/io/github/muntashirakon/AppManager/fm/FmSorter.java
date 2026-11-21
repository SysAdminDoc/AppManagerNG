// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.AlphanumComparator;

/**
 * Orders the file list. Name sorts A to Z (numbers in natural order, case ignored), date and size
 * put the newest and the largest first, and type groups by MIME type, then extension. Reverse
 * flips that order; items that tie keep A to Z name order either way, so the list doesn't shuffle
 * between refreshes. Folders stay on top when asked.
 */
final class FmSorter {
    private FmSorter() {
    }

    /**
     * Sorts in place. Each item's sort key is read once, since a type lookup can touch the disk.
     */
    @WorkerThread
    static void sort(@NonNull List<FmItem> items, @FmListOptions.SortOrder int sortBy, boolean reverse,
                     boolean foldersFirst) {
        int size = items.size();
        if (size < 2) {
            return;
        }
        Entry[] entries = new Entry[size];
        for (int i = 0; i < size; ++i) {
            entries[i] = new Entry(items.get(i), sortBy == FmListOptions.SORT_BY_TYPE);
        }
        Arrays.sort(entries, comparator(sortBy, reverse, foldersFirst));
        for (int i = 0; i < size; ++i) {
            items.set(i, entries[i].item);
        }
    }

    @NonNull
    private static Comparator<Entry> comparator(int sortBy, boolean reverse, boolean foldersFirst) {
        return (a, b) -> {
            if (foldersFirst && a.isDirectory != b.isDirectory) {
                return a.isDirectory ? -1 : 1;
            }
            int result;
            switch (sortBy) {
                case FmListOptions.SORT_BY_LAST_MODIFIED:
                    // Newest first
                    result = -Long.compare(a.lastModified, b.lastModified);
                    break;
                case FmListOptions.SORT_BY_SIZE:
                    // Largest first
                    result = -Long.compare(a.size, b.size);
                    break;
                case FmListOptions.SORT_BY_TYPE:
                    result = a.mimeType.compareTo(b.mimeType);
                    if (result == 0) {
                        result = a.extension.compareTo(b.extension);
                    }
                    break;
                case FmListOptions.SORT_BY_NAME:
                default:
                    return reverse ? -compareNames(a, b) : compareNames(a, b);
            }
            if (result != 0) {
                return reverse ? -result : result;
            }
            return compareNames(a, b);
        };
    }

    private static int compareNames(@NonNull Entry a, @NonNull Entry b) {
        int result = AlphanumComparator.compareStringIgnoreCase(a.name, b.name);
        if (result != 0) {
            return result;
        }
        // Names that differ only in case (a case-sensitive file system can hold both) still get a
        // fixed order
        result = a.name.compareTo(b.name);
        if (result != 0) {
            return result;
        }
        return a.item.path.toString().compareTo(b.item.path.toString());
    }

    private static final class Entry {
        @NonNull
        final FmItem item;
        @NonNull
        final String name;
        final boolean isDirectory;
        final long lastModified;
        final long size;
        @NonNull
        final String mimeType;
        @NonNull
        final String extension;

        Entry(@NonNull FmItem item, boolean needsType) {
            this.item = item;
            name = item.getName();
            isDirectory = item.isDirectory;
            lastModified = item.getLastModified();
            size = item.getSize();
            if (needsType) {
                mimeType = item.getType().toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            } else {
                mimeType = "";
                extension = "";
            }
        }
    }
}
