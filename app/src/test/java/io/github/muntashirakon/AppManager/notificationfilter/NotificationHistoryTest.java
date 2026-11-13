// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class NotificationHistoryTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long NOW = 400 * DAY;

    private NotificationHistoryDb mDb;
    private final AtomicLong mRetention = new AtomicLong(7 * DAY);
    private NotificationHistory mHistory;

    @Before
    public void setUp() {
        mDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NotificationHistoryDb.class)
                .allowMainThreadQueries()
                .build();
        mHistory = new NotificationHistory(mDb.dao(), Runnable::run, mRetention::get, () -> NOW);
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @NonNull
    private static NotificationHistoryEntry entry(long at, @NonNull String text) {
        return new NotificationHistoryEntry(at, "com.example.chat", "Chat",
                new NotificationFields("Carol", text, "Carol"), "SILENT", "Quiet Carol", false);
    }

    @NonNull
    private static List<String> texts(@NonNull List<NotificationHistoryEntry> entries) {
        List<String> texts = new ArrayList<>();
        for (NotificationHistoryEntry entry : entries) {
            texts.add(entry.text);
        }
        return texts;
    }

    @Test
    public void entryKeepsEveryFieldAndListsNewestFirst() {
        mHistory.record(entry(NOW - 2_000, "older"));
        mHistory.record(new NotificationHistoryEntry(NOW - 1_000, "com.example.shop", "Shop",
                new NotificationFields("Sale", "newer", "Shop"), "VIBRATE", null, true));

        List<NotificationHistoryEntry> all = mHistory.readAll();

        assertEquals(Arrays.asList("newer", "older"), texts(all));
        NotificationHistoryEntry newest = all.get(0);
        assertEquals(NOW - 1_000, newest.timestampMillis);
        assertEquals("com.example.shop", newest.packageName);
        assertEquals("Shop", newest.appLabel);
        assertEquals("Sale", newest.title);
        assertEquals("Shop", newest.sender);
        assertEquals("VIBRATE", newest.action);
        assertEquals(null, newest.ruleName);
        assertTrue(newest.byQuietMode);
        assertEquals("Quiet Carol", all.get(1).ruleName);
    }

    @Test
    public void entriesPastTheRetentionAreDropped() {
        mDb.dao().insert(entry(NOW - 8 * DAY, "last week"));
        mDb.dao().insert(entry(NOW - 6 * DAY, "this week"));

        assertEquals(Arrays.asList("this week"), texts(mHistory.readAll()));
        assertEquals(1, mDb.dao().count());
    }

    @Test
    public void recordingAlsoDropsWhatIsTooOld() {
        mDb.dao().insert(entry(NOW - 30 * DAY, "last month"));

        mHistory.record(entry(NOW, "now"));

        assertEquals(1, mDb.dao().count());
    }

    @Test
    public void shorterRetentionAppliesAtOnceOnPrune() {
        mHistory.record(entry(NOW - 2 * DAY, "two days ago"));
        mHistory.record(entry(NOW - 60_000, "a minute ago"));

        mRetention.set(DAY);
        mHistory.prune();

        assertEquals(1, mDb.dao().count());
        assertEquals(Arrays.asList("a minute ago"), texts(mDb.dao().getAll()));
    }

    @Test
    public void longerRetentionKeepsMore() {
        mRetention.set(30 * DAY);
        mDb.dao().insert(entry(NOW - 20 * DAY, "three weeks ago"));

        assertEquals(1, mHistory.readAll().size());
    }

    @Test
    public void onlyTheNewestEntriesAreKeptPastTheLimit() {
        for (int i = 0; i < 5; ++i) {
            mDb.dao().insert(entry(NOW - 5_000 + i, "entry " + i));
        }

        mDb.dao().prune(0, 2);

        assertEquals(Arrays.asList("entry 4", "entry 3"), texts(mDb.dao().getAll()));
    }

    @Test
    public void clearHandsBackWhatItRemovedAndRestorePutsItBack() {
        mHistory.record(entry(NOW - 2_000, "first"));
        mHistory.record(entry(NOW - 1_000, "second"));
        AtomicReference<List<NotificationHistoryEntry>> removed = new AtomicReference<>();

        mHistory.clear(removed::set);

        assertNotNull(removed.get());
        assertEquals(Arrays.asList("second", "first"), texts(removed.get()));
        assertEquals(0, mDb.dao().count());

        mHistory.record(entry(NOW, "after clear"));
        mHistory.restore(removed.get());

        assertEquals(Arrays.asList("after clear", "second", "first"), texts(mHistory.readAll()));
    }
}
