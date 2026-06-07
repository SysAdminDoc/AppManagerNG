// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BackupOpOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    public final BackupFlags flags;
    @Nullable
    public final String backupName;
    public final boolean override;
    @Nullable
    public final String[] exclusionGlobs;

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName, boolean override) {
        this(packageName, userId, flags, backupName, override, null);
    }

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName,
                           boolean override, @Nullable String[] exclusionGlobs) {
        this.packageName = BackupOperationOptionValidator.requirePackageName(packageName);
        this.userId = BackupOperationOptionValidator.requireUserId(userId);
        this.flags = BackupOperationOptionValidator.requireBackupFlags(flags);
        this.backupName = BackupOperationOptionValidator.sanitizeBackupName(backupName);
        this.override = override;
        this.exclusionGlobs = BackupOperationOptionValidator.sanitizeExclusionGlobs(exclusionGlobs);
    }

    protected BackupOpOptions(@NonNull Parcel in) {
        packageName = BackupOperationOptionValidator.requirePackageName(in.readString());
        userId = BackupOperationOptionValidator.requireUserId(in.readInt());
        flags = BackupOperationOptionValidator.requireBackupFlags(in.readInt());
        backupName = BackupOperationOptionValidator.sanitizeBackupName(in.readString());
        override = ParcelCompat.readBoolean(in);
        exclusionGlobs = BackupOperationOptionValidator.sanitizeExclusionGlobs(in.createStringArray());
    }

    public static final Creator<BackupOpOptions> CREATOR = new Creator<BackupOpOptions>() {
        @Override
        @NonNull
        public BackupOpOptions createFromParcel(@NonNull Parcel in) {
            return new BackupOpOptions(in);
        }

        @Override
        @NonNull
        public BackupOpOptions[] newArray(int size) {
            return new BackupOpOptions[size];
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
        dest.writeInt(BackupOperationOptionValidator.requireBackupFlags(this.flags.getFlags()).getFlags());
        dest.writeString(BackupOperationOptionValidator.sanitizeBackupName(backupName));
        ParcelCompat.writeBoolean(dest, override);
        dest.writeStringArray(BackupOperationOptionValidator.sanitizeExclusionGlobs(exclusionGlobs));
    }

    public BackupOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        try {
            packageName = BackupOperationOptionValidator.requirePackageName(jsonObject.getString("package_name"));
            userId = BackupOperationOptionValidator.requireUserId(jsonObject.getInt("user_id"));
            flags = BackupOperationOptionValidator.requireBackupFlags(jsonObject.getInt("flags"));
            backupName = BackupOperationOptionValidator.sanitizeBackupName(JSONUtils.optString(jsonObject, "backup_name"));
            override = jsonObject.getBoolean("override");
            exclusionGlobs = BackupOperationOptionValidator.sanitizeExclusionGlobs(
                    BackupOperationOptionValidator.readStringArray(jsonObject, "exclusion_globs",
                            false, "exclusion glob"));
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
        jsonObject.put("flags", BackupOperationOptionValidator.requireBackupFlags(flags.getFlags()).getFlags());
        jsonObject.put("backup_name", BackupOperationOptionValidator.sanitizeBackupName(backupName));
        jsonObject.put("override", override);
        jsonObject.put("exclusion_globs", JSONUtils.getJSONArray(
                BackupOperationOptionValidator.sanitizeExclusionGlobs(exclusionGlobs)));
        return jsonObject;
    }
}
