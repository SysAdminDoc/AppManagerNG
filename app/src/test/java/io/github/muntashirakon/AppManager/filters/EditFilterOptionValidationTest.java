// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
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
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.filters.options.TargetSdkOption;
import io.github.muntashirakon.view.TextInputLayoutCompat;

/**
 * A filter value that didn't work showed a toast and closed the dialog, dropping the filter.
 */
@RunWith(RobolectricTestRunner.class)
public class EditFilterOptionValidationTest {
    private ActivityController<AppCompatActivity> mController;
    private final List<String> mCalls = new ArrayList<>();

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
    public void addingWithNoFilterPickedSaysSoAtThePicker() {
        AlertDialog dialog = open(null);

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(dialog.isShowing());
        TextInputLayout picker = dialog.findViewById(R.id.filter_selector_spinner);
        assertEquals(mController.get().getString(R.string.key_name_cannot_be_null), String.valueOf(picker.getError()));
        assertEquals(Collections.emptyList(), mCalls);
    }

    @Test
    public void aValueThatDoesNotWorkStaysForFixing() {
        TargetSdkOption option = new TargetSdkOption();
        option.setKeyValue("eq", "30");
        AlertDialog dialog = open(option);
        TextInputEditText value = dialog.findViewById(R.id.input_string);
        TextInputLayout valueLayout = TextInputLayoutCompat.fromTextInputEditText(value);

        value.setText("thirty");
        value.onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("thirty", value.getText().toString());
        assertEquals(mController.get().getString(R.string.error_evaluating_input), String.valueOf(valueLayout.getError()));
        assertEquals(Collections.emptyList(), mCalls);

        value.setText("33");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertFalse(dialog.isShowing());
        assertEquals(Collections.singletonList("update 4 target_sdk_eq_33"), mCalls);
    }

    @NonNull
    private AlertDialog open(@Nullable FilterOption option) {
        EditFilterOptionFragment fragment = new EditFilterOptionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EditFilterOptionFragment.ARG_OPTION, option);
        args.putInt(EditFilterOptionFragment.ARG_POSITION, 4);
        fragment.setArguments(args);
        fragment.setOnClickDialogButtonInterface(new EditFilterOptionFragment.OnClickDialogButtonInterface() {
            @Override
            public void onDeleteItem(int position, int id) {
                mCalls.add("delete " + position);
            }

            @Override
            public void onUpdateItem(int position, @NonNull FilterOption item) {
                mCalls.add("update " + position + " " + item.type + "_" + item.getKey() + "_" + item.getValue());
            }

            @Override
            public void onAddItem(@NonNull FilterOption item) {
                mCalls.add("add " + item.type);
            }
        });
        fragment.showNow(mController.get().getSupportFragmentManager(), EditFilterOptionFragment.TAG);
        idle();
        AlertDialog dialog = (AlertDialog) fragment.getDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
