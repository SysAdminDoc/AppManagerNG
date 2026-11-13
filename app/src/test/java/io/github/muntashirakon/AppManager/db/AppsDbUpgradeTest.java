// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.Room;
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
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.App;

/**
 * Opens an apps database built from an exported older schema with the app's own migrations and no destructive
 * fallback, so Room's schema check fails the test if a migration doesn't produce what the entities describe.
 */
@RunWith(RobolectricTestRunner.class)
public class AppsDbUpgradeTest {
    private static final String DB_NAME = "apps-migration-test.db";
    private static final String SCHEMAS = "schemas/io.github.muntashirakon.AppManager.db.AppsDb";

    private Context mContext;
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
