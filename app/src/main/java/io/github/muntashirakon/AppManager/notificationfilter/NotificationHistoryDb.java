// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Kept apart from {@code apps.db}, which the app may rebuild from scratch, so the history needs no migration there.
 * Android's backup rules leave every database out, so it never leaves the device.
 */
@Database(entities = {NotificationHistoryEntry.class}, version = 1)
public abstract class NotificationHistoryDb extends RoomDatabase {
    public static final String NAME = "notification_history.db";

    @NonNull
    static NotificationHistoryDb open(@NonNull Context appContext) {
        return Room.databaseBuilder(appContext, NotificationHistoryDb.class, NAME).build();
    }

    @NonNull
    public abstract NotificationHistoryDao dao();
}
