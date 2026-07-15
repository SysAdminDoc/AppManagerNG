// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.settings.Prefs;
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
    public final boolean protectFromPrune;
    @Nullable
    public final String backupNote;
    @NonNull
    @CryptoUtils.Mode
    public final String cryptoMode;
    public final int retentionMaxCount;
    public final int retentionMaxAgeDays;
    @Nullable
    public final Uri destination;

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName, boolean override) {
        this(packageName, userId, flags, backupName, override, null);
    }

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName,
                           boolean override, @Nullable String[] exclusionGlobs) {
        this(packageName, userId, flags, backupName, override, exclusionGlobs, false, null);
    }

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName,
                           boolean override, @Nullable String[] exclusionGlobs,
                           boolean protectFromPrune, @Nullable String backupNote) {
        this(packageName, userId, flags, backupName, override, exclusionGlobs, protectFromPrune,
                backupNote, CryptoUtils.getMode(), Prefs.BackupRestore.getMaxBackupsPerApp(),
                Prefs.BackupRestore.getMaxBackupAgeDays(), null);
    }

    public BackupOpOptions(@NonNull String packageName, int userId, int flags, @Nullable String backupName,
                           boolean override, @Nullable String[] exclusionGlobs,
                           boolean protectFromPrune, @Nullable String backupNote,
                           @NonNull @CryptoUtils.Mode String cryptoMode,
                           int retentionMaxCount, int retentionMaxAgeDays,
                           @Nullable Uri destination) {
        this.packageName = BackupOperationOptionValidator.requirePackageName(packageName);
        this.userId = BackupOperationOptionValidator.requireUserId(userId);
        this.flags = BackupOperationOptionValidator.requireBackupFlags(flags);
        this.backupName = BackupOperationOptionValidator.sanitizeBackupName(backupName);
        this.override = override;
        this.exclusionGlobs = BackupOperationOptionValidator.sanitizeExclusionGlobs(exclusionGlobs);
        this.protectFromPrune = protectFromPrune;
        this.backupNote = BackupMetadataV5.Metadata.normalizeNote(backupNote);
        this.cryptoMode = requireCryptoMode(cryptoMode);
        this.retentionMaxCount = requireRetention(retentionMaxCount, "count");
        this.retentionMaxAgeDays = requireRetention(retentionMaxAgeDays, "age");
        this.destination = requireDestination(destination);
    }

    protected BackupOpOptions(@NonNull Parcel in) {
        packageName = BackupOperationOptionValidator.requirePackageName(in.readString());
        userId = BackupOperationOptionValidator.requireUserId(in.readInt());
        flags = BackupOperationOptionValidator.requireBackupFlags(in.readInt());
        backupName = BackupOperationOptionValidator.sanitizeBackupName(in.readString());
        override = ParcelCompat.readBoolean(in);
        exclusionGlobs = BackupOperationOptionValidator.sanitizeExclusionGlobs(in.createStringArray());
        protectFromPrune = ParcelCompat.readBoolean(in);
        backupNote = BackupMetadataV5.Metadata.normalizeNote(in.readString());
        cryptoMode = in.dataAvail() > 0 ? requireCryptoMode(in.readString()) : CryptoUtils.getMode();
        retentionMaxCount = in.dataAvail() > 0 ? requireRetention(in.readInt(), "count")
                : Prefs.BackupRestore.getMaxBackupsPerApp();
        retentionMaxAgeDays = in.dataAvail() > 0 ? requireRetention(in.readInt(), "age")
                : Prefs.BackupRestore.getMaxBackupAgeDays();
        String destinationString = in.dataAvail() > 0 ? in.readString() : null;
        destination = requireDestination(destinationString != null ? Uri.parse(destinationString) : null);
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
        ParcelCompat.writeBoolean(dest, protectFromPrune);
        dest.writeString(BackupMetadataV5.Metadata.normalizeNote(backupNote));
        dest.writeString(requireCryptoMode(cryptoMode));
        dest.writeInt(requireRetention(retentionMaxCount, "count"));
        dest.writeInt(requireRetention(retentionMaxAgeDays, "age"));
        dest.writeString(destination != null ? destination.toString() : null);
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
            protectFromPrune = jsonObject.optBoolean("protect_from_prune", false);
            backupNote = BackupMetadataV5.Metadata.normalizeNote(JSONUtils.optString(jsonObject, "backup_note"));
            cryptoMode = requireCryptoMode(jsonObject.optString("crypto_mode", CryptoUtils.getMode()));
            retentionMaxCount = requireRetention(jsonObject.optInt("retention_max_count",
                    Prefs.BackupRestore.getMaxBackupsPerApp()), "count");
            retentionMaxAgeDays = requireRetention(jsonObject.optInt("retention_max_age_days",
                    Prefs.BackupRestore.getMaxBackupAgeDays()), "age");
            String destinationString = JSONUtils.optString(jsonObject, "destination");
            destination = requireDestination(destinationString != null ? Uri.parse(destinationString) : null);
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
        jsonObject.put("protect_from_prune", protectFromPrune);
        jsonObject.put("backup_note", BackupMetadataV5.Metadata.normalizeNote(backupNote));
        jsonObject.put("crypto_mode", requireCryptoMode(cryptoMode));
        jsonObject.put("retention_max_count", requireRetention(retentionMaxCount, "count"));
        jsonObject.put("retention_max_age_days", requireRetention(retentionMaxAgeDays, "age"));
        jsonObject.put("destination", destination != null ? destination.toString() : JSONObject.NULL);
        return jsonObject;
    }

    @NonNull
    private static String requireCryptoMode(@Nullable String mode) {
        if (CryptoUtils.MODE_NO_ENCRYPTION.equals(mode) || CryptoUtils.MODE_AES.equals(mode)
                || CryptoUtils.MODE_RSA.equals(mode) || CryptoUtils.MODE_ECC.equals(mode)
                || CryptoUtils.MODE_OPEN_PGP.equals(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("Invalid backup crypto mode: " + mode);
    }

    private static int requireRetention(int value, @NonNull String field) {
        if (value < 0 || value > 36_500) {
            throw new IllegalArgumentException("Invalid backup retention " + field + ": " + value);
        }
        return value;
    }

    @Nullable
    private static Uri requireDestination(@Nullable Uri destination) {
        if (destination == null) return null;
        String scheme = destination.getScheme();
        if ((!ContentResolver.SCHEME_FILE.equals(scheme)
                && !ContentResolver.SCHEME_CONTENT.equals(scheme))
                || destination.getPath() == null || destination.toString().length() > 4096) {
            throw new IllegalArgumentException("Invalid backup destination: " + destination);
        }
        return destination;
    }
}
