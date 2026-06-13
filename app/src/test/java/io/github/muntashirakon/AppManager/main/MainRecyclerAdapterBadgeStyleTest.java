// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class MainRecyclerAdapterBadgeStyleTest {
    @Test
    public void trackerBadgeTextUsesSemanticContentColors() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(color(context, R.color.premium_success_content),
                MainRecyclerAdapter.getTrackerBadgeTextColor(context, true, 30));
        assertEquals(color(context, R.color.premium_success_content),
                MainRecyclerAdapter.getTrackerBadgeTextColor(context, false, 4));
        assertEquals(color(context, R.color.premium_warning_content),
                MainRecyclerAdapter.getTrackerBadgeTextColor(context, false, 5));
        assertEquals(color(context, R.color.premium_danger_content),
                MainRecyclerAdapter.getTrackerBadgeTextColor(context, false, 20));
    }

    @Test
    public void permissionBadgeTextUsesSemanticContentColors() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(color(context, R.color.premium_success_content),
                MainRecyclerAdapter.getPermissionBadgeTextColor(context, 0, 8));
        assertEquals(color(context, R.color.premium_success_content),
                MainRecyclerAdapter.getPermissionBadgeTextColor(context, 1, 8));
        assertEquals(color(context, R.color.premium_warning_content),
                MainRecyclerAdapter.getPermissionBadgeTextColor(context, 2, 8));
        assertEquals(color(context, R.color.premium_danger_content),
                MainRecyclerAdapter.getPermissionBadgeTextColor(context, 5, 12));
    }

    @Test
    public void backupBadgeTextUsesSemanticContentColors() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(color(context, R.color.premium_success_content),
                MainRecyclerAdapter.getBackupBadgeTextColor(context, true, true));
        assertEquals(color(context, R.color.premium_warning_content),
                MainRecyclerAdapter.getBackupBadgeTextColor(context, true, false));
        assertEquals(color(context, R.color.premium_danger_content),
                MainRecyclerAdapter.getBackupBadgeTextColor(context, false, false));
    }

    @Test
    public void expandTouchRectToMinimumGrowsSmallBadgeSymmetrically() {
        Rect rect = new Rect(10, 20, 34, 44);

        MainRecyclerAdapter.expandTouchRectToMinimum(rect, 48);

        assertEquals(new Rect(-2, 8, 46, 56), rect);
    }

    @Test
    public void expandTouchRectToMinimumLeavesLargeRectUnchanged() {
        Rect rect = new Rect(10, 20, 80, 90);

        MainRecyclerAdapter.expandTouchRectToMinimum(rect, 48);

        assertEquals(new Rect(10, 20, 80, 90), rect);
    }

    @Test
    public void getTouchDelegateBoundsUsesTargetLocalCoordinates() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout itemView = new FrameLayout(context);
        LinearLayout badgeRow = new LinearLayout(context);
        TextView badge = new TextView(context);
        itemView.addView(badgeRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        badgeRow.addView(badge, new LinearLayout.LayoutParams(24, 24));

        itemView.layout(0, 0, 300, 120);
        badgeRow.layout(50, 20, 250, 80);
        badge.layout(10, 5, 34, 29);

        Rect bounds = MainRecyclerAdapter.getTouchDelegateBounds(itemView, badge, 48);

        assertEquals(new Rect(48, 13, 96, 61), bounds);
    }

    private static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }
}
