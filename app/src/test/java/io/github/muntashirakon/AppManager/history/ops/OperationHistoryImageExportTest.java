// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;

/** The JSON history export carries the mutation images that belong to the rows it exports. */
@RunWith(RobolectricTestRunner.class)
public class OperationHistoryImageExportTest {
    @Test
    public void anExportWithoutImagesKeepsItsEarlierShape() throws Exception {
        Context context = RuntimeEnvironment.getApplication();

        JSONObject root = new JSONObject(OperationHistoryExporter.toJson(context,
                Collections.singletonList(row(1, "exec-a"))));

        assertFalse(root.has("mutation_images"));
        assertFalse(root.has("mutation_image_count"));
    }

    @Test
    public void imagesAreExportedWithWhetherEachVerified() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        List<MutationJournal.Entry> images = Arrays.asList(entry(1, "exec-a", true), entry(2, "exec-a", false));

        JSONObject root = new JSONObject(OperationHistoryExporter.toJson(context,
                Collections.singletonList(row(1, "exec-a")), images));
        JSONArray exported = root.getJSONArray("mutation_images");

        assertEquals(2, root.getInt("mutation_image_count"));
        assertEquals(2, exported.length());
        assertEquals("exec-a", exported.getJSONObject(0).getString("execution_id"));
        assertEquals("denied", exported.getJSONObject(0).getString("before_value"));
        assertEquals("granted", exported.getJSONObject(0).getString("post_value"));
        assertTrue(exported.getJSONObject(0).getBoolean("verified"));
        assertFalse(exported.getJSONObject(1).getBoolean("verified"));
    }

    @Test
    public void aFilteredExportTakesOnlyTheImagesOfItsRows() throws Exception {
        List<OpHistoryItem> rows = Arrays.asList(row(1, "exec-a"), row(2, null));
        List<MutationJournal.Entry> images = Arrays.asList(entry(1, "exec-a", true), entry(2, "exec-b", true),
                entry(3, "exec-a", true));

        List<MutationJournal.Entry> visible = OperationHistoryExporter.imagesFor(rows, images, false);
        List<MutationJournal.Entry> everything = OperationHistoryExporter.imagesFor(rows, images, true);

        assertEquals(2, visible.size());
        assertEquals(1, visible.get(0).image.id);
        assertEquals(3, visible.get(1).image.id);
        assertEquals(3, everything.size());
    }

    @Test
    public void aRowWrittenBeforeImagesHasNoExecution() throws Exception {
        assertNull(row(1, null).getExecutionId());
        assertEquals("exec-a", row(1, "exec-a").getExecutionId());
    }

    @NonNull
    private static OpHistoryItem row(long id, @Nullable String executionId) throws Exception {
        OpHistory opHistory = new OpHistory();
        opHistory.id = id;
        opHistory.type = OpHistoryManager.HISTORY_TYPE_INSTALLER;
        opHistory.execTime = 1_700_000_000_000L;
        opHistory.status = OpHistoryManager.STATUS_SUCCESS;
        opHistory.serializedData = new JSONObject()
                .put("package_name", "com.example.app")
                .put("app_label", "Example App")
                .put("install_existing", true)
                .toString();
        if (executionId != null) {
            opHistory.serializedExtra = new JSONObject()
                    .put("schema_version", 1)
                    .put("execution_id", executionId)
                    .toString();
        }
        return new OpHistoryItem(opHistory);
    }

    @NonNull
    private static MutationJournal.Entry entry(long id, @NonNull String executionId, boolean trusted) {
        MutationImage image = new MutationImage();
        image.id = id;
        image.executionId = executionId;
        image.executor = "root";
        image.operation = "batch:1";
        image.packageName = "com.example.app";
        image.kind = MutationState.KIND_PERMISSION;
        image.target = "android.permission.CAMERA";
        image.beforeValue = MutationState.DENIED;
        image.requestedValue = MutationState.GRANTED;
        image.postValue = MutationState.GRANTED;
        image.result = MutationJournal.RESULT_APPLIED;
        image.time = 1_700_000_000_000L + id;
        image.prevMac = "";
        image.mac = "";
        return new MutationJournal.Entry(image, trusted);
    }
}
