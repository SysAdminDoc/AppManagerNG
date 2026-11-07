// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import io.github.muntashirakon.AppManager.R;

/**
 * Pull request #23 read the new profile dialog's crash as this helper's casts. These check the
 * casts against the real dialog layout and Material's own end icon, so a Material update that moves
 * either view shows up here first.
 */
@RunWith(RobolectricTestRunner.class)
public class TextInputLayoutCompatTest {
    private final Context mContext = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            R.style.AppTheme_V2);

    @Test
    public void theNewProfileDialogsNameFieldReachesItsLayout() {
        View dialogView = View.inflate(mContext, R.layout.dialog_new_file, null);
        TextInputEditText name = dialogView.findViewById(R.id.name);

        TextInputLayout layout = TextInputLayoutCompat.fromTextInputEditText(name);

        assertNotNull(layout);
        assertSame(name, layout.getEditText());
        layout.setHelperText("Profile name");
        assertEquals("Profile name", layout.getHelperText().toString());
    }

    @Test
    public void fixEndIconResizesMaterialsEndIconInPlace() {
        // The resize is posted, and a posted runnable only runs once the view is in a window
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        Activity activity = controller.get();
        activity.setTheme(R.style.AppTheme_V2);
        controller.setup();
        TextInputLayout layout = new TextInputLayout(activity);
        TextInputEditText editText = new TextInputEditText(layout.getContext());
        layout.addView(editText);
        layout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        activity.setContentView(layout);
        View endIcon = layout.findViewById(com.google.android.material.R.id.text_input_end_icon);
        assertTrue(endIcon.getParent() instanceof FrameLayout);

        TextInputLayoutCompat.fixEndIcon(layout);
        shadowOf(Looper.getMainLooper()).idle();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) endIcon.getLayoutParams();
        int expectedHeight = layout.getEndIconMinSize() > 0 ? layout.getEndIconMinSize() : editText.getMinimumHeight();
        assertEquals(expectedHeight, params.height);
        assertEquals(Math.max(editText.getMinimumHeight(), expectedHeight), params.width);
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.END, params.gravity);
    }
}
