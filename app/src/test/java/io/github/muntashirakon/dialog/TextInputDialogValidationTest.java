// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * The positive button closed a text-input dialog whatever its listener decided, so a name that
 * failed a check showed a toast and threw away what was typed.
 */
@RunWith(RobolectricTestRunner.class)
public class TextInputDialogValidationTest {
    private static final String ERROR = "Not that one";

    private ActivityController<AppCompatActivity> mController;
    private final List<String> mChecked = new ArrayList<>();

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
    public void aRejectedValueKeepsTheDialogTheTextAndTheFocus() {
        AlertDialog dialog = show();
        type(dialog, "bad");

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("bad", input(dialog).getText().toString());
        assertEquals(ERROR, String.valueOf(layout(dialog).getError()));
        assertTrue(input(dialog).isFocused());
        assertEquals(Arrays.asList("bad"), mChecked);
    }

    @Test
    public void anAcceptedValueClosesTheDialogOnce() {
        AlertDialog dialog = show();
        type(dialog, "good");

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        // A second tap before the dialog is gone, then the keyboard's Done
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        input(dialog).onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();

        assertFalse(dialog.isShowing());
        assertEquals(Arrays.asList("good"), mChecked);
    }

    @Test
    public void theKeyboardsDoneGoesThroughTheSameCheck() {
        AlertDialog dialog = show();
        type(dialog, "bad");

        input(dialog).onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();
        assertTrue(dialog.isShowing());
        assertEquals(ERROR, String.valueOf(layout(dialog).getError()));

        type(dialog, "good");
        input(dialog).onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();
        assertFalse(dialog.isShowing());
        assertEquals(Arrays.asList("bad", "good"), mChecked);
    }

    @Test
    public void anOlderAndroidsHardwareEnterSubmitsOncePerPress() {
        // Before Android reported a hardware Enter as the field's action, it came as IME_NULL
        List<String> runs = new ArrayList<>();
        EditText field = new EditText(mController.get());
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        TextView.OnEditorActionListener listener = TextInputDialogBuilder.submitOnEnter(() -> runs.add("submit"));

        assertTrue(listener.onEditorAction(field, EditorInfo.IME_NULL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertTrue(listener.onEditorAction(field, EditorInfo.IME_NULL, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)));
        assertTrue(listener.onEditorAction(field, EditorInfo.IME_NULL,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_NUMPAD_ENTER)));
        assertFalse(listener.onEditorAction(field, EditorInfo.IME_NULL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)));

        assertEquals(Arrays.asList("submit", "submit"), runs);
    }

    @Test
    public void enterInAFieldOfSeveralLinesStillStartsANewLine() {
        List<String> runs = new ArrayList<>();
        EditText field = new EditText(mController.get());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setOnEditorActionListener(TextInputDialogBuilder.submitOnEnter(() -> runs.add("submit")));
        field.setText("a");
        field.setSelection(1);

        field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        idle();

        assertEquals("a\n", field.getText().toString());
        assertEquals(Collections.emptyList(), runs);
    }

    @Test
    public void aHardwareEnterGoesThroughTheSameCheck() {
        AlertDialog dialog = show();
        type(dialog, "bad");

        input(dialog).dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        input(dialog).dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("bad", input(dialog).getText().toString());
        assertEquals(Arrays.asList("bad"), mChecked);
    }

    @Test
    public void typingClearsTheError() {
        AlertDialog dialog = show();
        type(dialog, "bad");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(ERROR, String.valueOf(layout(dialog).getError()));

        input(dialog).append("!");

        assertNull(layout(dialog).getError());
    }

    @NonNull
    private AlertDialog show() {
        AlertDialog dialog = new TextInputDialogBuilder(mController.get(), "Name")
                .setInputInputType(InputType.TYPE_CLASS_TEXT)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE)
                .setValidatedPositiveButton(R.string.ok, (d, which, inputText, isChecked) -> {
                    String text = String.valueOf(inputText);
                    mChecked.add(text);
                    return "good".equals(text) ? null : ERROR;
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        idle();
        return dialog;
    }

    private static void type(@NonNull AlertDialog dialog, @NonNull String text) {
        input(dialog).setText(text);
    }

    @NonNull
    private static EditText input(@NonNull AlertDialog dialog) {
        return dialog.findViewById(android.R.id.input);
    }

    @NonNull
    private static TextInputLayout layout(@NonNull AlertDialog dialog) {
        return dialog.findViewById(android.R.id.text1);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
