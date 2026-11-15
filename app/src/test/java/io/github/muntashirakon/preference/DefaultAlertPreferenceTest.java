// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * A notice row's text only takes taps when it holds a link. Plain text has to let the tap through to the row, which
 * opens or hides the notice.
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultAlertPreferenceTest {
    private Context mContext;
    private PreferenceViewHolder mHolder;
    private TextView mSummary;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        LinearLayout row = new LinearLayout(mContext);
        TextView title = new TextView(mContext);
        title.setId(android.R.id.title);
        mSummary = new TextView(mContext);
        mSummary.setId(android.R.id.summary);
        row.addView(title);
        row.addView(mSummary);
        mHolder = PreferenceViewHolder.createInstanceForTests(row);
    }

    @Test
    public void plainTextLeavesTheTapToTheRow() {
        bind("This Android version hasn't been tested with this mode yet.");

        assertNull(mSummary.getMovementMethod());
        assertFalse(mSummary.isClickable());
        assertFalse(mSummary.isLongClickable());
    }

    @Test
    public void textWithALinkTakesTapsOnTheLink() {
        bind(linked());

        assertTrue(mSummary.getMovementMethod() instanceof LinkMovementMethod);
    }

    @Test
    public void aRecycledRowWithPlainTextDropsTheLinkHandling() {
        bind(linked());
        bind("This Android version hasn't been tested with this mode yet.");

        assertNull(mSummary.getMovementMethod());
        assertFalse(mSummary.isClickable());
        assertFalse(mSummary.isLongClickable());
    }

    private void bind(@NonNull CharSequence summary) {
        DefaultAlertPreference preference = new DefaultAlertPreference(mContext);
        preference.setTitle("Notice");
        preference.setSummary(summary);
        preference.onBindViewHolder(mHolder);
    }

    @NonNull
    private static CharSequence linked() {
        SpannableString text = new SpannableString("Read the guide for details.");
        text.setSpan(new URLSpan("https://example.com/guide"), 9, 14, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
