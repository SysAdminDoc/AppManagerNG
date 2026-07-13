// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.FreezeType;

@Dao
public interface FreezeTypeDao {
    @NonNull
    @Query("SELECT * FROM freeze_type")
    List<FreezeType> getAll();

    @Nullable
    @Query("SELECT * FROM freeze_type WHERE package_name = :packageName LIMIT 1")
    FreezeType get(String packageName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FreezeType freezeType);

    @Query("DELETE FROM freeze_type WHERE package_name = :packageName")
    void delete(String packageName);
}
