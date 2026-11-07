// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchRuleDao {
    @NonNull
    @Query("SELECT * FROM watch_rules ORDER BY package_name, user_id, type, name")
    List<WatchRule> getAll();

    @NonNull
    @Query("SELECT * FROM watch_rules WHERE package_name = :packageName AND user_id = :userId ORDER BY type, name")
    List<WatchRule> getForPackage(@NonNull String packageName, int userId);

    @Nullable
    @Query("SELECT * FROM watch_rules WHERE package_name = :packageName AND user_id = :userId AND type = :type"
            + " AND name = :name")
    WatchRule find(@NonNull String packageName, int userId, int type, @NonNull String name);

    /** Adds the rule, or replaces the one for the same app, user and target. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull WatchRule rule);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<WatchRule> rules);

    /** Matches on app, user and target only, so a rule whose state changed since it was read still goes. */
    @Delete
    int delete(@NonNull WatchRule rule);

    @Query("SELECT COUNT(*) FROM watch_rules")
    int count();
}
