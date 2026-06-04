// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Collections;

import io.github.muntashirakon.AppManager.R;
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
}
