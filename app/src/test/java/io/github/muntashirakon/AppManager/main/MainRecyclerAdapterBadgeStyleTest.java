// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;

import android.content.Context;

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

    private static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }
}
