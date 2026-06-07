// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Collections;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.oneclickops.LeftoverCleanupHistoryItem;
import io.github.muntashirakon.AppManager.oneclickops.LeftoverScanner;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsViewModel;

@RunWith(RobolectricTestRunner.class)
public class OpHistoryItemTest {
    @Test
    public void cleanupHistoryUsesCleanupTypeAndNonReplayableMetadata() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OneClickOpsViewModel.LeftoverEntry entry = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(new File("Android/data/com.example.gone"),
                        "com.example.gone", LeftoverScanner.KIND_DATA),
                256L);
        String operation = context.getString(R.string.detect_leftover_files);
        LeftoverCleanupHistoryItem cleanup = new LeftoverCleanupHistoryItem(
                operation, Collections.singletonList(entry), 0, 0L);

        OpHistory row = new OpHistory();
        row.id = 7L;
        row.type = OpHistoryManager.HISTORY_TYPE_CLEANUP;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_FAILURE;
        row.serializedData = cleanup.serializeToJson().toString();
        row.serializedExtra = OperationJournalMetadata.forOneClickCleanup(
                context, operation, 1, 1, cleanup.getTargetPreview()).serializeToJson().toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertEquals(context.getString(R.string.op_history_type_cleanup), item.getLocalizedType(context));
        assertEquals(operation, item.getLabel(context));
        assertEquals(OperationJournalMetadata.RISK_HIGH, item.getRisk());
        assertEquals(1, item.getTargetCount());
        assertEquals(1, item.getFailedCount());
        assertFalse(item.isReplayable());
        assertFalse(item.isReversible());
    }

    @Test
    public void cleanupHistoryIsNotReplayableWithoutMetadata() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LeftoverCleanupHistoryItem cleanup = new LeftoverCleanupHistoryItem(
                context.getString(R.string.detect_leftover_files), Collections.emptyList(), 0, 0L);
        OpHistory row = new OpHistory();
        row.id = 8L;
        row.type = OpHistoryManager.HISTORY_TYPE_CLEANUP;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.serializedData = cleanup.serializeToJson().toString();
        row.serializedExtra = null;

        assertFalse(new OpHistoryItem(row).isReplayable());
    }

    @Test
    public void singleAppActionHistoryUsesAppDetailsTypeAndTarget() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SingleAppActionHistoryItem historyItem = new SingleAppActionHistoryItem(
                SingleAppActionHistoryItem.ACTION_PERMISSION_GRANT,
                context.getString(R.string.op_history_single_action_grant_permission),
                "com.example.app",
                10,
                "android.permission.POST_NOTIFICATIONS",
                null);
        OpHistory row = new OpHistory();
        row.id = 9L;
        row.type = OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.serializedData = historyItem.serializeToJson().toString();
        row.serializedExtra = OperationJournalMetadata.forSingleAppAction(
                context, historyItem, true, OperationJournalMetadata.RISK_MEDIUM, false, null)
                .serializeToJson().toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertEquals(context.getString(R.string.op_history_type_single_app_action), item.getLocalizedType(context));
        assertEquals(context.getString(R.string.op_history_single_action_grant_permission), item.getLabel(context));
        assertEquals(1, item.getTargetCount());
        assertEquals(0, item.getFailedCount());
        assertFalse(item.isReplayable());
        Intent targetIntent = item.getPrimaryTargetIntent(context);
        assertNotNull(targetIntent);
        assertEquals("com.example.app", targetIntent.getStringExtra("android.intent.extra.PACKAGE_NAME"));
    }

    @Test
    public void componentActionHistoryIsNonReplayableAndAuditable() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SingleAppActionHistoryItem historyItem = new SingleAppActionHistoryItem(
                SingleAppActionHistoryItem.ACTION_COMPONENT_ACTION,
                context.getString(R.string.quick_assist_op_history_start_service),
                "com.example.app",
                0,
                "com.example.app.SyncService",
                "User: 0; Route: Privileged; Permission: com.example.SERVICE");
        OpHistory row = new OpHistory();
        row.id = 10L;
        row.type = OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_FAILURE;
        row.serializedData = historyItem.serializeToJson().toString();
        row.serializedExtra = OperationJournalMetadata.forSingleAppAction(
                context, historyItem, false, OperationJournalMetadata.RISK_MEDIUM, false,
                new IllegalStateException("blocked by service policy"))
                .serializeToJson().toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertEquals(context.getString(R.string.quick_assist_op_history_start_service), item.getLabel(context));
        assertEquals(OperationJournalMetadata.RISK_MEDIUM, item.getRisk());
        assertEquals(1, item.getTargetCount());
        assertEquals(1, item.getFailedCount());
        assertFalse(item.isReplayable());
        assertFalse(item.isReversible());
        assertTrue(item.getDetailMessage(context).contains("com.example.app.SyncService"));
        assertTrue(item.getDetailMessage(context).contains("blocked by service policy"));
    }

    @Test
    public void unknownHistoryTypeAndStatusUseSafeFallbacks() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistory row = new OpHistory();
        row.id = 11L;
        row.type = "future_history_type";
        row.execTime = 1_700_000_000_000L;
        row.status = "future_status";
        row.serializedData = new JSONObject()
                .put("package_name", "com.example.future")
                .toString();
        row.serializedExtra = new JSONObject()
                .put("schema_version", 1)
                .put("replayable", true)
                .toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertEquals("unknown", item.getType());
        assertEquals(context.getString(R.string.state_unknown), item.getLocalizedType(context));
        assertEquals(context.getString(R.string.state_unknown), item.getLabel(context));
        assertEquals(OpHistoryManager.STATUS_FAILURE, item.getStatusName());
        assertEquals(context.getString(R.string.op_history_status_failure), item.getLocalizedStatus(context));
        assertFalse(item.getStatus());
        assertFalse(item.isReplayable());
        assertNull(item.getPrimaryTargetIntent(context));

        JSONObject exportJson = item.getExportJson(context);
        assertEquals("unknown", exportJson.getString("type"));
        assertEquals(OpHistoryManager.STATUS_FAILURE, exportJson.getString("status"));
        assertEquals(context.getString(R.string.state_unknown), exportJson.getString("type_label"));
        assertEquals(context.getString(R.string.op_history_status_failure), exportJson.getString("status_label"));
    }

    @Test
    public void nullHistoryTypeAndStatusUseSafeFallbacks() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistory row = new OpHistory();
        row.id = 12L;
        row.type = null;
        row.execTime = 1_700_000_000_000L;
        row.status = null;
        row.serializedData = new JSONObject().toString();
        row.serializedExtra = null;

        OpHistoryItem item = new OpHistoryItem(row);

        assertEquals("unknown", item.getType());
        assertEquals(OpHistoryManager.STATUS_FAILURE, item.getStatusName());
        assertFalse(item.getStatus());
        assertFalse(item.isReplayable());
        assertNull(item.getPrimaryTargetIntent(context));
        assertTrue(item.getDetailMessage(context).contains(context.getString(R.string.state_unknown)));
    }

    @Test
    public void malformedReplayPayloadIsNotReplayableEvenWhenMetadataAllowsReplay() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistory row = new OpHistory();
        row.id = 13L;
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.serializedData = new JSONObject().toString();
        row.serializedExtra = createReplayableMetadata().toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertFalse(OpHistoryManager.canReplayHistoryItem(item));
        assertFalse(item.isReplayable());
        assertTrue(item.getDetailMessage(context).contains(context.getString(R.string.no)));
        assertTrue(item.getExecutionConfirmationMessage(context)
                .contains(context.getString(R.string.op_preflight_not_replayable_warning)));
    }

    @Test
    public void validBatchReplayPayloadRemainsReplayable() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistory row = new OpHistory();
        row.id = 14L;
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.execTime = 1_700_000_000_000L;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.serializedData = new JSONObject()
                .put("title_res", R.string.batch_ops)
                .put("op", BatchOpsManager.OP_FREEZE)
                .put("packages", new JSONArray().put("com.example.app"))
                .put("users", new JSONArray().put(0))
                .put("options", JSONObject.NULL)
                .toString();
        row.serializedExtra = createReplayableMetadata().toString();

        OpHistoryItem item = new OpHistoryItem(row);

        assertTrue(OpHistoryManager.canReplayHistoryItem(item));
        assertTrue(item.isReplayable());
        assertNotNull(OpHistoryManager.getExecutableIntent(context, item));
    }

    private static JSONObject createReplayableMetadata() throws Exception {
        return new JSONObject()
                .put("schema_version", 1)
                .put("mode_label", "ADB")
                .put("operation_label", "Replay")
                .put("target_count", 1)
                .put("failed_count", 0)
                .put("requires_restart", false)
                .put("replayable", true)
                .put("reversible", false)
                .put("risk", OperationJournalMetadata.RISK_MEDIUM)
                .put("rollback_hint", "none")
                .put("target_preview", new JSONArray().put("com.example.app"));
    }
}
