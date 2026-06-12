// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatterns;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchBackupOptions implements IBatchOpOptions {
    public static final String TAG = BatchBackupOptions.class.getSimpleName();

    @BackupFlags.BackupFlag
    private final int mFlags;
    @Nullable
    private final String[] mBackupNames;
    @Nullable
    private final String[] mRelativeDirs;
    @Nullable
    private final String[] mExclusionGlobs;
    private final boolean mProtectFromPrune;
    @Nullable
    private final String mBackupNote;

    public BatchBackupOptions(@BackupFlags.BackupFlag int flags,
                              @Nullable String[] backupNames,
                              @Nullable String[] relativeDirs) {
        this(flags, backupNames, relativeDirs, null);
    }

    public BatchBackupOptions(@BackupFlags.BackupFlag int flags,
                              @Nullable String[] backupNames,
                              @Nullable String[] relativeDirs,
                              @Nullable String[] exclusionGlobs) {
        this(flags, backupNames, relativeDirs, exclusionGlobs, false, null);
    }

    public BatchBackupOptions(@BackupFlags.BackupFlag int flags,
                              @Nullable String[] backupNames,
                              @Nullable String[] relativeDirs,
                              @Nullable String[] exclusionGlobs,
                              boolean protectFromPrune,
                              @Nullable String backupNote) {
        mFlags = requireValidFlags(flags);
        mBackupNames = requireValidBackupNames(backupNames);
        mRelativeDirs = requireValidRelativeDirs(relativeDirs);
        mExclusionGlobs = sanitizeExclusionGlobs(exclusionGlobs);
        mProtectFromPrune = protectFromPrune;
        mBackupNote = BackupMetadataV5.Metadata.normalizeNote(backupNote);
    }

    public BackupOpOptions getBackupOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        String backupName;
        boolean customBackup = (mFlags & BackupFlags.BACKUP_MULTIPLE) != 0;
        if (mBackupNames != null && mBackupNames.length > 0) {
            backupName = mBackupNames[0];
        } else {
            backupName = customBackup ? DateUtils.formatMediumDateTime(ContextUtils.getContext(), System.currentTimeMillis()) : null;
        }
        return new BackupOpOptions(packageName, userId, mFlags, backupName, !customBackup, mExclusionGlobs,
                mProtectFromPrune, mBackupNote);
    }

    public RestoreOpOptions getRestoreOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        // For restore operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        String relativeDir;
        if (mRelativeDirs != null && mRelativeDirs.length > 0) {
            relativeDir = mRelativeDirs[0];
        } else {
            if (mBackupNames == null || mBackupNames.length == 0) {
                // Base backup
                relativeDir = null;
            } else {
                // Generate relative directories
                Backup backup = BackupUtils.retrieveLatestBackupFromDb(userId, mBackupNames[0], packageName);
                if (backup == null) {
                    throw new IllegalArgumentException("Backup with name " + mBackupNames[0] + " doesn't exist.");
                }
                relativeDir = backup.relativeDir;
            }
        }
        return new RestoreOpOptions(packageName, userId, relativeDir, mFlags);
    }

    public DeleteOpOptions getDeleteOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        // For delete operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        String[] relativeDirs;
        if (mRelativeDirs != null) {
            relativeDirs = mRelativeDirs;
        } else {
            if (mBackupNames == null || mBackupNames.length == 0) {
                // Base backup
                relativeDirs = null;
            } else {
                // Generate relative directories
                relativeDirs = new String[mBackupNames.length];
                for (int i = 0; i < relativeDirs.length; ++i) {
                    Backup backup = BackupUtils.retrieveLatestBackupFromDb(userId, mBackupNames[i], packageName);
                    if (backup == null) {
                        throw new IllegalArgumentException("Backup with name " + mBackupNames[i] + " doesn't exist.");
                    }
                    relativeDirs[i] = backup.relativeDir;
                }
            }
        }
        return new DeleteOpOptions(packageName, userId, relativeDirs);
    }

    protected BatchBackupOptions(@NonNull Parcel in) {
        mFlags = requireValidFlags(in.readInt());
        mBackupNames = requireValidBackupNames(in.createStringArray());
        mRelativeDirs = requireValidRelativeDirs(in.createStringArray());
        mExclusionGlobs = sanitizeExclusionGlobs(in.createStringArray());
        mProtectFromPrune = ParcelCompat.readBoolean(in);
        mBackupNote = BackupMetadataV5.Metadata.normalizeNote(in.readString());
    }

    public static final Creator<BatchBackupOptions> CREATOR = new Creator<BatchBackupOptions>() {
        @Override
        @NonNull
        public BatchBackupOptions createFromParcel(@NonNull Parcel in) {
            return new BatchBackupOptions(in);
        }

        @Override
        @NonNull
        public BatchBackupOptions[] newArray(int size) {
            return new BatchBackupOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mFlags);
        dest.writeStringArray(requireValidBackupNames(mBackupNames));
        dest.writeStringArray(requireValidRelativeDirs(mRelativeDirs));
        dest.writeStringArray(sanitizeExclusionGlobs(mExclusionGlobs));
        ParcelCompat.writeBoolean(dest, mProtectFromPrune);
        dest.writeString(BackupMetadataV5.Metadata.normalizeNote(mBackupNote));
    }

    public BatchBackupOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        try {
            mFlags = requireValidFlags(jsonObject.getInt("flags"));
            mBackupNames = requireValidBackupNames(readStringArray(jsonObject, "backup_names",
                    true, "backup name"));
            mRelativeDirs = requireValidRelativeDirs(readStringArray(jsonObject, "relative_dirs",
                    false, "relative directory"));
            mExclusionGlobs = sanitizeExclusionGlobs(readStringArray(jsonObject, "exclusion_globs",
                    false, "exclusion glob"));
            mProtectFromPrune = jsonObject.optBoolean("protect_from_prune", false);
            mBackupNote = BackupMetadataV5.Metadata.normalizeNote(JSONUtils.optString(jsonObject, "backup_note"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    public static final JsonDeserializer.Creator<BatchBackupOptions> DESERIALIZER
            = BatchBackupOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("flags", mFlags);
        jsonObject.put("backup_names", JSONUtils.getJSONArray(requireValidBackupNames(mBackupNames)));
        jsonObject.put("relative_dirs", JSONUtils.getJSONArray(requireValidRelativeDirs(mRelativeDirs)));
        jsonObject.put("exclusion_globs", JSONUtils.getJSONArray(sanitizeExclusionGlobs(mExclusionGlobs)));
        jsonObject.put("protect_from_prune", mProtectFromPrune);
        jsonObject.put("backup_note", BackupMetadataV5.Metadata.normalizeNote(mBackupNote));
        return jsonObject;
    }

    @BackupFlags.BackupFlag
    private static int requireValidFlags(int flags) {
        if (flags < 0) {
            throw new IllegalArgumentException("Backup flags must not be negative: " + flags);
        }
        return flags;
    }

    @Nullable
    private static String[] readStringArray(@NonNull JSONObject jsonObject, @NonNull String key,
                                            boolean allowNullValues, @NonNull String fieldName)
            throws JSONException {
        Object value = jsonObject.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new JSONException("Invalid backup option " + fieldName + ".");
        }
        return readStringArray((JSONArray) value, allowNullValues, fieldName);
    }

    @Nullable
    private static String[] readStringArray(@NonNull JSONArray jsonArray, boolean allowNullValues,
                                            @NonNull String fieldName) throws JSONException {
        String[] values = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) {
            Object value = jsonArray.get(i);
            if (value == JSONObject.NULL) {
                if (!allowNullValues) {
                    throw new JSONException("Invalid backup option " + fieldName + ".");
                }
                values[i] = null;
                continue;
            }
            if (!(value instanceof String)) {
                throw new JSONException("Invalid backup option " + fieldName + ".");
            }
            values[i] = (String) value;
        }
        return values;
    }

    @Nullable
    private static String[] requireValidBackupNames(@Nullable String[] backupNames) {
        if (backupNames == null) {
            return null;
        }
        String[] validBackupNames = new String[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            String backupName = backupNames[i];
            if (backupName == null) {
                validBackupNames[i] = null;
                continue;
            }
            backupName = backupName.trim();
            if (backupName.isEmpty()) {
                throw new IllegalArgumentException("Backup name must not be blank.");
            }
            validBackupNames[i] = backupName;
        }
        return validBackupNames;
    }

    @Nullable
    private static String[] requireValidRelativeDirs(@Nullable String[] relativeDirs) {
        if (relativeDirs == null) {
            return null;
        }
        String[] validRelativeDirs = new String[relativeDirs.length];
        for (int i = 0; i < relativeDirs.length; ++i) {
            validRelativeDirs[i] = requireValidRelativeDir(relativeDirs[i]);
        }
        return validRelativeDirs;
    }

    @NonNull
    private static String requireValidRelativeDir(@Nullable String relativeDir) {
        if (relativeDir == null) {
            throw new IllegalArgumentException("Backup relative directory must not be null.");
        }
        String normalizedRelativeDir = relativeDir.trim().replace('\\', '/');
        if (normalizedRelativeDir.isEmpty()
                || !normalizedRelativeDir.contains("/")
                || normalizedRelativeDir.indexOf(':') != -1) {
            throw new IllegalArgumentException("Invalid backup relative directory: " + relativeDir);
        }
        String[] segments = normalizedRelativeDir.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Invalid backup relative directory: " + relativeDir);
            }
        }
        return normalizedRelativeDir;
    }

    @Nullable
    private static String[] sanitizeExclusionGlobs(@Nullable String[] exclusionGlobs) {
        if (exclusionGlobs == null) {
            return null;
        }
        return BackupPathExclusionPatterns.sanitize(exclusionGlobs);
    }
}
