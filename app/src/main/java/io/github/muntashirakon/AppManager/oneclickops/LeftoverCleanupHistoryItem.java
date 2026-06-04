// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;

public final class LeftoverCleanupHistoryItem implements IJsonSerializer {
    private static final int SCHEMA_VERSION = 1;

    private final String mLabel;
    private final List<OneClickOpsViewModel.LeftoverEntry> mEntries;
    private final int mDeletedCount;
    private final long mReclaimedBytes;

    public LeftoverCleanupHistoryItem(@NonNull String label,
                                      @NonNull List<OneClickOpsViewModel.LeftoverEntry> entries,
                                      int deletedCount,
                                      long reclaimedBytes) {
        mLabel = label;
        mEntries = new ArrayList<>(entries);
        mDeletedCount = deletedCount;
        mReclaimedBytes = reclaimedBytes;
    }

    @NonNull
    public List<String> getTargetPreview() {
        List<String> preview = new ArrayList<>(mEntries.size());
        for (OneClickOpsViewModel.LeftoverEntry entry : mEntries) {
            preview.add(entry.leftover.packageName + " · " + entry.leftover.kindLabel());
        }
        return preview;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject root = new JSONObject()
                .put("schema_version", SCHEMA_VERSION)
                .put("cleanup_type", "leftover_folders")
                .put("label", mLabel)
                .put("selected_count", mEntries.size())
                .put("deleted_count", mDeletedCount)
                .put("reclaimed_bytes", mReclaimedBytes);
        JSONArray entries = new JSONArray();
        for (OneClickOpsViewModel.LeftoverEntry entry : mEntries) {
            entries.put(new JSONObject()
                    .put("package_name", entry.leftover.packageName)
                    .put("kind", entry.leftover.kindLabel())
                    .put("size_bytes", entry.size)
                    .put("path", entry.leftover.path.getPath()));
        }
        root.put("entries", entries);
        return root;
    }
}
