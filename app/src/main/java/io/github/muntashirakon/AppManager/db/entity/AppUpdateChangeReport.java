// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Version-aware manifest delta captured when an installed app is replaced.
 * Collections are stored as JSON arrays so the report remains one atomic row
 * and can be rendered without a join across several child tables.
 */
@Entity(tableName = "app_update_change_report")
public final class AppUpdateChangeReport {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @ColumnInfo(name = "timestamp_millis")
    public long timestampMillis;

    @ColumnInfo(name = "before_version_code")
    public long beforeVersionCode;

    @ColumnInfo(name = "after_version_code")
    public long afterVersionCode;

    @NonNull
    @ColumnInfo(name = "added_permissions")
    public String addedPermissions = "[]";

    @NonNull
    @ColumnInfo(name = "removed_permissions")
    public String removedPermissions = "[]";

    @NonNull
    @ColumnInfo(name = "added_trackers")
    public String addedTrackers = "[]";

    @NonNull
    @ColumnInfo(name = "removed_trackers")
    public String removedTrackers = "[]";

    @NonNull
    @ColumnInfo(name = "added_components")
    public String addedComponents = "[]";

    @NonNull
    @ColumnInfo(name = "removed_components")
    public String removedComponents = "[]";
}
