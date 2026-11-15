// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One state change AppManagerNG made to one target of one app: the value read before the write, the value asked
 * for, and the value read back after it. Rows are only ever added; a trigger refuses UPDATE, and every row carries
 * a MAC that chains it to the row before it, so an edit made behind the app's back shows up when the journal is
 * verified.
 */
@Entity(tableName = "mutation_image",
        indices = {
                @Index(value = {"package_name", "user_id"}),
                @Index(value = {"execution_id"}),
                @Index(value = {"time"}),
        })
public class MutationImage {
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Ties every image of one batch run, or of one single-app action, to its history row. */
    @ColumnInfo(name = "execution_id")
    @NonNull
    public String executionId = "";

    /** The mode the write went through (root, ADB, no root). */
    @ColumnInfo(name = "executor")
    @NonNull
    public String executor = "";

    /** What was run, for example {@code batch:12} or {@code single:freeze}. */
    @ColumnInfo(name = "operation")
    @NonNull
    public String operation = "";

    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName = "";

    @ColumnInfo(name = "user_id")
    public int userId;

    /** What part of the app's state the row is about: freeze, permission, app_op or net_policy. */
    @ColumnInfo(name = "kind")
    @NonNull
    public String kind = "";

    /** The permission name or app op number; empty for kinds with one value per app. */
    @ColumnInfo(name = "target")
    @NonNull
    public String target = "";

    /** The value read before the write, or null when it couldn't be read. */
    @ColumnInfo(name = "before_value")
    @Nullable
    public String beforeValue;

    @ColumnInfo(name = "requested_value")
    @NonNull
    public String requestedValue = "";

    /** The value read back after the write, or null when it couldn't be read. */
    @ColumnInfo(name = "post_value")
    @Nullable
    public String postValue;

    /** applied, unconfirmed or failed. */
    @ColumnInfo(name = "result")
    @NonNull
    public String result = "";

    @ColumnInfo(name = "time")
    public long time;

    @ColumnInfo(name = "prev_mac")
    @NonNull
    public String prevMac = "";

    @ColumnInfo(name = "mac")
    @NonNull
    public String mac = "";
}
