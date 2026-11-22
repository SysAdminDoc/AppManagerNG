// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/**
 * The medium screen-time widget placed its bubbles and their labels with sp margins, so a larger
 * font moved them on its own: at 200% each label sat far below the bubble it names.
 */
@RunWith(RobolectricTestRunner.class)
public class ScreenTimeWidgetLayoutTest {
    private static final int[][] PAIRS = {
            {R.id.app1_time, R.id.app1_label},
            {R.id.app2_time, R.id.app2_label},
            {R.id.app3_time, R.id.app3_label},
    };

    @Test
    public void eachLabelStaysBesideItsBubbleAtAnyFontSize() {
        for (float fontScale : new float[]{1f, 1.3f, 2f}) {
            View widget = layOut(fontScale);
            for (int[] pair : PAIRS) {
                View bubble = widget.findViewById(pair[0]);
                View label = widget.findViewById(pair[1]);
                assertEquals("label centre at font scale " + fontScale, centreY(bubble), centreY(label), 1);
                assertTrue("label overlaps its bubble at font scale " + fontScale,
                        label.getLeft() >= bubble.getRight());
            }
        }
    }

    @Test
    public void theBubblesDoNotMoveWithTheFontSize() {
        View normal = layOut(1f);
        View large = layOut(2f);
        for (int[] pair : PAIRS) {
            View a = normal.findViewById(pair[0]);
            View b = large.findViewById(pair[0]);
            assertEquals(a.getTop(), b.getTop());
            assertEquals(a.getLeft(), b.getLeft());
            assertEquals(a.getHeight(), b.getHeight());
        }
    }

    @NonNull
    private static View layOut(float fontScale) {
        Context app = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        Context context = new ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.AppTheme_V2);
        View widget = LayoutInflater.from(context).inflate(R.layout.app_widget_screen_time, null);
        float density = context.getResources().getDisplayMetrics().density;
        // A medium widget: up to 250 dp wide, over 200 dp tall
        int width = Math.round(220 * density);
        int height = Math.round(240 * density);
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, width, height);
        return widget;
    }

    private static float centreY(@NonNull View view) {
        // Not attached to a window, so add up the tops by hand
        float top = 0;
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            top += v.getTop();
        }
        return top + view.getHeight() / 2f;
    }
}
