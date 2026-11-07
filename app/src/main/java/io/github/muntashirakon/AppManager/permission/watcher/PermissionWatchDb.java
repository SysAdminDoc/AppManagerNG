// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kept apart from {@code apps.db}, which is a cache the app may rebuild from scratch. The user's rules have to
 * survive that.
 */
@Database(entities = {WatchRule.class}, version = 1)
public abstract class PermissionWatchDb extends RoomDatabase {
    public static final String NAME = "permission_watch.db";

    @Nullable
    private static volatile PermissionWatchDb sInstance;

    @NonNull
    public static PermissionWatchDb getInstance(@NonNull Context context) {
        PermissionWatchDb db = sInstance;
        if (db == null) {
            synchronized (PermissionWatchDb.class) {
                db = sInstance;
                if (db == null) {
                    db = Room.databaseBuilder(context.getApplicationContext(), PermissionWatchDb.class, NAME)
                            .build();
                    sInstance = db;
                }
            }
        }
        return db;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable PermissionWatchDb db) {
        sInstance = db;
    }

    /**
     * Whether any rule could exist, without creating the database. Package updates ask this on the main thread, so
     * a user who never added a rule pays nothing for the watcher.
     */
    public static boolean mightHaveRules(@NonNull Context context) {
        return sInstance != null || context.getDatabasePath(NAME).exists();
    }

    /** The permissions of one app whose changes the user asked not to hear about. */
    @WorkerThread
    @NonNull
    public static Set<String> ignoredPermissions(@NonNull Context context, @NonNull String packageName, int userId) {
        Set<String> ignored = new HashSet<>();
        if (!mightHaveRules(context)) {
            return ignored;
        }
        List<WatchRule> rules = getInstance(context).ruleDao().getForPackage(packageName, userId);
        for (WatchRule rule : rules) {
            if (rule.type == WatchRule.TYPE_PERMISSION && rule.state == WatchRule.STATE_IGNORE) {
                ignored.add(rule.name);
            }
        }
        return ignored;
    }

    @NonNull
    public abstract WatchRuleDao ruleDao();
}
