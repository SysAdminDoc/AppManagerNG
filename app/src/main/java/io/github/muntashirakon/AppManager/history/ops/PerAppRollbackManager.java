// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.app.AppOpsManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;

public final class PerAppRollbackManager {
    @NonNull
    public static RollbackPlan buildPlan(@NonNull String packageName, int userId) {
        return buildPlan(OpHistoryManager.getAllHistoryItems(), packageName, userId);
    }

    @VisibleForTesting
    @NonNull
    static RollbackPlan buildPlan(@NonNull List<OpHistory> historyRows, @NonNull String packageName, int userId) {
        List<OpHistory> sortedRows = new ArrayList<>(historyRows);
        sortedRows.sort(Comparator.comparingLong((OpHistory row) -> row.execTime).reversed());
        List<BatchQueueItem> inverseQueueItems = new ArrayList<>();
        int matchedHistoryCount = 0;
        int manualReviewCount = 0;
        for (OpHistory row : sortedRows) {
            if (!OpHistoryManager.STATUS_SUCCESS.equals(OpHistoryManager.normalizeStatus(row.status))) {
                continue;
            }
            JSONObject sourceJson;
            try {
                sourceJson = new JSONObject(row.serializedData);
            } catch (JSONException e) {
                continue;
            }
            String historyType = OpHistoryManager.normalizeHistoryType(row.type);
            if (OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION.equals(historyType)) {
                if (!targetsSingleAppAction(sourceJson, packageName, userId)) {
                    continue;
                }
                ++matchedHistoryCount;
                BatchQueueItem inverseItem = getInverseSingleAppAction(sourceJson);
                if (inverseItem != null) {
                    inverseQueueItems.add(inverseItem);
                } else {
                    ++manualReviewCount;
                }
                continue;
            }
            if (!OpHistoryManager.HISTORY_TYPE_BATCH_OPS.equals(historyType)) {
                continue;
            }
            try {
                if (!targetsPackage(sourceJson, packageName, userId)) {
                    continue;
                }
            } catch (JSONException e) {
                continue;
            }
            ++matchedHistoryCount;
            BatchQueueItem sourceItem;
            try {
                sourceItem = BatchQueueItem.DESERIALIZER.deserialize(sourceJson);
            } catch (JSONException e) {
                ++manualReviewCount;
                continue;
            }
            int targetIndex = indexOf(sourceItem, packageName, userId);
            if (targetIndex < 0) {
                ++manualReviewCount;
                continue;
            }
            BatchQueueItem inverseItem = getInverseQueueItem(sourceItem, targetIndex);
            if (inverseItem != null) {
                inverseQueueItems.add(inverseItem);
            } else {
                ++manualReviewCount;
            }
        }
        return new RollbackPlan(packageName, userId, matchedHistoryCount, manualReviewCount, inverseQueueItems);
    }

    public static int start(@NonNull Context context, @NonNull RollbackPlan plan) {
        return start(context, plan, null);
    }

    /**
     * Run only the queue items whose index is true in {@code keep}; a null mask
     * runs the whole plan. EI-09 dry-run preview uses this to commit the user's
     * checkbox selection.
     */
    public static int start(@NonNull Context context, @NonNull RollbackPlan plan, @androidx.annotation.Nullable boolean[] keep) {
        List<BatchQueueItem> items = plan.getQueueItems();
        int queuedCount = 0;
        for (int i = 0; i < items.size(); ++i) {
            // A null mask runs everything; otherwise an item runs only if it is
            // explicitly selected. An index beyond the mask counts as NOT
            // selected — the previous `i < keep.length` guard short-circuited to
            // "run it", so a mask shorter than the plan silently committed every
            // unselected tail item (the opposite of the intended fail-safe).
            boolean selected = keep == null || (i < keep.length && keep[i]);
            if (!selected) continue;
            ContextCompat.startForegroundService(context, BatchOpsService.getServiceIntent(context, items.get(i)));
            ++queuedCount;
        }
        return queuedCount;
    }

