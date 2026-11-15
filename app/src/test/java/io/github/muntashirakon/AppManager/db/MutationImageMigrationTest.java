// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Version 14 adds the mutation image table. An upgraded database and a fresh one both refuse to change a stored
 * image, and the rows saved before the upgrade stay.
 */
@RunWith(RobolectricTestRunner.class)
public class MutationImageMigrationTest {
    private static final String DB_NAME = "mutation-image-migration-test.db";
    private static final String SCHEMAS = "schemas/io.github.muntashirakon.AppManager.db.AppsDb";
    private static final String INSERT = "INSERT INTO mutation_image (execution_id, executor, operation, "
            + "package_name, user_id, kind, target, before_value, requested_value, post_value, result, time, "
            + "prev_mac, mac) VALUES ('exec-a', 'root', 'batch:1', 'com.example.app', 0, 'permission', "
            + "'android.permission.CAMERA', 'denied', 'granted', 'granted', 'applied', 100, '', 'mac')";

    private Application mContext;
    private AppsDb mDb;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
        mContext.deleteDatabase(DB_NAME);
        AppsDbRecovery.clearFailedOpens(mContext, DB_NAME);
    }

    @Test
    public void anUpgradeFromVersion13AddsAnAppendOnlyImageTable() throws Exception {
        try (SQLiteDatabase db = createFromSchema(13)) {
            db.execSQL("INSERT INTO `op_history` (`type`, `time`, `data`, `status`, `extra`) "
                    + "VALUES ('batch', 1700000000000, '{}', 'success', NULL)");
        }

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();

        assertEquals(AppsDb.VERSION, db.getVersion());
        assertEquals(1, longOf(db, "SELECT COUNT(*) FROM op_history"));
        assertEquals(1, longOf(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' "
                + "AND name = 'mutation_image_append_only'"));
        assertAppendOnly(db);
    }

    @Test
    public void aFreshDatabaseIsAppendOnlyToo() {
        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
        SupportSQLiteDatabase db = mDb.getOpenHelper().getWritableDatabase();

        assertEquals(AppsDb.VERSION, db.getVersion());
        assertAppendOnly(db);
    }

    @Test
    public void aDroppedTriggerComesBackOnTheNextOpen() {
        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);
        mDb.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER mutation_image_append_only");
        mDb.close();

        mDb = AppsDb.open(mContext, DB_NAME, AppsDb.MIGRATIONS);

        assertAppendOnly(mDb.getOpenHelper().getWritableDatabase());
    }

    private static void assertAppendOnly(@NonNull SupportSQLiteDatabase db) {
        db.execSQL(INSERT);
        try {
            db.execSQL("UPDATE mutation_image SET post_value = 'denied'");
            fail("the append-only trigger let an UPDATE through");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("append-only"));
        }
        assertEquals(1, longOf(db, "SELECT COUNT(*) FROM mutation_image WHERE post_value = 'granted'"));
        // Pruning and clearing still work
        db.execSQL("DELETE FROM mutation_image");
        assertEquals(0, longOf(db, "SELECT COUNT(*) FROM mutation_image"));
    }

    private static long longOf(@NonNull SupportSQLiteDatabase db, @NonNull String query) {
        try (Cursor c = db.query(query)) {
            assertTrue(query, c.moveToFirst());
            return c.getLong(0);
        }
    }

    @NonNull
    private SQLiteDatabase createFromSchema(int version) throws Exception {
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
