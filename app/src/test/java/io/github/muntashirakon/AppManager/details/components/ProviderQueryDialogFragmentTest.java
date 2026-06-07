// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class ProviderQueryDialogFragmentTest {
    @Test
    public void formatExportSubjectPartNormalizesControlTextAndDefusesFormula() {
        assertEquals("' =provider name",
                ProviderQueryDialogFragment.formatExportSubjectPart("\t=provider\nname", "provider"));
        assertEquals("provider", ProviderQueryDialogFragment.formatExportSubjectPart("\n\t", "provider"));
    }

    @Test
    public void buildExportShareIntentUsesTsvMimeSubjectAndEscapedBody() {
        ProviderQueryUtils.QueryResult result = new ProviderQueryUtils.QueryResult(
                Uri.parse("content://settings/system"),
                new String[]{"name"},
                Collections.singletonList(Collections.singletonList("\t=cmd\npayload")),
                false,
                1);

        Intent intent = ProviderQueryDialogFragment.buildExportShareIntent(result, "Provider query");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/tab-separated-values", intent.getType());
        assertEquals("Provider query", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals("name\n' =cmd payload\n", intent.getStringExtra(Intent.EXTRA_TEXT));
        assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT).contains("\t"));
    }
}
