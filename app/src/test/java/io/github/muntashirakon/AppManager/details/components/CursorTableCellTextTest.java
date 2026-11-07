// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;

import io.github.muntashirakon.AppManager.R;

/**
 * Cells can be selected, so a line cap on them cut the next line in half instead of ending the text.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CursorTableCellTextTest {
    @Test
    public void aLongCellShowsEveryLine() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        CursorTableAdapter adapter = new CursorTableAdapter();
        CursorTableAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 40; ++i) {
            value.append("value").append(i).append(' ');
        }

        holder.bind(Collections.singletonList(value.toString()), false);

        TextView cell = (TextView) ((LinearLayoutCompat) holder.itemView).getChildAt(0);
        cell.measure(View.MeasureSpec.makeMeasureSpec(cell.getLayoutParams().width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        assertTrue(cell.getLayout().getLineCount() > 3);
        assertEquals(cell.getLayout().getHeight(),
                cell.getMeasuredHeight() - cell.getPaddingTop() - cell.getPaddingBottom());
    }
}
