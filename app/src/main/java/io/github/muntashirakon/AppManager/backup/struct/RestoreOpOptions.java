// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class RestoreOpOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final String relativeDir;
    public final BackupFlags flags;

    public RestoreOpOptions(@NonNull String packageName, int userId, @Nullable String relativeDir, int flags) {
        this.packageName = BackupOperationOptionValidator.requirePackageName(packageName);
        this.userId = BackupOperationOptionValidator.requireUserId(userId);
        this.relativeDir = BackupOperationOptionValidator.sanitizeRelativeDir(relativeDir);
        this.flags = BackupOperationOptionValidator.requireBackupFlags(flags);
    }

    protected RestoreOpOptions(@NonNull Parcel in) {
        packageName = BackupOperationOptionValidator.requirePackageName(in.readString());
        userId = BackupOperationOptionValidator.requireUserId(in.readInt());
        relativeDir = BackupOperationOptionValidator.sanitizeRelativeDir(in.readString());
        flags = BackupOperationOptionValidator.requireBackupFlags(in.readInt());
    }

    public static final Creator<RestoreOpOptions> CREATOR = new Creator<RestoreOpOptions>() {
        @Override
        @NonNull
        public RestoreOpOptions createFromParcel(@NonNull Parcel in) {
            return new RestoreOpOptions(in);
        }

        @Override
        @NonNull
        public RestoreOpOptions[] newArray(int size) {
            return new RestoreOpOptions[size];
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
        dest.writeString(BackupOperationOptionValidator.sanitizeRelativeDir(relativeDir));
        dest.writeInt(BackupOperationOptionValidator.requireBackupFlags(this.flags.getFlags()).getFlags());
    }

    public RestoreOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        try {
            packageName = BackupOperationOptionValidator.requirePackageName(jsonObject.getString("package_name"));
            userId = BackupOperationOptionValidator.requireUserId(jsonObject.getInt("user_id"));
            relativeDir = BackupOperationOptionValidator.sanitizeRelativeDir(JSONUtils.optString(jsonObject, "relative_dir"));
            flags = BackupOperationOptionValidator.requireBackupFlags(jsonObject.getInt("flags"));
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
        jsonObject.put("relative_dir", BackupOperationOptionValidator.sanitizeRelativeDir(relativeDir));
        jsonObject.put("flags", BackupOperationOptionValidator.requireBackupFlags(flags.getFlags()).getFlags());
        return jsonObject;
    }
}
