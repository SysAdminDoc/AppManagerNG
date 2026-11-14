// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Opens apps databases built from the exported older schemas with the app's own migrations and no destructive
 * fallback, so Room's schema check fails the test if a migration doesn't produce what the entities describe, and
 * checks that a file Room can't open is copied aside intact instead of being dropped.
 */
@RunWith(RobolectricTestRunner.class)
public class AppsDbUpgradeTest {
    private static final String DB_NAME = "apps-migration-test.db";
    private static final String SCHEMAS = "schemas/io.github.muntashirakon.AppManager.db.AppsDb";
    private static final String PKG = "com.example.app";
    private static final String[] SIDECARS = {"", "-wal", "-shm", "-journal"};

    private Application mContext;
    private AppsDb mDb;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        shadowOf(mContext).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mContext.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
        mContext.deleteDatabase(DB_NAME);
        // Both outlive a test: the count is a file outside Robolectric's files folder, and the notice can sit in memory
        AppsDbRecovery.clearFailedOpens(mContext, DB_NAME);
        for (int i = 0; i < 3; ++i) {
            String notice = AppsDbRecovery.peekUnseenNotice(mContext);
            if (notice != null) {
                AppsDbRecovery.dismissUnseenNotice(mContext, notice);
            }
        }
    }

    @Test
    public void anAppSavedAtVersion12SurvivesWithNoAdSourceSignalsYet() throws IOException, JSONException {
        try (SQLiteDatabase db = createFromSchema(12)) {
            db.execSQL("INSERT INTO `app` (`package_name`, `user_id`, `label`, `version_name`, `version_code`, "
                    + "`flags`, `uid`, `is_installed`, `tracker_count`, `dangerous_perm_granted`) "
                    + "VALUES ('com.example.app', 0, 'Example', '1.0', 1, 0, 10123, 1, 5, 2)");
        }

        mDb = Room.databaseBuilder(mContext, AppsDb.class, DB_NAME)
                .addMigrations(AppsDb.MIGRATIONS)
                .allowMainThreadQueries()
                .build();
        List<App> apps = mDb.appDao().getAll();

        assertEquals(1, apps.size());
        App app = apps.get(0);
        assertEquals("com.example.app", app.packageName);
        assertEquals(5, app.trackerCount);
        assertEquals(2, app.dangerousPermGranted);
        assertEquals(0, app.adSourceSignals);
    }

    @Test
    public void everyOlderVersionKeepsARowInEveryTableItHad() throws IOException, JSONException {
        for (int version = 1; version < AppsDb.VERSION; ++version) {
            mContext.deleteDatabase(DB_NAME);
            List<String> tables;
            try (SQLiteDatabase db = createFromSchema(version)) {
                tables = insertOneRowEach(db);
            }

            mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
            SupportSQLiteDatabase db = mDb.getOpenHelper().getReadableDatabase();
            assertEquals("version " + version, AppsDb.VERSION, db.getVersion());
            for (String table : tables) {
                assertEquals("version " + version + ", table " + table, 1,
                        longOf(db, "SELECT COUNT(*) FROM `" + table + "`"));
            }
            assertEquals("version " + version, 5, longOf(db, "SELECT tracker_count FROM app WHERE package_name = '"
                    + PKG + "'"));
            assertEquals("version " + version, 0, longOf(db, "SELECT ad_source_signals FROM app"));
            assertTrue("version " + version, recoveryCopies().isEmpty());
            assertNull("version " + version, shownNotification());
            mDb.close();
            mDb = null;
        }
    }

    @Test
    public void aNewerFileIsCopiedAsideIntactAndTheUserIsTold() throws Exception {
        try (SQLiteDatabase db = createFromSchema(AppsDb.VERSION)) {
            insertOneRowEach(db);
            db.setVersion(AppsDb.VERSION + 1);
        }
        byte[] original = sha256(mContext.getDatabasePath(DB_NAME));

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        SupportSQLiteDatabase db = mDb.getOpenHelper().getReadableDatabase();
        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(0, longOf(db, "SELECT COUNT(*) FROM app"));
        File copy = onlyRecoveredDbFile();
        assertArrayEquals(original, sha256(copy));
        assertEquals(1, rowsIn(copy, "app_note"));
        assertNotNull(shownNotification());
    }

    @Test
    public void aVersionWithNoMigrationPathIsCopiedAsideNotDropped() throws Exception {
        try (SQLiteDatabase db = createFromSchema(1)) {
            insertOneRowEach(db);
        }
        byte[] original = sha256(mContext.getDatabasePath(DB_NAME));
        Migration[] withoutFirst = Arrays.copyOfRange(AppsDb.MIGRATIONS, 1, AppsDb.MIGRATIONS.length);

        mDb = AppsDb.open(mContext, DB_NAME, withoutFirst);

        assertEquals(0, longOf(mDb.getOpenHelper().getReadableDatabase(), "SELECT COUNT(*) FROM app"));
        File copy = onlyRecoveredDbFile();
        assertArrayEquals(original, sha256(copy));
        assertEquals(1, rowsIn(copy, "backup"));
    }

    @Test
    public void aMigrationRoomRejectsLeavesTheOriginalCopiedAside() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        Migration[] broken = AppsDb.MIGRATIONS.clone();
        broken[broken.length - 1] = new Migration(12, 13) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                // Leaves out the column version 13 needs, so Room's schema check fails.
            }
        };

        byte[] original = sha256(mContext.getDatabasePath(DB_NAME));

        mDb = AppsDb.open(mContext, DB_NAME, broken);

        assertEquals(0, longOf(mDb.getOpenHelper().getReadableDatabase(), "SELECT COUNT(*) FROM app_note"));
        // The copy is made before Room opens the file and switches it to WAL, so it's the file exactly as it was.
        File copy = onlyRecoveredDbFile();
        assertArrayEquals(original, sha256(copy));
        assertEquals(12, versionOf(copy));
        assertEquals(1, rowsIn(copy, "freeze_type"));
        assertEquals(1, rowsIn(copy, "app_note"));
        assertNotNull(shownNotification());
    }

    @Test
    public void aMigrationWhoseSqlFailsIsCopiedAsideAndReplaced() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        byte[] original = sha256(mContext.getDatabasePath(DB_NAME));
        Migration[] broken = AppsDb.MIGRATIONS.clone();
        broken[broken.length - 1] = new Migration(12, 13) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                db.execSQL("ALTER TABLE `app` ADD COLUMN `tracker_count` INTEGER NOT NULL DEFAULT 0");
            }
        };

        mDb = AppsDb.open(mContext, DB_NAME, broken);

        // A database that fails on every query would be worse than a fresh one, as long as the old one is kept.
        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(0, longOf(db, "SELECT COUNT(*) FROM app_note"));
        File copy = onlyRecoveredDbFile();
        assertArrayEquals(original, sha256(copy));
        assertNotNull(shownNotification());
    }

    @Test
    public void aCorruptFileIsCopiedAsideAndTheUserIsTold() throws Exception {
        File dbFile = mContext.getDatabasePath(DB_NAME);
        assertTrue(dbFile.getParentFile().isDirectory() || dbFile.getParentFile().mkdirs());
        byte[] garbage = new byte[8192];
        Arrays.fill(garbage, (byte) 'x');
        Files.write(dbFile.toPath(), garbage);
        assertEquals(AppsDbRecovery.UNKNOWN_VERSION, AppsDbRecovery.readVersion(dbFile));

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(0, longOf(db, "SELECT COUNT(*) FROM app"));
        assertArrayEquals(garbage, Files.readAllBytes(onlyRecoveredDbFile().toPath()));
        assertNotNull(shownNotification());
    }

    @Test
    public void whenTheCopyBeforeAnUpgradeFailsTheFileIsNotTouched() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        File dbFile = mContext.getDatabasePath(DB_NAME);
        byte[] original = sha256(dbFile);
        File blocker = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        assertTrue(blocker.getParentFile().isDirectory() || blocker.getParentFile().mkdirs());
        assertTrue(blocker.createNewFile());
        try {
            mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

            SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
            assertEquals(0, longOf(db, "SELECT COUNT(*) FROM app_note"));
            // Room never opened it, so it's still version 12, byte for byte, with no WAL beside it.
            assertArrayEquals(original, sha256(dbFile));
            assertFalse(new File(dbFile.getPath() + "-wal").exists());
            assertEquals(12, AppsDbRecovery.readVersion(dbFile));
            // The file reads fine, so the message mustn't say it couldn't be read
            assertEquals(mContext.getString(R.string.apps_db_upgrade_skipped_text), textOf(shownNotification()));
        } finally {
            assertTrue(blocker.delete());
        }
    }

    @Test
    public void withNotificationsOffTheMessageWaitsForTheApp() throws Exception {
        shadowOf(mContext).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (SQLiteDatabase db = createFromSchema(AppsDb.VERSION)) {
            insertOneRowEach(db);
            db.setVersion(AppsDb.VERSION + 1);
        }

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        assertNull(shownNotification());
        String notice = AppsDb.peekUnseenNotice(mContext);
        assertNotNull(notice);
        assertTrue(notice, notice.contains(onlyRecoveredDbFile().getParent()));
        // A screen that closes before the user sees it loses nothing
        assertEquals(notice, AppsDb.peekUnseenNotice(mContext));
        AppsDb.dismissUnseenNotice(mContext, notice);
        assertNull(AppsDb.peekUnseenNotice(mContext));
    }

    @Test
    public void dismissingAMessageKeepsANewerOne() {
        AppsDbRecovery.saveUnseenNotice(mContext, "first");
        String shown = AppsDb.peekUnseenNotice(mContext);
        AppsDbRecovery.saveUnseenNotice(mContext, "second");

        AppsDb.dismissUnseenNotice(mContext, shown);

        assertEquals("second", AppsDb.peekUnseenNotice(mContext));
    }

    @Test
    public void aMessageThatCantBeWrittenWaitsInMemory() throws Exception {
        File blocker = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        assertTrue(blocker.getParentFile().isDirectory() || blocker.getParentFile().mkdirs());
        assertTrue(blocker.createNewFile());
        try {
            AppsDbRecovery.saveUnseenNotice(mContext, "kept");

            assertEquals("kept", AppsDb.peekUnseenNotice(mContext));
            AppsDb.dismissUnseenNotice(mContext, "kept");
            assertNull(AppsDb.peekUnseenNotice(mContext));
        } finally {
            assertTrue(blocker.delete());
        }
    }

    @Test
    public void aNotificationThatThrowsLeavesTheMessageForTheApp() throws Exception {
        try (SQLiteDatabase db = createFromSchema(AppsDb.VERSION)) {
            insertOneRowEach(db);
            db.setVersion(AppsDb.VERSION + 1);
        }
        // As on systems that throw from notify() even with notifications allowed
        AppsDbRecovery.sNotifierForTest = text -> {
            throw new SecurityException("not allowed to post");
        };
        try {
            mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
        } finally {
            AppsDbRecovery.sNotifierForTest = null;
        }

        assertNull(shownNotification());
        String notice = AppsDb.peekUnseenNotice(mContext);
        assertNotNull(notice);
        assertTrue(notice, notice.contains(onlyRecoveredDbFile().getParent()));
    }

    @Test
    public void aCopyThatFailsHalfwayLeavesNoFolderBehind() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        File dbFile = mContext.getDatabasePath(DB_NAME);
        Files.write(new File(dbFile.getPath() + "-journal").toPath(), new byte[]{1, 2, 3});
        int[] copied = {0};
        try {
            AppsDbRecovery.preserve(mContext, DB_NAME, 12, "test", false, (source, target) -> {
                if (++copied[0] == 2) {
                    throw new IOException("disk full");
                }
                Files.copy(source.toPath(), target.toPath());
            });
            throw new AssertionError("The copy should have failed");
        } catch (IOException expected) {
            assertEquals(2, copied[0]);
        } finally {
            assertTrue(new File(dbFile.getPath() + "-journal").delete());
        }
        File[] left = new File(mContext.getFilesDir(), AppsDbRecovery.DIR).listFiles();
        assertTrue(Arrays.toString(left), left == null || left.length == 0);
    }

    @Test
    public void onlyTheNewestCopiesAreKept() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        File root = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        List<File> old = new ArrayList<>();
        for (int i = 1; i <= AppsDbRecovery.COPIES_KEPT + 1; ++i) {
            File dir = new File(root, DB_NAME + "-20200101-000000-00" + i + "-v11");
            assertTrue(dir.mkdirs());
            assertTrue(new File(dir, DB_NAME).createNewFile());
            old.add(dir);
        }
        File other = new File(root, "other.db-20200101-000000-000-v1");
        assertTrue(other.mkdirs());

        File copy = AppsDbRecovery.preserve(mContext, DB_NAME, 12, "test");

        assertTrue(copy.isDirectory());
        assertTrue(other.isDirectory());
        int kept = 0;
        for (File dir : old) {
            kept += dir.exists() ? 1 : 0;
        }
        assertEquals(AppsDbRecovery.COPIES_KEPT - 1, kept);
        assertFalse(old.get(0).exists());
        assertTrue(old.get(old.size() - 1).exists());
    }

    @Test
    public void aNewCopyIsKeptEvenWhenTheClockWentBack() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        // Copies stamped later than now, as after the clock was set back.
        List<File> later = keptCopies("20990101-000000-00");

        File copy = AppsDbRecovery.preserve(mContext, DB_NAME, 12, "test");

        assertTrue(new File(copy, DB_NAME).isFile());
        assertFalse(later.get(0).exists());
        assertTrue(later.get(1).exists());
        assertTrue(later.get(2).exists());
        assertEquals(AppsDbRecovery.COPIES_KEPT, recoveryCopies().size());
    }

    @Test
    public void anUpgradeThatWorksLeavesEarlierCopiesAloneAndClearsWhatAKilledRunLeft() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        List<File> earlier = keptCopies("20200101-000000-00");
        File root = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        File halfMade = new File(root, DB_NAME + "-20200102-000000-000-v12.partial");
        File neverKept = new File(root, DB_NAME + "-20200102-000000-000-v12.upgrading");
        assertTrue(halfMade.mkdirs());
        assertTrue(neverKept.mkdirs());

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        assertEquals(1, longOf(mDb.getOpenHelper().getReadableDatabase(), "SELECT COUNT(*) FROM app_note"));
        // The copy made before the upgrade didn't push any of them out, and it's gone now the upgrade worked.
        for (File dir : earlier) {
            assertTrue(dir.getName(), dir.isDirectory());
        }
        assertFalse(halfMade.exists());
        assertFalse(neverKept.exists());
        assertEquals(Arrays.toString(root.list()), earlier.size(), root.list().length);
        assertNull(shownNotification());
    }

    @Test
    public void aFailedUpgradesCopyIsKeptWithTheNewestEarlierOnes() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        List<File> earlier = keptCopies("20200101-000000-00");
        Migration[] broken = AppsDb.MIGRATIONS.clone();
        broken[broken.length - 1] = new Migration(12, 13) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                // Leaves out the column version 13 needs, so Room's schema check fails.
            }
        };

        mDb = AppsDb.open(mContext, DB_NAME, broken);

        File[] dirs = new File(mContext.getFilesDir(), AppsDbRecovery.DIR).listFiles();
        assertNotNull(dirs);
        assertEquals(Arrays.toString(dirs), AppsDbRecovery.COPIES_KEPT, dirs.length);
        assertFalse(earlier.get(0).exists());
        File kept = null;
        for (File dir : dirs) {
            if (!earlier.contains(dir)) {
                kept = dir;
            }
        }
        assertNotNull(kept);
        assertFalse(kept.getName(), kept.getName().endsWith(".upgrading"));
        assertTrue(new File(kept, "reason.txt").isFile());
        assertEquals(12, versionOf(new File(kept, DB_NAME)));
        assertNotNull(shownNotification());
    }

    @Test
    public void aCopyThatWentMissingIsNeverPointedAt() {
        File gone = new File(new File(mContext.getFilesDir(), AppsDbRecovery.DIR), DB_NAME + "-x-v12.upgrading");

        assertNull(AppsDbRecovery.keep(mContext, DB_NAME, gone, "test"));
    }

    @Test
    public void aLockedFileIsLeftForRoomToRetryAndNoCopyStaysBehind() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        // Another connection holds the write lock, so neither the version check nor Room's upgrade can read the file.
        SQLiteDatabase holder = SQLiteDatabase.openDatabase(mContext.getDatabasePath(DB_NAME).getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            holder.beginTransaction();
            mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
            assertTrue(recoveryCopies().toString(), recoveryCopies().isEmpty());
        } finally {
            holder.endTransaction();
            holder.close();
        }

        // Room tries again on the next query, and the upgrade goes through with everything kept.
        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(1, longOf(db, "SELECT COUNT(*) FROM app_note"));
        assertTrue(recoveryCopies().isEmpty());
        assertNull(shownNotification());
    }

    @Test
    public void aBlockedAlertsChannelSavesTheMessageForTheApp() throws Exception {
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(NotificationUtils.HIGH_PRIORITY_CHANNEL_ID, "Alerts",
                NotificationManager.IMPORTANCE_NONE));
        try (SQLiteDatabase db = createFromSchema(AppsDb.VERSION)) {
            insertOneRowEach(db);
            db.setVersion(AppsDb.VERSION + 1);
        }

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        assertNull(shownNotification());
        String notice = AppsDb.peekUnseenNotice(mContext);
        assertNotNull(notice);
        assertTrue(notice, notice.contains(onlyRecoveredDbFile().getParent()));
    }

    @Test
    public void onlyTroubleAFreshFileWouldntFixIsLeftForRoomToRetry() {
        assertTrue(AppsDb.isTransient(new SQLiteDatabaseLockedException()));
        assertTrue(AppsDb.isTransient(new SQLiteDiskIOException()));
        assertTrue(AppsDb.isTransient(new SQLiteFullException()));
        assertFalse(AppsDb.isTransient(new SQLiteException("duplicate column name: tracker_count")));
        assertFalse(AppsDb.isTransient(new SQLiteDatabaseCorruptException()));
        assertFalse(AppsDb.isTransient(new IllegalStateException("Migration didn't properly handle: app")));
        // Android 5 to 9 wrap the open's own error
        assertTrue(AppsDb.isTransient(new SQLiteException("Failed to change locale for db",
                new SQLiteCantOpenDatabaseException("unable to open database file"))));
        assertFalse(AppsDb.isTransient(new SQLiteException("Failed to change locale for db",
                new SQLiteDatabaseCorruptException())));
    }

    @Test
    public void aMigrationThatThrowsSomethingElseIsCopiedAsideAndReplaced() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        Migration[] broken = AppsDb.MIGRATIONS.clone();
        broken[broken.length - 1] = new Migration(12, 13) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                throw new NullPointerException("a bug in the migration");
            }
        };

        mDb = AppsDb.open(mContext, DB_NAME, broken);

        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(1, rowsIn(onlyRecoveredDbFile(), "app_note"));
        assertNotNull(shownNotification());
    }

    @Test
    public void openFailingThreeLaunchesInARowIsReported() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        for (int launch = 1; launch <= AppsDbRecovery.FAILED_OPENS_BEFORE_TELLING; ++launch) {
            SQLiteDatabase holder = SQLiteDatabase.openDatabase(mContext.getDatabasePath(DB_NAME).getPath(), null,
                    SQLiteDatabase.OPEN_READWRITE);
            try {
                holder.beginTransaction();
                mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
            } finally {
                holder.endTransaction();
                holder.close();
            }
            mDb.close();
            mDb = null;
            if (launch < AppsDbRecovery.FAILED_OPENS_BEFORE_TELLING) {
                assertNull("launch " + launch, shownNotification());
            }
        }
        assertEquals(mContext.getString(R.string.apps_db_open_failing_text), textOf(shownNotification()));

        // An open that works starts the count again
        ((NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
        assertEquals(1, longOf(mDb.getOpenHelper().getReadableDatabase(), "SELECT COUNT(*) FROM app_note"));
        assertFalse(new File(androidx.core.content.ContextCompat.getNoBackupFilesDir(mContext),
                DB_NAME + ".failed_opens").exists());
    }

    @Test
    public void aCorruptFileThatCantBeCopiedIsLeftInPlaceAndTheUserIsTold() throws Exception {
        File dbFile = mContext.getDatabasePath(DB_NAME);
        assertTrue(dbFile.getParentFile().isDirectory() || dbFile.getParentFile().mkdirs());
        byte[] garbage = new byte[8192];
        Arrays.fill(garbage, (byte) 'x');
        Files.write(dbFile.toPath(), garbage);
        SupportSQLiteOpenHelper helper = AppsDbRecovery.corruptionGuard(mContext, DB_NAME).create(
                SupportSQLiteOpenHelper.Configuration.builder(mContext)
                        .name(DB_NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            }

                            @Override
                            public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                            }
                        })
                        .build());
        File blocker = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        assertTrue(blocker.getParentFile().isDirectory() || blocker.getParentFile().mkdirs());
        assertTrue(blocker.createNewFile());
        try {
            try {
                helper.getWritableDatabase();
            } catch (RuntimeException expected) {
                // SQLite can't use the file, and nothing may delete it
            } finally {
                helper.close();
            }

            assertArrayEquals(garbage, Files.readAllBytes(dbFile.toPath()));
            assertEquals(mContext.getString(R.string.apps_db_corrupt_kept_text), textOf(shownNotification()));
        } finally {
            assertTrue(blocker.delete());
        }
    }

    @Test
    public void aCopyMadeWhileRowsAreOnlyInTheWalHasThem() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        File dbFile = mContext.getDatabasePath(DB_NAME);
        SQLiteDatabase open = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            // One small insert stays in the WAL until this connection closes
            assertTrue(open.enableWriteAheadLogging());
            open.execSQL("INSERT INTO `app_note` (`package_name`, `note`, `updated_at`) "
                    + "VALUES ('com.example.other', 'only in the wal', 1700000000001)");
            File wal = new File(dbFile.getPath() + "-wal");
            assertTrue("the new row sits in the WAL", wal.isFile() && wal.length() > 0);

            File copy = AppsDbRecovery.preserve(mContext, DB_NAME, 12, "test");

            assertEquals(2, rowsIn(new File(copy, DB_NAME), "app_note"));
        } finally {
            open.close();
        }
    }

    @Test
    public void aFileThatChangesAfterItsCopyFailsTheWholeCopy() throws Exception {
        try (SQLiteDatabase db = createFromSchema(12)) {
            insertOneRowEach(db);
        }
        File dbFile = mContext.getDatabasePath(DB_NAME);
        Files.write(new File(dbFile.getPath() + "-journal").toPath(), new byte[]{1, 2, 3});
        try {
            AppsDbRecovery.preserve(mContext, DB_NAME, 12, "test", false, (source, target) -> {
                Files.copy(source.toPath(), target.toPath());
                if (source.getName().endsWith("-journal")) {
                    // An open connection writes the main file, which is already copied
                    try (java.io.RandomAccessFile main = new java.io.RandomAccessFile(dbFile, "rw")) {
                        main.seek(main.length() - 1);
                        int last = main.read();
                        main.seek(main.length() - 1);
                        main.write(last ^ 0xff);
                    }
                }
            });
            throw new AssertionError("The copy should have failed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("changed while it was copied"));
        } finally {
            assertTrue(new File(dbFile.getPath() + "-journal").delete());
        }
        File[] left = new File(mContext.getFilesDir(), AppsDbRecovery.DIR).listFiles();
        assertTrue(Arrays.toString(left), left == null || left.length == 0);
    }

    @Test
    public void whenTheCopyFailsTheFileIsLeftAloneAndTheRunUsesMemory() throws Exception {
        try (SQLiteDatabase db = createFromSchema(AppsDb.VERSION)) {
            insertOneRowEach(db);
            db.setVersion(AppsDb.VERSION + 1);
        }
        File dbFile = mContext.getDatabasePath(DB_NAME);
        byte[] original = sha256(dbFile);
        // A plain file where the recovery folder goes, so the copy can't be made.
        File blocker = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        assertTrue(blocker.getParentFile().isDirectory() || blocker.getParentFile().mkdirs());
        assertTrue(blocker.createNewFile());
        try {
            mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

            SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();
            db.execSQL("INSERT INTO log_filter (name) VALUES ('kept in memory')");
            assertEquals(1, longOf(db, "SELECT COUNT(*) FROM log_filter"));
            assertArrayEquals(original, sha256(dbFile));
            assertEquals(AppsDb.VERSION + 1, AppsDbRecovery.readVersion(dbFile));
            assertNotNull(shownNotification());
        } finally {
            assertTrue(blocker.delete());
        }
    }

    @Test
    public void migrationPathsFollowTheChainRoomWouldTake() {
        Migration[] all = AppsDb.MIGRATIONS;
        for (int version = 1; version <= AppsDb.VERSION; ++version) {
            assertTrue("version " + version, AppsDbRecovery.canMigrate(version, AppsDb.VERSION, all));
        }
        assertTrue(AppsDbRecovery.canMigrate(0, AppsDb.VERSION, all));
        assertTrue(AppsDbRecovery.canMigrate(AppsDbRecovery.UNKNOWN_VERSION, AppsDb.VERSION, all));
        assertFalse(AppsDbRecovery.canMigrate(AppsDb.VERSION + 1, AppsDb.VERSION, all));
        List<Migration> withoutSixToSeven = new ArrayList<>(Arrays.asList(all));
        assertEquals(6, withoutSixToSeven.remove(5).endVersion - 1);
        Migration[] gap = withoutSixToSeven.toArray(new Migration[0]);
        assertFalse(AppsDbRecovery.canMigrate(1, AppsDb.VERSION, gap));
        assertTrue(AppsDbRecovery.canMigrate(7, AppsDb.VERSION, gap));
    }

    /** One row in every table the schema at that version has, using only columns every version shares. */
    @NonNull
    private static List<String> insertOneRowEach(@NonNull SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)) {
            while (c.moveToNext()) {
                tables.add(c.getString(0));
            }
        }
        List<String> filled = new ArrayList<>();
        fill(db, tables, filled, "app", "INSERT INTO `app` (`package_name`, `user_id`, `label`, `version_name`, "
                + "`version_code`, `flags`, `uid`, `is_installed`, `tracker_count`, `rules_count`) "
                + "VALUES ('" + PKG + "', 0, 'Example', '1.0', 7, 0, 10123, 1, 5, 3)");
        fill(db, tables, filled, "log_filter", "INSERT INTO `log_filter` (`name`) VALUES ('my filter')");
        fill(db, tables, filled, "backup", "INSERT INTO `backup` (`package_name`, `backup_name`, `label`, "
                + "`version_name`, `version_code`, `is_system`, `has_splits`, `has_rules`, `backup_time`, `crypto`, "
                + "`meta_version`, `flags`, `user_id`, `tar_type`, `has_key_store`, `installer_app`, `info_hash`) "
                + "VALUES ('" + PKG + "', '0_base', 'Example', '1.0', 7, 0, 0, 0, 1700000000000, 'none', 5, 0, 0, "
                + "'z', 0, 'com.android.vending', 'dir')");
        fill(db, tables, filled, "op_history", "INSERT INTO `op_history` (`type`, `time`, `data`, `status`, "
                + "`extra`) VALUES ('batch', 1700000000000, '{}', 'success', NULL)");
        fill(db, tables, filled, "fm_favorite", "INSERT INTO `fm_favorite` (`name`, `uri`, `init_uri`, `options`, "
                + "`order`, `type`) VALUES ('Downloads', 'file:///sdcard/Download', NULL, 0, 0, 0)");
        fill(db, tables, filled, "freeze_type", "INSERT INTO `freeze_type` (`package_name`, `type`) "
                + "VALUES ('" + PKG + "', 2)");
        fill(db, tables, filled, "cached_scan_result", "INSERT INTO `cached_scan_result` (`package_name`, "
                + "`version_code`, `scan_timestamp`, `tracker_count`, `library_count`, `trackers_json`, "
                + "`libraries_json`) VALUES ('" + PKG + "', 7, 1700000000000, 5, 2, '[]', '[]')");
        fill(db, tables, filled, "app_update_change_report", "INSERT INTO `app_update_change_report` "
                + "(`package_name`, `timestamp_millis`, `before_version_code`, `after_version_code`, "
                + "`added_permissions`, `removed_permissions`, `added_trackers`, `removed_trackers`, "
                + "`added_components`, `removed_components`) "
                + "VALUES ('" + PKG + "', 1700000000000, 6, 7, 'android.permission.CAMERA', '', '', '', '', '')");
        fill(db, tables, filled, "app_note", "INSERT INTO `app_note` (`package_name`, `note`, `updated_at`) "
                + "VALUES ('" + PKG + "', 'keep this', 1700000000000)");
        return filled;
    }

    private static void fill(@NonNull SQLiteDatabase db, @NonNull List<String> tables, @NonNull List<String> filled,
                             @NonNull String table, @NonNull String insert) {
        if (tables.contains(table)) {
            db.execSQL(insert);
            filled.add(table);
        }
    }

    private static long longOf(@NonNull SupportSQLiteDatabase db, @NonNull String query) {
        try (Cursor c = db.query(query)) {
            assertTrue(query, c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static long rowsIn(@NonNull File dbFile, @NonNull String table) throws IOException {
        File atShortPath = atShortPath(dbFile);
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(atShortPath.getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery("SELECT COUNT(*) FROM `" + table + "`", null)) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        } finally {
            deleteWithSidecars(atShortPath);
        }
    }

    private static int versionOf(@NonNull File dbFile) throws IOException {
        File atShortPath = atShortPath(dbFile);
        try {
            return AppsDbRecovery.readVersion(atShortPath);
        } finally {
            deleteWithSidecars(atShortPath);
        }
    }

    /**
     * SQLite's Windows file layer can't open a path over 260 characters, and a recovery copy inside Robolectric's
     * per-test folder runs past that on a Windows host, so the copy is read from a short temp path instead.
     */
    @NonNull
    private static File atShortPath(@NonNull File dbFile) throws IOException {
        File dir = Files.createTempDirectory("amdb").toFile();
        File target = new File(dir, "c.db");
        for (String suffix : SIDECARS) {
            File source = new File(dbFile.getPath() + suffix);
            if (source.isFile()) {
                Files.copy(source.toPath(), new File(target.getPath() + suffix).toPath());
            }
        }
        return target;
    }

    private static void deleteWithSidecars(@NonNull File dbFile) {
        for (String suffix : SIDECARS) {
            //noinspection ResultOfMethodCallIgnored
            new File(dbFile.getPath() + suffix).delete();
        }
        //noinspection ResultOfMethodCallIgnored
        dbFile.getParentFile().delete();
    }

    /** {@link AppsDbRecovery#COPIES_KEPT} kept copies whose names start with the stamp, oldest first. */
    @NonNull
    private List<File> keptCopies(@NonNull String stampPrefix) throws IOException {
        File root = new File(mContext.getFilesDir(), AppsDbRecovery.DIR);
        List<File> copies = new ArrayList<>();
        for (int i = 1; i <= AppsDbRecovery.COPIES_KEPT; ++i) {
            File dir = new File(root, DB_NAME + "-" + stampPrefix + i + "-v11");
            assertTrue(dir.mkdirs());
            assertTrue(new File(dir, DB_NAME).createNewFile());
            copies.add(dir);
        }
        return copies;
    }

    @NonNull
    private List<File> recoveryCopies() {
        List<File> copies = new ArrayList<>();
        File[] dirs = new File(mContext.getFilesDir(), AppsDbRecovery.DIR).listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles((d, name) -> name.equals(DB_NAME));
                if (files != null) {
                    copies.addAll(Arrays.asList(files));
                }
            }
        }
        return copies;
    }

    @NonNull
    private File onlyRecoveredDbFile() {
        List<File> copies = recoveryCopies();
        assertEquals(copies.toString(), 1, copies.size());
        File reason = new File(copies.get(0).getParentFile(), "reason.txt");
        assertTrue(reason.isFile());
        return copies.get(0);
    }

    @Nullable
    private StatusBarNotification shownNotification() {
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        for (StatusBarNotification sbn : manager.getActiveNotifications()) {
            if (AppsDbRecovery.NOTIFICATION_TAG.equals(sbn.getTag())) {
                return sbn;
            }
        }
        return null;
    }

    @Nullable
    private static String textOf(@Nullable StatusBarNotification sbn) {
        assertNotNull("no notification shown", sbn);
        CharSequence text = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        return text != null ? text.toString() : null;
    }

    @NonNull
    private static byte[] sha256(@NonNull File file) throws IOException, NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
    }

    /** Builds the database exactly as Room exported that version's schema. */
    @NonNull
    private SQLiteDatabase createFromSchema(int version) throws IOException, JSONException {
        JSONObject database = new JSONObject(new String(Files.readAllBytes(schemaFile(version).toPath()),
                StandardCharsets.UTF_8)).getJSONObject("database");
        File file = mContext.getDatabasePath(DB_NAME);
        File dir = file.getParentFile();
        if (dir != null && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not make " + dir);
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        JSONArray entities = database.getJSONArray("entities");
        for (int i = 0; i < entities.length(); ++i) {
            JSONObject entity = entities.getJSONObject(i);
            String table = entity.getString("tableName");
            db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
            JSONArray indices = entity.optJSONArray("indices");
            for (int j = 0; indices != null && j < indices.length(); ++j) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
            }
        }
        JSONArray setup = database.getJSONArray("setupQueries");
        for (int i = 0; i < setup.length(); ++i) {
            db.execSQL(setup.getString(i));
        }
        db.setVersion(version);
        return db;
    }

    /** Gradle runs unit tests from the module, and an IDE may run them from the project. */
    @NonNull
    private static File schemaFile(int version) {
        File file = new File(SCHEMAS, version + ".json");
        if (!file.isFile()) {
            file = new File("app", file.getPath());
        }
        assertTrue("missing " + file.getAbsolutePath(), file.isFile());
        return file;
    }
}
