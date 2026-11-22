// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.os.Looper;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * A tag name that isn't valid showed a toast and closed the dialog, dropping what was typed.
 */
@RunWith(RobolectricTestRunner.class)
public class AppInfoNewTagDialogTest {
    private ActivityController<AppCompatActivity> mController;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity.class);
        mController.get().setTheme(R.style.AppTheme_V2);
        mController.setup();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
    }

    @Test
    public void anInvalidTagStaysForFixingAndAValidOneIsAdded() {
        List<String> added = new ArrayList<>();
        AlertDialog dialog = AppInfoFragment.newTagDialog(mController.get(), added::add).create();
        dialog.show();
        idle();
        EditText input = dialog.findViewById(android.R.id.input);
        TextInputLayout layout = dialog.findViewById(android.R.id.text1);

        input.setText("Work Apps!");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("Work Apps!", input.getText().toString());
        assertEquals(mController.get().getString(R.string.invalid_tag), String.valueOf(layout.getError()));
        assertEquals(Collections.emptyList(), added);

        input.setText("work");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertFalse(dialog.isShowing());
        assertEquals(Collections.singletonList("work"), added);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
