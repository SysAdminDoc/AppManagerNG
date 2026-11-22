// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuCapabilities.Operation;

@RunWith(RobolectricTestRunner.class)
public class DhizukuStatusTextTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void eachStateListsExactlyWhatTheCapabilityModelRuns() {
        List<DhizukuState> states = new ArrayList<>(Arrays.asList(DhizukuState.values()));
        states.add(null);
        for (int sdk : new int[]{23, 24, 28, 36}) {
            for (DhizukuState state : states) {
                String text = DhizukuStatusText.describe(mContext, state, sdk);
                List<Operation> runnable = DhizukuCapabilities.runnable(state, sdk);
                assertTrue(state + " on " + sdk + ": " + text,
                        text.startsWith(mContext.getString(DhizukuStatusText.headlineRes(state))));
                for (Operation operation : Operation.values()) {
                    assertEquals(state + " on " + sdk + " lists " + operation + ": " + text,
                            runnable.contains(operation), text.contains(mContext.getString(operation.labelRes)));
                }
                // Whatever Dhizuku runs, the rest is named as needing another mode
                assertEquals(state + " on " + sdk + ": " + text, !runnable.isEmpty(),
                        text.contains(mContext.getString(R.string.dhizuku_still_needs_other_mode)));
            }
        }
    }

    @Test
    public void everyStateHasItsOwnHeadline() {
        Set<Integer> headlines = new HashSet<>();
        for (DhizukuState state : DhizukuState.values()) {
            headlines.add(DhizukuStatusText.headlineRes(state));
        }
        headlines.add(DhizukuStatusText.headlineRes(null));
        assertEquals(DhizukuState.values().length + 1, headlines.size());
    }

    @Test
    public void onlyThePermittedStateReadsAsReady() {
        String permitted = DhizukuStatusText.describe(mContext, DhizukuState.PERMITTED, 36);
        for (DhizukuState state : DhizukuState.values()) {
            if (state == DhizukuState.PERMITTED) continue;
            assertNotEquals(DhizukuStatusText.headlineRes(DhizukuState.PERMITTED), DhizukuStatusText.headlineRes(state));
            assertTrue(state.name(), !DhizukuStatusText.describe(mContext, state, 36).contains(
                    mContext.getString(R.string.dhizuku_status_permitted)));
        }
        assertTrue(permitted.contains(mContext.getString(R.string.dhizuku_status_permitted)));
    }
}
