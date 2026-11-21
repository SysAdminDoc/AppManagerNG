// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.PathAttributes;
import io.github.muntashirakon.io.Paths;

/**
 * Sorting by name, date, type and size, each either way, with folders kept on top when asked (#37).
 */
@RunWith(RobolectricTestRunner.class)
public class FmSorterTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void nameIgnoresCaseAndOrdersNumbersNaturally() {
        List<FmItem> items = list(file("cherry", 0, 0), file("Banana", 0, 0), file("apple", 0, 0),
                file("file10", 0, 0), file("file2", 0, 0));

        assertOrder(items, FmListOptions.SORT_BY_NAME, false, false,
                "apple", "Banana", "cherry", "file2", "file10");
        assertOrder(items, FmListOptions.SORT_BY_NAME, true, false,
                "file10", "file2", "cherry", "Banana", "apple");
    }

    @Test
    public void namesThatDifferOnlyInCaseStillSortTheSameWayEveryTime() {
        // A case-sensitive file system can hold both
        List<FmItem> items = list(file("readme.txt", 0, 0), file("README.txt", 0, 0), file("Readme.txt", 0, 0));
        List<FmItem> shuffled = new ArrayList<>(items);
        Collections.reverse(shuffled);

        assertEquals(names(sorted(items, FmListOptions.SORT_BY_NAME, false, false)),
                names(sorted(shuffled, FmListOptions.SORT_BY_NAME, false, false)));
    }

    @Test
    public void lastModifiedPutsTheNewestFirstAndReverseTheOldest() {
        List<FmItem> items = list(file("old", 1 * DAY, 0), file("new", 3 * DAY, 0), file("middle", 2 * DAY, 0));

        assertOrder(items, FmListOptions.SORT_BY_LAST_MODIFIED, false, false, "new", "middle", "old");
        assertOrder(items, FmListOptions.SORT_BY_LAST_MODIFIED, true, false, "old", "middle", "new");
    }

    @Test
    public void filesChangedAtTheSameTimeKeepNameOrderBothWays() {
        List<FmItem> items = list(file("b", DAY, 0), file("C", DAY, 0), file("a", DAY, 0), file("z", 2 * DAY, 0));

        assertOrder(items, FmListOptions.SORT_BY_LAST_MODIFIED, false, false, "z", "a", "b", "C");
        assertOrder(items, FmListOptions.SORT_BY_LAST_MODIFIED, true, false, "a", "b", "C", "z");
    }

    @Test
    public void sizePutsTheLargestFirstAndReverseTheSmallest() {
        List<FmItem> items = list(file("small", 0, 10), file("large", 0, 3000), file("medium", 0, 200));

        assertOrder(items, FmListOptions.SORT_BY_SIZE, false, false, "large", "medium", "small");
        assertOrder(items, FmListOptions.SORT_BY_SIZE, true, false, "small", "medium", "large");
    }

    @Test
    public void filesOfTheSameSizeKeepNameOrderBothWays() {
        List<FmItem> items = list(file("Beta", 0, 5), file("alpha", 0, 5), file("big", 0, 50));

        assertOrder(items, FmListOptions.SORT_BY_SIZE, false, false, "big", "alpha", "Beta");
        assertOrder(items, FmListOptions.SORT_BY_SIZE, true, false, "alpha", "Beta", "big");
    }

    @Test
    public void typeGroupsByFormatThenExtensionThenName() {
        List<FmItem> items = list(
                typed("b.png", "image/png"),
                typed("notes.TXT", "text/plain"),
                typed("a.txt", "text/plain"),
                typed("build.log", "text/plain"),
                typed("d.zip", "application/zip"),
                typed("E.PNG", "image/png"));

        assertOrder(items, FmListOptions.SORT_BY_TYPE, false, false,
                "d.zip", "b.png", "E.PNG", "build.log", "a.txt", "notes.TXT");
        // Reverse flips the groups; names inside a group stay A to Z
        assertOrder(items, FmListOptions.SORT_BY_TYPE, true, false,
                "a.txt", "notes.TXT", "build.log", "b.png", "E.PNG", "d.zip");
    }

    @Test
    public void typeIgnoresCaseInTheMimeType() {
        List<FmItem> items = list(typed("b.jpg", "IMAGE/JPEG"), typed("a.jpg", "image/jpeg"));

        assertOrder(items, FmListOptions.SORT_BY_TYPE, false, false, "a.jpg", "b.jpg");
    }

    @Test
    public void foldersStayOnTopWhicheverWayTheListIsSorted() {
        List<FmItem> items = list(file("b-file", DAY, 10), folder("z-folder", 3 * DAY),
                file("a-file", 2 * DAY, 20), folder("A-folder", DAY));

        assertOrder(items, FmListOptions.SORT_BY_NAME, false, true, "A-folder", "z-folder", "a-file", "b-file");
        assertOrder(items, FmListOptions.SORT_BY_NAME, true, true, "z-folder", "A-folder", "b-file", "a-file");
        assertOrder(items, FmListOptions.SORT_BY_LAST_MODIFIED, false, true,
                "z-folder", "A-folder", "a-file", "b-file");
        assertOrder(items, FmListOptions.SORT_BY_SIZE, true, true, "A-folder", "z-folder", "b-file", "a-file");
    }

    @Test
    public void withoutFoldersFirstFoldersSortAmongTheFiles() {
        List<FmItem> items = list(file("b-file", 0, 0), folder("c-folder", 0), file("a-file", 0, 0));

        assertOrder(items, FmListOptions.SORT_BY_NAME, false, false, "a-file", "b-file", "c-folder");
    }

    @Test
    public void anEmptyListStaysEmpty() {
        List<FmItem> items = new ArrayList<>();
        FmSorter.sort(items, FmListOptions.SORT_BY_TYPE, true, true);
        assertEquals(0, items.size());
    }

    private static void assertOrder(List<FmItem> items, int sortBy, boolean reverse, boolean foldersFirst,
                                    String... expected) {
        assertEquals(Arrays.asList(expected), names(sorted(items, sortBy, reverse, foldersFirst)));
    }

    private static List<FmItem> sorted(List<FmItem> items, int sortBy, boolean reverse, boolean foldersFirst) {
        List<FmItem> copy = new ArrayList<>(items);
        FmSorter.sort(copy, sortBy, reverse, foldersFirst);
        return copy;
    }

    private static List<String> names(List<FmItem> items) {
        List<String> names = new ArrayList<>();
        for (FmItem item : items) {
            names.add(item.getName());
        }
        return names;
    }

    private static List<FmItem> list(FmItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static FmItem file(String name, long lastModified, long size) {
        return item(name, "application/octet-stream", lastModified, size, false);
    }

    private static FmItem typed(String name, String mimeType) {
        return item(name, mimeType, 0, 0, false);
    }

    private static FmItem folder(String name, long lastModified) {
        return item(name, "resource/folder", lastModified, 4096, true);
    }

    private static FmItem item(@NonNull String name, @Nullable String mimeType, long lastModified, long size,
                               boolean directory) {
        PathAttributes attributes = new PathAttributes(name, mimeType, lastModified, 0, 0, !directory,
                directory, false, size) {
        };
        return new FmItem(Paths.get("/fm-sort-test/" + name), attributes);
    }
}
