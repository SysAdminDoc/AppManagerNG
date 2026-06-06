// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class AppActionShortcutInfo extends ShortcutInfo {
    public static final String ACTION_FREEZE = "freeze";
    public static final String ACTION_FORCE_STOP = "force_stop";
    public static final String ACTION_CLEAR_CACHE = "clear_cache";

    @StringDef({
            ACTION_FREEZE,
            ACTION_FORCE_STOP,
            ACTION_CLEAR_CACHE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShortcutAction {
    }

    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @NonNull
    @ShortcutAction
    public final String action;

    public AppActionShortcutInfo(@NonNull String packageName,
                                 @UserIdInt int userId,
                                 @NonNull @ShortcutAction String action) {
        validateTarget(packageName, userId, action);
        setId(buildId(packageName, userId, action));
        this.packageName = packageName;
        this.userId = userId;
        this.action = action;
    }

    protected AppActionShortcutInfo(Parcel in) {
        super(in);
        packageName = Objects.requireNonNull(in.readString());
        userId = in.readInt();
        action = Objects.requireNonNull(in.readString());
    }

    @NonNull
    public static String buildId(@NonNull String packageName,
                                 @UserIdInt int userId,
                                 @NonNull @ShortcutAction String action) {
        return "app-action:" + action + ":u=" + userId + ",p=" + packageName;
    }

    private static void validateTarget(@NonNull String packageName,
                                       @UserIdInt int userId,
                                       @NonNull String action) {
        if (!PackageUtils.validateName(packageName)) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }
        if (userId < 0) {
            throw new IllegalArgumentException("Invalid user id: " + userId);
        }
        if (!ACTION_FREEZE.equals(action) && !ACTION_FORCE_STOP.equals(action)
                && !ACTION_CLEAR_CACHE.equals(action)) {
            throw new IllegalArgumentException("Invalid shortcut action: " + action);
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(packageName);
        dest.writeInt(userId);
        dest.writeString(action);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        return AppActionShortcutActivity.getIntent(context, packageName, userId, action);
    }

    public static final Creator<AppActionShortcutInfo> CREATOR = new Creator<AppActionShortcutInfo>() {
        @Override
        public AppActionShortcutInfo createFromParcel(Parcel source) {
            return new AppActionShortcutInfo(source);
        }

        @Override
        public AppActionShortcutInfo[] newArray(int size) {
            return new AppActionShortcutInfo[size];
        }
    };
}
