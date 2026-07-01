// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class DeleteOpOptions implements Parcelable, IJsonSerializer {
    public static final int DELETE_SCOPE_BASE_ONLY = 0;
    public static final int DELETE_SCOPE_SELECTED = 1;
    public static final int DELETE_SCOPE_ALL_VERSIONS = 2;

    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final String[] relativeDirs;
    @DeleteScope
    public final int deleteScope;

    public DeleteOpOptions(@NonNull String packageName, @UserIdInt int userId, @Nullable String[] relativeDirs) {
        this(packageName, userId, relativeDirs, inferDeleteScope(relativeDirs));
    }

    public DeleteOpOptions(@NonNull String packageName, @UserIdInt int userId, @Nullable String[] relativeDirs,
                           @DeleteScope int deleteScope) {
        this.packageName = BackupOperationOptionValidator.requirePackageName(packageName);
        this.userId = BackupOperationOptionValidator.requireUserId(userId);
        this.relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(relativeDirs);
        this.deleteScope = requireDeleteScope(deleteScope, this.relativeDirs);
    }

    protected DeleteOpOptions(@NonNull Parcel in) {
        packageName = BackupOperationOptionValidator.requirePackageName(in.readString());
        userId = BackupOperationOptionValidator.requireUserId(in.readInt());
        relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(in.createStringArray());
        deleteScope = requireDeleteScope(in.dataAvail() > 0 ? in.readInt() : inferDeleteScope(relativeDirs),
                relativeDirs);
    }

    public static final Creator<DeleteOpOptions> CREATOR = new Creator<DeleteOpOptions>() {
        @Override
        @NonNull
        public DeleteOpOptions createFromParcel(@NonNull Parcel in) {
            return new DeleteOpOptions(in);
        }

        @Override
        @NonNull
        public DeleteOpOptions[] newArray(int size) {
            return new DeleteOpOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(BackupOperationOptionValidator.requirePackageName(packageName));
        dest.writeInt(BackupOperationOptionValidator.requireUserId(userId));
        dest.writeStringArray(BackupOperationOptionValidator.requireRelativeDirs(relativeDirs));
        dest.writeInt(requireDeleteScope(deleteScope, relativeDirs));
    }

    public DeleteOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        try {
            packageName = BackupOperationOptionValidator.requirePackageName(jsonObject.getString("package_name"));
            userId = BackupOperationOptionValidator.requireUserId(jsonObject.getInt("user_id"));
            relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(
                    BackupOperationOptionValidator.readStringArray(jsonObject, "relative_dirs",
                            false, "relative directory"));
            deleteScope = requireDeleteScope(jsonObject.optInt("delete_scope", inferDeleteScope(relativeDirs)),
                    relativeDirs);
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("package_name", BackupOperationOptionValidator.requirePackageName(packageName));
        jsonObject.put("user_id", BackupOperationOptionValidator.requireUserId(userId));
        jsonObject.put("relative_dirs", JSONUtils.getJSONArray(
                BackupOperationOptionValidator.requireRelativeDirs(relativeDirs)));
        jsonObject.put("delete_scope", requireDeleteScope(deleteScope, relativeDirs));
        return jsonObject;
    }

    @DeleteScope
    public static int inferDeleteScope(@Nullable String[] relativeDirs) {
        return relativeDirs == null ? DELETE_SCOPE_BASE_ONLY : DELETE_SCOPE_SELECTED;
    }

    @DeleteScope
    private static int requireDeleteScope(int deleteScope, @Nullable String[] relativeDirs) {
        if (deleteScope != DELETE_SCOPE_BASE_ONLY
                && deleteScope != DELETE_SCOPE_SELECTED
                && deleteScope != DELETE_SCOPE_ALL_VERSIONS) {
            throw new IllegalArgumentException("Invalid backup delete scope: " + deleteScope);
        }
        if (deleteScope == DELETE_SCOPE_BASE_ONLY && relativeDirs != null) {
            throw new IllegalArgumentException("Base-only backup delete scope cannot include relative directories.");
        }
        if (deleteScope == DELETE_SCOPE_ALL_VERSIONS && relativeDirs != null) {
            throw new IllegalArgumentException("All-version backup delete scope cannot include relative directories.");
        }
        if (deleteScope == DELETE_SCOPE_SELECTED && (relativeDirs == null || relativeDirs.length == 0)) {
            throw new IllegalArgumentException("Selected backup delete scope requires relative directories.");
        }
        return deleteScope;
    }

    @IntDef({DELETE_SCOPE_BASE_ONLY, DELETE_SCOPE_SELECTED, DELETE_SCOPE_ALL_VERSIONS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeleteScope {
    }
}
