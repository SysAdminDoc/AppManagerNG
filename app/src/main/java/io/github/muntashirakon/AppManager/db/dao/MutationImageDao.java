// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;

/** Append and read only. There's no update: the table's trigger refuses one. */
@Dao
public interface MutationImageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(@NonNull MutationImage image);

    /** Every row in the order it was written, which is the order the MAC chain runs in. */
    @Query("SELECT * FROM mutation_image ORDER BY id")
    @NonNull
    List<MutationImage> getAll();

    @Query("SELECT * FROM mutation_image ORDER BY id DESC LIMIT 1")
    @Nullable
    MutationImage getLast();

    @Query("SELECT COUNT(*) FROM mutation_image")
    int count();

    /**
     * Deletes the oldest rows up to the first one written at or after the cutoff. Only a run from the start goes, so
     * the chain that's left has no gap; a row whose time is off because the clock moved waits for the rows around
     * it.
     */
    @Query("DELETE FROM mutation_image WHERE id < COALESCE("
            + "(SELECT MIN(id) FROM mutation_image WHERE time >= :cutoffTime), "
            + "(SELECT MAX(id) FROM mutation_image) + 1)")
    int deleteOldestBefore(long cutoffTime);

    @Query("DELETE FROM mutation_image")
    void deleteAll();
}
