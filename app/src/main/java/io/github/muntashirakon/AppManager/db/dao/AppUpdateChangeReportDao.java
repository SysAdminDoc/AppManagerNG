// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;

@Dao
public interface AppUpdateChangeReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(@NonNull AppUpdateChangeReport report);

    @Query("SELECT * FROM app_update_change_report ORDER BY timestamp_millis DESC, id DESC LIMIT :limit")
    @NonNull
    List<AppUpdateChangeReport> getRecent(int limit);

    @Query("DELETE FROM app_update_change_report WHERE timestamp_millis < :cutoffMillis")
    int deleteOlderThan(long cutoffMillis);

    @Query("DELETE FROM app_update_change_report")
    void deleteAll();
}
