// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class EditFiltersDialogFragmentTest {
    @Test
    public void booleanHighlightUsesWordBoundaries() {
        String expression = "com.truecaller true";
        SpannableStringBuilder text = new SpannableStringBuilder(expression);

        EditFiltersDialogFragment.highlightExpression(text, 0xFF123456, 0xFF654321);

        ForegroundColorSpan[] spans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        assertEquals(1, spans.length);
        assertEquals(0xFF654321, spans[0].getForegroundColor());
        assertEquals(expression.lastIndexOf("true"), text.getSpanStart(spans[0]));
        assertEquals(expression.length(), text.getSpanEnd(spans[0]));
    }
}
