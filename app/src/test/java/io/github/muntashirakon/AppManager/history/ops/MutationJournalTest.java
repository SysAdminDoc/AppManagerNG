// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.MutationImage;

/**
 * The journal's promises: rows chain under a per-install key, a row changed or removed outside the app stops
 * verifying, pruning the oldest rows keeps the rest trusted, and the chain still verifies after a restart.
 */
@RunWith(RobolectricTestRunner.class)
public class MutationJournalTest {
    private static final String DB_NAME = "mutation-journal-test.db";
    private static final byte[] KEY = keyOf(1);
    private static final byte[] OTHER_KEY = keyOf(2);

    private Application mContext;
    private AppsDb mDb;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.deleteDatabase(DB_NAME);
        mDb = open();
        MutationJournal.forgetCachedKey();
        deleteKeyFile();
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
        mContext.deleteDatabase(DB_NAME);
        MutationJournal.forgetCachedKey();
        deleteKeyFile();
    }

    @Test
    public void appendedRowsChainAndAllVerify() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 101));
        journal.append(image("b", "android.permission.CAMERA", "granted", "denied", 200));

        List<MutationJournal.Entry> entries = journal.readAll();

        assertEquals(3, entries.size());
        assertEquals("", entries.get(0).image.prevMac);
        assertEquals(entries.get(0).image.mac, entries.get(1).image.prevMac);
        assertEquals(entries.get(1).image.mac, entries.get(2).image.prevMac);
        for (MutationJournal.Entry entry : entries) {
            assertTrue(entry.trusted);
        }
    }

    @Test
    public void theDatabaseRefusesToChangeAStoredRow() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));

        try {
            db().execSQL("UPDATE mutation_image SET post_value = 'denied'");
            fail("the append-only trigger let an UPDATE through");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("append-only"));
        }
        assertEquals("granted", journal.readAll().get(0).image.postValue);
    }

    @Test
    public void aRowEditedPastTheTriggerNoLongerVerifies() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 101));
        journal.append(image("a", "android.permission.READ_CONTACTS", "denied", "granted", 102));

        db().execSQL("DROP TRIGGER mutation_image_append_only");
        db().execSQL("UPDATE mutation_image SET before_value = 'granted' WHERE target = 'android.permission.RECORD_AUDIO'");
        List<MutationJournal.Entry> entries = journal.readAll();

        assertTrue(entries.get(0).trusted);
        assertFalse(entries.get(1).trusted);
        // Its stored MAC is untouched, so the next row still links to it and verifies on its own
        assertTrue(entries.get(2).trusted);
    }

    @Test
    public void aRowResignedWithoutTheInstallKeyFailsAndBreaksTheNextLink() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 101));
        journal.append(image("a", "android.permission.READ_CONTACTS", "denied", "granted", 102));
        MutationImage forged = journal.readAll().get(1).image;
        forged.beforeValue = "granted";
        String forgedMac = MutationJournal.mac(OTHER_KEY, forged);

        db().execSQL("DROP TRIGGER mutation_image_append_only");
        db().execSQL("UPDATE mutation_image SET before_value = 'granted', mac = ? WHERE id = ?",
                new Object[]{forgedMac, forged.id});
        List<MutationJournal.Entry> entries = journal.readAll();

        assertTrue(entries.get(0).trusted);
        assertFalse(entries.get(1).trusted);
        assertFalse(entries.get(2).trusted);
    }

    @Test
    public void aRowDeletedFromTheMiddleBreaksTheLinkOfTheNext() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        long middle = journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 101));
        journal.append(image("a", "android.permission.READ_CONTACTS", "denied", "granted", 102));

        db().execSQL("DELETE FROM mutation_image WHERE id = ?", new Object[]{middle});
        List<MutationJournal.Entry> entries = journal.readAll();

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).trusted);
        assertFalse(entries.get(1).trusted);
    }

    @Test
    public void pruningTheOldestRowsKeepsTheRestTrusted() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 200));
        journal.append(image("b", "android.permission.CAMERA", "granted", "denied", 300));
        journal.append(image("c", "android.permission.CAMERA", "denied", "granted", 400));

        assertEquals(2, journal.prune(250));
        List<MutationJournal.Entry> entries = journal.readAll();

        assertEquals(2, entries.size());
        assertEquals(300, entries.get(0).image.time);
        assertTrue(entries.get(0).trusted);
        assertTrue(entries.get(1).trusted);
        // New rows still chain onto what's left
        journal.append(image("d", "android.permission.CAMERA", "denied", "granted", 500));
        for (MutationJournal.Entry entry : journal.readAll()) {
            assertTrue(entry.trusted);
        }
    }

    @Test
    public void pruningStopsAtTheFirstRowInsideTheWindowSoNoGapOpens() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 300));
        // The clock went back between these two
        journal.append(image("b", "android.permission.CAMERA", "granted", "denied", 200));

        assertEquals(1, journal.prune(250));
        List<MutationJournal.Entry> entries = journal.readAll();

        assertEquals(2, entries.size());
        assertEquals(200, entries.get(1).image.time);
        assertTrue(entries.get(0).trusted);
        assertTrue(entries.get(1).trusted);
    }

    @Test
    public void pruningPastEveryRowEmptiesTheJournal() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 200));

        assertEquals(2, journal.prune(1000));
        assertTrue(journal.readAll().isEmpty());
    }

    @Test
    public void clearEmptiesTheJournal() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));

        journal.clear();

        assertTrue(journal.readAll().isEmpty());
        journal.append(image("a", "android.permission.CAMERA", "granted", "denied", 200));
        List<MutationJournal.Entry> entries = journal.readAll();
        assertEquals("", entries.get(0).image.prevMac);
        assertTrue(entries.get(0).trusted);
    }

    @Test
    public void rowsStillVerifyAfterARestartWithTheSavedKey() {
        byte[] key = MutationJournal.loadKey(mContext);
        new MutationJournal(mDb, key).append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        new MutationJournal(mDb, key).append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 101));
        mDb.close();

        MutationJournal.forgetCachedKey();
        mDb = open();
        byte[] reloaded = MutationJournal.loadKey(mContext);
        List<MutationJournal.Entry> entries = new MutationJournal(mDb, reloaded).readFor("com.example.a", 0);

        assertArrayEquals(key, reloaded);
        assertEquals(32, keyFile().length());
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).trusted);
        assertTrue(entries.get(1).trusted);
    }

    @Test
    public void rowsSignedWithAnotherKeyDoNotVerify() {
        new MutationJournal(mDb, KEY).append(image("a", "android.permission.CAMERA", "denied", "granted", 100));

        List<MutationJournal.Entry> entries = new MutationJournal(mDb, OTHER_KEY).readAll();

        assertFalse(entries.get(0).trusted);
    }

    @Test
    public void aKeyFileOfTheWrongLengthIsReplacedNotTrusted() throws Exception {
        File file = keyFile();
        File dir = file.getParentFile();
        if (dir != null && !dir.mkdirs() && !dir.isDirectory()) {
            fail("could not make " + dir);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[16]);
        }

        byte[] key = MutationJournal.loadKey(mContext);

        assertEquals(32, key.length);
        assertEquals(32, file.length());
        assertNotEquals(0, anyBits(key));
    }

    @Test
    public void readForKeepsOnlyOneAppButJudgesTheWholeChain() {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 100));
        long other = journal.append(image("b", "android.permission.CAMERA", "denied", "granted", 101));
        journal.append(image("a", "android.permission.RECORD_AUDIO", "denied", "granted", 102));

        db().execSQL("DELETE FROM mutation_image WHERE id = ?", new Object[]{other});
        List<MutationJournal.Entry> entries = journal.readFor("com.example.a", 0);

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).trusted);
        assertFalse(entries.get(1).trusted);
        assertTrue(journal.readFor("com.example.a", 10).isEmpty());
    }

    @Test
    public void exportCarriesEveryFieldAndWhetherTheRowVerified() throws Exception {
        MutationJournal journal = new MutationJournal(mDb, KEY);
        MutationImage unread = image("a", "61", null, null, 100);
        unread.kind = MutationState.KIND_APP_OP;
        unread.requestedValue = "1";
        unread.result = MutationJournal.RESULT_UNCONFIRMED;
        journal.append(unread);
        journal.append(image("a", "android.permission.CAMERA", "denied", "granted", 101));
        db().execSQL("DROP TRIGGER mutation_image_append_only");
        db().execSQL("UPDATE mutation_image SET result = 'failed' WHERE target = 'android.permission.CAMERA'");

        JSONArray exported = MutationJournal.toJson(journal.readAll());

        assertEquals(2, exported.length());
        JSONObject first = exported.getJSONObject(0);
        assertEquals("exec-a", first.getString("execution_id"));
        assertEquals("root", first.getString("executor"));
        assertEquals("batch:1", first.getString("operation"));
        assertEquals("com.example.a", first.getString("package_name"));
        assertEquals(0, first.getInt("user_id"));
        assertEquals(MutationState.KIND_APP_OP, first.getString("kind"));
        assertEquals("61", first.getString("target"));
        assertTrue(first.isNull("before_value"));
        assertEquals("1", first.getString("requested_value"));
        assertTrue(first.isNull("post_value"));
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, first.getString("result"));
        assertEquals(100, first.getLong("time"));
        assertEquals("", first.getString("prev_mac"));
        assertEquals(64, first.getString("mac").length());
        assertTrue(first.getBoolean("verified"));
        assertFalse(exported.getJSONObject(1).getBoolean("verified"));
    }

    @NonNull
    private AppsDb open() {
        return Room.databaseBuilder(mContext, AppsDb.class, DB_NAME)
                .addCallback(AppsDb.APPEND_ONLY_GUARD)
                .allowMainThreadQueries()
                .build();
    }

    @NonNull
    private SupportSQLiteDatabase db() {
        return mDb.getOpenHelper().getWritableDatabase();
    }

    @NonNull
    private File keyFile() {
        return new File(mContext.getFilesDir(), MutationJournal.KEY_FILE);
    }

    private void deleteKeyFile() {
        //noinspection ResultOfMethodCallIgnored
        keyFile().delete();
    }

    /** A permission image of {@code com.example.<app>}, written by execution {@code exec-<app>}. */
    @NonNull
    private static MutationImage image(@NonNull String app, @NonNull String target, @Nullable String before,
                                       @Nullable String post, long time) {
        MutationImage image = new MutationImage();
        image.executionId = "exec-" + app;
        image.executor = "root";
        image.operation = "batch:1";
        image.packageName = "com.example." + app;
        image.userId = 0;
        image.kind = MutationState.KIND_PERMISSION;
        image.target = target;
        image.beforeValue = before;
        image.requestedValue = post != null ? post : MutationState.GRANTED;
        image.postValue = post;
        image.result = MutationJournal.RESULT_APPLIED;
        image.time = time;
        return image;
    }

    @NonNull
    private static byte[] keyOf(int seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; ++i) {
            key[i] = (byte) (seed * 31 + i);
        }
        return key;
    }

    private static int anyBits(@NonNull byte[] bytes) {
        int bits = 0;
        for (byte b : bytes) {
            bits |= b;
        }
        return bits;
    }
}
