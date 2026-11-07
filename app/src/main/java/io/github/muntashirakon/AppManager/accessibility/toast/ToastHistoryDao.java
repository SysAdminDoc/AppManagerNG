// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class ToastHistoryDao {
    @Insert
    public abstract long insert(@NonNull ToastEntry entry);

    /** Puts rows back with the ids they had, so an undo can't duplicate a row that is still there. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(@NonNull List<ToastEntry> entries);

    /** @return Every toast, newest first. */
    @NonNull
    @Query("SELECT * FROM toast_history ORDER BY timestamp_millis DESC, id DESC")
    public abstract List<ToastEntry> getAll();

    /** @return One app's toasts, newest first. */
    @NonNull
    @Query("SELECT * FROM toast_history WHERE package_name = :packageName ORDER BY timestamp_millis DESC, id DESC")
    public abstract List<ToastEntry> getByPackage(@NonNull String packageName);

    @Query("SELECT COUNT(*) FROM toast_history")
    public abstract int count();

    @Query("DELETE FROM toast_history")
    public abstract void deleteAll();

    @Query("DELETE FROM toast_history WHERE timestamp_millis < :cutoffMillis")
    public abstract int deleteOlderThan(long cutoffMillis);

    /** Keeps the newest {@code keep} rows. */
    @Query("DELETE FROM toast_history WHERE id NOT IN "
            + "(SELECT id FROM toast_history ORDER BY timestamp_millis DESC, id DESC LIMIT :keep)")
    public abstract int trimTo(int keep);

    @Transaction
    public void insertAndPrune(@NonNull ToastEntry entry, long cutoffMillis, int keep) {
        insert(entry);
        deleteOlderThan(cutoffMillis);
        trimTo(keep);
    }

    @Transaction
    public void restoreAndPrune(@NonNull List<ToastEntry> entries, long cutoffMillis, int keep) {
        insertAll(entries);
        deleteOlderThan(cutoffMillis);
        trimTo(keep);
    }

    /** @return What was removed, newest first, for an undo. */
    @NonNull
    @Transaction
    public List<ToastEntry> takeAll() {
        List<ToastEntry> all = getAll();
        deleteAll();
        return all;
    }
}
