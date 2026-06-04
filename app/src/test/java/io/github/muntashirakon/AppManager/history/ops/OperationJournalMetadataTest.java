// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;

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

    private static List<String> createWarnings(int count) {
        ArrayList<String> warnings = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            warnings.add("warning-" + i);
        }
        return warnings;
    }
}
