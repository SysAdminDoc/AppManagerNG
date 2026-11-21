// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import io.github.muntashirakon.AppManager.R;

/**
 * #57: switches in rows with only a title (Use VirusTotal, Display "Files" in app drawer) were
 * cut flat at the top and bottom. The widget frame matched a row that was measured from its text
 * alone, so a one-line title left less height than the switch needs.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreferenceSwitchRowTest {
    private static final int ROW_WIDTH_PIXELS = 720;

    private static void assertSwitchFits(@LayoutRes int rowLayout, boolean withSummary) {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        LayoutInflater inflater = LayoutInflater.from(context);
        View row = inflater.inflate(rowLayout, new FrameLayout(context), false);
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        title.setText("Use VirusTotal");
        if (withSummary) {
            summary.setText("Remove annotation blocks from decoded smali.");
            summary.setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
        }
        ViewGroup widgetFrame = row.findViewById(android.R.id.widget_frame);
        View switchView = inflater.inflate(io.github.muntashirakon.ui.R.layout.m3_preference_widget_material_switch,
                widgetFrame, false);
        widgetFrame.addView(switchView);

        // What the switch asks for when nothing squeezes it
        switchView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int naturalHeight = switchView.getMeasuredHeight();
        assertTrue(naturalHeight > 0);

        row.measure(View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PIXELS, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());

        assertTrue("switch is " + switchView.getHeight() + "px tall but needs " + naturalHeight + "px",
                switchView.getHeight() >= naturalHeight);
        assertTrue("switch sticks out of its frame",
                switchView.getTop() >= 0 && switchView.getBottom() <= widgetFrame.getHeight());
        int[] frameInRow = new int[2];
        offsetIn(row, widgetFrame, frameInRow);
        assertTrue("the frame sticks out of the row",
                frameInRow[1] >= 0 && frameInRow[1] + widgetFrame.getHeight() <= row.getHeight());
    }

    private static void offsetIn(@NonNull View ancestor, @NonNull View view, @NonNull int[] out) {
        int y = 0;
        View current = view;
        while (current != ancestor) {
            y += current.getTop();
            current = (View) current.getParent();
        }
        out[0] = 0;
        out[1] = y;
    }

    @Test
    public void aTitleOnlyRowShowsTheWholeSwitch() {
        assertSwitchFits(io.github.muntashirakon.ui.R.layout.m3_preference, false);
    }

    @Test
    public void aRowWithASummaryStillShowsTheWholeSwitch() {
        assertSwitchFits(io.github.muntashirakon.ui.R.layout.m3_preference, true);
    }

    @Test
    public void theTopSwitchRowShowsTheWholeSwitch() {
        assertSwitchFits(io.github.muntashirakon.ui.R.layout.m3_preference_top_switch, false);
    }

    @Test
    public void anAlertRowWithAWidgetShowsAllOfIt() {
        assertSwitchFits(io.github.muntashirakon.ui.R.layout.m3_preference_alert, false);
    }
}
