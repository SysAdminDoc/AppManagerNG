// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.history.ops.MutationJournal;
import io.github.muntashirakon.AppManager.history.ops.MutationRecorder;
import io.github.muntashirakon.AppManager.history.ops.MutationState;

/** Saving a network policy in App Info stores the policy before and after, so it can be put back. */
@RunWith(RobolectricTestRunner.class)
public class AppInfoNetPolicyImageTest {
    private static final String PKG = "com.example.app";

    @Test
    public void aSavedPolicyAndARefusedOneBothLeaveAnImage() {
        Map<String, String> state = new HashMap<>();
        state.put(MutationState.KIND_NET_POLICY, "0");
        List<MutationImage> stored = new ArrayList<>();
        MutationRecorder recorder = new MutationRecorder("exec-1", "single:net_policy", "root",
                (kind, target, pkg, user) -> state.get(kind), stored::add);

        AppInfoFragment.setNetPolicyWithImage(recorder, PKG, 10, 1,
                () -> state.put(MutationState.KIND_NET_POLICY, "1"));
        assertThrows(SecurityException.class, () -> AppInfoFragment.setNetPolicyWithImage(recorder, PKG, 10, 4,
                () -> {
                    throw new SecurityException("refused");
                }));

        assertEquals(2, stored.size());
        MutationImage saved = stored.get(0);
        assertEquals(MutationState.KIND_NET_POLICY, saved.kind);
        assertEquals("", saved.target);
        assertEquals(PKG, saved.packageName);
        assertEquals(10, saved.userId);
        assertEquals("0", saved.beforeValue);
        assertEquals("1", saved.requestedValue);
        assertEquals("1", saved.postValue);
        assertEquals(MutationJournal.RESULT_APPLIED, saved.result);
        MutationImage refused = stored.get(1);
        assertEquals("1", refused.beforeValue);
        assertEquals("4", refused.requestedValue);
        assertEquals("1", refused.postValue);
        assertEquals(MutationJournal.RESULT_FAILED, refused.result);
    }
}
