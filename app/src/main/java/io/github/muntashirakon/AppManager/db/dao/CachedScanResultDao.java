// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.github.muntashirakon.AppManager.db.entity.CachedScanResult;

@Dao
public interface CachedScanResultDao {
    @Nullable
    @Query("SELECT * FROM cached_scan_result WHERE package_name = :packageName AND version_code = :versionCode LIMIT 1")
    CachedScanResult get(String packageName, long versionCode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(CachedScanResult result);

    @Query("DELETE FROM cached_scan_result WHERE package_name = :packageName")
    void deleteByPackage(String packageName);

    @Query("DELETE FROM cached_scan_result")
    void deleteAll();
}
