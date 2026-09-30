// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/**
 * The preview used AlertDialog's message and item list together, and AppCompat drops the list when a
 * message is set, so the sections never showed; its handler for a missing section also cast the
 * AppCompat dialog to android.app.AlertDialog. The sections now live in the dialog's own view.
 */
@RunWith(RobolectricTestRunner.class)
public class SnapshotImportPreviewDialogTest {
    private static final String[] LABELS = {"Preferences", "Profiles", "Rules"};

    @Test
    public void theDialogShowsTheSummaryAndEverySection() {
        boolean[] available = {true, false, true};
        boolean[] checked = available.clone();
        ScrollView view = showInDialog(available, checked);
        LinearLayout column = (LinearLayout) view.getChildAt(0);

        assertTrue(view.isShown());
        assertEquals("Created from a test", ((TextView) column.getChildAt(0)).getText().toString());
        for (int i = 0; i < LABELS.length; ++i) {
            MaterialCheckBox box = (MaterialCheckBox) column.getChildAt(i + 1);
            assertEquals(LABELS[i], box.getText().toString());
            assertTrue(box.isShown());
        }
    }

    @Test
    public void tappingAMissingSectionLeavesItUncheckedAndOthersStillToggle() {
        boolean[] available = {true, false, true};
        boolean[] checked = available.clone();
        LinearLayout column = (LinearLayout) showInDialog(available, checked).getChildAt(0);
        MaterialCheckBox preferences = (MaterialCheckBox) column.getChildAt(1);
        MaterialCheckBox profiles = (MaterialCheckBox) column.getChildAt(2);

        assertFalse(profiles.isEnabled());
        // performClick() toggles even a disabled box, the worst case a tap could reach.
        profiles.performClick();
        assertFalse(profiles.isChecked());
        assertFalse(checked[1]);

        preferences.performClick();
        assertFalse(preferences.isChecked());
        assertFalse(checked[0]);
        preferences.performClick();
        assertTrue(checked[0]);
        assertTrue(checked[2]);
    }

    private static ScrollView showInDialog(boolean[] available, boolean[] checked) {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme_V2);
        ScrollView view = PrivacyPreferences.buildImportPreviewView(activity, "Created from a test", LABELS,
                available, checked);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.snapshot_import_preview_title)
                .setView(view)
                .setPositiveButton(R.string.action_import, null)
                .show();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(dialog.isShowing());
        return view;
    }
}
