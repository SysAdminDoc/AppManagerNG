// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Looper;
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
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;

/**
 * A log file name that can't be used showed a toast and closed the dialog, dropping what was typed.
 */
@RunWith(RobolectricTestRunner.class)
public class LogFilenameDialogTest {
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
    public void saveLogKeepsABadNameForFixing() {
        List<String> saved = new ArrayList<>();
        AlertDialog dialog = AbsLogViewerFragment.saveLogDialog(mController.get(), saved::add).create();
        dialog.show();
        idle();
        EditText input = dialog.findViewById(android.R.id.input);

        input.setText("my log.txt");
        input.onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();

        assertTrue(dialog.isShowing());
        assertEquals("my log.txt", input.getText().toString());
        assertEquals(goodFilename(), errorOf(dialog));
        assertEquals(Collections.emptyList(), saved);

        input.setText("mine.log");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertFalse(dialog.isShowing());
        assertEquals(Collections.singletonList("mine.log"), saved);
    }

    @Test
    public void recordLogKeepsABadNameForFixing() {
        RecordLogDialogFragment fragment = RecordLogDialogFragment.getInstance(null, null);
        fragment.showNow(mController.get().getSupportFragmentManager(), RecordLogDialogFragment.TAG);
        idle();
        AlertDialog dialog = (AlertDialog) fragment.getDialog();
        assertNotNull(dialog);
        EditText input = dialog.findViewById(android.R.id.input);

        input.setText("record/me.log");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(dialog.isShowing());
        assertTrue(fragment.isAdded());
        assertEquals("record/me.log", input.getText().toString());
        assertEquals(goodFilename(), errorOf(dialog));
    }

    @Test
    public void recordLogTakesAGoodNameOnceAndGoesAway() throws InterruptedException {
        RecordLogDialogFragment fragment = RecordLogDialogFragment.getInstance(null, null);
        List<String> dismissed = new ArrayList<>();
        fragment.setOnDismissListener(d -> dismissed.add("dismissed"));
        fragment.showNow(mController.get().getSupportFragmentManager(), RecordLogDialogFragment.TAG);
        idle();
        AlertDialog dialog = (AlertDialog) fragment.getDialog();
        assertNotNull(dialog);
        EditText input = dialog.findViewById(android.R.id.input);

        input.setText("mine.log");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        // A second tap and the keyboard's Done before it's gone
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        input.onEditorAction(EditorInfo.IME_ACTION_DONE);
        idle();

        assertFalse(dialog.isShowing());
        assertFalse(fragment.isAdded());
        assertEquals(Collections.singletonList("dismissed"), dismissed);
        // The recording starts once, from a background lookup; wait for it so it can't land in another test
        Intent started = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (started == null && System.nanoTime() < deadline) {
            Thread.sleep(20);
            idle();
            started = shadowOf(mController.get().getApplication()).getNextStartedService();
        }
        assertNotNull(started);
        assertEquals("mine.log", started.getStringExtra(LogcatRecordingService.EXTRA_FILENAME));
        assertNull(shadowOf(mController.get().getApplication()).getNextStartedService());
    }

    private String goodFilename() {
        return mController.get().getString(R.string.enter_good_filename);
    }

    @NonNull
    private static String errorOf(@NonNull AlertDialog dialog) {
        TextInputLayout layout = dialog.findViewById(android.R.id.text1);
        return String.valueOf(layout.getError());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
