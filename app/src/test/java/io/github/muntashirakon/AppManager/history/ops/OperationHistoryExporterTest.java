// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import io.github.muntashirakon.AppManager.db.entity.OpHistory;

@RunWith(RobolectricTestRunner.class)
public class OperationHistoryExporterTest {
    @Test
    public void exportJsonUsesSafeStructuredFields() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem history = createInstallerHistory();

        String exported = OperationHistoryExporter.toJson(context, Collections.singletonList(history));
        JSONObject root = new JSONObject(exported);
        JSONObject entry = root.getJSONArray("entries").getJSONObject(0);

        assertEquals(1, root.getInt("entry_count"));
        assertEquals(42, entry.getLong("id"));
        assertEquals("installer", entry.getString("type"));
        assertEquals("Example App", entry.getString("label"));
        assertEquals("success", entry.getString("status"));
        assertEquals(1, entry.getJSONArray("target_preview").length());
        assertFalse(exported.contains("serializedData"));
        assertFalse(exported.contains("apk_source"));
    }

    @Test
    public void exportCsvEscapesValues() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem history = createInstallerHistory();

        String exported = OperationHistoryExporter.toCsv(context, Collections.singletonList(history));

        assertTrue(exported.startsWith("\"id\",\"time\",\"type\""));
        assertTrue(exported.contains("\"Example App\""));
        assertTrue(exported.contains("\"com.example.app\""));
    }

    private static OpHistoryItem createInstallerHistory() throws Exception {
        OpHistory opHistory = new OpHistory();
        opHistory.id = 42;
        opHistory.type = OpHistoryManager.HISTORY_TYPE_INSTALLER;
        opHistory.execTime = 1_700_000_000_000L;
        opHistory.status = OpHistoryManager.STATUS_SUCCESS;
        opHistory.serializedData = new JSONObject()
                .put("package_name", "com.example.app")
                .put("app_label", "Example App")
                .put("install_existing", true)
                .toString();
        opHistory.serializedExtra = new JSONObject()
                .put("schema_version", 1)
                .put("mode_label", "ADB")
                .put("operation_label", "Install")
                .put("target_count", 1)
                .put("failed_count", 0)
                .put("requires_restart", false)
                .put("replayable", true)
                .put("reversible", false)
                .put("risk", OperationJournalMetadata.RISK_MEDIUM)
                .put("rollback_hint", "reinstall")
                .put("target_preview", new JSONArray().put("com.example.app"))
                .toString();
        return new OpHistoryItem(opHistory);
    }
}
