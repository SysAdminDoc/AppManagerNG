// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ToastHistoryDaoTest {
    private static final String ONE = "com.example.one";
    private static final String TWO = "com.example.two";

    private ToastHistoryDb mDb;
    private ToastHistoryDao mDao;

    @Before
    public void setUp() {
        mDb = ToastHistoryTestAccess.newInMemoryDb(RuntimeEnvironment.getApplication());
        mDao = mDb.dao();
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    private long insert(long time, @NonNull String packageName, @NonNull String text) {
        return mDao.insert(new ToastEntry(time, packageName, "Label", text));
    }

    @NonNull
    private static List<String> texts(@NonNull List<ToastEntry> entries) {
        List<String> texts = new ArrayList<>();
        for (ToastEntry entry : entries) {
            texts.add(entry.text);
        }
        return texts;
    }

    @Test
    public void everythingComesBackNewestFirst() {
        insert(2_000L, ONE, "middle");
        insert(3_000L, TWO, "newest");
        insert(1_000L, ONE, "oldest");

        assertEquals(Arrays.asList("newest", "middle", "oldest"), texts(mDao.getAll()));
    }

    @Test
    public void toastsInTheSameMillisecondKeepTheOrderTheyCameIn() {
        insert(1_000L, ONE, "first");
        insert(1_000L, ONE, "second");

        assertEquals(Arrays.asList("second", "first"), texts(mDao.getAll()));
    }

    @Test
    public void filteringByPackageKeepsOnlyThatAppNewestFirst() {
        insert(1_000L, ONE, "one old");
        insert(2_000L, TWO, "two");
        insert(3_000L, ONE, "one new");

        assertEquals(Arrays.asList("one new", "one old"), texts(mDao.getByPackage(ONE)));
        assertTrue(mDao.getByPackage("com.example.none").isEmpty());
    }

    @Test
    public void everyFieldIsKept() {
        long id = mDao.insert(new ToastEntry(1_234L, ONE, "Example", "Line one\nLine two"));

        ToastEntry saved = mDao.getAll().get(0);

        assertEquals(id, saved.id);
        assertEquals(1_234L, saved.timestampMillis);
        assertEquals(ONE, saved.packageName);
        assertEquals("Example", saved.appLabel);
        assertEquals("Line one\nLine two", saved.text);
    }

    @Test
    public void trimmingKeepsTheNewest() {
        for (int i = 1; i <= 5; ++i) {
            insert(i * 1_000L, ONE, "t" + i);
        }

        assertEquals(2, mDao.trimTo(3));

        assertEquals(Arrays.asList("t5", "t4", "t3"), texts(mDao.getAll()));
    }

    @Test
    public void oldToastsAreDropped() {
        insert(1_000L, ONE, "old");
        insert(5_000L, ONE, "new");

        assertEquals(1, mDao.deleteOlderThan(5_000L));

        assertEquals(Arrays.asList("new"), texts(mDao.getAll()));
    }

    @Test
    public void insertAndPruneAppliesBothLimits() {
        insert(1_000L, ONE, "too old");
        insert(6_000L, ONE, "kept");
        insert(7_000L, ONE, "kept too");

        mDao.insertAndPrune(new ToastEntry(8_000L, ONE, "Label", "new"), 5_000L, 2);

        assertEquals(Arrays.asList("new", "kept too"), texts(mDao.getAll()));
    }

    @Test
    public void takeAllEmptiesTheTableAndHandsBackWhatWasThere() {
        insert(1_000L, ONE, "a");
        insert(2_000L, TWO, "b");

        List<ToastEntry> removed = mDao.takeAll();

        assertEquals(Arrays.asList("b", "a"), texts(removed));
        assertEquals(0, mDao.count());
    }

    @Test
    public void restoringPutsRowsBackWithTheirIdsAndNeverTwice() {
        insert(1_000L, ONE, "a");
        insert(2_000L, TWO, "b");
        List<ToastEntry> removed = mDao.takeAll();
        insert(3_000L, ONE, "arrived after the clear");

        mDao.restoreAndPrune(removed, 0L, 100);
        mDao.restoreAndPrune(removed, 0L, 100);

        List<ToastEntry> all = mDao.getAll();
        assertEquals(Arrays.asList("arrived after the clear", "b", "a"), texts(all));
        assertEquals(removed.get(0).id, all.get(1).id);
    }

    @Test
    public void restoringStillAppliesTheLimits() {
        List<ToastEntry> removed = new ArrayList<>(Collections.singletonList(
                new ToastEntry(1_000L, ONE, "Label", "too old")));
        removed.add(new ToastEntry(9_000L, ONE, "Label", "fine"));

        mDao.restoreAndPrune(removed, 5_000L, 100);

        assertEquals(Arrays.asList("fine"), texts(mDao.getAll()));
    }
}
