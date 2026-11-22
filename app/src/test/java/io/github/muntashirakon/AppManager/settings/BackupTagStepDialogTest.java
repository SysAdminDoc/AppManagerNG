// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
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
 * The backup tag policy's first step showed a toast and closed on a tag that doesn't work, so the
 * tag had to be typed again from the start.
 */
@RunWith(RobolectricTestRunner.class)
public class BackupTagStepDialogTest {
    private ActivityController<AppCompatActivity> mController;
    private final List<String> mTags = new ArrayList<>();

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
    public void aTagThatDoesNotWorkStaysForFixing() {
        AlertDialog dialog = show("");
        EditText input = dialog.findViewById(android.R.id.input);

        input.setText("Work Apps!");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("Work Apps!", input.getText().toString());
        TextInputLayout layout = dialog.findViewById(android.R.id.text1);
        assertEquals(mController.get().getString(R.string.invalid_tag), String.valueOf(layout.getError()));
        assertEquals(Collections.emptyList(), mTags);
    }

    @Test
    public void aGoodTagMovesOnOnceInItsStoredForm() {
        AlertDialog dialog = show("");
        EditText input = dialog.findViewById(android.R.id.input);

        input.setText("  Work-Apps ");
        input.onEditorAction(EditorInfo.IME_ACTION_DONE);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertFalse(dialog.isShowing());
        assertEquals(Collections.singletonList("work-apps"), mTags);
    }

    @Test
    public void theTagIsOneLine() {
        EditText input = show("work").findViewById(android.R.id.input);

        assertEquals(0, input.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        assertEquals("work", input.getText().toString());
    }

    @NonNull
    private AlertDialog show(@NonNull String tag) {
        AlertDialog dialog = BackupTagPoliciesPreferences.tagStepDialog(mController.get(), false, tag, mTags::add)
                .create();
        dialog.show();
        idle();
        return dialog;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
