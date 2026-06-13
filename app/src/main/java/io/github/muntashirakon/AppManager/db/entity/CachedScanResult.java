// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "cached_scan_result", primaryKeys = {"package_name", "version_code"})
public class CachedScanResult {
    @NonNull
    @ColumnInfo(name = "package_name")
    public String packageName = "";

    @ColumnInfo(name = "version_code")
    public long versionCode;

    @ColumnInfo(name = "scan_timestamp")
    public long scanTimestamp;

    @ColumnInfo(name = "tracker_count")
    public int trackerCount;

    @ColumnInfo(name = "library_count")
    public int libraryCount;

    @Nullable
    @ColumnInfo(name = "trackers_json")
    public String trackersJson;

    @Nullable
    @ColumnInfo(name = "libraries_json")
    public String librariesJson;
}
