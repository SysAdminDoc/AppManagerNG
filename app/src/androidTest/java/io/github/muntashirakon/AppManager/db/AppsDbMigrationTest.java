// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Sanity tests for the AppsDb v7 -> v8 migration.
 *
 * <p>v8 added the {@code tracker_blocked_count INTEGER NOT NULL DEFAULT 0} column on
 * the {@code app} table so the main list can render an "X/N" or "✓N" tracker
 * blocked-count badge. Critically:
 *   <ul>
 *     <li>existing rows must preserve every column they had at v7,</li>
 *     <li>the new column must default to 0 for those rows (no NULLs, no random
 *         values),</li>
 *     <li>Room must validate the resulting schema against
 *         {@code app/schemas/.../8.json} without raising an
 *         {@code IllegalStateException}.</li>
 *   </ul>
 *
 * <p>If any of these regressions, the v7 -> v8 migration would either drop user
 * data or fail the runtime schema check on first launch after upgrade.
 *
 * <p>Schemas are auto-exported by the Room compiler and live at
 * {@code app/schemas/io.github.muntashirakon.AppManager.db.AppsDb/}; the test
 * source set already maps that directory into androidTest assets via
 * {@code app/build.gradle}.
 */
@RunWith(AndroidJUnit4.class)
public class AppsDbMigrationTest {
    private static final String TEST_DB = "apps-migration-test";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            AppsDb.class,
            java.util.Collections.emptyList(),
            new FrameworkSQLiteOpenHelperFactory());

    /**
     * Inserts a row at v7, runs the v7 -> v8 migration, and asserts that the row
     * survives with its existing columns intact and the new column defaulted to 0.
     */
    @Test
    public void migrate7To8_preservesRow_andDefaultsTrackerBlockedCount() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);
        // Minimal row covering the columns we'll re-read post-migration. The full
        // app table has many more columns; we only need a representative subset
        // that proves the row wasn't dropped.
        db.execSQL("INSERT INTO `app` (`package_name`, `user_id`, `label`, `version_name`, "
                + "`version_code`, `flags`, `uid`, `is_installed`, `tracker_count`, `rules_count`) "
                + "VALUES ('com.example.test', 0, 'Test', '1.0', 1, 0, 10000, 1, 5, 0)");
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true,
                AppsDb.M_7_8);

        try (android.database.Cursor c = migrated.query(
                "SELECT package_name, tracker_count, tracker_blocked_count FROM app "
                        + "WHERE package_name = 'com.example.test'")) {
            assertTrue("row must survive migration", c.moveToFirst());
            assertEquals("com.example.test", c.getString(0));
            assertEquals("tracker_count must be preserved", 5, c.getInt(1));
            assertEquals("tracker_blocked_count must default to 0", 0, c.getInt(2));
        }
        migrated.close();
    }

    /**
     * Opens a v7 db with no rows and applies the migration. Useful for catching a
     * regression where the ALTER TABLE itself fails on an empty schema (would
     * affect fresh-install device upgrades that haven't yet populated app rows).
     */
    @Test
    public void migrate7To8_emptyDb_addsColumn() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true,
                AppsDb.M_7_8);

        try (android.database.Cursor c = migrated.query("PRAGMA table_info(`app`)")) {
            boolean foundNewColumn = false;
            while (c.moveToNext()) {
                String columnName = c.getString(c.getColumnIndexOrThrow("name"));
                if ("tracker_blocked_count".equals(columnName)) {
                    foundNewColumn = true;
                    String defaultValue = c.getString(c.getColumnIndexOrThrow("dflt_value"));
                    int notNull = c.getInt(c.getColumnIndexOrThrow("notnull"));
                    assertNotNull("new column must declare a default", defaultValue);
                    assertEquals("default value must be 0", "0", defaultValue);
                    assertEquals("new column must be NOT NULL", 1, notNull);
                    break;
                }
            }
            assertTrue("v8 must add tracker_blocked_count to app table", foundNewColumn);
        }
        migrated.close();
    }

    /**
     * Smoke test: open the database via the regular Room builder after migration
     * to make sure the runtime schema validator agrees with the migrated state
     * (catches ALTER TABLE that's column-correct but type-mismatched against the
     * compiled entity).
     */
    @Test
    public void migrate7To8_runtimeRoomOpenSucceeds() throws IOException {
        helper.createDatabase(TEST_DB, 7).close();
        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppsDb.M_7_8).close();

        // Open via Room itself; throws on schema mismatch.
        AppsDb db = Room.databaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppsDb.class,
                        TEST_DB)
                .addMigrations(AppsDb.M_2_3, AppsDb.M_3_4, AppsDb.M_4_5,
                        AppsDb.M_5_6, AppsDb.M_6_7, AppsDb.M_7_8)
                .build();
        // Force Room to materialize and validate the schema.
        assertNotNull(db.appDao());
        db.close();
    }
}
