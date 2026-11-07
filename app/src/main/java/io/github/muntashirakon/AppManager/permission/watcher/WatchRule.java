// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

/**
 * What the user wants one permission or app op of one app to stay at. {@link #STATE_IGNORE} keeps the entry but
 * tells the watcher to leave it alone, and keeps it out of the permission change alerts.
 */
@Entity(tableName = "watch_rules", primaryKeys = {"package_name", "user_id", "type", "name"})
public class WatchRule {
    public static final int TYPE_PERMISSION = 0;
    public static final int TYPE_APP_OP = 1;

    @IntDef({TYPE_PERMISSION, TYPE_APP_OP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    public static final int STATE_IGNORE = 0;
    public static final int STATE_ALLOW = 1;
    public static final int STATE_DENY = 2;

    @IntDef({STATE_IGNORE, STATE_ALLOW, STATE_DENY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    @NonNull
    @ColumnInfo(name = "package_name")
    public final String packageName;
    @ColumnInfo(name = "user_id")
    public final int userId;
    @Type
    @ColumnInfo(name = "type")
    public final int type;
    /** The permission's name, or the app op's name as {@link AppOpsManagerCompat#opToName(int)} gives it. */
    @NonNull
    @ColumnInfo(name = "name")
    public final String name;
    /** The app op's code, or {@link AppOpsManagerCompat#OP_NONE} for a permission. */
    @ColumnInfo(name = "op")
    public final int op;
    @State
    @ColumnInfo(name = "state")
    public final int state;
    @ColumnInfo(name = "updated_millis")
    public final long updatedMillis;

    public WatchRule(@NonNull String packageName, int userId, @Type int type, @NonNull String name, int op,
                     @State int state, long updatedMillis) {
        this.packageName = packageName;
        this.userId = userId;
        this.type = type;
        this.name = name;
        this.op = op;
        this.state = state;
        this.updatedMillis = updatedMillis;
    }

    @NonNull
    public static WatchRule forPermission(@NonNull String packageName, int userId, @NonNull String permission,
                                          @State int state) {
        return new WatchRule(packageName, userId, TYPE_PERMISSION, permission, AppOpsManagerCompat.OP_NONE, state,
                System.currentTimeMillis());
    }

    @NonNull
    public static WatchRule forAppOp(@NonNull String packageName, int userId, int op, @NonNull String opName,
                                     @State int state) {
        return new WatchRule(packageName, userId, TYPE_APP_OP, opName, op, state, System.currentTimeMillis());
    }

    /** The same target with another state, stamped now. */
    @NonNull
    public WatchRule withState(@State int newState) {
        return new WatchRule(packageName, userId, type, name, op, newState, System.currentTimeMillis());
    }

    /** {@code CAMERA} for {@code android.permission.CAMERA}, so a row or an alert stays short. */
    @NonNull
    public String shortName() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WatchRule)) return false;
        WatchRule that = (WatchRule) o;
        return userId == that.userId && type == that.type && op == that.op && state == that.state
                && updatedMillis == that.updatedMillis && packageName.equals(that.packageName)
                && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, userId, type, name, op, state, updatedMillis);
    }

    @NonNull
    @Override
    public String toString() {
        return "WatchRule{" + packageName + "/" + userId + " " + (type == TYPE_APP_OP ? "op " : "") + name
                + " state=" + state + "}";
    }
}
