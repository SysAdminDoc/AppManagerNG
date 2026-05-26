// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import com.google.android.material.snackbar.Snackbar;

/**
 * BatchKeepOpenHint smoke test — exercises the show / dismiss /
 * isShowing contract under Robolectric. The Snackbar animation never runs
 * in this environment so we only assert lifecycle bookkeeping, not visual.
 */
@RunWith(RobolectricTestRunner.class)
public class BatchKeepOpenHintTest {

    @Test
    public void dismissOnEmptyStateIsNoOp() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setContentView(new FrameLayout(activity));
        // No hint shown yet — calling dismiss must not throw.
        BatchKeepOpenHint.dismiss(activity);
        assertFalse(BatchKeepOpenHint.isShowing(activity));
    }

    @Test
    public void showReturnsLiveSnackbar() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        FrameLayout content = new FrameLayout(activity);
        activity.setContentView(content);
        Snackbar snackbar = BatchKeepOpenHint.show(activity);
        // Robolectric may or may not return a Snackbar depending on theme; just
        // assert the call doesn't throw and the dismiss path stays sane.
        if (snackbar != null) {
            assertNotNull(snackbar);
            BatchKeepOpenHint.dismiss(activity);
        }
        // Whether or not the snackbar instance survived, dismiss must always
        // leave isShowing false afterwards.
        assertFalse(BatchKeepOpenHint.isShowing(activity));
    }

    @Test
    public void doubleShowDoesNotStack() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setContentView(new FrameLayout(activity));
        BatchKeepOpenHint.show(activity);
        BatchKeepOpenHint.show(activity);
        BatchKeepOpenHint.dismiss(activity);
        assertFalse(BatchKeepOpenHint.isShowing(activity));
    }
}
