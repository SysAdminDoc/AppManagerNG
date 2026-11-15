// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;

@RunWith(RobolectricTestRunner.class)
public class MutationRecorderTest {
    private static final String PKG = "com.example.app";
    private static final String CAMERA = "android.permission.CAMERA";

    private final Map<String, String> mState = new HashMap<>();
    private final List<MutationImage> mStored = new ArrayList<>();

    @Test
    public void anImageHoldsTheValuesBeforeAndAfterTheWrite() {
        mState.put(CAMERA, MutationState.DENIED);
        MutationRecorder recorder = recorder();

        MutationRecorder.Pending pending = recorder.begin(PKG, 10, MutationState.KIND_PERMISSION, CAMERA,
                MutationState.GRANTED);
        mState.put(CAMERA, MutationState.GRANTED);
        pending.finish(true);

        assertEquals(1, mStored.size());
        MutationImage image = mStored.get(0);
        assertEquals("exec-1", image.executionId);
        assertEquals("root", image.executor);
        assertEquals("batch:5", image.operation);
        assertEquals(PKG, image.packageName);
        assertEquals(10, image.userId);
        assertEquals(MutationState.KIND_PERMISSION, image.kind);
        assertEquals(CAMERA, image.target);
        assertEquals(MutationState.DENIED, image.beforeValue);
        assertEquals(MutationState.GRANTED, image.requestedValue);
        assertEquals(MutationState.GRANTED, image.postValue);
        assertEquals(MutationJournal.RESULT_APPLIED, image.result);
        assertTrue(image.time > 0);
    }

    @Test
    public void onlyTheFirstFinishIsStored() {
        mState.put(CAMERA, MutationState.DENIED);
        MutationRecorder.Pending pending = recorder().begin(PKG, 0, MutationState.KIND_PERMISSION, CAMERA,
                MutationState.GRANTED);

        pending.finish(false);
        mState.put(CAMERA, MutationState.GRANTED);
        pending.finish(true);

        assertEquals(1, mStored.size());
        assertEquals(MutationJournal.RESULT_FAILED, mStored.get(0).result);
        assertEquals(MutationState.DENIED, mStored.get(0).postValue);
    }

    @Test
    public void aStateThatCantBeReadIsStoredAsUnknownNeverAsAValue() {
        MutationRecorder recorder = new MutationRecorder("exec-1", "batch:5", "root",
                (kind, target, pkg, user) -> {
                    throw new SecurityException("no access");
                }, mStored::add);

        recorder.begin(PKG, 0, MutationState.KIND_PERMISSION, CAMERA, MutationState.GRANTED).finish(true);

        MutationImage image = mStored.get(0);
        assertNull(image.beforeValue);
        assertNull(image.postValue);
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, image.result);
    }

    @Test
    public void aSinkThatFailsNeverStopsTheWrite() {
        MutationRecorder recorder = new MutationRecorder("exec-1", "batch:5", "root",
                (kind, target, pkg, user) -> MutationState.DENIED, image -> {
                    throw new IllegalStateException("disk full");
                });

        // Nothing is thrown back to the operation
        recorder.begin(PKG, 0, MutationState.KIND_PERMISSION, CAMERA, MutationState.GRANTED).finish(true);
    }

    @Test
    public void everyTargetOfABatchGetsItsOwnImage() {
        mState.put("1", "3");
        mState.put("2", "0");
        MutationRecorder recorder = recorder();

        List<MutationRecorder.Pending> pending = recorder.beginAll(PKG, 0, MutationState.KIND_APP_OP,
                MutationRecorder.targetsOf(new int[]{1, 2}), "1");
        mState.put("1", "1");
        MutationRecorder.finishAll(pending, true);

        assertEquals(2, mStored.size());
        assertEquals("1", mStored.get(0).target);
        assertEquals(MutationJournal.RESULT_APPLIED, mStored.get(0).result);
        assertEquals("2", mStored.get(1).target);
        assertEquals("0", mStored.get(1).postValue);
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, mStored.get(1).result);
    }

    @Test
    public void resultSaysWhatTheReadBackShows() {
        assertEquals(MutationJournal.RESULT_FAILED, MutationRecorder.resultOf(false, MutationState.KIND_APP_OP,
                "1", "1"));
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, MutationRecorder.resultOf(true,
                MutationState.KIND_APP_OP, "1", null));
        assertEquals(MutationJournal.RESULT_APPLIED, MutationRecorder.resultOf(true, MutationState.KIND_APP_OP,
                "1", "1"));
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, MutationRecorder.resultOf(true,
                MutationState.KIND_APP_OP, "1", "0"));
    }

    @Test
    public void aFreezeHeldAlongsideAnotherMethodStillCountsAsApplied() {
        assertEquals(MutationJournal.RESULT_APPLIED, MutationRecorder.resultOf(true, MutationState.KIND_FREEZE,
                MutationState.SUSPENDED, "disabled+suspended"));
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, MutationRecorder.resultOf(true,
                MutationState.KIND_FREEZE, MutationState.SUSPENDED, MutationState.DISABLED));
        assertEquals(MutationJournal.RESULT_UNCONFIRMED, MutationRecorder.resultOf(true,
                MutationState.KIND_FREEZE, MutationState.UNFROZEN, MutationState.HIDDEN));
    }

    @Test
    public void freezeValuesJoinTheMethodsInAFixedOrder() {
        assertEquals(MutationState.UNFROZEN, MutationState.freezeValue(false, false, false));
        assertEquals("disabled+suspended+hidden", MutationState.freezeValue(true, true, true));
        assertEquals("suspended+hidden", MutationState.freezeValue(false, true, true));
        assertEquals(Arrays.asList(MutationState.DISABLED, MutationState.SUSPENDED, MutationState.HIDDEN),
                Arrays.asList(MutationState.freezeValue(true, false, false),
                        MutationState.freezeValue(false, true, false),
                        MutationState.freezeValue(false, false, true)));
    }

    @Test
    public void aroundFinishesEveryImageAsWrittenOrAsFailedAndStillThrows() {
        String location = "android.permission.ACCESS_FINE_LOCATION";
        mState.put(CAMERA, MutationState.DENIED);
        mState.put(location, MutationState.DENIED);
        MutationRecorder recorder = recorder();

        MutationRecorder.around(recorder.beginAll(PKG, 0, MutationState.KIND_PERMISSION,
                Arrays.asList(CAMERA, location), MutationState.GRANTED), () -> {
            mState.put(CAMERA, MutationState.GRANTED);
            mState.put(location, MutationState.GRANTED);
        });
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> MutationRecorder.around(
                Collections.singletonList(recorder.begin(PKG, 0, MutationState.KIND_PERMISSION, CAMERA,
                        MutationState.DENIED)), () -> {
                    throw new IllegalStateException("refused");
                }));

        assertEquals("refused", e.getMessage());
        assertEquals(3, mStored.size());
        for (int i = 0; i < 2; ++i) {
            assertEquals(MutationState.DENIED, mStored.get(i).beforeValue);
            assertEquals(MutationState.GRANTED, mStored.get(i).postValue);
            assertEquals(MutationJournal.RESULT_APPLIED, mStored.get(i).result);
        }
        assertEquals(MutationState.GRANTED, mStored.get(2).beforeValue);
        assertEquals(MutationState.GRANTED, mStored.get(2).postValue);
        assertEquals(MutationJournal.RESULT_FAILED, mStored.get(2).result);
    }

    @NonNull
    private MutationRecorder recorder() {
        return new MutationRecorder("exec-1", "batch:5", "root",
                (kind, target, pkg, user) -> mState.get(target), mStored::add);
    }
}
