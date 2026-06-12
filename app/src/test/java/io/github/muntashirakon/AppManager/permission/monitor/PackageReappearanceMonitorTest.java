// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;

public class PackageReappearanceMonitorTest {
    private static final long NOW = 10_000_000L;

    @Test
    public void findsRecentSuccessfulUninstallForPackage() throws JSONException {
        OpHistory older = row(NOW - 2_000L, OpHistoryManager.STATUS_SUCCESS,
                BatchOpsManager.OP_UNINSTALL, "com.example.app");
        OpHistory newer = row(NOW - 1_000L, OpHistoryManager.STATUS_SUCCESS,
                BatchOpsManager.OP_UNINSTALL, "com.example.app");

        assertSame(newer, PackageReappearanceMonitor.findRecentSuccessfulUninstall(
                Arrays.asList(older, newer), "com.example.app", NOW));
    }

    @Test
    public void ignoresFailedOldWrongOperationAndOtherPackageRows() throws JSONException {
        OpHistory failed = row(NOW - 1_000L, OpHistoryManager.STATUS_FAILURE,
                BatchOpsManager.OP_UNINSTALL, "com.example.app");
        OpHistory old = row(NOW - PackageReappearanceMonitor.RECENT_UNINSTALL_WINDOW_MILLIS - 1L,
                OpHistoryManager.STATUS_SUCCESS, BatchOpsManager.OP_UNINSTALL, "com.example.app");
        OpHistory wrongOp = row(NOW - 1_000L, OpHistoryManager.STATUS_SUCCESS,
                BatchOpsManager.OP_FREEZE, "com.example.app");
        OpHistory otherPackage = row(NOW - 1_000L, OpHistoryManager.STATUS_SUCCESS,
                BatchOpsManager.OP_UNINSTALL, "com.example.other");

        assertNull(PackageReappearanceMonitor.findRecentSuccessfulUninstall(
                Arrays.asList(failed, old, wrongOp, otherPackage), "com.example.app", NOW));
    }

    @Test
    public void ignoresMalformedHistoryRows() {
        OpHistory row = new OpHistory();
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.status = OpHistoryManager.STATUS_SUCCESS;
        row.execTime = NOW - 1_000L;
        row.serializedData = "{not-json";

        assertNull(PackageReappearanceMonitor.findRecentSuccessfulUninstall(
                Collections.singletonList(row), "com.example.app", NOW));
    }

    private static OpHistory row(long execTime, String status, int op, String packageName) throws JSONException {
        OpHistory row = new OpHistory();
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.status = status;
        row.execTime = execTime;
        row.serializedData = new JSONObject()
                .put("op", op)
                .put("packages", new JSONArray().put(packageName))
                .toString();
        return row;
    }
}
