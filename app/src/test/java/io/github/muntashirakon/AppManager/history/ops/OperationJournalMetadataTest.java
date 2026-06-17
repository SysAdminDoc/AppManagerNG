// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;
import io.github.muntashirakon.AppManager.profiles.struct.AppsBaseProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.profiles.struct.ProfileApplierResult;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
public class OperationJournalMetadataTest {
    @Test
    public void batchWarningsAreBoundedAndRoundTrip() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsManager.Result result = new BatchOpsManager.Result(Collections.emptyList());
        result.addWarnings(createWarnings(30));
        ArrayList<String> packages = new ArrayList<>();
        packages.add("com.example.app");
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_RESTORE_BACKUP, packages, null, null);

        OperationJournalMetadata metadata = OperationJournalMetadata.forBatchOperation(context, item, result);
        OperationJournalMetadata restored = OperationJournalMetadata.fromJson(
                metadata.serializeToJson().toString());

        assertEquals(24, restored.getWarnings().size());
        assertEquals("warning-0", restored.getWarnings().get(0));
        assertEquals("warning-23", restored.getWarnings().get(23));
    }

    @Test
    public void historyDetailsAndJsonExposeWarnings() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsManager.Result result = new BatchOpsManager.Result(Collections.emptyList());
        result.addWarnings(Collections.singletonList(
                "PERMISSION android.permission.CAMERA: runtime permission grant/revoke unavailable"));
        ArrayList<String> packages = new ArrayList<>();
        packages.add("com.example.app");
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_RESTORE_BACKUP, packages, null, null);

        OpHistory row = new OpHistory();
        row.id = 12L;
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.serializedData = new JSONObject().put("op", BatchOpsManager.OP_RESTORE_BACKUP).toString();
        row.serializedExtra = OperationJournalMetadata.forBatchOperation(context, queueItem, result)
                .serializeToJson().toString();
        OpHistoryItem history = new OpHistoryItem(row);

        assertTrue(history.getDetailMessage(context).contains("Warnings"));
        assertTrue(history.getDetailMessage(context).contains("runtime permission grant/revoke unavailable"));
        assertEquals(1, history.getExportJson(context).getJSONArray("warnings").length());
    }

    @Test
    public void failedRestoreHistoryExposesRecoveryActions() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsManager.Result result = new BatchOpsManager.Result(Collections.singletonList(
                new UserPackagePair("com.example.app", 0)));
        result.setRequiresRestart(true);
        ArrayList<String> packages = new ArrayList<>();
        packages.add("com.example.app");
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_RESTORE_BACKUP, packages, null, null);

        OpHistory row = new OpHistory();
        row.id = 13L;
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_FAILURE;
        row.serializedData = new JSONObject().put("op", BatchOpsManager.OP_RESTORE_BACKUP).toString();
        row.serializedExtra = OperationJournalMetadata.forBatchOperation(context, queueItem, result)
                .serializeToJson().toString();
        OpHistoryItem history = new OpHistoryItem(row);

        assertTrue(history.hasRecoveryGuidance());
        String detail = history.getDetailMessage(context);
        assertTrue(detail.contains(context.getString(R.string.op_history_detail_recovery_actions)));
        assertTrue(detail.contains(context.getString(R.string.op_history_recovery_rerun_failed)));
        assertTrue(detail.contains(context.getString(R.string.op_history_recovery_restore_backup)));
        assertTrue(detail.contains(context.getString(R.string.op_history_recovery_restart)));
        assertTrue(history.getRecoveryGuidanceMessage(context)
                .contains(context.getString(R.string.op_history_recovery_review_logs)));
        assertEquals(6, history.getExportJson(context).getJSONArray("recovery_actions").length());
    }

    @Test
    public void failedProfileHistoryUsesProfileResultCountsAndMessage() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ProfileApplierResult result = new ProfileApplierResult();
        result.setTargetCount(3);
        result.recordFailedPackages(Collections.singletonList("com.example.failed"));
        result.recordSkippedOperations(Collections.singletonList(AppsBaseProfile.PROFILE_OP_FREEZE));
        ProfileQueueItem item = ProfileQueueItem.DESERIALIZER.deserialize(new JSONObject()
                .put("profile_id", "profile-a")
                .put("profile_type", BaseProfile.PROFILE_TYPE_APPS)
                .put("profile_name", "Profile A")
                .put("state", BaseProfile.STATE_ON));

        OperationJournalMetadata metadata = OperationJournalMetadata.forProfile(context, item,
                false, false, result, "Operation needs attention");
        OperationJournalMetadata restored = OperationJournalMetadata.fromJson(
                metadata.serializeToJson().toString());

        assertNotNull(restored);
        assertEquals(3, restored.getTargetCount());
        assertEquals(3, restored.getFailedCount());
        assertEquals("Operation needs attention", restored.getFailureMessage());
        assertTrue(restored.getTargetPreview().contains("Profile A"));
        assertTrue(restored.getTargetPreview().contains("com.example.failed"));
        assertTrue(restored.getLocalizedRecoveryActions(context)
                .contains(context.getString(R.string.op_history_recovery_rerun_failed)));
        assertTrue(restored.getLocalizedRecoveryActions(context)
                .contains(context.getString(R.string.op_history_recovery_manual_reapply)));
    }

    @Test
    public void restoredMetadataSanitizesTargetPreviewAndWarningArrays() throws Exception {
        JSONObject hiddenTarget = new JSONObject().put("hidden", "bad-target");
        JSONArray targetPreview = new JSONArray()
                .put(" com.example.first ")
                .put("")
                .put("   ")
                .put(JSONObject.NULL)
                .put(42)
                .put(hiddenTarget);
        for (int i = 0; i < 12; ++i) {
            targetPreview.put("target-" + i);
        }
        JSONArray warnings = new JSONArray()
                .put(" warning-padded ")
                .put("")
                .put(JSONObject.NULL)
                .put(new JSONObject().put("hidden", "bad-warning"))
                .put(7);
        for (int i = 0; i < 30; ++i) {
            warnings.put("warning-" + i);
        }
        OperationJournalMetadata metadata = OperationJournalMetadata.fromJson(new JSONObject()
                .put("schema_version", 1)
                .put("operation_label", "Cleanup")
                .put("target_preview", targetPreview)
                .put("warnings", warnings)
                .toString());
        assertNotNull(metadata);

        assertEquals(8, metadata.getTargetPreview().size());
        assertEquals("com.example.first", metadata.getTargetPreview().get(0));
        assertEquals("target-6", metadata.getTargetPreview().get(7));
        assertEquals(24, metadata.getWarnings().size());
        assertEquals("warning-padded", metadata.getWarnings().get(0));
        assertEquals("warning-22", metadata.getWarnings().get(23));
        assertFalse(metadata.getSearchableText().contains("bad-target"));
        assertFalse(metadata.getSearchableText().contains("bad-warning"));

        JSONObject serialized = metadata.serializeToJson();
        assertEquals(8, serialized.getJSONArray("target_preview").length());
        assertEquals("com.example.first", serialized.getJSONArray("target_preview").getString(0));
        assertEquals(24, serialized.getJSONArray("warnings").length());
        assertFalse(serialized.toString().contains("bad-target"));
        assertFalse(serialized.toString().contains("bad-warning"));
    }

    @Test
    public void restoredMetadataSanitizesScalarCountsAndRisk() throws Exception {
        OperationJournalMetadata metadata = OperationJournalMetadata.fromJson(new JSONObject()
                .put("schema_version", 1)
                .put("target_count", 2)
                .put("failed_count", 5)
                .put("exit_code", -7)
                .put("risk", 99)
                .toString());
        assertNotNull(metadata);

        assertEquals(2, metadata.getTargetCount());
        assertEquals(2, metadata.getFailedCount());
        assertEquals(Integer.valueOf(-7), metadata.getExitCode());
        assertEquals(OperationJournalMetadata.RISK_MEDIUM, metadata.getRisk());

        JSONObject serialized = metadata.serializeToJson();
        assertEquals(2, serialized.getInt("target_count"));
        assertEquals(2, serialized.getInt("failed_count"));
        assertEquals(-7, serialized.getInt("exit_code"));
        assertEquals(OperationJournalMetadata.RISK_MEDIUM, serialized.getInt("risk"));
    }

    @Test
    public void restoredMetadataDropsMalformedScalarValues() throws Exception {
        OperationJournalMetadata metadata = OperationJournalMetadata.fromJson(new JSONObject()
                .put("schema_version", 1)
                .put("target_count", -3)
                .put("failed_count", -1)
                .put("exit_code", "bad")
                .toString());
        assertNotNull(metadata);

        assertEquals(0, metadata.getTargetCount());
        assertEquals(0, metadata.getFailedCount());
        assertEquals(null, metadata.getExitCode());

        JSONObject serialized = metadata.serializeToJson();
        assertEquals(0, serialized.getInt("target_count"));
        assertEquals(0, serialized.getInt("failed_count"));
        assertFalse(serialized.has("exit_code"));
    }

    private static List<String> createWarnings(int count) {
        ArrayList<String> warnings = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            warnings.add("warning-" + i);
        }
        return warnings;
    }
}
