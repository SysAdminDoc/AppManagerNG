// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatterns;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

final class BackupOperationOptionValidator {
    private BackupOperationOptionValidator() {
    }

    @NonNull
    static String requirePackageName(@Nullable String packageName) {
        if (packageName == null) {
            throw new IllegalArgumentException("Backup package name must not be null.");
        }
        String normalizedPackageName = packageName.trim();
        if (!PackageUtils.validateName(normalizedPackageName)) {
            throw new IllegalArgumentException("Invalid backup package name: " + packageName);
        }
        return normalizedPackageName;
    }

    @UserIdInt
    static int requireUserId(int userId) {
        if (userId < 0) {
            throw new IllegalArgumentException("Backup user ID must not be negative: " + userId);
        }
        return userId;
    }

    @NonNull
    static BackupFlags requireBackupFlags(int flags) {
        if (flags < 0) {
            throw new IllegalArgumentException("Backup flags must not be negative: " + flags);
        }
        return new BackupFlags(flags);
    }

    @Nullable
    static String sanitizeBackupName(@Nullable String backupName) {
        if (backupName == null) {
            return null;
        }
        String normalizedBackupName = backupName.trim();
        if (normalizedBackupName.isEmpty()) {
            throw new IllegalArgumentException("Backup name must not be blank.");
        }
        return normalizedBackupName;
    }

    @Nullable
    static String sanitizeRelativeDir(@Nullable String relativeDir) {
        if (relativeDir == null) {
            return null;
        }
        return requireRelativeDir(relativeDir);
    }

    @Nullable
    static String[] requireRelativeDirs(@Nullable String[] relativeDirs) {
        if (relativeDirs == null) {
            return null;
        }
        String[] validRelativeDirs = new String[relativeDirs.length];
        for (int i = 0; i < relativeDirs.length; ++i) {
            validRelativeDirs[i] = requireRelativeDir(relativeDirs[i]);
        }
        return validRelativeDirs;
    }

    @Nullable
    static String[] sanitizeExclusionGlobs(@Nullable String[] exclusionGlobs) {
        if (exclusionGlobs == null) {
            return null;
        }
        return BackupPathExclusionPatterns.sanitize(exclusionGlobs);
    }

    @Nullable
    static String[] readStringArray(@NonNull JSONObject jsonObject, @NonNull String key,
                                    boolean allowNullValues, @NonNull String fieldName) throws JSONException {
        Object value = jsonObject.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof JSONArray)) {
            throw new JSONException("Invalid backup option " + fieldName + ".");
        }
        JSONArray jsonArray = (JSONArray) value;
        String[] values = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) {
            value = jsonArray.get(i);
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

    @NonNull
    private static String requireRelativeDir(@Nullable String relativeDir) {
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
}
