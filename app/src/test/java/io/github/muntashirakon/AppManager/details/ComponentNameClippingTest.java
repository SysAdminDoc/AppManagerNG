// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import io.github.muntashirakon.AppManager.R;

/**
 * Component rows showed the top of a cut-off second line. A selectable TextView ignores maxLines
 * when it lays out, so the name wrapped inside a view only one line tall.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ComponentNameClippingTest {
    private static final String LONG_NAME =
            "com.example.some.long.package.receiver.SomeVeryLongReceiverNameThatNeverFitsOnOneLineOfThisRow";
    private static final int ROW_WIDTH_PIXELS = 400;

    @NonNull
    private static TextView nameInARow() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        View row = LayoutInflater.from(context)
                .inflate(R.layout.item_app_details_primary, new FrameLayout(context), false);
        TextView name = row.findViewById(R.id.name);
        name.setText(LONG_NAME);
        return name;
    }

    private static void layOut(@NonNull TextView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PIXELS, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void assertEveryLineFits(@NonNull TextView view) {
        Layout layout = view.getLayout();
        assertEquals(layout.getHeight(), view.getMeasuredHeight() - view.getPaddingTop() - view.getPaddingBottom());
    }

    @Test
    public void aCollapsedRowShowsOneWholeLineWithAnEllipsis() {
        TextView name = nameInARow();

        AppDetailsComponentsFragment.bindNameExpansion(name, false);
        layOut(name);

        assertEquals(1, name.getLayout().getLineCount());
        assertTrue(name.getLayout().getEllipsisCount(0) > 0);
        assertEveryLineFits(name);
    }

    @Test
    public void anExpandedRowShowsTheWholeNameAndLetsItBeSelected() {
        TextView name = nameInARow();

        AppDetailsComponentsFragment.bindNameExpansion(name, true);
        layOut(name);

        assertTrue(name.getLayout().getLineCount() > 1);
        assertEveryLineFits(name);
        assertTrue(name.isTextSelectable());
        assertEquals(LONG_NAME, name.getText().toString());
    }

    @Test
    public void aRecycledRowGoesBackToOneLineAndATapReachesTheRow() {
        TextView name = nameInARow();
        AppDetailsComponentsFragment.bindNameExpansion(name, true);
        layOut(name);

        AppDetailsComponentsFragment.bindNameExpansion(name, false);
        layOut(name);

        assertEquals(1, name.getLayout().getLineCount());
        assertEveryLineFits(name);
        assertFalse(name.isTextSelectable());
        assertFalse(name.isClickable());
    }

    @Test
    public void aSelectableNameWithALineCapIsWhatClippedIt() {
        TextView name = nameInARow();
        name.setTextIsSelectable(true);
        name.setMaxLines(1);
        layOut(name);

        assertTrue(name.getLayout().getLineCount() > 1);
        assertTrue(name.getLayout().getHeight()
                > name.getMeasuredHeight() - name.getPaddingTop() - name.getPaddingBottom());
    }
}
