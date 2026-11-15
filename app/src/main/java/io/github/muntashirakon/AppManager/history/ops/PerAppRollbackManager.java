// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Builds the steps that put one app back the way it was before AppManagerNG changed it.
 * <p>
 * Changes with mutation images are reverted from the recorded values, and only while the target still holds the
 * value AppManagerNG left: anything else means it was changed elsewhere, and that row is shown but never written.
 * History rows from before images existed fall back to inverting the saved operation.
 */
public final class PerAppRollbackManager {
    public static final String TAG = PerAppRollbackManager.class.getSimpleName();

    /** Queues one batch item. Tests swap it for a list. */
    @VisibleForTesting
    interface Launcher {
        void launch(@NonNull BatchQueueItem item);
    }

    @WorkerThread
    @NonNull
    public static RollbackPlan buildPlan(@NonNull String packageName, int userId) {
        List<MutationJournal.Entry> images;
        try {
            images = MutationJournal.getInstance().readFor(packageName, userId);
        } catch (Throwable th) {
            // Without images every change falls back to its history row, which is how rollback worked before them
            Log.w(TAG, "Could not read the mutation journal", th);
            images = Collections.emptyList();
        }
        return buildPlan(OpHistoryManager.getAllHistoryItems(), images, MutationState.SYSTEM, packageName, userId);
    }

    @VisibleForTesting
    @NonNull
    static RollbackPlan buildPlan(@NonNull List<OpHistory> historyRows, @NonNull String packageName, int userId) {
        return buildPlan(historyRows, Collections.emptyList(), (kind, target, pkg, user) -> null, packageName,
                userId);
    }

    @VisibleForTesting
    @NonNull
    static RollbackPlan buildPlan(@NonNull List<OpHistory> historyRows,
                                  @NonNull List<MutationJournal.Entry> images,
                                  @NonNull MutationState.Reader reader,
                                  @NonNull String packageName,
                                  int userId) {
        Tally tally = new Tally();
        List<Step> runnable = new ArrayList<>();
        List<Step> changedElsewhere = new ArrayList<>();
        Set<String> imagedExecutions = new HashSet<>();
        Set<String> imagedTargets = new HashSet<>();
        for (List<MutationJournal.Entry> group : groupByTarget(images, packageName, userId)) {
            for (MutationJournal.Entry entry : group) {
                imagedExecutions.add(entry.image.executionId);
                imagedTargets.add(targetKey(entry.image.kind, entry.image.target));
            }
            Step step = imageStep(group, reader, packageName, userId, tally);
            if (step == null) {
                continue;
            }
            if (step.isChangedElsewhere()) {
                changedElsewhere.add(step);
            } else {
                runnable.add(step);
            }
        }
        addLegacySteps(historyRows, packageName, userId, imagedExecutions, imagedTargets, runnable, tally);
        return new RollbackPlan(packageName, userId, tally.matchedHistory, tally.manualReview, runnable,
                changedElsewhere);
    }

    /** The image groups of one app, one per target, newest target change first. */
    @NonNull
    private static List<List<MutationJournal.Entry>> groupByTarget(@NonNull List<MutationJournal.Entry> images,
                                                                   @NonNull String packageName, int userId) {
        List<MutationJournal.Entry> sorted = new ArrayList<>();
        for (MutationJournal.Entry entry : images) {
            if (entry.image.userId == userId && packageName.equals(entry.image.packageName)) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator.comparingLong(entry -> entry.image.id));
        Map<String, List<MutationJournal.Entry>> groups = new LinkedHashMap<>();
        for (MutationJournal.Entry entry : sorted) {
            String key = targetKey(entry.image.kind, entry.image.target);
            List<MutationJournal.Entry> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(entry);
        }
        List<List<MutationJournal.Entry>> result = new ArrayList<>(groups.values());
        result.sort((a, b) -> Long.compare(b.get(b.size() - 1).image.id, a.get(a.size() - 1).image.id));
        return result;
    }

