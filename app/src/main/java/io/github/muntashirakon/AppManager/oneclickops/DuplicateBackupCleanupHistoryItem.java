// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.BackupRetentionPolicy;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;

public final class DuplicateBackupCleanupHistoryItem implements IJsonSerializer {
    private static final int SCHEMA_VERSION = 1;

    private final String mLabel;
    private final CharSequence mBaseBackupLabel;
    private final BackupRetentionPolicy.DuplicateKeepStrategy mStrategy;
    private final List<OneClickOpsViewModel.DuplicateBackupEntry> mEntries;
    private final int mDeletedCount;
    private final long mReclaimedBytes;

    public DuplicateBackupCleanupHistoryItem(@NonNull String label,
                                             @NonNull CharSequence baseBackupLabel,
                                             @NonNull BackupRetentionPolicy.DuplicateKeepStrategy strategy,
                                             @NonNull List<OneClickOpsViewModel.DuplicateBackupEntry> entries,
                                             int deletedCount,
                                             long reclaimedBytes) {
        mLabel = label;
        mBaseBackupLabel = baseBackupLabel;
        mStrategy = strategy;
        mEntries = new ArrayList<>(entries);
        mDeletedCount = deletedCount;
        mReclaimedBytes = reclaimedBytes;
    }

    @NonNull
    public List<String> getTargetPreview() {
        List<String> preview = new ArrayList<>(mEntries.size());
        for (OneClickOpsViewModel.DuplicateBackupEntry entry : mEntries) {
            Backup backup = entry.backup;
            preview.add(backup.packageName + " (" + getDisplayBackupName(backup) + ") v"
                    + backup.versionCode + " u" + backup.userId);
        }
        return preview;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject root = new JSONObject()
                .put("schema_version", SCHEMA_VERSION)
                .put("cleanup_type", "duplicate_backups")
                .put("label", mLabel)
                .put("keep_strategy", mStrategy.name())
                .put("selected_count", mEntries.size())
                .put("deleted_count", mDeletedCount)
                .put("reclaimed_bytes", mReclaimedBytes);
        JSONArray entries = new JSONArray();
        for (OneClickOpsViewModel.DuplicateBackupEntry entry : mEntries) {
            Backup backup = entry.backup;
            entries.put(new JSONObject()
                    .put("package_name", backup.packageName)
                    .put("user_id", backup.userId)
                    .put("version_code", backup.versionCode)
                    .put("backup_name", getSerializedBackupName(backup.backupName))
                    .put("backup_label", getDisplayBackupName(backup))
                    .put("relative_dir", backup.relativeDir)
                    .put("size_bytes", entry.size));
        }
        root.put("entries", entries);
        return root;
    }

    @NonNull
    private String getDisplayBackupName(@NonNull Backup backup) {
        return BackupUtils.getDisplayBackupName(mBaseBackupLabel, backup.backupName).toString();
    }

    @NonNull
    private static String getSerializedBackupName(@Nullable String backupName) {
        if (backupName == null) {
            return "";
        }
        String trimmedBackupName = backupName.trim();
        return trimmedBackupName.isEmpty() ? "" : trimmedBackupName;
    }
}
