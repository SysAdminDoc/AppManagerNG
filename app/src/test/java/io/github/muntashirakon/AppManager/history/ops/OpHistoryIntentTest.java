// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class OpHistoryIntentTest {
    @Test
    public void filteredHistoryIntentNormalizesTypeAndStatus() {
        Context context = RuntimeEnvironment.getApplication();

        Intent intent = OpHistoryManager.getHistoryActivityIntent(context,
                OpHistoryManager.HISTORY_TYPE_INSTALLER, OpHistoryManager.STATUS_SUCCESS);

        assertEquals(OpHistoryManager.HISTORY_TYPE_INSTALLER,
                intent.getStringExtra(OpHistoryManager.EXTRA_FILTER_TYPE));
        assertEquals(OpHistoryManager.STATUS_SUCCESS,
                intent.getStringExtra(OpHistoryManager.EXTRA_FILTER_STATUS));
    }

    @Test
    public void filteredHistoryIntentFallsBackToFailureAndUnknown() {
        Context context = RuntimeEnvironment.getApplication();

        Intent intent = OpHistoryManager.getHistoryActivityIntent(context, "bad-type", "bad-status");

        assertEquals(OpHistoryManager.HISTORY_TYPE_UNKNOWN,
                intent.getStringExtra(OpHistoryManager.EXTRA_FILTER_TYPE));
        assertEquals(OpHistoryManager.STATUS_FAILURE,
                intent.getStringExtra(OpHistoryManager.EXTRA_FILTER_STATUS));
    }
}
