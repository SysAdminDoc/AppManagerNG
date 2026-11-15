// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

/**
 * Rollback from mutation images: it reads each target first, restores only what still holds AppManagerNG's value,
 * shows a target someone else changed without writing it, and keeps each target of a batch on its own.
 */
@RunWith(RobolectricTestRunner.class)
public class PerAppRollbackImageTest {
    private static final String PKG = "com.example.app";
    private static final int USER = 0;
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    private static final String FREEZE = MutationState.KIND_FREEZE;
    private static final String PERMISSION = MutationState.KIND_PERMISSION;
    private static final String APP_OP = MutationState.KIND_APP_OP;
    private static final String NET_POLICY = MutationState.KIND_NET_POLICY;

    private final Map<String, String> mCurrent = new HashMap<>();
    private final MutationState.Reader mReader = (kind, target, pkg, user) ->
            PKG.equals(pkg) && user == USER ? mCurrent.get(kind + ":" + target) : null;
    private long mNextId = 1;

    @Test
    public void aTargetStillHoldingTheRecordedValueIsRestored() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(1, plan.getRunnableCount());
        assertEquals(0, plan.getManualReviewCount());
        assertTrue(plan.getChangedElsewhere().isEmpty());
        PerAppRollbackManager.Step step = plan.getSteps().get(0);
        assertTrue(step.isImageStep());
        assertEquals(MutationState.GRANTED, step.getRecordedValue());
        assertEquals(MutationState.GRANTED, step.getCurrentValue());
        assertEquals(MutationState.DENIED, step.getRestoreValue());
        BatchQueueItem item = step.getQueueItem();
        assertEquals(BatchOpsManager.OP_REVOKE_PERMISSIONS, item.getOp());
        assertArrayEquals(new String[]{CAMERA}, ((BatchPermissionOptions) item.getOptions()).getPermissions());
        assertEquals(Collections.singletonList(PKG), item.getPackages());
    }

    @Test
    public void aTargetChangedElsewhereIsShownWithBothValuesAndNeverWritten() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", APP_OP, "26", "3", "1"));
        current(APP_OP, "26", "0");

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);
        List<BatchQueueItem> launched = new ArrayList<>();
        int queued = PerAppRollbackManager.start(plan, null, mReader, launched::add);

        assertEquals(0, plan.getRunnableCount());
        assertFalse(plan.hasRunnableActions());
        assertEquals(1, plan.getChangedElsewhere().size());
        PerAppRollbackManager.Step step = plan.getChangedElsewhere().get(0);
        assertTrue(step.isChangedElsewhere());
        assertNull(step.getQueueItem());
        assertEquals("1", step.getRecordedValue());
        assertEquals("0", step.getCurrentValue());
        assertEquals(0, queued);
        assertTrue(launched.isEmpty());
    }

    @Test
    public void anImageThatFailsVerificationGoesToManualReview() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                untrusted(image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED)));
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(0, plan.getRunnableCount());
        assertEquals(1, plan.getManualReviewCount());
        assertTrue(plan.getChangedElsewhere().isEmpty());
    }

    @Test
    public void aTargetThatCantBeReadNowGoesToManualReview() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED));

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(0, plan.getRunnableCount());
        assertEquals(1, plan.getManualReviewCount());
    }

    @Test
    public void anImageWhoseResultWasNeverReadBackGoesToManualReview() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, null));
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(0, plan.getRunnableCount());
        assertEquals(1, plan.getManualReviewCount());
    }

    @Test
    public void changesThatLedStraightIntoTheNewestAreUndoneWithIt() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", APP_OP, "26", "3", "1"),
                image("exec-b", APP_OP, "26", "1", "0"));
        current(APP_OP, "26", "0");

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(1, plan.getRunnableCount());
        PerAppRollbackManager.Step step = plan.getSteps().get(0);
        assertEquals("3", step.getRestoreValue());
        BatchAppOpsOptions options = (BatchAppOpsOptions) step.getQueueItem().getOptions();
        assertEquals(BatchOpsManager.OP_SET_APP_OPS, step.getQueueItem().getOp());
        assertArrayEquals(new int[]{26}, options.getAppOps());
        assertEquals(3, options.getMode());
    }

    @Test
    public void aChangeMadeElsewhereBetweenTwoOfOursIsKept() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", APP_OP, "26", "3", "1"),
                // Something set 2 after the first write; the second write found 2
                image("exec-b", APP_OP, "26", "2", "0"));
        current(APP_OP, "26", "0");

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(1, plan.getRunnableCount());
        assertEquals("2", plan.getSteps().get(0).getRestoreValue());
        assertEquals(0, plan.getManualReviewCount());
    }

    @Test
    public void anOlderChangeThatFailsVerificationStopsTheWalkAtTheNewestTrustedOne() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                untrusted(image("exec-a", APP_OP, "26", "3", "1")),
                image("exec-b", APP_OP, "26", "1", "0"));
        current(APP_OP, "26", "0");

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(1, plan.getRunnableCount());
        assertEquals("1", plan.getSteps().get(0).getRestoreValue());
        assertEquals(1, plan.getManualReviewCount());
    }

    @Test
    public void changesThatCancelOutLeaveNothingToUndo() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED),
                image("exec-b", PERMISSION, CAMERA, MutationState.GRANTED, MutationState.DENIED));
        current(PERMISSION, CAMERA, MutationState.DENIED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(0, plan.getRunnableCount());
        assertEquals(0, plan.getManualReviewCount());
        assertTrue(plan.getChangedElsewhere().isEmpty());
    }

    @Test
    public void eachTargetOfAPartlyFailedBatchStandsOnItsOwn() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED),
                // This one failed and nothing changed
                image("exec-a", PERMISSION, LOCATION, MutationState.DENIED, MutationState.DENIED),
                image("exec-a", PERMISSION, "android.permission.READ_CONTACTS", MutationState.DENIED,
                        MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);
        current(PERMISSION, LOCATION, MutationState.DENIED);
        // Revoked by hand since
        current(PERMISSION, "android.permission.READ_CONTACTS", MutationState.DENIED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(1, plan.getRunnableCount());
        assertEquals(CAMERA, plan.getSteps().get(0).getTarget());
        assertEquals(1, plan.getChangedElsewhere().size());
        assertEquals("android.permission.READ_CONTACTS", plan.getChangedElsewhere().get(0).getTarget());
        assertEquals(0, plan.getManualReviewCount());
    }

    @Test
    public void freezeIsUndoneByTheMatchingOperation() throws Exception {
        List<MutationJournal.Entry> frozeIt = Collections.singletonList(
                image("exec-a", FREEZE, "", MutationState.UNFROZEN, MutationState.DISABLED));
        current(FREEZE, "", MutationState.DISABLED);
        assertEquals(BatchOpsManager.OP_UNFREEZE,
                plan(Collections.emptyList(), frozeIt).getSteps().get(0).getQueueItem().getOp());

        List<MutationJournal.Entry> unfrozeIt = Collections.singletonList(
                image("exec-b", FREEZE, "", MutationState.HIDDEN, MutationState.UNFROZEN));
        current(FREEZE, "", MutationState.UNFROZEN);
        BatchQueueItem refreeze = plan(Collections.emptyList(), unfrozeIt).getSteps().get(0).getQueueItem();
        assertEquals(BatchOpsManager.OP_ADVANCED_FREEZE, refreeze.getOp());
        assertEquals(FreezeUtils.FREEZE_HIDE, ((BatchFreezeOptions) refreeze.getOptions()).getType());
    }

    @Test
    public void movingBetweenTwoFrozenStatesGoesToManualReview() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", FREEZE, "", MutationState.SUSPENDED, MutationState.DISABLED));
        current(FREEZE, "", MutationState.DISABLED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);

        assertEquals(0, plan.getRunnableCount());
        assertEquals(1, plan.getManualReviewCount());
    }

    @Test
    public void networkPolicyIsPutBackToTheValueFound() throws Exception {
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", NET_POLICY, "", "0", "4"));
        current(NET_POLICY, "", "4");

        BatchQueueItem item = plan(Collections.emptyList(), images).getSteps().get(0).getQueueItem();

        assertEquals(BatchOpsManager.OP_NET_POLICY, item.getOp());
        assertEquals(0, ((BatchNetPolicyOptions) item.getOptions()).getPolicies());
    }

    @Test
    public void aHistoryRowFromBeforeImagesIsStillInverted() throws Exception {
        OpHistory legacyFreeze = history(1, 100, BatchOpsManager.OP_FREEZE, null, null);
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.singletonList(legacyFreeze), images);

        assertEquals(2, plan.getRunnableCount());
        assertTrue(plan.getSteps().get(0).isImageStep());
        assertFalse(plan.getSteps().get(1).isImageStep());
        assertEquals(BatchOpsManager.OP_UNFREEZE, plan.getSteps().get(1).getQueueItem().getOp());
        assertEquals(1, plan.getMatchedHistoryCount());
    }

    @Test
    public void aRowWhoseExecutionLeftImagesIsNotInvertedAgain() throws Exception {
        OpHistory grant = history(1, 100, BatchOpsManager.OP_GRANT_PERMISSIONS,
                new BatchPermissionOptions(new String[]{CAMERA}), "exec-a");
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.singletonList(grant), images);

        assertEquals(1, plan.getRunnableCount());
        assertTrue(plan.getSteps().get(0).isImageStep());
        assertEquals(1, plan.getMatchedHistoryCount());
        assertEquals(0, plan.getManualReviewCount());
    }

    @Test
    public void aRowWithAnExecutionIdButNoImagesIsStillInverted() throws Exception {
        // Tracker blocking records no image, yet every batch row now carries an execution id
        OpHistory trackers = history(1, 100, BatchOpsManager.OP_BLOCK_TRACKERS, null, "exec-t");

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.singletonList(trackers),
                Collections.emptyList());

        assertEquals(1, plan.getRunnableCount());
        assertEquals(BatchOpsManager.OP_UNBLOCK_TRACKERS, plan.getSteps().get(0).getQueueItem().getOp());
    }

    @Test
    public void anOlderBlindInverseLeavesOutTargetsTheImagesTrack() throws Exception {
        OpHistory legacyGrant = history(1, 100, BatchOpsManager.OP_GRANT_PERMISSIONS,
                new BatchPermissionOptions(new String[]{CAMERA, LOCATION}), null);
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-b", PERMISSION, CAMERA, MutationState.GRANTED, MutationState.DENIED));
        current(PERMISSION, CAMERA, MutationState.DENIED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.singletonList(legacyGrant), images);

        assertEquals(2, plan.getRunnableCount());
        BatchQueueItem legacy = plan.getSteps().get(1).getQueueItem();
        assertEquals(BatchOpsManager.OP_REVOKE_PERMISSIONS, legacy.getOp());
        assertArrayEquals(new String[]{LOCATION}, ((BatchPermissionOptions) legacy.getOptions()).getPermissions());
    }

    @Test
    public void anOlderBlindInverseCoveredByImagesGoesToManualReview() throws Exception {
        OpHistory legacyFreeze = history(1, 100, BatchOpsManager.OP_FREEZE, null, null);
        List<MutationJournal.Entry> images = Collections.singletonList(
                image("exec-b", FREEZE, "", MutationState.DISABLED, MutationState.UNFROZEN));
        current(FREEZE, "", MutationState.UNFROZEN);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.singletonList(legacyFreeze), images);

        assertEquals(1, plan.getRunnableCount());
        assertTrue(plan.getSteps().get(0).isImageStep());
        assertEquals(1, plan.getManualReviewCount());
    }

    @Test
    public void startSkipsATargetThatChangedAfterThePlanWasBuilt() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED),
                image("exec-a", PERMISSION, LOCATION, MutationState.DENIED, MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);
        current(PERMISSION, LOCATION, MutationState.GRANTED);
        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);
        assertEquals(2, plan.getRunnableCount());

        current(PERMISSION, LOCATION, MutationState.DENIED);
        List<BatchQueueItem> launched = new ArrayList<>();
        int queued = PerAppRollbackManager.start(plan, null, mReader, launched::add);

        assertEquals(1, queued);
        assertEquals(1, launched.size());
        assertArrayEquals(new String[]{CAMERA},
                ((BatchPermissionOptions) launched.get(0).getOptions()).getPermissions());
    }

    @Test
    public void startRunsOnlyTheSelectedSteps() throws Exception {
        List<MutationJournal.Entry> images = Arrays.asList(
                image("exec-a", PERMISSION, CAMERA, MutationState.DENIED, MutationState.GRANTED),
                image("exec-a", PERMISSION, LOCATION, MutationState.DENIED, MutationState.GRANTED));
        current(PERMISSION, CAMERA, MutationState.GRANTED);
        current(PERMISSION, LOCATION, MutationState.GRANTED);
        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), images);
        String second = plan.getSteps().get(1).getTarget();

        List<BatchQueueItem> launched = new ArrayList<>();
        int queued = PerAppRollbackManager.start(plan, new boolean[]{false, true}, mReader, launched::add);
        List<BatchQueueItem> none = new ArrayList<>();
        int shortMask = PerAppRollbackManager.start(plan, new boolean[]{false}, mReader, none::add);

        assertEquals(1, queued);
        assertArrayEquals(new String[]{second},
                ((BatchPermissionOptions) launched.get(0).getOptions()).getPermissions());
        assertEquals(0, shortMask);
        assertTrue(none.isEmpty());
    }

    @Test
    public void imagesOfOtherAppsAndUsersAreIgnored() throws Exception {
        MutationJournal.Entry otherApp = image("exec-a", PERMISSION, CAMERA, MutationState.DENIED,
                MutationState.GRANTED);
        otherApp.image.packageName = "com.example.other";
        MutationJournal.Entry otherUser = image("exec-a", PERMISSION, CAMERA, MutationState.DENIED,
                MutationState.GRANTED);
        otherUser.image.userId = 10;
        current(PERMISSION, CAMERA, MutationState.GRANTED);

        PerAppRollbackManager.RollbackPlan plan = plan(Collections.emptyList(), Arrays.asList(otherApp, otherUser));

        assertEquals(0, plan.getRunnableCount());
        assertEquals(0, plan.getManualReviewCount());
    }

    @NonNull
    private PerAppRollbackManager.RollbackPlan plan(@NonNull List<OpHistory> rows,
                                                    @NonNull List<MutationJournal.Entry> images) {
        return PerAppRollbackManager.buildPlan(rows, images, mReader, PKG, USER);
    }

    private void current(@NonNull String kind, @NonNull String target, @NonNull String value) {
        mCurrent.put(kind + ":" + target, value);
    }

    @NonNull
    private MutationJournal.Entry image(@NonNull String executionId, @NonNull String kind, @NonNull String target,
                                        @Nullable String before, @Nullable String post) {
        MutationImage image = new MutationImage();
        image.id = mNextId++;
        image.executionId = executionId;
        image.executor = "root";
        image.operation = "batch:1";
        image.packageName = PKG;
        image.userId = USER;
        image.kind = kind;
        image.target = target;
        image.beforeValue = before;
        image.requestedValue = post != null ? post : "";
        image.postValue = post;
        image.result = post != null ? MutationJournal.RESULT_APPLIED : MutationJournal.RESULT_UNCONFIRMED;
        image.time = 1000 + image.id;
        image.prevMac = "";
        image.mac = "";
        return new MutationJournal.Entry(image, true);
    }

    @NonNull
    private static MutationJournal.Entry untrusted(@NonNull MutationJournal.Entry entry) {
        return new MutationJournal.Entry(entry.image, false);
    }

    @NonNull
    private static OpHistory history(long id, long time, int op, @Nullable IBatchOpOptions options,
                                     @Nullable String executionId) throws Exception {
        ArrayList<String> packages = new ArrayList<>();
        packages.add(PKG);
        ArrayList<Integer> users = new ArrayList<>();
        users.add(USER);
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, packages, users, options);
        OpHistory history = new OpHistory();
        history.id = id;
        history.execTime = time;
        history.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        history.status = OpHistoryManager.STATUS_SUCCESS;
        history.serializedData = item.serializeToJson().toString();
        if (executionId != null) {
            OperationJournalMetadata metadata = OperationJournalMetadata.fromJson("{}");
            history.serializedExtra = metadata.withExecutionId(executionId).serializeToJson().toString();
        }
        return history;
    }
}
