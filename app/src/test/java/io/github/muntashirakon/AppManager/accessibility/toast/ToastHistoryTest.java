// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteFullException;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class ToastHistoryTest {
    private static final String APP = "com.example.app";
    private static final long NOW = 100L * 24 * 60 * 60 * 1000;

    private final List<Runnable> mQueued = new ArrayList<>();
    private final Executor mQueue = mQueued::add;
    private ToastHistoryDb mDb;

    @Before
    public void setUp() {
        mDb = ToastHistoryTestAccess.newInMemoryDb(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    private void runQueued() {
        while (!mQueued.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(mQueued);
            mQueued.clear();
            for (Runnable task : tasks) {
                task.run();
            }
        }
        ShadowLooper.idleMainLooper();
    }

    @NonNull
    private ToastHistory history(@NonNull ToastHistoryDao dao) {
        return new ToastHistory(dao, mQueue, packageName -> "Example", () -> NOW);
    }

    @NonNull
    private ToastHistory history() {
        return history(mDb.dao());
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
    public void nothingIsWrittenOnTheCallersThread() {
        ToastHistory history = history();

        history.record(NOW, APP, "Saved");

        assertTrue(history.readAll().isEmpty());
        assertEquals(1, mQueued.size());
    }

    @Test
    public void aToastIsSavedWithItsTimeSourceAndName() {
        ToastHistory history = history();

        history.record(NOW - 1_000L, APP, "Saved");
        runQueued();

        List<ToastEntry> saved = history.readAll();
        assertEquals(1, saved.size());
        assertEquals(NOW - 1_000L, saved.get(0).timestampMillis);
        assertEquals(APP, saved.get(0).packageName);
        assertEquals("Example", saved.get(0).appLabel);
        assertEquals("Saved", saved.get(0).text);
    }

    @Test
    public void theNewestComesFirst() {
        ToastHistory history = history();

        history.record(NOW - 2_000L, APP, "First");
        history.record(NOW - 1_000L, APP, "Second");
        runQueued();

        assertEquals(Arrays.asList("Second", "First"), texts(history.readAll()));
    }

    @Test
    public void theAppNameAndroidAddsUnderTheMessageIsLeftOut() {
        // What an Android 15 emulator sent for a toast from Clock: the message, then the name beside the app's icon.
        ToastHistory history = history();

        history.record(NOW, APP, "Alarm set for 8 hours from now.\nExample");
        runQueued();

        assertEquals("Alarm set for 8 hours from now.", history.readAll().get(0).text);
    }

    @Test
    public void aMessageThatIsOnlyTheAppNameIsKept() {
        ToastHistory history = history();

        history.record(NOW - 2_000L, APP, "Example");
        history.record(NOW - 1_000L, APP, "Not\nExample too");
        runQueued();

        assertEquals(Arrays.asList("Not\nExample too", "Example"), texts(history.readAll()));
    }

    @Test
    public void aVeryLongAppNameIsCut() {
        char[] name = new char[1000];
        Arrays.fill(name, 'n');
        ToastHistory history = new ToastHistory(mDb.dao(), mQueue, packageName -> new String(name), () -> NOW);

        history.record(NOW, APP, "Saved");
        runQueued();

        assertTrue(history.readAll().get(0).appLabel.length() < 1000);
    }

    @Test
    public void savingAnnouncesAChange() {
        ToastHistory history = history();
        assertNull(history.getChanges().getValue());

        history.record(NOW, APP, "Saved");
        runQueued();

        assertNotNull(history.getChanges().getValue());
    }

    @Test
    public void onlyTheNewestToastsAreKept() {
        mDb.runInTransaction(() -> {
            for (int i = 0; i < ToastHistory.MAX_ENTRIES; ++i) {
                mDb.dao().insert(new ToastEntry(NOW - 100_000L + i, APP, "Example", "t" + i));
            }
        });
        ToastHistory history = history();

        history.record(NOW, APP, "newest");
        runQueued();

        List<ToastEntry> saved = history.readAll();
        assertEquals(ToastHistory.MAX_ENTRIES, saved.size());
        assertEquals("newest", saved.get(0).text);
        assertEquals("t1", saved.get(saved.size() - 1).text);
    }

    @Test
    public void toastsOlderThanTheLimitAreDropped() {
        mDb.dao().insert(new ToastEntry(NOW - ToastHistory.MAX_AGE_MILLIS - 1, APP, "Example", "too old"));
        mDb.dao().insert(new ToastEntry(NOW - ToastHistory.MAX_AGE_MILLIS + 1, APP, "Example", "just in time"));
        ToastHistory history = history();

        history.record(NOW, APP, "new");
        runQueued();

        assertEquals(Arrays.asList("new", "just in time"), texts(history.readAll()));
    }

    @Test
    public void clearingWaitsForToastsStillBeingSavedAndHandsThemBack() {
        ToastHistory history = history();
        List<List<ToastEntry>> results = new ArrayList<>();

        history.record(NOW, APP, "Saved");
        history.clear(results::add);
        runQueued();

        assertEquals(1, results.size());
        assertEquals(Collections.singletonList("Saved"), texts(results.get(0)));
        assertTrue(history.readAll().isEmpty());
    }

    @Test
    public void undoPutsTheClearedToastsBackAlongsideNewerOnes() {
        ToastHistory history = history();
        List<List<ToastEntry>> results = new ArrayList<>();
        history.record(NOW - 2_000L, APP, "a");
        history.record(NOW - 1_000L, APP, "b");
        history.clear(results::add);
        runQueued();
        history.record(NOW, APP, "after");
        runQueued();

        history.restore(results.get(0));
        runQueued();

        assertEquals(Arrays.asList("after", "b", "a"), texts(history.readAll()));
    }

    @Test
    public void aFailedSaveTellsTheUserOnceUntilSavingWorksAgain() {
        FlakyDao dao = new FlakyDao(mDb.dao());
        ToastHistory history = history(dao);
        int before = ShadowToast.shownToastCount();

        dao.failing = true;
        history.record(NOW, APP, "Lost");
        history.record(NOW, APP, "Lost too");
        runQueued();
        assertEquals(before + 1, ShadowToast.shownToastCount());
        assertEquals(ApplicationProvider.getApplicationContext().getString(R.string.toast_history_save_failed),
                ShadowToast.getTextOfLatestToast());

        dao.failing = false;
        history.record(NOW, APP, "Saved");
        runQueued();
        assertEquals(before + 1, ShadowToast.shownToastCount());

        dao.failing = true;
        history.record(NOW, APP, "Lost again");
        runQueued();
        assertEquals(before + 2, ShadowToast.shownToastCount());
        assertEquals(Collections.singletonList("Saved"), texts(history.readAll()));
    }

    @Test
    public void aFailedClearSaysSoWithNull() {
        FlakyDao dao = new FlakyDao(mDb.dao());
        ToastHistory history = history(dao);
        List<List<ToastEntry>> results = new ArrayList<>();
        dao.failing = true;

        history.clear(results::add);
        runQueued();

        assertEquals(Collections.singletonList(null), results);
    }

    /** Fails the writes the history makes while {@link #failing} is set, the way a full disk would. */
    private static final class FlakyDao extends ToastHistoryDao {
        private final ToastHistoryDao mReal;
        boolean failing;

        FlakyDao(@NonNull ToastHistoryDao real) {
            mReal = real;
        }

        @Override
        public long insert(@NonNull ToastEntry entry) {
            return mReal.insert(entry);
        }

        @Override
        public void insertAll(@NonNull List<ToastEntry> entries) {
            mReal.insertAll(entries);
        }

        @NonNull
        @Override
        public List<ToastEntry> getAll() {
            return mReal.getAll();
        }

        @NonNull
        @Override
        public List<ToastEntry> getNewest(int limit) {
            return mReal.getNewest(limit);
        }

        @NonNull
        @Override
        public List<ToastEntry> getByPackage(@NonNull String packageName) {
            return mReal.getByPackage(packageName);
        }

        @Override
        public int count() {
            return mReal.count();
        }

        @Override
        public void deleteAll() {
            mReal.deleteAll();
        }

        @Override
        public int deleteOlderThan(long cutoffMillis) {
            return mReal.deleteOlderThan(cutoffMillis);
        }

        @Override
        public int trimTo(int keep) {
            return mReal.trimTo(keep);
        }

        @Override
        public void insertAndPrune(@NonNull ToastEntry entry, long cutoffMillis, int keep) {
            if (failing) {
                throw new SQLiteFullException("full");
            }
            mReal.insertAndPrune(entry, cutoffMillis, keep);
        }

        @NonNull
        @Override
        public List<ToastEntry> takeAll() {
            if (failing) {
                throw new SQLiteFullException("full");
            }
            return mReal.takeAll();
        }
    }
}