    @NonNull
    private static String targetKey(@NonNull String kind, @NonNull String target) {
        // No kind holds a colon, so the first one always ends the kind
        return kind + ":" + target;
    }

    /**
     * The step for one target, or null when nothing of AppManagerNG's is left to undo. The newest change sets the
     * value the target must still hold; older changes that led straight into it are undone with it, back to the
     * first value AppManagerNG found. A change made elsewhere in between ends that walk.
     */
    @Nullable
    private static Step imageStep(@NonNull List<MutationJournal.Entry> group, @NonNull MutationState.Reader reader,
                                  @NonNull String packageName, int userId, @NonNull Tally tally) {
        List<MutationJournal.Entry> changes = new ArrayList<>(group.size());
        for (MutationJournal.Entry entry : group) {
            MutationImage image = entry.image;
            boolean unchanged = image.beforeValue != null && image.beforeValue.equals(image.postValue);
            if (!unchanged) {
                changes.add(entry);
            }
        }
        if (changes.isEmpty()) {
            return null;
        }
        MutationJournal.Entry newest = changes.get(changes.size() - 1);
        if (!newest.trusted || newest.image.beforeValue == null || newest.image.postValue == null) {
            ++tally.manualReview;
            return null;
        }
        String recorded = newest.image.postValue;
        String restore = newest.image.beforeValue;
        for (int i = changes.size() - 2; i >= 0; --i) {
            MutationJournal.Entry older = changes.get(i);
            if (older.image.postValue == null) {
                // Whether that change stuck is unknown, so what came before it can't be undone safely
                ++tally.manualReview;
                break;
            }
            if (!older.image.postValue.equals(restore)) {
                // Something else changed the target between the two, and that change stays
                break;
            }
            if (!older.trusted || older.image.beforeValue == null) {
                ++tally.manualReview;
                break;
            }
            restore = older.image.beforeValue;
        }
        if (restore.equals(recorded)) {
            // AppManagerNG's changes cancel out
            return null;
        }
        String kind = newest.image.kind;
        String target = newest.image.target;
        String current;
        try {
            current = reader.read(kind, target, packageName, userId);
        } catch (Throwable th) {
            current = null;
        }
        if (current == null) {
            ++tally.manualReview;
            return null;
        }
        if (!current.equals(recorded)) {
            return Step.changedElsewhere(kind, target, recorded, current, restore);
        }
        BatchQueueItem item = restoreItem(kind, target, recorded, restore, packageName, userId);
        if (item == null) {
            ++tally.manualReview;
            return null;
        }
        return Step.image(item, kind, target, recorded, current, restore);
    }

