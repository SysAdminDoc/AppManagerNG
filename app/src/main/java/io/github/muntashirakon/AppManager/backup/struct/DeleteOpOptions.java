// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class DeleteOpOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final String[] relativeDirs;

    public DeleteOpOptions(@NonNull String packageName, @UserIdInt int userId, @Nullable String[] relativeDirs) {
        this.packageName = BackupOperationOptionValidator.requirePackageName(packageName);
        this.userId = BackupOperationOptionValidator.requireUserId(userId);
        this.relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(relativeDirs);
    }

    protected DeleteOpOptions(@NonNull Parcel in) {
        packageName = BackupOperationOptionValidator.requirePackageName(in.readString());
        userId = BackupOperationOptionValidator.requireUserId(in.readInt());
        relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(in.createStringArray());
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
    }

    public DeleteOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        try {
            packageName = BackupOperationOptionValidator.requirePackageName(jsonObject.getString("package_name"));
            userId = BackupOperationOptionValidator.requireUserId(jsonObject.getInt("user_id"));
            relativeDirs = BackupOperationOptionValidator.requireRelativeDirs(
                    BackupOperationOptionValidator.readStringArray(jsonObject, "relative_dirs",
                            false, "relative directory"));
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
        return jsonObject;
    }
}
