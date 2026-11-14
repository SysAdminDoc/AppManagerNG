// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import android.content.Context;
import android.database.sqlite.SQLiteAccessPermException;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOutOfMemoryException;
import android.database.sqlite.SQLiteReadOnlyDatabaseException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.AppNoteDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.dao.CachedScanResultDao;
import io.github.muntashirakon.AppManager.db.dao.FmFavoriteDao;
import io.github.muntashirakon.AppManager.db.dao.FreezeTypeDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.dao.OpHistoryDao;
import io.github.muntashirakon.AppManager.db.dao.AppUpdateChangeReportDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.AppNote;
import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.CachedScanResult;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.logs.Log;

@Database(entities = {App.class, AppNote.class, LogFilter.class, Backup.class, OpHistory.class, FmFavorite.class, FreezeType.class, CachedScanResult.class, AppUpdateChangeReport.class}, version = AppsDb.VERSION)
public abstract class AppsDb extends RoomDatabase {
    private static final String TAG = AppsDb.class.getSimpleName();

    static final int VERSION = 13;
    static final String NAME = "apps.db";

    private static AppsDb sAppsDb;

    /** v2 adds the storage, data usage, usage stats and keystore columns to {@code app}. */
    public static final Migration M_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `has_keystore` INTEGER NOT NULL DEFAULT false");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `uses_saf` INTEGER NOT NULL DEFAULT false");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `ssaid` TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `code_size` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `data_size` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `mobile_data` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `wifi_data` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `open_count` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `screen_time` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `last_usage_time` INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration M_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `op_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `time` INTEGER NOT NULL, `data` TEXT NOT NULL, `status` TEXT NOT NULL, `extra` TEXT)");
        }
    };
    public static final Migration M_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `fm_favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `uri` TEXT NOT NULL, `init_uri` TEXT, `options` INTEGER NOT NULL, `order` INTEGER NOT NULL, `type` INTEGER NOT NULL)");
        }
    };
    public static final Migration M_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `freeze_type` (`package_name` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`package_name`))");
        }
    };
    public static final Migration M_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `is_only_data_installed` INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration M_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS `file_hash`");
        }
    };
    /**
     * v8 adds {@code tracker_blocked_count} so the main list can render an
     * "N/T" or "✓" badge once a user has applied tracker-blocking rules to
     * an app. Populated alongside {@code rules_count} during the existing
     * AppDb refresh pass (no extra ComponentsBlocker open required).
     */
    public static final Migration M_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `tracker_blocked_count` INTEGER NOT NULL DEFAULT 0");
        }
    };
    /**
     * v9 adds {@code dangerous_perm_total} and {@code dangerous_perm_granted}
     * so the main list can render a per-app dangerous-permissions badge and the
     * aggregate status line can total granted perms across the visible set.
     * Populated alongside {@code rules_count} during the existing AppDb refresh
     * pass. Both columns are bigints because some system apps (e.g. Settings,
     * Phone) declare 50+ dangerous permissions; an INT default of 0 still fits
     * easily but matching column width to the underlying counts is cleaner.
     */
    public static final Migration M_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `dangerous_perm_total` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `app` ADD COLUMN `dangerous_perm_granted` INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration M_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_scan_result` ("
                    + "`package_name` TEXT NOT NULL, "
                    + "`version_code` INTEGER NOT NULL, "
                    + "`scan_timestamp` INTEGER NOT NULL, "
                    + "`tracker_count` INTEGER NOT NULL, "
                    + "`library_count` INTEGER NOT NULL, "
                    + "`trackers_json` TEXT, "
                    + "`libraries_json` TEXT, "
                    + "PRIMARY KEY(`package_name`, `version_code`))");
        }
    };
    public static final Migration M_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_update_change_report` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`package_name` TEXT NOT NULL, "
                    + "`timestamp_millis` INTEGER NOT NULL, "
                    + "`before_version_code` INTEGER NOT NULL, "
                    + "`after_version_code` INTEGER NOT NULL, "
                    + "`added_permissions` TEXT NOT NULL, "
                    + "`removed_permissions` TEXT NOT NULL, "
                    + "`added_trackers` TEXT NOT NULL, "
                    + "`removed_trackers` TEXT NOT NULL, "
                    + "`added_components` TEXT NOT NULL, "
                    + "`removed_components` TEXT NOT NULL)");
        }
    };
    public static final Migration M_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_note` ("
                    + "`package_name` TEXT NOT NULL, "
                    + "`note` TEXT NOT NULL, "
                    + "`updated_at` INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(`package_name`))");
        }
    };
    /** v13 adds {@code ad_source_signals}, which the next refresh pass fills in. */
    public static final Migration M_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `ad_source_signals` INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration[] MIGRATIONS = {M_1_2, M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10,
            M_10_11, M_11_12, M_12_13};

    public static synchronized AppsDb getInstance() {
        // Synchronized: a concurrent first-caller could otherwise build a second
        // RoomDatabase, run its migrations, and leak it when sAppsDb is overwritten.
        if (sAppsDb == null) {
            sAppsDb = open(ContextUtils.getContext(), NAME, MIGRATIONS);
        }
        return sAppsDb;
    }

    /**
     * Opens the database without ever dropping what's in it. Notes, history, favorites, freeze choices and backup
     * records can't all be rebuilt, so there's no destructive fallback.
     * <ul>
     * <li>Before Room touches a file that needs upgrading or can't be read, every file of it is copied to
     * {@code files/db_recovery/}. Room switches the file to WAL before it migrates, so this is the only copy of it as
     * it was. The copy is removed once the upgrade works.
     * <li>A file Room can't open (a downgrade, a version with no migration path, a migration that fails or that
     * Room's schema check rejects, or a file SQLite reports as corrupt) is replaced with a fresh database, and the
     * user is told where the copy is.
     * <li>If a copy can't be made, nothing is deleted and this run uses a database in memory.
     * </ul>
     */
    @NonNull
    static AppsDb open(@NonNull Context context, @NonNull String name, @NonNull Migration[] migrations) {
        AppsDbRecovery.clearLeftovers(context, name);
        File dbFile = context.getDatabasePath(name);
        int onDisk = AppsDbRecovery.readVersion(dbFile);
        if (!AppsDbRecovery.canMigrate(onDisk, VERSION, migrations)) {
            return startOver(context, name, migrations, onDisk,
                    "Version " + onDisk + " has no migration path to version " + VERSION, null);
        }
        File beforeUpgrade = null;
        if (dbFile.isFile() && onDisk != 0 && onDisk != VERSION) {
            try {
                beforeUpgrade = AppsDbRecovery.preserveBeforeUpgrade(context, name, onDisk,
                        "Copied before upgrading from version " + onDisk + " to " + VERSION);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not copy " + name + " before upgrading it, so it stays untouched and this run "
                        + "keeps data in memory", e);
                AppsDbRecovery.notifyUpgradeSkipped(context);
                return inMemory(context);
            }
        }
        AppsDb db = build(context, name, migrations);
        try {
            db.getOpenHelper().getWritableDatabase();
        } catch (RuntimeException e) {
            // Any failure lands here, a migration's own bug included: left to the caller it would fail every launch
            if (isTransient(e)) {
                // Disk, lock or permission trouble that a fresh file wouldn't fix: Room tries again on the next query.
                // A failed upgrade rolls back and nothing here deletes the file, and the next launch copies it again
                // before trying, so this copy would only take up space.
                Log.w(TAG, e);
                if (beforeUpgrade != null) {
                    AppsDbRecovery.discard(beforeUpgrade);
                }
                AppsDbRecovery.countFailedOpen(context, name);
                return db;
            }
            // Room's missing-migration, downgrade and schema-check failures, and SQL that fails in a migration. The
            // upgrade ran in a transaction, and the copy holds the file as it was before Room opened it.
            Log.e(TAG, "Could not open " + name, e);
            db.close();
            return startOver(context, name, migrations, onDisk, String.valueOf(e.getMessage()), beforeUpgrade);
        }
        if (beforeUpgrade != null) {
            AppsDbRecovery.discard(beforeUpgrade);
        }
        AppsDbRecovery.clearFailedOpens(context, name);
        return db;
    }

    /**
     * Whether a fresh file wouldn't fix the failure. Android 5 to 9 wrap an error from opening the file in an
     * {@link SQLiteException} ("Failed to change locale"), so the causes count too.
     */
    @VisibleForTesting
    static boolean isTransient(@NonNull Throwable e) {
        // Bounded, in case a cause chain loops
        for (int depth = 0; e != null && depth < 16; e = e.getCause(), ++depth) {
            if (e instanceof SQLiteDatabaseLockedException || e instanceof SQLiteDiskIOException
                    || e instanceof SQLiteFullException || e instanceof SQLiteCantOpenDatabaseException
                    || e instanceof SQLiteAccessPermException || e instanceof SQLiteReadOnlyDatabaseException
                    || e instanceof SQLiteOutOfMemoryException) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static AppsDb build(@NonNull Context context, @NonNull String name, @NonNull Migration[] migrations) {
        return Room.databaseBuilder(context, AppsDb.class, name)
                .openHelperFactory(AppsDbRecovery.corruptionGuard(context, name))
                .addMigrations(migrations)
                .build();
    }

    @NonNull
    private static AppsDb inMemory(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, AppsDb.class).build();
    }

    /**
     * Replaces the file with a fresh database once it's copied aside. {@code copy} is a copy already made of the
     * file as it was, or null to make one now.
     */
    @NonNull
    private static AppsDb startOver(@NonNull Context context, @NonNull String name, @NonNull Migration[] migrations,
                                    int onDisk, @NonNull String reason, @Nullable File copy) {
        if (copy != null) {
            copy = AppsDbRecovery.keep(context, name, copy, reason);
        }
        if (copy == null) {
            try {
                copy = AppsDbRecovery.preserve(context, name, onDisk, reason);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not copy " + name + " aside, so it stays untouched and this run keeps data in "
                        + "memory", e);
                AppsDbRecovery.notifyUser(context, null, true);
                return inMemory(context);
            }
        }
        if (!AppsDbRecovery.deleteDatabaseFiles(context, name)) {
            Log.e(TAG, "Could not delete all of " + name + " after copying it to " + copy + ", so this run keeps "
                    + "data in memory");
            AppsDbRecovery.notifyUser(context, copy, true);
            return inMemory(context);
        }
        Log.w(TAG, "Started " + name + " over, the old file is in " + copy + ". " + reason);
        AppsDbRecovery.notifyUser(context, copy, false);
        return build(context, name, migrations);
    }

    /**
     * A message about saved data that notifications couldn't show, or null. It stays until
     * {@link #dismissUnseenNotice} is called with it, so a screen that closes before the user sees it loses nothing.
     */
    @WorkerThread
    @Nullable
    public static String peekUnseenNotice(@NonNull Context context) {
        return AppsDbRecovery.peekUnseenNotice(context);
    }

    /** The user dismissed {@code notice}. A newer message saved since then stays. */
    @WorkerThread
    public static void dismissUnseenNotice(@NonNull Context context, @NonNull String notice) {
        AppsDbRecovery.dismissUnseenNotice(context, notice);
    }

    public abstract AppDao appDao();

    @NonNull
    public abstract AppNoteDao appNoteDao();

    public abstract BackupDao backupDao();

    public abstract LogFilterDao logFilterDao();

    public abstract OpHistoryDao opHistoryDao();

    @NonNull
    public abstract AppUpdateChangeReportDao appUpdateChangeReportDao();

    public abstract FmFavoriteDao fmFavoriteDao();

    public abstract FreezeTypeDao freezeTypeDao();

    @NonNull
    public abstract CachedScanResultDao cachedScanResultDao();
}
