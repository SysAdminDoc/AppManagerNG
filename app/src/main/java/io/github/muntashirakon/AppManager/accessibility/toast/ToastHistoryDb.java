// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Kept apart from {@code apps.db}: that one is a cache the app may rebuild from scratch, and toasts read from other
 * apps have no business in it. Android's backup rules already leave every database out.
 */
@Database(entities = {ToastEntry.class}, version = 1)
public abstract class ToastHistoryDb extends RoomDatabase {
    public static final String NAME = "toast_history.db";

    @NonNull
    static ToastHistoryDb open(@NonNull Context appContext) {
        return Room.databaseBuilder(appContext, ToastHistoryDb.class, NAME).build();
    }

    @NonNull
    public abstract ToastHistoryDao dao();
}
