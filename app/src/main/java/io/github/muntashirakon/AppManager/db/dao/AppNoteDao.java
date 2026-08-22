// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.AppNote;

@Dao
public interface AppNoteDao {
    @Query("SELECT * FROM app_note WHERE package_name = :packageName LIMIT 1")
    @Nullable
    AppNote get(@NonNull String packageName);

    @Query("SELECT * FROM app_note ORDER BY package_name")
    @NonNull
    List<AppNote> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(@NonNull AppNote note);

    @Query("DELETE FROM app_note WHERE package_name = :packageName")
    void delete(@NonNull String packageName);

    @Query("DELETE FROM app_note")
    void deleteAll();
}
