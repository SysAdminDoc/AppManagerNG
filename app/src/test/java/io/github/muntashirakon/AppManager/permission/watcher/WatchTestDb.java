// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

final class WatchTestDb {
    private WatchTestDb() {
    }

    @NonNull
    static PermissionWatchDb newInMemory(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, PermissionWatchDb.class).allowMainThreadQueries().build();
    }

    /** An in-memory database the app's code gets from {@link PermissionWatchDb#getInstance}. */
    @NonNull
    static PermissionWatchDb install(@NonNull Context context) {
        PermissionWatchDb db = newInMemory(context);
        PermissionWatchDb.setInstanceForTesting(db);
        return db;
    }

    static void uninstall(@Nullable PermissionWatchDb db) {
        PermissionWatchDb.setInstanceForTesting(null);
        if (db != null) {
            db.close();
        }
    }

    @NonNull
    static WatchRule permission(@NonNull String packageName, int userId, @NonNull String name,
                                @WatchRule.State int state) {
        return new WatchRule(packageName, userId, WatchRule.TYPE_PERMISSION, name, -1, state, 1_000L);
    }

    @NonNull
    static WatchRule appOp(@NonNull String packageName, int userId, int op, @NonNull String name,
                           @WatchRule.State int state) {
        return new WatchRule(packageName, userId, WatchRule.TYPE_APP_OP, name, op, state, 1_000L);
    }
}
