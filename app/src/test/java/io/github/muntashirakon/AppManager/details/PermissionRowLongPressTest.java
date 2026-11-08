// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;

/**
 * Every line of text in a permission or app op row can be selected, so a long press on the text picked a word and
 * the row's menu (watch, flags, mode picker) only opened from the switch or the padding.
 */
@RunWith(RobolectricTestRunner.class)
public class PermissionRowLongPressTest {
    private final AtomicInteger mMenusOpened = new AtomicInteger();

    @NonNull
    private ViewGroup row(@LayoutRes int layout) {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme_V2);
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        ViewGroup row = (ViewGroup) activity.getLayoutInflater().inflate(layout, parent, false);
        parent.addView(row);
        row.setOnLongClickListener(v -> {
            mMenusOpened.incrementAndGet();
            return true;
        });
        return row;
    }

    @NonNull
    private static List<TextView> selectableTexts(@NonNull ViewGroup group, @NonNull List<TextView> found) {
        for (int i = 0; i < group.getChildCount(); ++i) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                selectableTexts((ViewGroup) child, found);
            } else if (child instanceof TextView && ((TextView) child).isTextSelectable()) {
                ((TextView) child).setText("android.permission.CAMERA");
                found.add((TextView) child);
            }
        }
        return found;
    }

    private void assertTheTextOpensTheRowsMenu(@LayoutRes int layout) {
        ViewGroup row = row(layout);
        List<TextView> texts = selectableTexts(row, new ArrayList<>());
        assertTrue(texts.size() >= 5);
        for (TextView text : texts) {
            text.performLongClick();
        }
        assertEquals("the text kept the long press before", 0, mMenusOpened.get());

        AppDetailsAdapterUtils.passLongPressToRow(row, true);

        for (TextView text : texts) {
            assertTrue(text.performLongClick());
        }
        assertEquals(texts.size(), mMenusOpened.get());
    }

    @Test
    public void aLongPressOnAPermissionsTextOpensTheRowsMenu() {
        assertTheTextOpensTheRowsMenu(R.layout.item_app_details_perm);
    }

    @Test
    public void aLongPressOnAnAppOpsTextOpensTheRowsMenu() {
        assertTheTextOpensTheRowsMenu(R.layout.item_app_details_appop);
    }

    @Test
    public void aRowWithNothingToShowLeavesItsTextSelectingAsBefore() {
        ViewGroup row = row(R.layout.item_app_details_perm);
        List<TextView> texts = selectableTexts(row, new ArrayList<>());
        AppDetailsAdapterUtils.passLongPressToRow(row, true);

        AppDetailsAdapterUtils.passLongPressToRow(row, false);

        for (TextView text : texts) {
            text.performLongClick();
            assertTrue(text.isTextSelectable());
        }
        assertEquals(0, mMenusOpened.get());
        assertFalse(texts.isEmpty());
    }
}
