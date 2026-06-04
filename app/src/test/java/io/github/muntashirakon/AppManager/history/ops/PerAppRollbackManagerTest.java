// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;

import android.app.AppOpsManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;

@RunWith(RobolectricTestRunner.class)
public class PerAppRollbackManagerTest {
    @Test
    public void buildPlanQueuesInverseOperationsNewestFirst() throws Exception {
        OpHistory freeze = history(1, 100, BatchOpsManager.OP_FREEZE, null,
                "com.example.app", 0);
        OpHistory unblockTrackers = history(2, 200, BatchOpsManager.OP_UNBLOCK_TRACKERS, null,
                "com.example.app", 0);

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Arrays.asList(freeze, unblockTrackers), "com.example.app", 0);

        assertEquals(2, plan.getMatchedHistoryCount());
        assertEquals(0, plan.getManualReviewCount());
        assertEquals(2, plan.getRunnableCount());
        assertEquals(BatchOpsManager.OP_BLOCK_TRACKERS, plan.getQueueItems().get(0).getOp());
        assertEquals(BatchOpsManager.OP_UNFREEZE, plan.getQueueItems().get(1).getOp());
    }

    @Test
    public void buildPlanInvertsExplicitPermissionsAndNetworkPolicy() throws Exception {
        BatchPermissionOptions permissionOptions = new BatchPermissionOptions(
                new String[]{"android.permission.POST_NOTIFICATIONS"});
        BatchNetPolicyOptions netPolicyOptions = new BatchNetPolicyOptions(4);
        OpHistory grantPermission = history(1, 100, BatchOpsManager.OP_GRANT_PERMISSIONS, permissionOptions,
                "com.example.app", 10);
        OpHistory networkPolicy = history(2, 200, BatchOpsManager.OP_NET_POLICY, netPolicyOptions,
                "com.example.app", 10);

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Arrays.asList(grantPermission, networkPolicy), "com.example.app", 10);

        assertEquals(2, plan.getRunnableCount());
        assertEquals(BatchOpsManager.OP_NET_POLICY, plan.getQueueItems().get(0).getOp());
        BatchNetPolicyOptions inverseNetPolicy = (BatchNetPolicyOptions) plan.getQueueItems().get(0).getOptions();
        assertEquals(0, inverseNetPolicy.getPolicies());
        assertEquals(BatchOpsManager.OP_REVOKE_PERMISSIONS, plan.getQueueItems().get(1).getOp());
    }

    @Test
    public void buildPlanResetsAppOpsToDefault() throws Exception {
        int opRunInBackground = 42;
        BatchAppOpsOptions appOpsOptions = new BatchAppOpsOptions(
                new int[]{opRunInBackground}, AppOpsManager.MODE_IGNORED);
        OpHistory setAppOp = history(1, 100, BatchOpsManager.OP_SET_APP_OPS, appOpsOptions,
                "com.example.app", 0);

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Collections.singletonList(setAppOp), "com.example.app", 0);

        assertEquals(1, plan.getRunnableCount());
        BatchAppOpsOptions inverse = (BatchAppOpsOptions) plan.getQueueItems().get(0).getOptions();
        assertEquals(AppOpsManager.MODE_DEFAULT, inverse.getMode());
        assertEquals(opRunInBackground, inverse.getAppOps()[0]);
    }

    @Test
    public void buildPlanIncludesSingleAppActionRows() throws Exception {
        OpHistory freeze = singleAction(1, 100, SingleAppActionHistoryItem.ACTION_FREEZE,
                "com.example.app", 0, "Freeze");
        OpHistory grantPermission = singleAction(2, 200, SingleAppActionHistoryItem.ACTION_PERMISSION_GRANT,
                "com.example.app", 0, "android.permission.POST_NOTIFICATIONS");
        OpHistory appOp = singleAction(3, 300, SingleAppActionHistoryItem.ACTION_APP_OP_SET,
                "com.example.app", 0, "RUN_IN_BACKGROUND");

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Arrays.asList(freeze, grantPermission, appOp), "com.example.app", 0);

        assertEquals(3, plan.getMatchedHistoryCount());
        assertEquals(1, plan.getManualReviewCount());
        assertEquals(2, plan.getRunnableCount());
        assertEquals(BatchOpsManager.OP_REVOKE_PERMISSIONS, plan.getQueueItems().get(0).getOp());
        BatchPermissionOptions permissionOptions =
                (BatchPermissionOptions) plan.getQueueItems().get(0).getOptions();
        assertEquals("android.permission.POST_NOTIFICATIONS", permissionOptions.getPermissions()[0]);
        assertEquals(BatchOpsManager.OP_UNFREEZE, plan.getQueueItems().get(1).getOp());
    }

    @Test
    public void buildPlanSkipsUnrelatedAndManualOnlyRows() throws Exception {
        OpHistory uninstall = history(1, 100, BatchOpsManager.OP_UNINSTALL, null,
                "com.example.app", 0);
        OpHistory otherUserFreeze = history(2, 200, BatchOpsManager.OP_FREEZE, null,
                "com.example.app", 10);
        OpHistory failedFreeze = history(3, 300, BatchOpsManager.OP_FREEZE, null,
                "com.example.app", 0);
        failedFreeze.status = OpHistoryManager.STATUS_FAILURE;

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Arrays.asList(uninstall, otherUserFreeze, failedFreeze), "com.example.app", 0);

        assertEquals(1, plan.getMatchedHistoryCount());
        assertEquals(1, plan.getManualReviewCount());
        assertEquals(0, plan.getRunnableCount());
    }

    @Test
    public void buildPlanOnlyCountsMalformedRowsAfterTargetMatch() throws Exception {
        OpHistory malformedUnrelated = new OpHistory();
        malformedUnrelated.id = 1;
        malformedUnrelated.execTime = 100;
        malformedUnrelated.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        malformedUnrelated.status = OpHistoryManager.STATUS_SUCCESS;
        malformedUnrelated.serializedData = "{not-json";

        JSONObject malformedTarget = new JSONObject()
                .put("title_res", 0)
                .put("op", BatchOpsManager.OP_BLOCK_COMPONENTS)
                .put("packages", new JSONArray().put("com.example.app"))
                .put("users", new JSONArray().put(0))
                .put("options", new JSONObject().put("tag", "invalid"));
        OpHistory malformedMatched = new OpHistory();
        malformedMatched.id = 2;
        malformedMatched.execTime = 200;
        malformedMatched.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        malformedMatched.status = OpHistoryManager.STATUS_SUCCESS;
        malformedMatched.serializedData = malformedTarget.toString();

        PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(
                Arrays.asList(malformedUnrelated, malformedMatched), "com.example.app", 0);

        assertEquals(1, plan.getMatchedHistoryCount());
        assertEquals(1, plan.getManualReviewCount());
        assertEquals(0, plan.getRunnableCount());
    }

    private static OpHistory history(long id, long time, int op, IBatchOpOptions options, String packageName, int userId)
            throws Exception {
        ArrayList<String> packages = new ArrayList<>();
        packages.add(packageName);
        ArrayList<Integer> users = new ArrayList<>();
        users.add(userId);
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, packages, users, options);
        OpHistory history = new OpHistory();
        history.id = id;
        history.execTime = time;
        history.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        history.status = OpHistoryManager.STATUS_SUCCESS;
        history.serializedData = item.serializeToJson().toString();
        return history;
    }

    private static OpHistory singleAction(long id, long time, String action, String packageName, int userId,
                                          String targetLabel) throws Exception {
        OpHistory history = new OpHistory();
        history.id = id;
        history.execTime = time;
        history.type = OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION;
        history.status = OpHistoryManager.STATUS_SUCCESS;
        history.serializedData = new JSONObject()
                .put("action", action)
                .put("operation_label", action)
                .put("package_name", packageName)
                .put("user_id", userId)
                .put("target_label", targetLabel)
                .toString();
        return history;
    }
}