    /** The batch item that writes {@code restore} back, or null when no batch operation can. */
    @Nullable
    private static BatchQueueItem restoreItem(@NonNull String kind, @NonNull String target, @NonNull String recorded,
                                              @NonNull String restore, @NonNull String packageName, int userId) {
        try {
            switch (kind) {
                case MutationState.KIND_FREEZE: {
                    if (MutationState.UNFROZEN.equals(restore)) {
                        return singleTargetQueue(BatchOpsManager.OP_UNFREEZE, packageName, userId, null);
                    }
                    Integer method = MutationState.freezeMethodFor(restore);
                    if (method != null && MutationState.UNFROZEN.equals(recorded)) {
                        return singleTargetQueue(BatchOpsManager.OP_ADVANCED_FREEZE, packageName, userId,
                                new BatchFreezeOptions(method, false));
                    }
                    // Moving between two frozen states, or into several at once, has no single batch operation
                    return null;
                }
                case MutationState.KIND_PERMISSION:
                    if (MutationState.GRANTED.equals(restore)) {
                        return singleTargetQueue(BatchOpsManager.OP_GRANT_PERMISSIONS, packageName, userId,
                                new BatchPermissionOptions(new String[]{target}));
                    }
                    if (MutationState.DENIED.equals(restore)) {
                        return singleTargetQueue(BatchOpsManager.OP_REVOKE_PERMISSIONS, packageName, userId,
                                new BatchPermissionOptions(new String[]{target}));
                    }
                    return null;
                case MutationState.KIND_APP_OP:
                    return singleTargetQueue(BatchOpsManager.OP_SET_APP_OPS, packageName, userId,
                            new BatchAppOpsOptions(new int[]{Integer.parseInt(target)}, Integer.parseInt(restore)));
                case MutationState.KIND_NET_POLICY:
                    return singleTargetQueue(BatchOpsManager.OP_NET_POLICY, packageName, userId,
                            new BatchNetPolicyOptions(Integer.parseInt(restore)));
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Inverts history rows the images don't cover: rows from before images existed, and kinds no image records
     * (trackers, components). A row whose execution left images is already handled by them.
     */
    private static void addLegacySteps(@NonNull List<OpHistory> historyRows, @NonNull String packageName, int userId,
                                       @NonNull Set<String> imagedExecutions, @NonNull Set<String> imagedTargets,
                                       @NonNull List<Step> steps, @NonNull Tally tally) {
        List<OpHistory> sortedRows = new ArrayList<>(historyRows);
        sortedRows.sort(Comparator.comparingLong((OpHistory row) -> row.execTime).reversed());
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
            boolean imaged = hasImages(row, imagedExecutions);
            if (OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION.equals(historyType)) {
                if (!targetsSingleAppAction(sourceJson, packageName, userId)) {
                    continue;
                }
                ++tally.matchedHistory;
                if (imaged) {
                    continue;
                }
                addLegacyStep(getInverseSingleAppAction(sourceJson), imagedTargets, steps, tally);
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
            ++tally.matchedHistory;
            if (imaged) {
                continue;
            }
            BatchQueueItem sourceItem;
            try {
                sourceItem = BatchQueueItem.DESERIALIZER.deserialize(sourceJson);
            } catch (JSONException e) {
                ++tally.manualReview;
                continue;
            }
            int targetIndex = indexOf(sourceItem, packageName, userId);
            if (targetIndex < 0) {
                ++tally.manualReview;
                continue;
            }
            addLegacyStep(getInverseQueueItem(sourceItem, targetIndex), imagedTargets, steps, tally);
        }
    }

    private static void addLegacyStep(@Nullable BatchQueueItem inverseItem, @NonNull Set<String> imagedTargets,
                                      @NonNull List<Step> steps, @NonNull Tally tally) {
        if (inverseItem == null) {
            ++tally.manualReview;
            return;
        }
        BatchQueueItem uncovered = withoutImagedTargets(inverseItem, imagedTargets);
        if (uncovered == null) {
            // The images own that target now, and a blind write after them would undo their checked restore
            ++tally.manualReview;
            return;
        }
        steps.add(Step.legacy(uncovered));
    }

    private static boolean hasImages(@NonNull OpHistory row, @NonNull Set<String> imagedExecutions) {
        OperationJournalMetadata metadata = OperationJournalMetadata.fromJson(row.serializedExtra);
        String executionId = metadata != null ? metadata.getExecutionId() : null;
        return executionId != null && imagedExecutions.contains(executionId);
    }

    /**
     * An older blind inverse with every target the images track taken out, or null when nothing is left. Freeze and
     * network policy are one value per app; permissions and app ops are filtered one by one.
     */
    @Nullable
    private static BatchQueueItem withoutImagedTargets(@NonNull BatchQueueItem inverseItem,
                                                       @NonNull Set<String> imagedTargets) {
        IBatchOpOptions options = inverseItem.getOptions();
        switch (inverseItem.getOp()) {
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_UNFREEZE:
                return imagedTargets.contains(targetKey(MutationState.KIND_FREEZE, "")) ? null : inverseItem;
            case BatchOpsManager.OP_NET_POLICY:
                return imagedTargets.contains(targetKey(MutationState.KIND_NET_POLICY, "")) ? null : inverseItem;
            case BatchOpsManager.OP_GRANT_PERMISSIONS:
            case BatchOpsManager.OP_REVOKE_PERMISSIONS: {
                if (!(options instanceof BatchPermissionOptions)) {
                    return inverseItem;
                }
                String[] permissions = ((BatchPermissionOptions) options).getPermissions();
                List<String> left = new ArrayList<>(permissions.length);
                for (String permission : permissions) {
                    if (!imagedTargets.contains(targetKey(MutationState.KIND_PERMISSION, permission))) {
                        left.add(permission);
                    }
                }
                if (left.size() == permissions.length) {
                    return inverseItem;
                }
                return left.isEmpty() ? null : singleTargetQueue(inverseItem.getOp(), inverseItem, 0,
                        new BatchPermissionOptions(left.toArray(new String[0])));
            }
            case BatchOpsManager.OP_SET_APP_OPS: {
                if (!(options instanceof BatchAppOpsOptions)) {
                    return inverseItem;
                }
                BatchAppOpsOptions appOpsOptions = (BatchAppOpsOptions) options;
                int[] ops = appOpsOptions.getAppOps();
                List<Integer> left = new ArrayList<>(ops.length);
                for (int op : ops) {
                    if (!imagedTargets.contains(targetKey(MutationState.KIND_APP_OP, String.valueOf(op)))) {
                        left.add(op);
                    }
                }
                if (left.size() == ops.length) {
                    return inverseItem;
                }
                if (left.isEmpty()) {
                    return null;
                }
                int[] leftOps = new int[left.size()];
                for (int i = 0; i < leftOps.length; ++i) {
                    leftOps[i] = left.get(i);
                }
                return singleTargetQueue(inverseItem.getOp(), inverseItem, 0,
                        new BatchAppOpsOptions(leftOps, appOpsOptions.getMode()));
            }
            default:
                return inverseItem;
        }
    }

    /**
     * Queues the selected runnable steps; a null mask runs them all. An image step reads its target again first
     * and is skipped when the value no longer matches, since the plan may be minutes old by now.
     */
    @WorkerThread
    public static int start(@NonNull Context context, @NonNull RollbackPlan plan, @Nullable boolean[] keep) {
        return start(plan, keep, MutationState.SYSTEM,
                item -> ContextCompat.startForegroundService(context, BatchOpsService.getServiceIntent(context, item)));
    }

    @VisibleForTesting
    static int start(@NonNull RollbackPlan plan, @Nullable boolean[] keep, @NonNull MutationState.Reader reader,
                     @NonNull Launcher launcher) {
        List<Step> steps = plan.getSteps();
        int queuedCount = 0;
        for (int i = 0; i < steps.size(); ++i) {
            // An index beyond the mask counts as not selected, so a short mask never runs unselected steps
            boolean selected = keep == null || (i < keep.length && keep[i]);
            if (!selected) continue;
            Step step = steps.get(i);
            if (step.isImageStep() && !stillHolds(step, plan, reader)) {
                Log.i(TAG, "Skipped %s %s: it changed after the plan was built", step.getKind(), step.getTarget());
                continue;
            }
            launcher.launch(step.getQueueItem());
            ++queuedCount;
        }
        return queuedCount;
    }

    private static boolean stillHolds(@NonNull Step step, @NonNull RollbackPlan plan,
                                      @NonNull MutationState.Reader reader) {
        String current;
        try {
            current = reader.read(step.getKind(), step.getTarget(), plan.getPackageName(), plan.getUserId());
        } catch (Throwable th) {
            current = null;
        }
        return current != null && current.equals(step.getRecordedValue());
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
    // AppOpsManagerCompat exposes zero on releases where each operation is absent.
    @SuppressLint("NewApi")
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

    private static final class Tally {
        int matchedHistory;
        int manualReview;
    }

    /**
     * One row of the plan. An image step carries the recorded, current and restore values; a legacy step only the
     * inverse operation. A changed-elsewhere step has no operation and is never run.
     */
    public static final class Step {
        @Nullable
        private final BatchQueueItem mQueueItem;
        @Nullable
        private final String mKind;
        @Nullable
        private final String mTarget;
        @Nullable
        private final String mRecordedValue;
        @Nullable
        private final String mCurrentValue;
        @Nullable
        private final String mRestoreValue;

        private Step(@Nullable BatchQueueItem queueItem, @Nullable String kind, @Nullable String target,
                     @Nullable String recordedValue, @Nullable String currentValue, @Nullable String restoreValue) {
            mQueueItem = queueItem;
            mKind = kind;
            mTarget = target;
            mRecordedValue = recordedValue;
            mCurrentValue = currentValue;
            mRestoreValue = restoreValue;
        }

        @NonNull
        static Step image(@NonNull BatchQueueItem item, @NonNull String kind, @NonNull String target,
                          @NonNull String recorded, @NonNull String current, @NonNull String restore) {
            return new Step(item, kind, target, recorded, current, restore);
        }

        @NonNull
        static Step changedElsewhere(@NonNull String kind, @NonNull String target, @NonNull String recorded,
                                     @NonNull String current, @NonNull String restore) {
            return new Step(null, kind, target, recorded, current, restore);
        }

        @NonNull
        static Step legacy(@NonNull BatchQueueItem item) {
            return new Step(item, null, null, null, null, null);
        }

        /** The operation to run; null only for a changed-elsewhere step. */
        @Nullable
        public BatchQueueItem getQueueItem() {
            return mQueueItem;
        }

        public boolean isImageStep() {
            return mKind != null;
        }

        public boolean isChangedElsewhere() {
            return mQueueItem == null;
        }

        @Nullable
        public String getKind() {
            return mKind;
        }

        @Nullable
        public String getTarget() {
            return mTarget;
        }

        /** The value AppManagerNG left the target holding. */
        @Nullable
        public String getRecordedValue() {
            return mRecordedValue;
        }

        /** The value the target held when the plan was built. */
        @Nullable
        public String getCurrentValue() {
            return mCurrentValue;
        }

        /** The value the step writes back. */
        @Nullable
        public String getRestoreValue() {
            return mRestoreValue;
        }
    }

    public static final class RollbackPlan {
        @NonNull
        private final String mPackageName;
        private final int mUserId;
        private final int mMatchedHistoryCount;
        private final int mManualReviewCount;
        @NonNull
        private final List<Step> mSteps;
        @NonNull
        private final List<Step> mChangedElsewhere;
        @NonNull
        private final List<BatchQueueItem> mQueueItems;

        private RollbackPlan(@NonNull String packageName,
                             int userId,
                             int matchedHistoryCount,
                             int manualReviewCount,
                             @NonNull List<Step> steps,
                             @NonNull List<Step> changedElsewhere) {
            mPackageName = packageName;
            mUserId = userId;
            mMatchedHistoryCount = matchedHistoryCount;
            mManualReviewCount = manualReviewCount;
            mSteps = Collections.unmodifiableList(new ArrayList<>(steps));
            mChangedElsewhere = Collections.unmodifiableList(new ArrayList<>(changedElsewhere));
            List<BatchQueueItem> queueItems = new ArrayList<>(steps.size());
            for (Step step : steps) {
                queueItems.add(step.getQueueItem());
            }
            mQueueItems = Collections.unmodifiableList(queueItems);
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
            return mSteps.size();
        }

        public boolean hasRunnableActions() {
            return !mSteps.isEmpty();
        }

        /** The runnable steps, in the order they're queued. */
        @NonNull
        public List<Step> getSteps() {
            return mSteps;
        }

        /** Targets that no longer hold what AppManagerNG left; shown, never written. */
        @NonNull
        public List<Step> getChangedElsewhere() {
            return mChangedElsewhere;
        }

        /** The operations of {@link #getSteps()}, index for index. */
        @NonNull
        public List<BatchQueueItem> getQueueItems() {
            return mQueueItems;
        }
    }

    private PerAppRollbackManager() {
    }
}
