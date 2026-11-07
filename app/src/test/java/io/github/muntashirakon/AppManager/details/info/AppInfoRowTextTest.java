// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;

/**
 * App Info rows hold paths and other long values in a selectable subtitle, and the layout they share
 * caps the subtitle at two lines, which cut the third line in half.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppInfoRowTextTest {
    private static final String LONG_PATH = "/data/app/~~someLongRandomSegmentAbcDefGhi==/io.example.app-anotherRandomSegment"
            + "==/lib/arm64/some/more/deeply/nested/folders/that/keep/going/libnative-with-a-long-name.so";

    @NonNull
    private static AppInfoRecyclerAdapter.ViewHolder bound(@NonNull ListItem... items) {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        AppInfoRecyclerAdapter adapter = new AppInfoRecyclerAdapter(context);
        adapter.setAdapterList(Arrays.asList(items));
        AppInfoRecyclerAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), items[0].type);
        adapter.onBindViewHolder(holder, 0);
        holder.subtitle.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        holder.subtitle.layout(0, 0, holder.subtitle.getMeasuredWidth(), holder.subtitle.getMeasuredHeight());
        return holder;
    }

    private static void assertWholeValueShows(@NonNull AppInfoRecyclerAdapter.ViewHolder holder) {
        assertTrue(holder.subtitle.getLayout().getLineCount() > 2);
        assertEquals(holder.subtitle.getLayout().getHeight(), holder.subtitle.getMeasuredHeight()
                - holder.subtitle.getPaddingTop() - holder.subtitle.getPaddingBottom());
    }

    @Test
    public void aLongSelectableValueShowsEveryLine() {
        assertWholeValueShows(bound(ListItem.newSelectableRegularItem("Path", LONG_PATH)));
    }

    @Test
    public void aLongPlainValueShowsEveryLine() {
        assertWholeValueShows(bound(ListItem.newRegularItem("Path", LONG_PATH)));
    }
}
