// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class OpHistoryActivityTest {
    @Test
    public void buildHistoryShareIntentUsesPlainTextSubjectAndBody() {
        Intent intent = OpHistoryActivity.buildHistoryShareIntent("Operation history", "body\nline");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("Operation history", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals("body\nline", intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void buildHistoryShareIntentRejectsEmptyBody() {
        assertThrows(IllegalArgumentException.class,
                () -> OpHistoryActivity.buildHistoryShareIntent("Operation history", ""));
    }
}