    private static boolean targetsPackage(@NonNull JSONObject jsonObject,
                                          @NonNull String packageName,
                                          int userId) throws JSONException {
        JSONArray packages = jsonObject.getJSONArray("packages");
        JSONArray users = jsonObject.getJSONArray("users");
        int count = Math.min(packages.length(), users.length());
        for (int i = 0; i < count; ++i) {
            String targetPackage = getPackageName(packages.opt(i));
            Integer targetUser = OpHistoryManager.normalizeUserId(users.opt(i));
            if (packageName.equals(targetPackage) && targetUser != null && userId == targetUser) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetsSingleAppAction(@NonNull JSONObject jsonObject,
                                                  @NonNull String packageName,
                                                  int userId) {
        String targetPackage = getPackageName(jsonObject.opt("package_name"));
        Integer targetUser = OpHistoryManager.normalizeUserId(jsonObject.opt("user_id"));
        return packageName.equals(targetPackage) && targetUser != null && userId == targetUser;
    }

    private static int indexOf(@NonNull BatchQueueItem item, @NonNull String packageName, int userId) {
        List<String> packages = item.getPackages();
        List<Integer> users = item.getUsers();
        int count = Math.min(packages.size(), users.size());
        for (int i = 0; i < count; ++i) {
            if (packageName.equals(packages.get(i)) && userId == users.get(i)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static String getPackageName(@Nullable Object value) {
        return value instanceof String ? OpHistoryManager.normalizePackageName((String) value) : null;
    }

    @Nullable
    private static BatchQueueItem getInverseSingleAppAction(@NonNull JSONObject jsonObject) {
        String packageName = getPackageName(jsonObject.opt("package_name"));
        if (packageName == null) {
            return null;
        }
        Integer userId = OpHistoryManager.normalizeUserId(jsonObject.opt("user_id"));
        if (userId == null) {
            return null;
        }
        String action = jsonObject.optString("action");
        String target = jsonObject.optString("target_label");
        switch (action) {
            case SingleAppActionHistoryItem.ACTION_FREEZE:
                return singleTargetQueue(BatchOpsManager.OP_UNFREEZE, packageName, userId, null);
            case SingleAppActionHistoryItem.ACTION_UNFREEZE:
                return singleTargetQueue(BatchOpsManager.OP_FREEZE, packageName, userId, null);
            case SingleAppActionHistoryItem.ACTION_PERMISSION_GRANT:
                if (target.isEmpty()) {
                    return null;
                }
                return singleTargetQueue(BatchOpsManager.OP_REVOKE_PERMISSIONS, packageName, userId,
                        new BatchPermissionOptions(new String[]{target}));
            case SingleAppActionHistoryItem.ACTION_PERMISSION_REVOKE:
                if (target.isEmpty()) {
                    return null;
                }
                return singleTargetQueue(BatchOpsManager.OP_GRANT_PERMISSIONS, packageName, userId,
                        new BatchPermissionOptions(new String[]{target}));
            default:
                return null;
        }
    }

    @Nullable
    private static BatchQueueItem getInverseQueueItem(@NonNull BatchQueueItem sourceItem, int targetIndex) {
        IBatchOpOptions options = sourceItem.getOptions();
        switch (sourceItem.getOp()) {
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_FREEZE:
                return singleTargetQueue(BatchOpsManager.OP_UNFREEZE, sourceItem, targetIndex, null);
            case BatchOpsManager.OP_UNFREEZE:
                return singleTargetQueue(BatchOpsManager.OP_FREEZE, sourceItem, targetIndex, null);
            case BatchOpsManager.OP_BLOCK_TRACKERS:
                return singleTargetQueue(BatchOpsManager.OP_UNBLOCK_TRACKERS, sourceItem, targetIndex, null);
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
                return singleTargetQueue(BatchOpsManager.OP_BLOCK_TRACKERS, sourceItem, targetIndex, null);
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
                if (options instanceof BatchComponentOptions) {
                    return singleTargetQueue(BatchOpsManager.OP_UNBLOCK_COMPONENTS, sourceItem, targetIndex, options);
                }
                return null;
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
                if (options instanceof BatchComponentOptions) {
                    return singleTargetQueue(BatchOpsManager.OP_BLOCK_COMPONENTS, sourceItem, targetIndex, options);
                }
                return null;
            case BatchOpsManager.OP_GRANT_PERMISSIONS:
                if (isExplicitPermissionList(options)) {
                    return singleTargetQueue(BatchOpsManager.OP_REVOKE_PERMISSIONS, sourceItem, targetIndex, options);
                }
                return null;
            case BatchOpsManager.OP_REVOKE_PERMISSIONS:
                if (isExplicitPermissionList(options)) {
                    return singleTargetQueue(BatchOpsManager.OP_GRANT_PERMISSIONS, sourceItem, targetIndex, options);
                }
                return null;
            case BatchOpsManager.OP_SET_APP_OPS:
                if (options instanceof BatchAppOpsOptions) {
                    BatchAppOpsOptions appOpsOptions = (BatchAppOpsOptions) options;
                    return singleTargetQueue(BatchOpsManager.OP_SET_APP_OPS, sourceItem, targetIndex,
                            new BatchAppOpsOptions(appOpsOptions.getAppOps(), AppOpsManager.MODE_DEFAULT));
                }
                return null;
            case BatchOpsManager.OP_DISABLE_BACKGROUND: {
                int[] backgroundOps = getBackgroundAppOps();
                if (backgroundOps.length == 0) {
                    return null;
                }
                return singleTargetQueue(BatchOpsManager.OP_SET_APP_OPS, sourceItem, targetIndex,
                        new BatchAppOpsOptions(backgroundOps, AppOpsManager.MODE_DEFAULT));
            }
            case BatchOpsManager.OP_NET_POLICY:
                return singleTargetQueue(BatchOpsManager.OP_NET_POLICY, sourceItem, targetIndex,
                        new BatchNetPolicyOptions(0));
            default:
                return null;
        }
    }

    private static boolean isExplicitPermissionList(@Nullable IBatchOpOptions options) {
        if (!(options instanceof BatchPermissionOptions)) {
            return false;
        }
        String[] permissions = ((BatchPermissionOptions) options).getPermissions();
        return permissions.length != 1 || !"*".equals(permissions[0]);
    }

    @NonNull
    private static int[] getBackgroundAppOps() {
        List<Integer> ops = new ArrayList<>(2);
        if (AppOpsManagerCompat.OP_RUN_IN_BACKGROUND != 0) {
            ops.add(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND);
        }
        if (AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND != 0
                && AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND != AppOpsManagerCompat.OP_RUN_IN_BACKGROUND) {
            ops.add(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND);
        }
        int[] result = new int[ops.size()];
        for (int i = 0; i < ops.size(); ++i) {
            result[i] = ops.get(i);
        }
        return result;
    }

    @NonNull
    private static BatchQueueItem singleTargetQueue(@BatchOpsManager.OpType int op,
                                                    @NonNull BatchQueueItem sourceItem,
                                                    int targetIndex,
                                                    @Nullable IBatchOpOptions options) {
        return singleTargetQueue(op, sourceItem.getPackages().get(targetIndex), sourceItem.getUsers().get(targetIndex),
                options);
    }

    @NonNull
    private static BatchQueueItem singleTargetQueue(@BatchOpsManager.OpType int op,
                                                    @NonNull String packageName,
                                                    int userId,
                                                    @Nullable IBatchOpOptions options) {
        ArrayList<String> packages = new ArrayList<>(1);
        packages.add(packageName);
        ArrayList<Integer> users = new ArrayList<>(1);
        users.add(userId);
        return BatchQueueItem.getBatchOpQueue(op, packages, users, options);
    }

    public static final class RollbackPlan {
        @NonNull
        private final String mPackageName;
        private final int mUserId;
        private final int mMatchedHistoryCount;
        private final int mManualReviewCount;
        @NonNull
        private final List<BatchQueueItem> mQueueItems;

        private RollbackPlan(@NonNull String packageName,
                             int userId,
                             int matchedHistoryCount,
                             int manualReviewCount,
                             @NonNull List<BatchQueueItem> queueItems) {
            mPackageName = packageName;
            mUserId = userId;
            mMatchedHistoryCount = matchedHistoryCount;
            mManualReviewCount = manualReviewCount;
            mQueueItems = Collections.unmodifiableList(new ArrayList<>(queueItems));
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        public int getUserId() {
            return mUserId;
        }

        public int getMatchedHistoryCount() {
            return mMatchedHistoryCount;
        }

        public int getManualReviewCount() {
            return mManualReviewCount;
        }

        public int getRunnableCount() {
            return mQueueItems.size();
        }

        public boolean hasRunnableActions() {
            return !mQueueItems.isEmpty();
        }

        @NonNull
        public List<BatchQueueItem> getQueueItems() {
            return mQueueItems;
        }
    }

    private PerAppRollbackManager() {
    }
}
