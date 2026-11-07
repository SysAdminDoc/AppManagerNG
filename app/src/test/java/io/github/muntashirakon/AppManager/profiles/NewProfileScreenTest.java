// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * A new profile opens on its app list, and that list's empty state is a whole layout rather than a
 * lone text view, so reading it as one took the screen down the moment the profile opened.
 */
@RunWith(RobolectricTestRunner.class)
public class NewProfileScreenTest {
    private ActivityController<AppsProfileActivity> mController;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Ops.setAuthenticated(context, true);
        mController = Robolectric.buildActivity(AppsProfileActivity.class,
                AppsProfileActivity.getNewProfileIntent(context, "Untitled profile"));
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    public void theAppListOfANewProfileOpensAndSaysItHasNoApps() {
        AppsProfileActivity activity = mController.setup().get();
        AppsFragment fragment = new AppsFragment();

        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View emptyView = fragment.requireView().findViewById(android.R.id.empty);
        assertNotNull(emptyView);
        TextView title = emptyView.findViewById(R.id.empty_state_title);
        assertEquals(activity.getString(R.string.no_apps), title.getText().toString());
        assertEquals(View.GONE, emptyView.findViewById(R.id.empty_state_summary).getVisibility());
        assertEquals(View.GONE, emptyView.findViewById(R.id.empty_state_action).getVisibility());
    }
}
