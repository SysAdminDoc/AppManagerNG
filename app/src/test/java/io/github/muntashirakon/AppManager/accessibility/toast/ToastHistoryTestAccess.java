// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

/** Lets tests in other packages run the history on the calling thread against a database of their own. */
public final class ToastHistoryTestAccess {
    @Nullable
    private static ToastHistoryDb sDb;

    private ToastHistoryTestAccess() {
    }

    @NonNull
    public static ToastHistoryDb newInMemoryDb(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, ToastHistoryDb.class)
                .allowMainThreadQueries()
                .build();
    }

    @NonNull
    public static ToastHistory useInMemory(@NonNull Context context) {
        reset();
        sDb = newInMemoryDb(context);
        ToastHistory history = new ToastHistory(sDb.dao(), Runnable::run, packageName -> "Label of " + packageName,
                System::currentTimeMillis);
        // Wired as in the app, so the notification in the shade is tested through the same path.
        Context appContext = context.getApplicationContext();
        history.setAfterChange(() -> ToastShadeNotification.update(appContext, history));
        ToastHistory.setInstanceForTesting(history);
        return history;
    }

    public static void reset() {
        ToastHistory.setInstanceForTesting(null);
        if (sDb != null) {
            sDb.close();
            sDb = null;
        }
    }
}
