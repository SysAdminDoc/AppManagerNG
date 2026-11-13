// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class NotificationHistoryDao {
    @Insert
    public abstract long insert(@NonNull NotificationHistoryEntry entry);

    /** Puts rows back with the ids they had, so an undo can't duplicate a row that is still there. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(@NonNull List<NotificationHistoryEntry> entries);

    /** @return Every entry, newest first. */
    @NonNull
    @Query("SELECT * FROM notification_history ORDER BY timestamp_millis DESC, id DESC")
    public abstract List<NotificationHistoryEntry> getAll();

    @Query("SELECT COUNT(*) FROM notification_history")
    public abstract int count();

    @Query("DELETE FROM notification_history")
    public abstract void deleteAll();

    @Query("DELETE FROM notification_history WHERE timestamp_millis < :cutoffMillis")
    public abstract int deleteOlderThan(long cutoffMillis);

    /** Keeps the newest {@code keep} rows. */
    @Query("DELETE FROM notification_history WHERE id NOT IN "
            + "(SELECT id FROM notification_history ORDER BY timestamp_millis DESC, id DESC LIMIT :keep)")
    public abstract int trimTo(int keep);

    @Transaction
    public void insertAndPrune(@NonNull NotificationHistoryEntry entry, long cutoffMillis, int keep) {
        insert(entry);
        prune(cutoffMillis, keep);
    }

    @Transaction
    public void restoreAndPrune(@NonNull List<NotificationHistoryEntry> entries, long cutoffMillis, int keep) {
        insertAll(entries);
        prune(cutoffMillis, keep);
    }

    @Transaction
    public void prune(long cutoffMillis, int keep) {
        deleteOlderThan(cutoffMillis);
        trimTo(keep);
    }

    /** @return What was removed, newest first, for an undo. */
    @NonNull
    @Transaction
    public List<NotificationHistoryEntry> takeAll() {
        List<NotificationHistoryEntry> all = getAll();
        deleteAll();
        return all;
    }
}
