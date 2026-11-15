// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.history.ops.MutationJournal;
import io.github.muntashirakon.AppManager.history.ops.MutationRecorder;
import io.github.muntashirakon.AppManager.history.ops.MutationState;

/** Resetting every app op from the App Ops tab stores an image of each op it moves. */
@RunWith(RobolectricTestRunner.class)
public class AppDetailsAppOpImagesTest {
    private static final String PKG = "com.example.app";
    private static final int OP_CAMERA = 26;
    private static final int OP_RECORD_AUDIO = 27;
    private static final String IGNORED = "1";

    private final Map<String, String> mState = new HashMap<>();
    private final List<MutationImage> mStored = new ArrayList<>();

    @Test
    public void aResetStoresTheModeEachSetOpHadAndTheDefaultItGot() throws Exception {
        mState.put(String.valueOf(OP_CAMERA), IGNORED);
        mState.put(String.valueOf(OP_RECORD_AUDIO), IGNORED);

        AppDetailsViewModel.resetAppOpsWithImages(recorder(), PKG, 10, new int[]{OP_CAMERA, OP_RECORD_AUDIO}, () -> {
            mState.put(String.valueOf(OP_CAMERA), defaultOf(OP_CAMERA));
            mState.put(String.valueOf(OP_RECORD_AUDIO), defaultOf(OP_RECORD_AUDIO));
        });

        assertEquals(2, mStored.size());
        int[] ops = {OP_CAMERA, OP_RECORD_AUDIO};
        for (int i = 0; i < ops.length; ++i) {
            MutationImage image = mStored.get(i);
            assertEquals(MutationState.KIND_APP_OP, image.kind);
            assertEquals(String.valueOf(ops[i]), image.target);
            assertEquals(10, image.userId);
            assertEquals(IGNORED, image.beforeValue);
            assertEquals(defaultOf(ops[i]), image.requestedValue);
            assertEquals(defaultOf(ops[i]), image.postValue);
            assertEquals(MutationJournal.RESULT_APPLIED, image.result);
        }
    }

    @Test
    public void aResetThatFailsStoresFailedImagesAndStillThrows() {
        mState.put(String.valueOf(OP_CAMERA), IGNORED);

        assertThrows(RemoteException.class, () -> AppDetailsViewModel.resetAppOpsWithImages(recorder(), PKG, 0,
                new int[]{OP_CAMERA}, () -> {
                    throw new RemoteException("dead");
                }));

        assertEquals(1, mStored.size());
        assertEquals(IGNORED, mStored.get(0).postValue);
        assertEquals(MutationJournal.RESULT_FAILED, mStored.get(0).result);
    }

    @NonNull
    private static String defaultOf(int op) {
        return String.valueOf(AppOpsManagerCompat.opToDefaultMode(op));
    }

    @NonNull
    private MutationRecorder recorder() {
        return new MutationRecorder("exec-1", "single:app_ops_reset", "root",
                (kind, target, pkg, user) -> mState.get(target), mStored::add);
    }
}
