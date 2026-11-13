// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

/** One notification the filter saw, with what it did to it. */
@Entity(tableName = "notification_history",
        indices = {@Index("package_name"), @Index("timestamp_millis")})
public final class NotificationHistoryEntry {
    /** Recorded only because the user asked for every notification. Nothing was done to it. */
    public static final String ACTION_NONE = "NONE";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "timestamp_millis")
    public long timestampMillis;

    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName = "";

    @ColumnInfo(name = "app_label")
    @NonNull
    public String appLabel = "";

    @ColumnInfo(name = "title")
    @NonNull
    public String title = "";

    @ColumnInfo(name = "text")
    @NonNull
    public String text = "";

    @ColumnInfo(name = "sender")
    @NonNull
    public String sender = "";

    /** A {@link NotificationFilterRule.Action} name, or {@link #ACTION_NONE}. */
    @ColumnInfo(name = "action")
    @NonNull
    public String action = ACTION_NONE;

    @ColumnInfo(name = "rule_name")
    @Nullable
    public String ruleName;

    @ColumnInfo(name = "by_quiet_mode")
    public boolean byQuietMode;

    public NotificationHistoryEntry() {
    }

    @Ignore
    public NotificationHistoryEntry(long timestampMillis, @NonNull String packageName, @NonNull String appLabel,
                                    @NonNull NotificationFields fields, @NonNull String action,
                                    @Nullable String ruleName, boolean byQuietMode) {
        this.timestampMillis = timestampMillis;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.title = fields.title;
        this.text = fields.text;
        this.sender = fields.sender;
        this.action = action;
        this.ruleName = ruleName;
        this.byQuietMode = byQuietMode;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationHistoryEntry)) return false;
        NotificationHistoryEntry that = (NotificationHistoryEntry) o;
        return id == that.id
                && timestampMillis == that.timestampMillis
                && byQuietMode == that.byQuietMode
                && packageName.equals(that.packageName)
                && appLabel.equals(that.appLabel)
                && title.equals(that.title)
                && text.equals(that.text)
                && sender.equals(that.sender)
                && action.equals(that.action)
                && Objects.equals(ruleName, that.ruleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestampMillis, packageName, appLabel, title, text, sender, action, ruleName,
                byQuietMode);
    }
}
