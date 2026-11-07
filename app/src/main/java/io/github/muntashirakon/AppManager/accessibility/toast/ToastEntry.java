// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One recorded toast. The label is kept from when it was shown, so an app removed since then can still be named. */
@Entity(tableName = "toast_history",
        indices = {@Index("package_name"), @Index("timestamp_millis")})
public final class ToastEntry {
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

    @ColumnInfo(name = "text")
    @NonNull
    public String text = "";

    public ToastEntry() {
    }

    @Ignore
    public ToastEntry(long timestampMillis, @NonNull String packageName, @NonNull String appLabel,
                      @NonNull String text) {
        this.timestampMillis = timestampMillis;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.text = text;
    }

    /** Not thread safe, so every caller gets its own. */
    @NonNull
    public static DateFormat newTimeFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }

    @NonNull
    public String toCopyText(@NonNull DateFormat timeFormat) {
        return timeFormat.format(new Date(timestampMillis)) + "  " + appLabel + " (" + packageName + ")\n" + text;
    }

    @NonNull
    public static String toCopyText(@NonNull List<ToastEntry> entries) {
        DateFormat timeFormat = newTimeFormat();
        StringBuilder text = new StringBuilder();
        for (ToastEntry entry : entries) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(entry.toCopyText(timeFormat));
        }
        return text.toString();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof ToastEntry)) return false;
        ToastEntry that = (ToastEntry) o;
        return id == that.id
                && timestampMillis == that.timestampMillis
                && packageName.equals(that.packageName)
                && appLabel.equals(that.appLabel)
                && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestampMillis, packageName, appLabel, text);
    }
}
