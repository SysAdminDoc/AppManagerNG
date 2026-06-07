// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class OneClickOpsActivityTest {
    @Test
    public void buildLeftoverShareIntentUsesTsvMimeSubjectAndEscapedBody() {
        OneClickOpsViewModel.LeftoverEntry entry = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(new File(" \t=cmd\npayload"),
                        " +pkg", LeftoverScanner.KIND_MEDIA),
                7L);

        Intent intent = OneClickOpsActivity.buildLeftoverShareIntent(
                Collections.singletonList(entry), "Leftover folders");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/tab-separated-values", intent.getType());
        assertEquals("Leftover folders", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        String body = intent.getStringExtra(Intent.EXTRA_TEXT);
        assertTrue(body.contains("' +pkg\tmedia\t7\t'  =cmd payload"));
        assertFalse(body.contains("\t=cmd"));
    }

    @Test
    public void buildLeftoverShareIntentRejectsEmptyExport() {
        assertThrows(IllegalArgumentException.class,
                () -> OneClickOpsActivity.buildLeftoverShareIntent(Collections.emptyList(), "Leftover folders"));
    }
}
