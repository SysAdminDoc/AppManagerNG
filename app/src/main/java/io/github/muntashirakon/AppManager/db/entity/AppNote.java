// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Durable user-authored note attached to an application package. */
@Entity(tableName = "app_note")
public final class AppNote {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @NonNull
    @ColumnInfo(name = "note")
    public String note = "";

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;
}
